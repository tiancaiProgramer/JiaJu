// Adapted from MIT-licensed kisharora/go2rtc, commit 76b3e456c2f1dda97cce9f2797a668818e165488.
package cs2

import (
	"encoding/binary"
	"fmt"
	"net"
	"strconv"
	"strings"
	"sync/atomic"
	"time"
)

// DialCloud establishes a cs2 connection via Mi-Cloud relay (UDP/32100)
// using NAT hole-punching. Required for cameras that do not expose the
// LAN port 32108 directly — typically the MJA1-secure-element generation
// of Xiaomi cameras (e.g. xiaomi.camera.c302n, xiaomi.camera.cw701,
// isa.camera.hlc7), which only stream via Mi-Cloud.
//
// p2pID is the device identifier from the Mi-Cloud vendor_params,
// of the form "PREFIX-NUM-SUFFIX" (e.g. "ABCDEF-123456-GHIJK").
// relayHost is the Mi-Cloud relay IP (one of the published Alibaba Cloud
// pool addresses, e.g. 47.236.156.107, 47.88.30.183, 47.88.30.225).
func DialCloud(p2pID string, servers []string) (*Conn, error) {
	conn, err := cloudHandshake(p2pID, servers)
	if err != nil {
		return nil, err
	}

	_, isTCP := conn.(*tcpConn)

	c := &Conn{
		Conn:  conn,
		isTCP: isTCP,
		done:  make(chan struct{}),
		channels: [4]*dataChannel{
			newDataChannel(0, 10), nil, newDataChannel(250, 1000), nil,
		},
	}
	go c.worker()
	return c, nil
}

// cs2CryptoKey is the universal key string used by the CS2-Network
// P2P_Proprietary_Encrypt cipher, extracted from camera firmware
// disassembly. The same constant is found in cameras of multiple
// vendors that license the CS2-Network SDK (see e.g. the VStarcam
// reverse engineering by BrownFineSecurity).
const cs2CryptoKey = "SSD@cs2-network."

// cs2LookupTable is the 256-byte permutation used by the CS2-Network
// cipher. Identical to the table observed in VStarcam firmware.
var cs2LookupTable = [256]byte{
	0x7c, 0x9c, 0xe8, 0x4a, 0x13, 0xde, 0xdc, 0xb2, 0x2f, 0x21, 0x23, 0xe4, 0x30, 0x7b, 0x3d, 0x8c,
	0xbc, 0x0b, 0x27, 0x0c, 0x3c, 0xf7, 0x9a, 0xe7, 0x08, 0x71, 0x96, 0x00, 0x97, 0x85, 0xef, 0xc1,
	0x1f, 0xc4, 0xdb, 0xa1, 0xc2, 0xeb, 0xd9, 0x01, 0xfa, 0xba, 0x3b, 0x05, 0xb8, 0x15, 0x87, 0x83,
	0x28, 0x72, 0xd1, 0x8b, 0x5a, 0xd6, 0xda, 0x93, 0x58, 0xfe, 0xaa, 0xcc, 0x6e, 0x1b, 0xf0, 0xa3,
	0x88, 0xab, 0x43, 0xc0, 0x0d, 0xb5, 0x45, 0x38, 0x4f, 0x50, 0x22, 0x66, 0x20, 0x7f, 0x07, 0x5b,
	0x14, 0x98, 0x1d, 0x9b, 0xa7, 0x2a, 0xb9, 0xa8, 0xcb, 0xf1, 0xfc, 0x49, 0x47, 0x06, 0x3e, 0xb1,
	0x0e, 0x04, 0x3a, 0x94, 0x5e, 0xee, 0x54, 0x11, 0x34, 0xdd, 0x4d, 0xf9, 0xec, 0xc7, 0xc9, 0xe3,
	0x78, 0x1a, 0x6f, 0x70, 0x6b, 0xa4, 0xbd, 0xa9, 0x5d, 0xd5, 0xf8, 0xe5, 0xbb, 0x26, 0xaf, 0x42,
	0x37, 0xd8, 0xe1, 0x02, 0x0a, 0xae, 0x5f, 0x1c, 0xc5, 0x73, 0x09, 0x4e, 0x69, 0x24, 0x90, 0x6d,
	0x12, 0xb3, 0x19, 0xad, 0x74, 0x8a, 0x29, 0x40, 0xf5, 0x2d, 0xbe, 0xa5, 0x59, 0xe0, 0xf4, 0x79,
	0xd2, 0x4b, 0xce, 0x89, 0x82, 0x48, 0x84, 0x25, 0xc6, 0x91, 0x2b, 0xa2, 0xfb, 0x8f, 0xe9, 0xa6,
	0xb0, 0x9e, 0x3f, 0x65, 0xf6, 0x03, 0x31, 0x2e, 0xac, 0x0f, 0x95, 0x2c, 0x5c, 0xed, 0x39, 0xb7,
	0x33, 0x6c, 0x56, 0x7e, 0xb4, 0xa0, 0xfd, 0x7a, 0x81, 0x53, 0x51, 0x86, 0x8d, 0x9f, 0x77, 0xff,
	0x6a, 0x80, 0xdf, 0xe2, 0xbf, 0x10, 0xd7, 0x75, 0x64, 0x57, 0x76, 0xf3, 0x55, 0xcd, 0xd0, 0xc8,
	0x18, 0xe6, 0x36, 0x41, 0x62, 0xcf, 0x99, 0xf2, 0x32, 0x4c, 0x67, 0x60, 0x61, 0x92, 0xca, 0xd3,
	0xea, 0x63, 0x7d, 0x16, 0xb6, 0x8e, 0xd4, 0x68, 0x35, 0xc3, 0x52, 0x9d, 0x46, 0x44, 0x1e, 0x17,
}

// deriveCryptoKey returns the 4-byte stream-cipher state derived from a
// key string. Mirrors the SDK's key-schedule (truncated at 20 bytes).
func deriveCryptoKey(key []byte) [4]byte {
	var c [4]byte
	n := len(key)
	if n > 20 {
		n = 20
	}
	for i := 0; i < n; i++ {
		b := key[i]
		c[0] += b
		c[1] -= b
		c[2] += b / 3
		c[3] ^= b
	}
	return c
}

// selectTableElement returns the keystream byte for the given previous
// ciphertext byte: table[(prev + c_key[prev & 3]) & 0xff].
func selectTableElement(c [4]byte, prev byte) byte {
	return cs2LookupTable[byte(prev+c[prev&3])]
}

// encryptP2P applies the CS2-Network P2P_Proprietary_Encrypt cipher
// to plaintext. The cipher is a stream cipher with previous-ciphertext-byte
// feedback; the same routine decrypts when fed ciphertext, because the
// feedback variable always reads from the cipher side.
func encryptP2P(plain []byte) []byte {
	c := deriveCryptoKey([]byte(cs2CryptoKey))
	out := make([]byte, len(plain))
	var prev byte = 0
	for i, p := range plain {
		out[i] = p ^ selectTableElement(c, prev)
		prev = out[i]
	}
	return out
}

// buildP2PIDBlock encodes a p2p_id string of the form "PREFIX-NUM-SUFFIX"
// into the 20-byte canonical block used by 0x20 (connect) and 0x41 (punch):
//
//	[ 0.. 5] prefix (6 bytes, null-padded)  e.g. "ABCDEF"
//	[ 6.. 7] padding 0x00 0x00
//	[ 8..11] num (uint32 big-endian)        e.g. 123456 -> 0x0001E240
//	[12..16] suffix (5 bytes, null-padded)  e.g. "GHIJK"
//	[17..19] padding 0x00 0x00 0x00
func buildP2PIDBlock(p2pID string) ([]byte, error) {
	parts := strings.Split(p2pID, "-")
	if len(parts) != 3 {
		return nil, fmt.Errorf("cs2 cloud: invalid p2p_id format: %s", p2pID)
	}
	prefix := parts[0]
	numStr := parts[1]
	suffix := parts[2]

	if len(prefix) == 0 || len(prefix) > 8 {
		return nil, fmt.Errorf("cs2 cloud: p2p_id prefix too long: %s", prefix)
	}
	if len(suffix) > 5 {
		return nil, fmt.Errorf("cs2 cloud: p2p_id suffix too long: %s", suffix)
	}

	num, err := strconv.ParseUint(numStr, 10, 32)
	if err != nil {
		return nil, fmt.Errorf("cs2 cloud: invalid p2p_id num %q: %w", numStr, err)
	}

	block := make([]byte, 20)
	copy(block[0:8], []byte(prefix))
	binary.BigEndian.PutUint32(block[8:12], uint32(num))
	copy(block[12:17], []byte(suffix))
	return block, nil
}

// buildCloudConnect builds the 0x20 connect-request message (40 bytes total:
// 4-byte header + 36-byte payload, made of the 20-byte p2p_id block and a
// 16-byte nonce/timestamp tail).
func buildCloudConnect(p2pID string, localIP net.IP, localPort uint16) ([]byte, error) {
	block, err := buildP2PIDBlock(p2pID)
	if err != nil {
		return nil, err
	}

	// Relay-validated format: block[0:20] + zeros[20:22] + BigEndian unix
	// timestamp at [22:26]. Any sockaddr bytes in the tail (family/port/IP)
	// flip the 0x21 status to ff000000 and no 0x40 ever arrives.
	payload := make([]byte, 36)
	copy(payload[0:20], block)
	binary.BigEndian.PutUint32(payload[22:26], uint32(time.Now().Unix()))

	msg := make([]byte, 4+36)
	msg[0] = magic
	msg[1] = msgCloudConnect
	binary.BigEndian.PutUint16(msg[2:4], 36)
	copy(msg[4:], payload)
	return msg, nil
}

// buildCloudPunch builds the 0x41 (msgPunchPkt) frame with the 20-byte
// p2p_id block as payload. In cloud-relay mode the camera silently drops
// the empty 4-byte punch used by the LAN handshake and only responds when
// the punch frame carries the p2p_id block (so the cam can identify which
// session the punch belongs to).
func buildCloudPunch(p2pID string) ([]byte, error) {
	block, err := buildP2PIDBlock(p2pID)
	if err != nil {
		return nil, err
	}

	msg := make([]byte, 4+20)
	msg[0] = magic
	msg[1] = msgPunchPkt
	binary.BigEndian.PutUint16(msg[2:4], 20)
	copy(msg[4:], block)
	return msg, nil
}

// cloudAuthUnknownBlock is the 16-byte payload at plaintext offset 20-35
// of the 0xF9 auth message. Captured verbatim from a Mi-Home iOS
// handshake; the byte pattern (sparse, small values) looks like protocol
// flags or channel constants rather than client-specific data, and
// transmitting it verbatim works against the relay and camera. Zeroing
// this block caused the camera to silently drop subsequent punch packets.
// Values not verified against a second account; treat as opaque.
var cloudAuthUnknownBlock = [16]byte{
	0x00, 0x00, 0x00, 0x06, 0x00, 0x14, 0x00, 0x01,
	0x00, 0x88, 0x00, 0x00, 0x7a, 0x00, 0x00, 0x00,
}

// buildCloudAuth builds the 0xF9 session-auth message (88 bytes total)
// by encrypting an 84-byte plaintext that carries the device_uid block,
// an opaque flags block and the client's own UDP address.
//
// Plaintext layout (84 bytes, verified by decrypting a captured 0xF9):
//
//	[ 0..19] device_uid     = encoded p2p_id (prefix + num_be + suffix)
//	[20..35] unknown        = 16-byte opaque flags/counter block
//	[36..51] local_address  = Xiaomi sockaddr-style: 2B family (00 00) +
//	                          2B port LE + 4B ipv4 reverse-LE + 8B pad
//	[52..67] wan_address    = same format; equal to local when the client
//	                          sits on a public IP without NAT
//	[68..83] device_address = zeros (the camera-side LAN address is
//	                          unknown to us; the relay reports the cam's
//	                          WAN address back in its 0x40 reply anyway)
//
// Zero-filling everything past device_uid produces a working relay
// response (0x21 ACK, 0x40 punch info) but the camera then ignores
// punches — likely because the cam validates the addresses against
// the source it sees on subsequent packets.
func buildCloudAuth(p2pID string, localIP net.IP, localPort uint16) ([]byte, error) {
	uidBlock, err := buildP2PIDBlock(p2pID)
	if err != nil {
		return nil, err
	}

	plain := make([]byte, 84)
	copy(plain[0:20], uidBlock)
	copy(plain[20:36], cloudAuthUnknownBlock[:])

	if v4 := localIP.To4(); v4 != nil && localPort != 0 {
		// local_addr [36..51]
		binary.LittleEndian.PutUint16(plain[38:40], localPort)
		plain[40] = v4[3]
		plain[41] = v4[2]
		plain[42] = v4[1]
		plain[43] = v4[0]
		// wan_addr [52..67] — identical to local for clients without
		// a NAT layer in front (e.g. a server with a public IP).
		binary.LittleEndian.PutUint16(plain[54:56], localPort)
		plain[56] = v4[3]
		plain[57] = v4[2]
		plain[58] = v4[1]
		plain[59] = v4[0]
	}

	// Post-submit findings from the PR author's Frida captures of the
	// Mi-Home Android app: plaintext byte 23 is 0x01 (not 0x06), and
	// bytes 28-29 carry a per-session sequence ID (not 0x0088).
	plain[23] = 0x01
	binary.BigEndian.PutUint16(plain[28:30], uint16(time.Now().UnixNano()))

	cipher := encryptP2P(plain)

	msg := make([]byte, 4+len(cipher))
	msg[0] = magic
	msg[1] = msgCloudAuth
	binary.BigEndian.PutUint16(msg[2:4], uint16(len(cipher)))
	copy(msg[4:], cipher)
	return msg, nil
}

func buildRelayMediaAuth(p2pID string, localIP net.IP, localPort uint16, wanIP net.IP, wanPort uint16, mediaIP net.IP, mediaPort uint16, sessionID uint32, connectMs uint16) ([]byte, error) {
	uidBlock, err := buildP2PIDBlock(p2pID)
	if err != nil {
		return nil, err
	}

	plain := make([]byte, 84)
	copy(plain[0:20], uidBlock)
	copy(plain[20:36], cloudAuthUnknownBlock[:])

	// Xiaomi's final F9 reports the SDK session handle and connection time.
	// Fresh captures increment the handle across direct sessions; 2 is not
	// a relay-media connection type, and the duration is not a random ID.
	binary.BigEndian.PutUint32(plain[20:24], sessionID)
	binary.BigEndian.PutUint16(plain[28:30], connectMs)

	// local_address [36..51]
	if v4 := localIP.To4(); v4 != nil && localPort != 0 {
		plain[36], plain[37] = 0x00, 0x02
		binary.LittleEndian.PutUint16(plain[38:40], localPort)
		plain[40] = v4[3]
		plain[41] = v4[2]
		plain[42] = v4[1]
		plain[43] = v4[0]
	}

	// wan_address [52..67]
	if wanIP == nil || wanPort == 0 {
		wanIP = localIP
		wanPort = localPort
	}
	if v4 := wanIP.To4(); v4 != nil && wanPort != 0 {
		plain[52], plain[53] = 0x00, 0x02
		binary.LittleEndian.PutUint16(plain[54:56], wanPort)
		plain[56] = v4[3]
		plain[57] = v4[2]
		plain[58] = v4[1]
		plain[59] = v4[0]
	}

	// device_address [68..83]: the chosen media session endpoint
	if v4 := mediaIP.To4(); v4 != nil && mediaPort != 0 {
		plain[68], plain[69] = 0x00, 0x02
		binary.LittleEndian.PutUint16(plain[70:72], mediaPort)
		plain[72] = v4[3]
		plain[73] = v4[2]
		plain[74] = v4[1]
		plain[75] = v4[0]
	}

	cipher := encryptP2P(plain)

	msg := make([]byte, 4+len(cipher))
	msg[0] = magic
	msg[1] = msgCloudAuth
	binary.BigEndian.PutUint16(msg[2:4], uint16(len(cipher)))
	copy(msg[4:], cipher)
	return msg, nil
}

var cloudSessionCounter atomic.Uint32

// registerCloudSession mirrors the F9 reports sent to every lookup relay
// after Xiaomi has received P2P-ready, before it authenticates on CH0.
// Use the observed WAN mapping and the actual responding peer, and keep
// subsequent command/media writes addressed to that peer.
func registerCloudSession(uc *udpConn, p2pID string, relays []*net.UDPAddr, localIP net.IP, localPort uint16, wanIP net.IP, wanPort uint16, peer *net.UDPAddr, sessionID uint32, started time.Time) (net.Conn, error) {
	ms := time.Since(started).Milliseconds()
	if ms > 65535 {
		ms = 65535
	}
	if ms < 0 {
		ms = 0
	}
	report, err := buildRelayMediaAuth(p2pID, localIP, localPort, wanIP, wanPort, peer.IP, uint16(peer.Port), sessionID, uint16(ms))
	if err == nil {
		for _, relay := range relays {
			if _, err = uc.UDPConn.WriteToUDP(report, relay); err != nil {
				break
			}
		}
	}
	if err == nil {
		err = uc.SetDeadline(time.Time{})
	}
	if err != nil {
		_ = uc.Close()
		return nil, fmt.Errorf("cs2 cloud: register session: %w", err)
	}
	uc.addr = peer
	return uc, nil
}

// detectOutboundIP returns the local IPv4 address the OS would use to
// reach dest. Uses Go's UDP-dial trick — no packet is sent, only the
// routing table is consulted. Returns nil on error, in which case
// buildCloudAuth leaves the address fields zeroed.
func detectOutboundIP(dest string) net.IP {
	conn, err := net.Dial("udp", net.JoinHostPort(dest, "1"))
	if err != nil {
		return nil
	}
	defer conn.Close()
	if u, ok := conn.LocalAddr().(*net.UDPAddr); ok {
		return u.IP
	}
	return nil
}

// cloudHandshake performs the cs2 cloud-relay handshake against the
// Mi-Cloud relay at relayHost:32100. Steps:
//
//  1. Send 0x20 (connect request) and 0xF9 (encrypted session auth) to
//     the relay; the relay replies with 0x21 (ACK) and 0x40 (NAT-punch
//     info containing the camera's external address).
//  2. Switch the UDP socket's remote to the camera's address from 0x40
//     and send 0x41 (msgPunchPkt) carrying the p2p_id block.
//  3. Wait for 0x42 (P2P-ready UDP) from the camera and return.
//
// Cloud mode is UDP-only: TCP would require TCP hole-punching from both
// sides, which is not feasible behind typical NATs.
// i2-region relay cluster observed in Xiaomi Home captures (2026-09-06):
// the app dials every member so the camera's relay learns the session no
// matter which one the camera is registered on.
func cloudHandshake(p2pID string, servers []string) (net.Conn, error) {
	if len(servers) == 0 {
		return nil, fmt.Errorf("no camera directory servers")
	}
	relayHost := servers[0]
	started := time.Now()
	sessionID := cloudSessionCounter.Add(1)
	udp, err := newUDPConn(relayHost, 32100)
	if err != nil {
		return nil, err
	}

	uc := udp.(*udpConn)

	var localPort uint16
	if la, ok := uc.UDPConn.LocalAddr().(*net.UDPAddr); ok {
		localPort = uint16(la.Port)
	}
	localIP := detectOutboundIP(relayHost)
	relayAddrs := make([]*net.UDPAddr, 0, 4)
	for _, rip := range servers {
		relayAddrs = append(relayAddrs, &net.UDPAddr{IP: net.ParseIP(rip), Port: 32100})
	}

	req20, err := buildCloudConnect(p2pID, localIP, localPort)
	if err != nil {
		_ = udp.Close()
		return nil, err
	}
	reqF9, err := buildCloudAuth(p2pID, localIP, localPort)
	if err != nil {
		_ = udp.Close()
		return nil, err
	}

	block, berr := buildP2PIDBlock(p2pID)
	if berr != nil {
		_ = udp.Close()
		return nil, berr
	}
	req67 := make([]byte, 0, 24)
	req67 = append(req67, magic, 0x67, 0x00, 0x14)
	req67 = append(req67, block...)

	ping := []byte{magic, 0x00, 0x00, 0x00}

	// ---- Phase A: ping + 0x20 burst to EVERY relay (app-verified) ----
	for _, ra := range relayAddrs {
		uc.addr = ra
		_, _ = uc.Write(ping)
	}
	for _, ra := range relayAddrs {
		uc.addr = ra
		if _, err = uc.Write(req20); err != nil {
			_ = udp.Close()
			return nil, fmt.Errorf("cs2 cloud: send 0x20: %w", err)
		}
	}

	buf := make([]byte, 1500)
	var natInfo []byte
	lanPunch, lanPunchErr := buildCloudPunch(p2pID)
	if lanPunchErr != nil {
		_ = udp.Close()
		return nil, lanPunchErr
	}
	// Every public address the relays reported for the camera.
	var camPubs []*net.UDPAddr
	// The camera's LAN addresses. Reachable when the camera shares the
	// server's LAN; otherwise the punches go nowhere.
	var camLAN []*net.UDPAddr
	var mediaToks []handoffTok
	var wanIP net.IP
	var wanPort uint16
	finish := func(peer *net.UDPAddr) (net.Conn, error) {
		return registerCloudSession(uc, p2pID, relayAddrs, localIP, localPort, wanIP, wanPort, peer, sessionID, started)
	}
	// Fail fast and let a fresh attempt (new socket, new NAT port) try
	// again: Xiaomi Home has its punch info in 40 ms (PCAPdroid 2026-10-01).
	deadline := time.Now().Add(10 * time.Second)
	lastResend := time.Now()
	sent67 := false

	collect := func(n int) {
		if n >= 4 && buf[0] == magic {
			switch buf[1] {
			case 0x40, 0x82, 0x69, 0x01, 0x21, 0x84, 0x28, 0x71, 0x73, 0x81:
			}
		}
		if n < 4 || buf[0] != magic {
			return
		}
		switch buf[1] {
		case 0x01:
			if n >= 12 {
				wanPort = binary.LittleEndian.Uint16(buf[6:8])
				wanIP = net.IPv4(buf[11], buf[10], buf[9], buf[8])
			}
		case msgNatPunch:
			if n >= 12 {
				port := int(binary.LittleEndian.Uint16(buf[6:8]))
				ip := net.IPv4(buf[11], buf[10], buf[9], buf[8])
				if !ip.IsPrivate() && !ip.IsLoopback() && ip.To4() != nil {
					seen := false
					for _, a := range camPubs {
						if a.IP.Equal(ip) && a.Port == port {
							seen = true
						}
					}
					if !seen {
						camPubs = append(camPubs, &net.UDPAddr{IP: ip, Port: port})
					}
					natInfo = make([]byte, n)
					copy(natInfo, buf[:n])
				} else if natInfo == nil {
					natInfo = make([]byte, n)
					copy(natInfo, buf[:n])
				}
				if ip.IsPrivate() && ip.To4() != nil {
					seen := false
					for _, a := range camLAN {
						if a.IP.Equal(ip) && a.Port == port {
							seen = true
						}
					}
					if !seen {
						camLAN = append(camLAN, &net.UDPAddr{IP: ip, Port: port})
						// Punch the LAN at once: on the same LAN this is the
						// whole connection (Xiaomi Home: video in 0.08 s).
						for dp := -3; dp <= 3; dp++ {
							uc.addr = &net.UDPAddr{IP: ip, Port: port + dp}
							_, _ = uc.Write(lanPunch)
						}
					}
				}
			}
		case msgMediaHandoff:
			// each 0x82 pairs a relay-media endpoint with a 4-byte session
			// token in its tail — the 0x83 punch must echo that token
			if n >= 16 {
				var tk [4]byte
				copy(tk[:], buf[n-4:n])
				dup := false
				for _, e := range mediaToks {
					if e.token == tk {
						dup = true
						break
					}
				}
				if !dup {
					port := binary.LittleEndian.Uint16(buf[6:8])
					ip := net.IPv4(buf[11], buf[10], buf[9], buf[8])
					mediaToks = append(mediaToks, handoffTok{addr: &net.UDPAddr{IP: ip, Port: int(port)}, token: tk})
				}
			}
		}
	}

	// ---- Phase B: wait for 0x40, resend 0x20 (app resends x9), 0x67 x3 ----
	for natInfo == nil {
		if time.Now().After(deadline) {
			_ = udp.Close()
			return nil, fmt.Errorf("cs2 cloud: timeout waiting for 0x40 NAT-punch info")
		}
		if time.Since(lastResend) > 1*time.Second {
			for i := 0; i < 3; i++ {
				_, _ = uc.Write(req20)
			}
			lastResend = time.Now()
		}
		_ = uc.UDPConn.SetReadDeadline(time.Now().Add(1 * time.Second))
		n, _, err := uc.UDPConn.ReadFromUDP(buf)
		if err != nil {
			if ne, ok := err.(net.Error); ok && ne.Timeout() {
				continue
			}
			_ = udp.Close()
			return nil, fmt.Errorf("cs2 cloud: read relay: %w", err)
		}
		collect(n)
		if natInfo != nil && !sent67 {
			sent67 = true
			// app: 0x20 resend burst right after the punch-infos, then 0x67 x3
			for i := 0; i < 3; i++ {
				_, _ = uc.Write(req20)
			}
			for i := 0; i < 3; i++ {
				_, _ = uc.Write(req67)
				time.Sleep(30 * time.Millisecond)
			}
		}
	}

	if len(natInfo) < 12 {
		_ = udp.Close()
		return nil, fmt.Errorf("cs2 cloud: 0x40 too short (%d bytes)", len(natInfo))
	}
	camPort := binary.LittleEndian.Uint16(natInfo[6:8])
	camIP := net.IPv4(natInfo[11], natInfo[10], natInfo[9], natInfo[8])
	camAddr := &net.UDPAddr{IP: camIP, Port: int(camPort)}

	punch, err := buildCloudPunch(p2pID)
	if err != nil {
		_ = udp.Close()
		return nil, err
	}
	// latestCamAddr re-parses the newest 0x40; sweepCam punches the camera's
	// public address across port±3 (LAN addresses can't route from the server)
	latestCamAddr := func() *net.UDPAddr {
		if len(natInfo) < 12 {
			return camAddr
		}
		return &net.UDPAddr{
			IP:   net.IPv4(natInfo[11], natInfo[10], natInfo[9], natInfo[8]),
			Port: int(binary.LittleEndian.Uint16(natInfo[6:8])),
		}
	}
	sweepCam := func() {
		targets := camPubs
		if ca := latestCamAddr(); len(targets) == 0 &&
			!ca.IP.IsPrivate() && !ca.IP.IsLoopback() && ca.IP.To4() != nil {
			targets = []*net.UDPAddr{ca}
		}
		targets = append(append([]*net.UDPAddr{}, targets...), camLAN...)
		for _, ca := range targets {
			for dp := -3; dp <= 3; dp++ {
				uc.addr = &net.UDPAddr{IP: ca.IP, Port: ca.Port + dp}
				_, _ = uc.Write(punch)
			}
		}
	}

	// ---- Phase C: fast collect 0x82 endpoints while racing direct punch ----
	// Xiaomi Home's direct punch succeeds within ~50 ms; the shop's router
	// moves the camera's port, so keep sweeping port±3 for 1.5 s before
	// any fallback (the relay paths below take seconds when they fail).
	sweepCam()
	directStarted := time.Now()
	lastSweep := time.Now()
	for time.Since(directStarted) < directProbeWindow(len(mediaToks)) {
		if time.Since(lastSweep) > 250*time.Millisecond {
			sweepCam()
			lastSweep = time.Now()
		}
		_ = uc.UDPConn.SetReadDeadline(time.Now().Add(50 * time.Millisecond))
		n, from, err := uc.UDPConn.ReadFromUDP(buf)
		if err == nil {
			collect(n)
			if n >= 4 && buf[0] == magic && from != nil {
				if buf[1] == msgP2PRdyUDP {
					return finish(from)
				}
				if buf[1] == msgPunchPkt {
					uc.addr = from
					_, _ = uc.Write(punch)
					_, _ = uc.Write([]byte{magic, msgP2PRdyUDP, 0x00, 0x00})
				}
			}
		}
	}

	// ---- Phase E: relay-media session (app-exact, PCAPdroid 2026-09-06) ----
	// The 0x83 punch must ECHO the per-0x82 session token; a random nonce is
	// ignored by relay-media. Choreography: 0x70 hello to every endpoint,
	// first 0x71 responder is chosen, 0x72 to it, 0x73 {nonce, port},
	// 0x80 introduce x5 to the relay (block17 + zeros4 + 0x02 + portLE +
	// ipREV + zeros8 + nonce4) tells the camera where the session lives,
	// 0x84/0x28 arrive from the session server on an ephemeral port, echo the
	// 0x28 (flag byte -> 02), then 0xF9 auth x3 to the relay. The p2p block
	// is 17 bytes, so the punch payload = token4 + block17 + zeros7 = 0x1C.
	if len(mediaToks) > 0 {
		punchTokens := func() {
			toks := mediaToks
			if len(toks) > 12 {
				toks = toks[len(toks)-12:]
			}
			for _, tk := range toks {
				p83 := make([]byte, 0, 32)
				p83 = append(p83, magic, msgMediaPunch, 0x00, 0x1C)
				p83 = append(p83, tk.token[:]...)
				p83 = append(p83, block[:17]...) // block17: block20 minus 3 pad zeros
				p83 = append(p83, 0, 0, 0, 0, 0, 0, 0)
				uc.addr = tk.addr
				for i := 0; i < 4; i++ {
					_, _ = uc.Write(p83)
				}
			}
		}
		punchTokens()
		for _, tk := range mediaToks {
			uc.addr = tk.addr
			_, _ = uc.Write([]byte{magic, 0x70, 0x00, 0x00})
		}

		relaySeen := make(map[string]*net.UDPAddr, len(relayAddrs))
		for _, ra := range relayAddrs {
			relaySeen[ra.String()] = ra
		}
		var chosen *net.UDPAddr
		var sessNonce [4]byte
		var sessPort uint16
		got73 := false
		stageEnd := time.Now().Add(600 * time.Millisecond)
		for time.Now().Before(stageEnd) && chosen == nil {
			_ = uc.UDPConn.SetReadDeadline(time.Now().Add(80 * time.Millisecond))
			n, from, err := uc.UDPConn.ReadFromUDP(buf)
			if err != nil {
				continue
			}
			collect(n)
			if n >= 4 && buf[0] == magic && from != nil {
				if buf[1] == 0x71 {
					chosen = from
				} else if buf[1] == msgP2PRdyUDP {
					return finish(from)
				} else if buf[1] == msgPunchPkt {
					uc.addr = from
					_, _ = uc.Write(punch)
					_, _ = uc.Write([]byte{magic, msgP2PRdyUDP, 0x00, 0x00})
				}
			}
		}
		if chosen == nil {
			chosen = mediaToks[0].addr
		}
		uc.addr = chosen
		_, _ = uc.Write([]byte{magic, 0x72, 0x00, 0x00})
		stageEnd = time.Now().Add(800 * time.Millisecond)
		for time.Now().Before(stageEnd) && !got73 {
			_ = uc.UDPConn.SetReadDeadline(time.Now().Add(80 * time.Millisecond))
			n, from, err := uc.UDPConn.ReadFromUDP(buf)
			if err != nil {
				continue
			}
			collect(n)
			if n >= 4 && buf[0] == magic && from != nil {
				if n >= 12 && buf[1] == 0x73 && from.IP.Equal(chosen.IP) && from.Port == chosen.Port {
					copy(sessNonce[:], buf[4:8])
					sessPort = binary.BigEndian.Uint16(buf[8:10])
					got73 = true
				} else if buf[1] == msgP2PRdyUDP {
					return finish(from)
				} else if buf[1] == msgPunchPkt {
					uc.addr = from
					_, _ = uc.Write(punch)
					_, _ = uc.Write([]byte{magic, msgP2PRdyUDP, 0x00, 0x00})
				}
			}
		}

		var intro []byte
		var sessAddr *net.UDPAddr
		var p83Sess []byte
		if got73 {
			ip4 := chosen.IP.To4()
			intro = make([]byte, 0, 44)
			intro = append(intro, magic, 0x80, 0x00, 0x28)
			intro = append(intro, block[:17]...) // block17: payload must total 40B
			intro = append(intro, 0, 0, 0, 0)
			intro = append(intro, 0x02)
			var pl [2]byte
			binary.LittleEndian.PutUint16(pl[:], sessPort)
			intro = append(intro, pl[:]...)
			intro = append(intro, ip4[3], ip4[2], ip4[1], ip4[0])
			intro = append(intro, 0, 0, 0, 0, 0, 0, 0, 0)
			intro = append(intro, sessNonce[:]...)

			sessAddr = &net.UDPAddr{IP: chosen.IP, Port: int(sessPort)}
			p83Sess = make([]byte, 0, 32)
			p83Sess = append(p83Sess, magic, msgMediaPunch, 0x00, 0x1C)
			p83Sess = append(p83Sess, sessNonce[:]...)
			p83Sess = append(p83Sess, block[:17]...)
			p83Sess = append(p83Sess, 0, 0, 0, 0, 0, 0, 0)

			for _, ra := range relaySeen {
				uc.addr = ra
				for i := 0; i < 5; i++ {
					_, _ = uc.Write(intro)
				}
			}
			uc.addr = sessAddr
			for i := 0; i < 10; i++ {
				_, _ = uc.Write(p83Sess)
			}
		}

		var sessFrom *net.UDPAddr
		var sessExpiry time.Time
		pEnd := time.Now().Add(5 * time.Second)
		_ = udp.SetDeadline(time.Now().Add(6 * time.Second))
		var lastPunch, lastSessPunch, lastIntro, last67, last41 time.Time
		for time.Now().Before(pEnd) {
			if time.Since(lastPunch) > 1500*time.Millisecond {
				punchTokens()
				lastPunch = time.Now()
			}
			if sessAddr != nil && p83Sess != nil && time.Since(lastSessPunch) > 250*time.Millisecond {
				uc.addr = sessAddr
				for i := 0; i < 4; i++ {
					_, _ = uc.Write(p83Sess)
				}
				lastSessPunch = time.Now()
			}
			if time.Since(last41) > 500*time.Millisecond {
				// relay-forwarded punches: the camera only escalates to
				// relay-media after these reach it and its replies to us fail
				for _, ra := range relaySeen {
					uc.addr = ra
					_, _ = uc.Write(punch)
				}
				sweepCam()
				last41 = time.Now()
			}
			if time.Since(last67) > 2500*time.Millisecond {
				for _, ra := range relaySeen {
					uc.addr = ra
					_, _ = uc.Write(req67)
				}
				last67 = time.Now()
			}
			if intro != nil && time.Since(lastIntro) > 2000*time.Millisecond {
				for _, ra := range relaySeen {
					uc.addr = ra
					for i := 0; i < 5; i++ {
						_, _ = uc.Write(intro)
					}
				}
				lastIntro = time.Now()
			}
			_ = uc.UDPConn.SetReadDeadline(time.Now().Add(200 * time.Millisecond))
			n, from, err := uc.UDPConn.ReadFromUDP(buf)
			if err != nil {
				if ne, ok := err.(net.Error); ok && ne.Timeout() {
					if sessFrom != nil && time.Now().After(sessExpiry) {
						break
					}
					continue
				}
				break
			}
			collect(n)
			if from.Port == 32100 {
				if _, ok := relaySeen[from.String()]; !ok {
					relaySeen[from.String()] = from
				}
			}
			if n < 4 || buf[0] != magic || from == nil {
				continue
			}
			switch buf[1] {
			case msgPunchPkt:
				uc.addr = from
				_, _ = uc.Write(punch)
				_, _ = uc.Write([]byte{magic, msgP2PRdyUDP, 0x00, 0x00})
			case msgP2PRdyUDP:
				return finish(from)
			case 0x81:
				if sessAddr != nil && p83Sess != nil {
					uc.addr = sessAddr
					for i := 0; i < 8; i++ {
						_, _ = uc.Write(p83Sess)
					}
				}
			case msgMediaPunchAck, 0x28:
				if sessFrom == nil {
					sessFrom = from
					sessExpiry = time.Now().Add(1200 * time.Millisecond)
					sweepCam()
				}
				if buf[1] == 0x28 && n >= 52 && buf[5] == 0x02 {
					peerPort := binary.LittleEndian.Uint16(buf[46:48])
					peerIP := net.IPv4(buf[51], buf[50], buf[49], buf[48])
					if !peerIP.IsPrivate() && !peerIP.IsLoopback() && peerIP.To4() != nil && peerPort != 0 {
						for dp := -3; dp <= 3; dp++ {
							uc.addr = &net.UDPAddr{IP: peerIP, Port: int(peerPort) + dp}
							_, _ = uc.Write(punch)
						}
					}
				}
				if buf[1] == 0x28 {
					reply := relaySessionReply(buf[:n], localIP, int(localPort))
					if len(reply) > 0 {
						uc.addr = from
						uc.rememberSessionEcho(reply)
						if uc.renewSession() == nil {
							return finish(from)
						}
					}
				}
				if buf[1] == 0x28 && n >= 6 {
					echo := make([]byte, n)
					copy(echo, buf[:n])
					echo[5] = 0x02
					if n >= 64 {
						copy(echo[60:64], []byte{0xfe, 0xff, 0xff, 0xff})
					}
					if n >= 84 && localPort != 0 && localIP != nil {
						echo[68], echo[69] = 0x00, 0x02
						binary.LittleEndian.PutUint16(echo[70:72], localPort)
						if v4 := localIP.To4(); v4 != nil {
							echo[72], echo[73], echo[74], echo[75] = v4[3], v4[2], v4[1], v4[0]
						}
					}
					uc.addr = sessFrom
					_, _ = uc.Write(echo)
				}
			}
			if sessFrom != nil && time.Now().After(sessExpiry) {
				break
			}
		}
		if sessFrom != nil {
			return finish(sessFrom)
		}
	}

	// ---- Phase D: legacy direct punch (punchable cameras) — but keep
	// collecting 0x82 handoffs meanwhile; the camera may only register
	// with relay-media seconds after waking. ----
	uc.addr = camAddr
	_ = uc.UDPConn.SetReadDeadline(time.Time{})
	_ = udp.SetDeadline(time.Now().Add(6 * time.Second))
	if _, err = uc.Write(reqF9); err != nil {
		_ = udp.Close()
		return nil, fmt.Errorf("cs2 cloud: send 0xF9: %w", err)
	}
	sweepCam()
	directOK := false
	p2 := time.Now().Add(4 * time.Second)
	var lastPunch time.Time
	for time.Now().Before(p2) {
		if time.Since(lastPunch) > 500*time.Millisecond {
			sweepCam()
			lastPunch = time.Now()
		}
		_ = uc.UDPConn.SetReadDeadline(time.Now().Add(200 * time.Millisecond))
		n, _, err := uc.UDPConn.ReadFromUDP(buf)
		if err != nil {
			if ne, ok := err.(net.Error); ok && ne.Timeout() {
				continue
			}
			break
		}
		collect(n)
		if n >= 4 && buf[0] == magic {
			if buf[1] == msgPunchPkt {
				uc.addr = camAddr
				_, _ = uc.Write(punch)
				_, _ = uc.Write([]byte{magic, msgP2PRdyUDP, 0x00, 0x00})
			} else if buf[1] == msgP2PRdyUDP {
				directOK = true
				break
			}
		}
	}
	if directOK {
		return finish(uc.addr)
	}

	// ---- Phase F: legacy punch through the relay (proven path for punchable
	// cameras) — falls through to the relay-media handoff on failure ----
	uc.addr = &net.UDPAddr{IP: net.ParseIP(relayHost), Port: 32100}
	_ = udp.SetDeadline(time.Now().Add(5 * time.Second))
	relayOK := false
	if _, err = uc.Write(reqF9); err == nil {
		res2, perr := uc.WriteUntil(punch, func(res []byte) bool {
			return res[1] == msgP2PRdyUDP || res[1] == msgPunchPkt
		})
		if perr == nil {
			if res2[1] == msgPunchPkt {
				_, err = uc.WriteUntil(res2, func(res []byte) bool {
					return res[1] == msgP2PRdyUDP
				})
				relayOK = err == nil
			} else {
				relayOK = true
			}
		}
	}
	if relayOK {
		return finish(uc.addr)
	}

	_ = udp.Close()
	return nil, fmt.Errorf("cs2 cloud: all punch paths failed")
}

type handoffTok struct {
	addr  *net.UDPAddr
	token [4]byte
}

const (
	msgCloudConnect  = 0x20
	msgCloudAck      = 0x21
	msgNatPunch      = 0x40
	msgMediaHandoff  = 0x82
	msgMediaPunch    = 0x83
	msgMediaPunchAck = 0x84
	msgCloudAuth     = 0xF9
)

// If media handoff candidates already exist, begin that negotiation while
// continuing direct punches there. Do not wait 1.5 seconds before starting it.
func directProbeWindow(mediaCandidates int) time.Duration {
	if mediaCandidates > 0 {
		return 250 * time.Millisecond
	}
	return 1500 * time.Millisecond
}
