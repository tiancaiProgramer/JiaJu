// JiaJu's private pipe bridge. No HTTP listener, command line credentials or saved media.
package main

import (
	"bufio"
	"crypto/rand"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"golang.org/x/crypto/chacha20"
	"golang.org/x/crypto/nacl/box"
	"jiaju.camera/internal/cs2"
	"net"
	"os"
	"sync"
	"sync/atomic"
	"time"
)

type config struct {
	Host          string `json:"host"`
	Model         string `json:"model"`
	Vendor        int    `json:"vendor"`
	DevicePublic  string `json:"devicePublic"`
	Sign          string `json:"sign"`
	Quality       int    `json:"quality"`
	P2PID         string `json:"p2pId"`
	InitString    string `json:"initString"`
	TryLocal      *bool  `json:"tryLocal"`
	PlaybackStart int64  `json:"playbackStart"`
	PlaybackEnd   int64  `json:"playbackEnd"`
}

var output sync.Mutex
var commandReplies atomic.Uint64

func emit(kind byte, timestamp uint64, payload []byte) bool {
	output.Lock()
	defer output.Unlock()
	if len(payload) > 4*1024*1024 {
		return false
	}
	var head [13]byte
	head[0] = kind
	binary.BigEndian.PutUint32(head[1:], uint32(len(payload)))
	binary.BigEndian.PutUint64(head[5:], timestamp)
	if _, err := os.Stdout.Write(head[:]); err != nil {
		return false
	}
	_, err := os.Stdout.Write(payload)
	return err == nil
}
func event(name, code string) {
	b, _ := json.Marshal(map[string]string{"event": name, "code": code})
	emit(1, 0, b)
}
func cipher(data, key, nonce []byte) ([]byte, error) {
	iv := make([]byte, 12)
	copy(iv[4:], nonce)
	c, err := chacha20.NewUnauthenticatedCipher(key, iv)
	if err != nil {
		return nil, err
	}
	out := make([]byte, len(data))
	c.XORKeyStream(out, data)
	return out, nil
}
func send(conn *cs2.Conn, key []byte, cmd uint32, args []byte) error {
	plain := binary.BigEndian.AppendUint32(nil, cmd)
	plain = append(plain, args...)
	nonce := make([]byte, 8)
	if _, err := rand.Read(nonce); err != nil {
		return err
	}
	body, err := cipher(plain, key, nonce)
	if err != nil {
		return err
	}
	return conn.WriteCommand(0x1001, append(nonce, body...))
}
func emitMetrics(conn *cs2.Conn) {
	data, _ := json.Marshal(map[string]interface{}{"event": "metrics", "relayRenewals": conn.SessionRenewals(), "commandReplies": commandReplies.Load(), "mediaPackets": conn.MediaPackets(), "handoffAttempts": conn.MediaHandoffAttempts()})
	emit(1, 0, data)
}
func supportedCameraModel(model string) bool {
	return model == "chuangmi.camera.069a01" || model == "chuangmi.camera.039c01" || model == "xiaomi.camera.c302"
}
func main() {
	public, private, err := box.GenerateKey(rand.Reader)
	if err != nil {
		event("error", "key_failed")
		return
	}
	hello, _ := json.Marshal(map[string]string{"event": "key", "public": hex.EncodeToString(public[:])})
	if !emit(1, 0, hello) {
		return
	}
	input := bufio.NewReaderSize(os.Stdin, 16384)
	line, err := input.ReadSlice('\n')
	if err != nil {
		event("error", "config_missing")
		return
	}
	var cfg config
	if json.Unmarshal(line, &cfg) != nil || !supportedCameraModel(cfg.Model) || cfg.Vendor != 4 || len(cfg.Sign) != 64 {
		event("error", "unsupported_camera")
		return
	}
	ip := net.ParseIP(cfg.Host)
	local := ip != nil && ip.To4() != nil && ip.IsPrivate() && !ip.IsLoopback()
	servers := cs2.DirectoryServers(cfg.InitString)
	if !local && len(servers) == 0 {
		event("error", "local_address_missing")
		return
	}
	bytes, err := hex.DecodeString(cfg.DevicePublic)
	if err != nil || len(bytes) != 32 {
		event("error", "device_key_invalid")
		return
	}
	var peer, shared [32]byte
	copy(peer[:], bytes)
	box.Precompute(&shared, &peer, private)
	// Dial and authentication must never keep the app waiting indefinitely.
	timer := time.AfterFunc(40*time.Second, func() { event("error", "connect_timeout"); os.Exit(2) })
	defer timer.Stop()
	var conn *cs2.Conn
	localFirst := cfg.TryLocal == nil || *cfg.TryLocal || len(servers) == 0
	if local && localFirst {
		conn, err = cs2.Dial(cfg.Host, "")
	}
	if conn == nil && len(servers) > 0 && cfg.P2PID != "" {
		event("cloud_connect", "")
		conn, err = cs2.DialCloud(cfg.P2PID, servers)
		if err != nil {
			event("diagnostic", cs2.CloudFailureCode(err))
		}
	}
	if conn == nil && local && !localFirst {
		event("local_fallback", "")
		conn, err = cs2.Dial(cfg.Host, "")
	}
	if conn == nil || err != nil {
		event("error", "media_connect_failed")
		return
	}
	defer conn.Close()
	defer emitMetrics(conn)
	auth, _ := json.Marshal(map[string]interface{}{"public_key": hex.EncodeToString(public[:]), "sign": cfg.Sign, "uuid": "", "support_encrypt": 0})
	if conn.WriteCommand(0x100, auth) != nil {
		event("error", "auth_send_failed")
		return
	}
	_, reply, err := conn.ReadCommand()
	if err != nil {
		event("error", "auth_read_failed")
		return
	}
	var accepted struct {
		Result string `json:"result"`
	}
	if json.Unmarshal(reply, &accepted) != nil || accepted.Result != "success" {
		event("error", "auth_rejected")
		return
	}
	// Continue consuming asynchronous device replies after login. Leaving CH0
	// unread fills its small queue and closes an otherwise healthy video stream.
	var listen, talkRequested, talkReady atomic.Bool
	var talkGeneration atomic.Uint64
	var outgoingCodec atomic.Uint32
	var playbackReady atomic.Bool
	playbackReady.Store(cfg.PlaybackStart == 0)
	var expectedPlayback atomic.Int64
	go func() {
		for {
			cmd, body, err := conn.ReadCommand()
			if err != nil {
				return
			}
			commandReplies.Add(1)
			decoded, payload := decodeControlReply(cmd, body, shared[:])
			switch decoded {
			case 0x107:
				if talkRequested.Load() {
					if commandAccepted(payload) {
						talkReady.Store(true)
						event("talk_ready", "")
					} else {
						talkRequested.Store(false)
						event("talk_failed", replyShape(payload))
					}
				}
			case 0x113:
				if commandAccepted(payload) {
					event("ptz_result", "accepted")
				} else {
					event("ptz_result", "unconfirmed")
				}
			case 0x10e:
				if playbackFound(payload, expectedPlayback.Load()) {
					conn.DiscardQueuedMedia()
					playbackReady.Store(true)
					event("playback_result", "accepted")
				} else {
					event("playback_result", "unconfirmed")
				}

			}
		}
	}()
	quality := cfg.Quality
	if quality < 0 || quality > 3 {
		quality = 1
	}
	indexReady := make(chan []recordingClip, 1)
	var indexChunks, indexDecodeErrors, indexParseErrors, indexBytes, indexWant atomic.Uint64
	go func() {
		var index recordingIndex
		for {
			chunk, readErr := conn.ReadIndexChunk()
			if readErr != nil {
				return
			}
			if len(chunk) < 8 || len(chunk) > 2<<20 {
				continue
			}
			indexChunks.Add(1)
			plain, decryptErr := cipher(chunk[8:], shared[:], chunk[:8])
			if decryptErr != nil {
				indexDecodeErrors.Add(1)
				continue
			}
			clips, done, parseErr := index.feed(plain)
			indexBytes.Store(uint64(len(index.body)))
			indexWant.Store(uint64(index.want))
			if parseErr != nil {
				indexParseErrors.Add(1)
				index = recordingIndex{}
				continue
			}
			if done {
				select {
				case indexReady <- clips:
				default:
				}
				index = recordingIndex{}
			}
		}
	}()
	request, _ := json.Marshal(map[string]int{"videoquality": quality, "enableaudio": 0})
	startCommand := uint32(0x102)
	startRequest := request
	if cfg.PlaybackStart > 0 {
		if send(conn, shared[:], 0x102, request) != nil {
			event("error", "video_start_failed")
			return
		}
		drainStop := make(chan struct{})
		drainDone := make(chan struct{})
		go func() { defer close(drainDone); conn.DiscardMediaUntil(drainStop) }()
		plain := make([]byte, 8)
		binary.LittleEndian.PutUint32(plain, 6)
		binary.LittleEndian.PutUint32(plain[4:], 0)
		nonce := make([]byte, 8)
		if _, err = rand.Read(nonce); err != nil {
			return
		}
		encrypted, cryptErr := cipher(plain, shared[:], nonce)
		if cryptErr != nil || conn.WriteIndexRequest(append(nonce, encrypted...)) != nil {
			event("error", "playback_index_failed")
			return
		}
		var clips []recordingClip
		select {
		case clips = <-indexReady:
		case <-time.After(30 * time.Second):
			event("diagnostic", fmt.Sprintf("index_timeout_chunks_%d_decode_%d_parse_%d_bytes_%d_want_%d", indexChunks.Load(), indexDecodeErrors.Load(), indexParseErrors.Load(), indexBytes.Load(), indexWant.Load()))
			event("error", "playback_index_timeout")
			return
		}
		close(drainStop)
		select {
		case <-drainDone:
		case <-time.After(time.Second):
			event("error", "playback_warmup_failed")
			return
		}
		_ = send(conn, shared[:], 0x103, nil)
		clip, selectErr := selectRecording(clips, cfg.PlaybackStart)
		if selectErr != nil {
			event("error", "playback_not_found")
			return
		}
		expectedPlayback.Store(clip.Start)
		startCommand = 0x10d
		startRequest, err = playbackPayload(clip.Start, cfg.PlaybackEnd, time.Now())
		if err != nil {
			event("error", "playback_invalid")
			return
		}
		var body map[string]interface{}
		_ = json.Unmarshal(startRequest, &body)
		body["offset"] = cfg.PlaybackStart - clip.Start
		startRequest, _ = json.Marshal(body)
	}

	if send(conn, shared[:], startCommand, startRequest) != nil {
		event("error", "video_start_failed")
		return
	}
	defer send(conn, shared[:], 0x103, nil)
	timer.Stop()
	event("connected", conn.Protocol())
	var paused atomic.Bool
	go func() {
		var talkListenAdded bool
		scanner := bufio.NewScanner(input)
		scanner.Buffer(make([]byte, 128), 4096)
		for scanner.Scan() {
			var control struct {
				Op        string `json:"op"`
				Direction int    `json:"direction"`
				Enabled   bool   `json:"enabled"`
				Data      string `json:"data"`
				Codec     uint32 `json:"codec"`
			}
			if json.Unmarshal(scanner.Bytes(), &control) != nil {
				continue
			}
			switch control.Op {
			case "listen":
				listen.Store(control.Enabled)
				cmd := uint32(0x105)
				if control.Enabled {
					cmd = 0x104
				}
				if send(conn, shared[:], cmd, nil) != nil {
					event("listen_failed", "command_unconfirmed")
				}
			case "ptz":
				body, invalid := motorPayload(control.Direction)
				if invalid == nil {
					if send(conn, shared[:], 0x112, body) != nil {
						event("ptz_result", "unconfirmed")
					}
				}
			case "talk_start":
				if !listen.Load() {
					listen.Store(true)
					talkListenAdded = true
					_ = send(conn, shared[:], 0x104, nil)
				}
				token := talkGeneration.Add(1)
				talkRequested.Store(true)
				talkReady.Store(false)
				if send(conn, shared[:], 0x106, nil) != nil {
					talkRequested.Store(false)
					event("talk_failed", "speaker_start_failed")
				} else {
					time.AfterFunc(5*time.Second, func() {
						if talkGeneration.Load() == token && talkRequested.Load() && !talkReady.Load() {
							talkRequested.Store(false)
							event("talk_failed", "speaker_reply_timeout")
						}
					})
				}
			case "talk_stop":
				if talkListenAdded {
					talkListenAdded = false
					listen.Store(false)
					_ = send(conn, shared[:], 0x105, nil)
				}
				talkGeneration.Add(1)
				talkRequested.Store(false)
				talkReady.Store(false)
				_ = send(conn, shared[:], 0x108, nil)
			case "talk_audio":
				if !talkReady.Load() || control.Codec != outgoingCodec.Load() || (control.Codec != 1024 && control.Codec != 1026 && control.Codec != 1027 && control.Codec != 1032) {
					continue
				}
				packet, invalid := base64.StdEncoding.DecodeString(control.Data)
				if invalid != nil || len(packet) < 1 || len(packet) > 2048 {
					continue
				}
				nonce := make([]byte, 8)
				if _, err := rand.Read(nonce); err != nil {
					continue
				}
				encrypted, err := cipher(packet, shared[:], nonce)
				if err != nil {
					continue
				}
				header := make([]byte, 32)
				binary.LittleEndian.PutUint32(header, uint32(len(encrypted)+8))
				binary.LittleEndian.PutUint32(header[4:], control.Codec)
				binary.LittleEndian.PutUint64(header[16:], uint64(time.Now().UnixMilli()))
				if conn.WritePacket(header, append(nonce, encrypted...)) != nil {
					talkReady.Store(false)
					event("talk_failed", "audio_send_failed")
				}

			case "pause":
				paused.Store(true)
				_ = conn.SetDeadline(time.Now().Add(12 * time.Second))
				if send(conn, shared[:], 0x103, nil) != nil {
					_ = conn.Close()
					return
				}
				emitMetrics(conn)
				event("paused", "")
			case "stats":
				emitMetrics(conn)
			case "resume":
				paused.Store(false)
				_ = conn.SetDeadline(time.Now().Add(12 * time.Second))
				if send(conn, shared[:], 0x102, request) != nil {
					_ = conn.Close()
					return
				}
				event("resumed", "")
			}
		}
		_ = conn.Close()
	}()
	lastMetrics := time.Now()
	var audioCodec uint32
	var audioRate, audioChannels int
	for {
		_ = conn.SetDeadline(time.Now().Add(12 * time.Second))
		confirmedBeforeRead := playbackReady.Load()
		head, data, err := conn.ReadPacket()
		if err != nil {
			emitMetrics(conn)
			event("error", "stream_read_failed_"+conn.FailureCode())
			return
		}
		if cfg.PlaybackStart > 0 && !confirmedBeforeRead {
			continue
		}
		if len(head) < 32 || len(data) < 8 {
			event("error", "frame_invalid")
			return
		}
		if time.Since(lastMetrics) > 15*time.Second {
			emitMetrics(conn)
			lastMetrics = time.Now()
		}
		if paused.Load() {
			continue
		}
		codec := binary.LittleEndian.Uint32(head[4:])
		if codec != 4 && codec != 5 && !listen.Load() {
			continue
		}
		if codec != 4 && codec != 5 && codec != 1024 && codec != 1026 && codec != 1027 && codec != 1032 {
			continue
		}
		frame, err := cipher(data[8:], shared[:], data[:8])
		if err != nil {
			event("error", "frame_decrypt_failed")
			return
		}
		if codec != 4 && codec != 5 {
			outgoingCodec.Store(codec)
			rate, channels := 8000, 1
			flags := binary.LittleEndian.Uint32(head[12:])
			if (flags>>3)&15 != 0 {
				rate = 16000
			}
			if codec == 1032 {
				rate = 48000
				channels = 2
			}
			if codec != audioCodec || rate != audioRate || channels != audioChannels {
				audioCodec, audioRate, audioChannels = codec, rate, channels
				b, _ := json.Marshal(map[string]interface{}{"event": "audio_format", "codec": codec, "sampleRate": rate, "channels": channels})
				emit(1, 0, b)
			}
			kind := byte(10)
			switch codec {
			case 1026:
				kind = 11
			case 1027:
				kind = 12
			case 1032:
				kind = 13
			}
			if !emit(kind, binary.LittleEndian.Uint64(head[16:]), frame) {
				return
			}
			continue
		}
		if !emit(byte(codec), binary.LittleEndian.Uint64(head[16:]), frame) {
			return
		}
	}
}
