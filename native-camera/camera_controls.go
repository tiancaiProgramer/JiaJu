package main

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"math/bits"
	"time"
)

// Fixed status field names and bounded numeric codes only; never log a reply body.
func replyShape(body []byte) string {
	if len(body) == 4 {
		a, b := binary.LittleEndian.Uint32(body), binary.BigEndian.Uint32(body)
		if a <= 65535 || b <= 65535 {
			return fmt.Sprintf("binary32_le_%d_be_%d", a, b)
		}
		return "binary32_other"
	}
	var value map[string]interface{}
	if json.Unmarshal(bytes.Trim(body, "\x00 \r\n\t"), &value) != nil {
		return fmt.Sprintf("non_json_reply_length_%d", len(body))
	}
	for _, key := range []string{"result", "code", "ret", "status", "error"} {
		if raw, ok := value[key]; ok {
			switch v := raw.(type) {
			case float64:
				if v >= -1000 && v <= 1000 && v == float64(int(v)) {
					return fmt.Sprintf("%s_number_%d", key, int(v))
				}
			case string:
				if v == "ok" || v == "success" || v == "0" || v == "fail" || v == "failed" || v == "filefound" || v == "filenotfound" {
					return key + "_text_" + v
				}
				return key + "_text_other"
			case bool:
				if v {
					return key + "_true"
				}
				return key + "_false"
			default:
				return key + "_structured"
			}
		}
	}
	return "json_unknown_status"
}

func motorPayload(operation int) ([]byte, error) {
	if operation < 1 || operation > 4 {
		return nil, errors.New("invalid direction")
	}
	return json.Marshal(map[string]int{"operation": operation})
}
func playbackPayload(start, end int64, now time.Time) ([]byte, error) {
	if start < 946684800 || start > now.Unix()+60 || end <= start || end-start > 86400 {
		return nil, errors.New("invalid playback interval")
	}
	return json.Marshal(map[string]interface{}{"sessionid": start, "starttime": start, "endtime": end, "offset": 0, "speed": 1, "avchannelmerge": 1, "autoswitchtolive": 0})
}
func decodeControlReply(cmd uint32, data, key []byte) (uint32, []byte) {
	if cmd == 0x1001 || bits.ReverseBytes32(cmd) == 0x1001 {
		if len(data) < 12 || len(data) > 8192 {
			return 0, nil
		}
		plain, err := cipher(data[8:], key, data[:8])
		if err != nil || len(plain) < 4 {
			return 0, nil
		}
		return binary.BigEndian.Uint32(plain[:4]), plain[4:]
	}
	if cmd > 0xffff {
		cmd = bits.ReverseBytes32(cmd)
	}
	return cmd, data
}
func commandAccepted(body []byte) bool {
	if len(body) == 4 {
		return binary.LittleEndian.Uint32(body) == 0
	}
	var result map[string]interface{}
	if json.Unmarshal(bytes.Trim(body, "\x00 \r\n\t"), &result) != nil {
		return false
	}
	for _, key := range []string{"result", "code"} {
		if value, ok := result[key]; ok {
			switch v := value.(type) {
			case string:
				return v == "success" || v == "ok"
			case float64:
				return v == 0
			}
		}
	}
	return false
}

func playbackFound(body []byte, start int64) bool {
	var status struct {
		Status string `json:"status"`
		Start  int64  `json:"starttime"`
	}
	return json.Unmarshal(bytes.Trim(body, "\x00 \r\n\t"), &status) == nil && status.Status == "filefound" && status.Start == start
}
