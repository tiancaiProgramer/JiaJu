package main

import (
	"encoding/binary"
	"encoding/json"
	"testing"
	"time"
)

func TestCameraControlPayloads(t *testing.T) {
	for _, direction := range []int{0, -1, 5, 999} {
		if _, err := motorPayload(direction); err == nil {
			t.Fatal("invalid direction accepted")
		}
	}
	for direction := 1; direction <= 4; direction++ {
		body, err := motorPayload(direction)
		var payload map[string]int
		if err != nil || json.Unmarshal(body, &payload) != nil || payload["operation"] != direction || len(payload) != 1 {
			t.Fatal("motor payload changed")
		}
	}
	now := time.Unix(1800000000, 0)
	body, err := playbackPayload(now.Unix()-600, now.Unix(), now)
	var payload map[string]int64
	if err != nil || json.Unmarshal(body, &payload) != nil || payload["autoswitchtolive"] != 0 || payload["starttime"] != now.Unix()-600 || payload["sessionid"] != payload["starttime"] || payload["speed"] != 1 {
		t.Fatal("playback payload invalid")
	}
	for _, bounds := range [][2]int64{{0, 1}, {1800001000, 1800001100}, {1800000000, 1800000000}, {1700000000, 1800000000}} {
		if _, err := playbackPayload(bounds[0], bounds[1], now); err == nil {
			t.Fatal("invalid playback accepted")
		}
	}
}
func TestCameraControlReply(t *testing.T) {
	if !playbackFound([]byte(`{"status":"filefound","starttime":1800000000}`), 1800000000) || playbackFound([]byte(`{"status":"filefound","starttime":1800000001}`), 1800000000) || playbackFound([]byte(`{"status":"filenotfound","starttime":1800000000}`), 1800000000) {
		t.Fatal("playback must confirm the requested clip")
	}
	if !commandAccepted([]byte{0, 0, 0, 0}) || commandAccepted([]byte{1, 0, 0, 0}) || commandAccepted([]byte{0, 0, 0}) {
		t.Fatal("binary response must be exactly a zero int32")
	}
	for _, body := range []string{`{"result":0}`, `{"code":0}`, `{"result":"ok"}`, `{"result":"success"}`, "{\"result\":0}\x00"} {
		if !commandAccepted([]byte(body)) {
			t.Fatal("valid ack rejected")
		}
	}
	for _, body := range []string{`{}`, `{"result":1}`, `{"result":false}`, `{"result":[0]}`, `bad`} {
		if commandAccepted([]byte(body)) {
			t.Fatal("unknown ack accepted")
		}
	}
	key := make([]byte, 32)
	nonce := make([]byte, 8)
	plain := make([]byte, 4)
	binary.BigEndian.PutUint32(plain, 0x107)
	plain = append(plain, []byte(`{"result":0}`)...)
	encrypted, err := cipher(plain, key, nonce)
	if err != nil {
		t.Fatal(err)
	}
	cmd, decoded := decodeControlReply(0x1001, append(nonce, encrypted...), key)
	if cmd != 0x107 || !commandAccepted(decoded) {
		t.Fatal("encrypted reply decode failed")
	}
	cmd, _ = decodeControlReply(0x1001, make([]byte, 11), key)
	if cmd != 0 {
		t.Fatal("short packet accepted")
	}
	cmd, _ = decodeControlReply(0x1001, make([]byte, 8193), key)
	if cmd != 0 {
		t.Fatal("oversize packet accepted")
	}
}
