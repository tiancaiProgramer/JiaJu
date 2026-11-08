package cs2

import "net"

type directoryWriter interface {
	WriteToUDP([]byte, *net.UDPAddr) (int, error)
}

// A direct probe changes udpConn.addr. Directory requests must always use the
// ticket's explicit directory targets, including resends after a LAN response.
func sendDirectoryBurst(writer directoryWriter, targets []*net.UDPAddr, packet []byte, count int) error {
	for _, target := range targets {
		for i := 0; i < count; i++ {
			if _, err := writer.WriteToUDP(packet, target); err != nil {
				return err
			}
		}
	}
	return nil
}
