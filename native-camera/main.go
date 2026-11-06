// JiaJu's private pipe bridge. No HTTP listener, command line credentials or saved media.
package main

import (
	"bufio"
	"crypto/rand"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
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
	Host         string `json:"host"`
	Model        string `json:"model"`
	Vendor       int    `json:"vendor"`
	DevicePublic string `json:"devicePublic"`
	Sign         string `json:"sign"`
	Quality      int    `json:"quality"`
	P2PID        string `json:"p2pId"`
	InitString   string `json:"initString"`
	TryLocal     *bool  `json:"tryLocal"`
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
	data, _ := json.Marshal(map[string]interface{}{"event": "metrics", "relayRenewals": conn.SessionRenewals(), "commandReplies": commandReplies.Load(), "mediaPackets": conn.MediaPackets()})
	emit(1, 0, data)
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
	if json.Unmarshal(line, &cfg) != nil || cfg.Model != "chuangmi.camera.069a01" || cfg.Vendor != 4 || len(cfg.Sign) != 64 {
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
	go func() {
		for {
			if _, _, err := conn.ReadCommand(); err != nil {
				return
			}
			commandReplies.Add(1)
		}
	}()
	quality := cfg.Quality
	if quality < 0 || quality > 3 {
		quality = 1
	}
	request, _ := json.Marshal(map[string]int{"videoquality": quality, "enableaudio": 0})
	if send(conn, shared[:], 0x102, request) != nil {
		event("error", "video_start_failed")
		return
	}
	defer send(conn, shared[:], 0x103, nil)
	timer.Stop()
	event("connected", conn.Protocol())
	var paused atomic.Bool
	go func() {
		scanner := bufio.NewScanner(input)
		scanner.Buffer(make([]byte, 128), 512)
		for scanner.Scan() {
			var control struct {
				Op string `json:"op"`
			}
			if json.Unmarshal(scanner.Bytes(), &control) != nil {
				continue
			}
			switch control.Op {
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
	for {
		_ = conn.SetDeadline(time.Now().Add(12 * time.Second))
		head, data, err := conn.ReadPacket()
		if err != nil {
			emitMetrics(conn)
			event("error", "stream_read_failed_"+conn.FailureCode())
			return
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
		if codec != 4 && codec != 5 {
			continue
		}
		frame, err := cipher(data[8:], shared[:], data[:8])
		if err != nil {
			event("error", "frame_decrypt_failed")
			return
		}
		if !emit(byte(codec), binary.LittleEndian.Uint64(head[16:]), frame) {
			return
		}
	}
}
