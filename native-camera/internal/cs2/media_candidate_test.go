package cs2

import (
	"net"
	"testing"
)

func TestMediaCandidateFallbackSkipsFailedEndpoint(t *testing.T) {
	first := &net.UDPAddr{IP: net.IPv4(192, 0, 2, 1), Port: 32100}
	second := &net.UDPAddr{IP: net.IPv4(192, 0, 2, 2), Port: 32100}
	// Different handoff tokens for the same failed endpoint are not new peers.
	tokens := []handoffTok{{addr: first}, {addr: first, token: [4]byte{1}}, {addr: second}}
	failed := map[string]bool{}
	if nextMediaCandidate(tokens, failed) != first {
		t.Fatal("first candidate missing")
	}
	failed[first.String()] = true
	if nextMediaCandidate(tokens, failed) != second {
		t.Fatal("failed media endpoint selected again")
	}
	failed[second.String()] = true
	if nextMediaCandidate(tokens, failed) != nil {
		t.Fatal("exhausted media candidates were retried")
	}
	if maxMediaHandoffAttempts != 2 {
		t.Fatal("media fallback must stay bounded at two candidates")
	}
}
