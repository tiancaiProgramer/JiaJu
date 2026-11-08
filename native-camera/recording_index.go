package main

import (
	"encoding/binary"
	"errors"
	"sort"
	"time"
)

// Read-only RDT command 6. Never asks for a media file or writes card contents.
type recordingClip struct {
	Start    int64
	Duration int64
}
type recordingIndex struct {
	want int
	body []byte
}

func (r *recordingIndex) feed(plain []byte) ([]recordingClip, bool, error) {
	if r.want == 0 {
		if len(plain) < 8 {
			return nil, false, errors.New("short index header")
		}
		if binary.LittleEndian.Uint32(plain) != 6 {
			return nil, false, nil
		}
		r.want = int(binary.LittleEndian.Uint32(plain[4:]))
		if r.want < 4 || r.want > 2<<20 {
			return nil, false, errors.New("index size invalid")
		}
		if r.want%8 == 0 {
			r.want += 4
		}
		plain = plain[8:]
	}
	if len(r.body)+len(plain) > r.want {
		return nil, false, errors.New("index overflow")
	}
	r.body = append(r.body, plain...)
	if len(r.body) < r.want {
		return nil, false, nil
	}
	clips := []recordingClip{}
	seen := map[int64]bool{}
	for at := 4; at+8 <= len(r.body); at += 8 {
		start := int64(binary.LittleEndian.Uint32(r.body[at:]))
		flags := binary.LittleEndian.Uint32(r.body[at+4:])
		duration := int64(flags & 255)
		if start < 1577836800 || start > time.Now().Unix()+86400 || flags & ^uint32(511) != 0 || duration == 0 || seen[start] {
			continue
		}
		seen[start] = true
		clips = append(clips, recordingClip{start, duration})
	}
	sort.Slice(clips, func(i, j int) bool { return clips[i].Start < clips[j].Start })
	return clips, true, nil
}
func selectRecording(clips []recordingClip, wanted int64) (recordingClip, error) {
	for _, clip := range clips {
		if wanted >= clip.Start && wanted < clip.Start+clip.Duration {
			return clip, nil
		}
	}
	return recordingClip{}, errors.New("recording not listed")
}
