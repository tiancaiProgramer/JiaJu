package cs2

import (
	"bytes"
	"encoding/binary"
	"fmt"
	"net"
	"testing"
	"time"
)

func TestMediaWarmupCanStopWithoutAnotherFrame(t *testing.T) {
	c := &Conn{}
	c.channels[2] = newDataChannel(0, 2)
	stop := make(chan struct{})
	done := make(chan struct{})
	go func() { c.DiscardMediaUntil(stop); close(done) }()
	close(stop)
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("warmup waited for a frame after cancellation")
	}
	c.channels[2].Close()
	done = make(chan struct{})
	go func() { c.DiscardQueuedMedia(); close(done) }()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("closed media queue did not stop draining")
	}
}

func TestCloudFailureDiagnosticsAreFixedEnums(t *testing.T) {
	for err, want := range map[error]string{
		ErrCloudLookupTimeout:                     "cloud_lookup_timeout",
		ErrCloudNoMediaHandoff:                    "cloud_no_media_handoff",
		ErrCloudRelayNotReady:                     "cloud_relay_not_ready",
		fmt.Errorf("private endpoint and ticket"): "cloud_connect_failed",
	} {
		if got := CloudFailureCode(fmt.Errorf("wrapped: %w", err)); got != want {
			t.Fatalf("diagnostic = %q, want %q", got, want)
		}
	}
}

func TestDirectoryKnownWireSample(t *testing.T) {
	// Public PPPP_DecodeString interoperability sample, not a user ticket.
	init := "EDHNFGBILOINGGIGFDHHFKFIGNNIHDMFHCEEABDGBAIHKPKCCMBLCPOJHBKGJGKABDMJLKCGPFMH"
	servers := DirectoryServers(init + ":camera")
	want := []string{"3.10.99.101", "139.9.86.167", "3.227.45.161"}
	if len(servers) != len(want) {
		t.Fatalf("directory count = %d", len(servers))
	}
	for i := range want {
		if servers[i] != want[i] {
			t.Fatalf("directory %d mismatch", i)
		}
	}
	for _, bad := range []string{"", "A", "ZZ", "AAAA", "EBGDEOBOKGICGAIAFFGNEAFMGPMHGFNDGNFLBHDKADJGLBKFDOBEDGPEHAKBIJLMBG"} {
		if len(DirectoryServers(bad)) != 0 {
			t.Fatal("accepted invalid or loopback directory")
		}
	}
}
func TestExactUIDBlock(t *testing.T) {
	b, err := buildP2PIDBlock("XMSYCNB-303021-WPSGK")
	if err != nil {
		t.Fatal(err)
	}
	if string(b[:7]) != "XMSYCNB" || binary.BigEndian.Uint32(b[8:12]) != 303021 || string(b[12:17]) != "WPSGK" {
		t.Fatal("UID block corrupted")
	}
	for _, bad := range []string{"bad", "TOOLONGXX-1-ABCDE", "A--B", "A-4294967296-B"} {
		if _, err := buildP2PIDBlock(bad); err == nil {
			t.Fatal("accepted malformed UID")
		}
	}
}
func TestBoundedChannelAssembly(t *testing.T) {
	c := newDataChannel(0, 2)
	payload := []byte{1, 2, 3, 4, 5}
	wire := binary.BigEndian.AppendUint32(nil, uint32(len(payload)))
	wire = append(wire, payload...)
	for _, fragment := range [][]byte{wire[:3], wire[3:6], wire[6:]} {
		if err := c.Push(fragment); err != nil {
			t.Fatal(err)
		}
	}
	got, ok := c.Pop()
	if !ok || len(got) != 5 || got[4] != 5 {
		t.Fatal("fragmented frame not assembled")
	}
	for _, size := range []uint32{0, 4*1024*1024 + 1, 0xffffffff} {
		d := newDataChannel(0, 1)
		wire := binary.BigEndian.AppendUint32(nil, size)
		wire = append(wire, 0)
		if d.Push(wire) == nil {
			t.Fatal("unbounded media length accepted")
		}
	}
}

func TestDirectProbeBudgetKeepsFallback(t *testing.T) {
	if directProbeWindow(0) != 1500*time.Millisecond {
		t.Fatal("unavailable relay changed direct fallback")
	}
	if directProbeWindow(1) != 250*time.Millisecond || directProbeWindow(4) != 250*time.Millisecond {
		t.Fatal("media discovery still waits serially")
	}
}

func TestSessionReplyWireAndBoundaries(t *testing.T) {
	packet := make([]byte, 92)
	packet[0] = magic
	packet[1] = 0x28
	binary.BigEndian.PutUint16(packet[2:4], 88)
	packet[4] = 0xa1
	packet[5] = 1
	copy(packet[8:12], []byte{1, 2, 3, 4})
	binary.LittleEndian.PutUint16(packet[46:48], 45678)
	copy(packet[48:52], []byte{9, 100, 51, 198})
	reply := relaySessionReply(packet, net.IPv4(192, 168, 1, 2), 34567)
	if len(reply) != 92 || reply[5] != 2 || packet[5] != 1 {
		t.Fatal("request mutated or response invalid")
	}
	if !bytes.Equal(reply[64:68], []byte{4, 3, 2, 1}) || !bytes.Equal(reply[60:64], []byte{254, 255, 255, 255}) {
		t.Fatal("session marker invalid")
	}
	if binary.LittleEndian.Uint16(reply[70:72]) != 34567 || !bytes.Equal(reply[72:76], []byte{2, 1, 168, 192}) {
		t.Fatal("local sockaddr invalid")
	}
	if binary.BigEndian.Uint16(reply[86:88]) != 45678 || !bytes.Equal(reply[88:92], []byte{198, 51, 100, 9}) {
		t.Fatal("external sockaddr invalid")
	}
	if relaySessionReply(reply, net.IPv4(192, 168, 1, 2), 34567) != nil {
		t.Fatal("reply loop allowed")
	}
	for i := 0; i < 92; i++ {
		if relaySessionReply(packet[:i], net.IPv4(192, 168, 1, 2), 1) != nil {
			t.Fatal("short request accepted")
		}
	}
	if relaySessionReply(packet, net.IPv6loopback, 1) != nil || relaySessionReply(packet, net.IPv4(192, 168, 1, 2), 0) != nil {
		t.Fatal("invalid local address accepted")
	}
	packet[2] = 0xff
	if relaySessionReply(packet, net.IPv4(192, 168, 1, 2), 1) != nil {
		t.Fatal("oversize length accepted")
	}
}

func TestCommandReplyQueueRequiresConsumer(t *testing.T) {
	wire := binary.BigEndian.AppendUint32(nil, 5)
	wire = append(wire, 1, 2, 3, 4, 5)
	unread := newDataChannel(0, 10)
	for i := 0; i < 10; i++ {
		if err := unread.Push(wire); err != nil {
			t.Fatal(err)
		}
	}
	if unread.Push(wire) == nil {
		t.Fatal("regression fixture did not reproduce unread reply overflow")
	}
	drained := newDataChannel(0, 10)
	for i := 0; i < 100; i++ {
		if err := drained.Push(wire); err != nil {
			t.Fatal("consumed replies overflowed")
		}
		if data, ok := drained.Pop(); !ok || len(data) != 5 {
			t.Fatal("reply drain lost framing")
		}
	}
}
