package cs2

import (
	"bytes"
	"errors"
	"net"
	"testing"
)

type recordedDirectoryWrite struct {
	addr *net.UDPAddr
	data []byte
}
type recordingDirectoryWriter struct {
	writes []recordedDirectoryWrite
	err    error
}

func (w *recordingDirectoryWriter) WriteToUDP(data []byte, addr *net.UDPAddr) (int, error) {
	if w.err != nil {
		return 0, w.err
	}
	w.writes = append(w.writes, recordedDirectoryWrite{addr: addr, data: bytes.Clone(data)})
	return len(data), nil
}

func TestDirectoryResendsUseAllExplicitTargets(t *testing.T) {
	// No sockets: changing the active direct-probe peer must not redirect lookups.
	active := &udpConn{addr: &net.UDPAddr{IP: net.IPv4(10, 1, 2, 3), Port: 32108}}
	w := &recordingDirectoryWriter{}
	targets := []*net.UDPAddr{
		{IP: net.IPv4(192, 0, 2, 1), Port: 32100},
		{IP: net.IPv4(192, 0, 2, 2), Port: 32100},
	}
	packet := []byte{magic, msgCloudConnect, 0, 0}
	if err := sendDirectoryBurst(w, targets, packet, 3); err != nil {
		t.Fatal(err)
	}
	if len(w.writes) != 6 {
		t.Fatal("not every ticket directory received the burst")
	}
	for i, write := range w.writes {
		if write.addr != targets[i/3] || !bytes.Equal(write.data, packet) || write.addr == active.addr {
			t.Fatal("directory request was redirected to a probe peer")
		}
	}
	if active.addr.Port != 32108 || !active.addr.IP.Equal(net.IPv4(10, 1, 2, 3)) {
		t.Fatal("directory burst changed active probe peer")
	}
}

func TestDirectoryBurstReturnsWriteFailure(t *testing.T) {
	want := errors.New("fixture write failure")
	w := &recordingDirectoryWriter{err: want}
	err := sendDirectoryBurst(w, []*net.UDPAddr{{Port: 32100}}, []byte{magic}, 3)
	if !errors.Is(err, want) || len(w.writes) != 0 {
		t.Fatal("directory write failure was discarded or retried")
	}
}
