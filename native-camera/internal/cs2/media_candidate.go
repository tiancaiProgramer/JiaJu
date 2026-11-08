package cs2

import "net"

const maxMediaHandoffAttempts = 2

func nextMediaCandidate(tokens []handoffTok, failed map[string]bool) *net.UDPAddr {
	for _, token := range tokens {
		if token.addr != nil && !failed[token.addr.String()] {
			return token.addr
		}
	}
	return nil
}

func (c *Conn) MediaHandoffAttempts() int {
	if udp, ok := c.Conn.(*udpConn); ok {
		return udp.handoffAttempts
	}
	return 0
}
