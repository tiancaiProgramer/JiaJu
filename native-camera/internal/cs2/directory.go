package cs2

import (
	"encoding/hex"
	"net"
	"strings"
)

// PPPP initializer wire format: A-P nibbles, fixed XOR table, cumulative
// plaintext XOR starting at 0x39. Protocol reference links are in README.md.
// Only public unicast IPv4 directory servers from this camera's own cloud
// ticket are accepted. No baked-in server pool or account identifier is used.
func DirectoryServers(initializer string) []string {
	encoded := strings.SplitN(initializer, ":", 2)[0]
	if len(encoded) < 2 || len(encoded) > 1024 || len(encoded)%2 != 0 {
		return nil
	}
	table, _ := hex.DecodeString("4959433db5bf6da347534f6165e371e9677f02030badb3892b2f35c16b8b959711e5a70deff1050783fb9d3bc5c713171d1f2529d3df")
	plain := make([]byte, len(encoded)/2)
	running := byte(0x39)
	for i := range plain {
		a, b := encoded[2*i], encoded[2*i+1]
		if a < 'A' || a > 'P' || b < 'A' || b > 'P' {
			return nil
		}
		plain[i] = ((a-'A')<<4 | (b - 'A')) ^ table[i%len(table)] ^ running
		running ^= plain[i]
	}
	var servers []string
	for _, entry := range strings.Split(string(plain), ",") {
		if entry == "" {
			continue
		}
		ip := net.ParseIP(entry)
		if ip == nil || ip.To4() == nil || !ip.IsGlobalUnicast() || ip.IsPrivate() {
			return nil
		}
		duplicate := false
		for _, old := range servers {
			duplicate = duplicate || old == ip.String()
		}
		if !duplicate {
			servers = append(servers, ip.String())
		}
		if len(servers) > 8 {
			return nil
		}
	}
	return servers
}
