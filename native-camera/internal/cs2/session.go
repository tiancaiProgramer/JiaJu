package cs2

import (
	"bytes"
	"encoding/binary"
	"net"
)

// Session linking wire format, from protocol interoperability observations.
// Only respond to a complete request from the already selected media peer.
// No account material or raw packets are logged or persisted.
func relaySessionReply(packet []byte, localIP net.IP, localPort int) []byte {
	if len(packet) < 92 || packet[0] != magic || packet[1] != 0x28 || packet[4] != 0xa1 || packet[5] != 1 {
		return nil
	}
	size := int(binary.BigEndian.Uint16(packet[2:4]))
	if size < 88 || size > len(packet)-4 || size > 4096 {
		return nil
	}
	ip := localIP.To4()
	if ip == nil || localPort < 1 || localPort > 65535 {
		return nil
	}
	reply := bytes.Clone(packet[:size+4])
	reply[5] = 2
	copy(reply[60:64], []byte{0xfe, 0xff, 0xff, 0xff})
	for i := 0; i < 4; i++ {
		reply[64+i] = packet[11-i]
	}
	for i := 68; i < 84; i++ {
		reply[i] = 0
	}
	reply[68] = 0
	reply[69] = 2
	binary.LittleEndian.PutUint16(reply[70:72], uint16(localPort))
	for i := 0; i < 4; i++ {
		reply[72+i] = ip[3-i]
	}
	reply[84] = 2
	reply[85] = 0
	binary.BigEndian.PutUint16(reply[86:88], binary.LittleEndian.Uint16(packet[46:48]))
	for i := 0; i < 4; i++ {
		reply[88+i] = packet[51-i]
	}
	return reply
}
func (c *udpConn) rememberSessionEcho(reply []byte) {
	if len(reply) == 0 {
		return
	}
	c.renewMu.Lock()
	c.sessionEcho = bytes.Clone(reply)
	c.renewMu.Unlock()
}
func (c *udpConn) renewSession() error {
	c.renewMu.Lock()
	reply := bytes.Clone(c.sessionEcho)
	c.renewMu.Unlock()
	if len(reply) == 0 {
		return nil
	}
	_, err := c.Write(reply)
	if err == nil {
		c.renewals.Add(1)
	}
	return err
}
func (c *Conn) SessionRenewals() uint64 {
	if udp, ok := c.Conn.(*udpConn); ok {
		return udp.renewals.Load()
	}
	return 0
}
