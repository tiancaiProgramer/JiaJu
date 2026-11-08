package main

import (
	"encoding/binary"
	"testing"
	"time"
)

func TestRecordingIndexFragmentsAndSelection(t *testing.T) {
	now := time.Now().Unix() - 300
	table := make([]byte, 4+24)
	for i := 0; i < 3; i++ {
		binary.LittleEndian.PutUint32(table[4+i*8:], uint32(now+int64(i)*60))
		binary.LittleEndian.PutUint32(table[8+i*8:], 316)
	}
	plain := make([]byte, 8)
	binary.LittleEndian.PutUint32(plain, 6)
	binary.LittleEndian.PutUint32(plain[4:], uint32(len(table)))
	plain = append(plain, table...)
	var reader recordingIndex
	clips, done, err := reader.feed(plain[:15])
	if err != nil || done || clips != nil {
		t.Fatal("partial index accepted")
	}
	clips, done, err = reader.feed(plain[15:])
	if err != nil || !done || len(clips) != 3 || clips[0].Duration != 60 {
		t.Fatal("index fragments/flags invalid")
	}
	clip, err := selectRecording(clips, now+83)
	if err != nil || clip.Start != now+60 {
		t.Fatal("did not choose exact clip boundary")
	}
	if _, err = selectRecording(clips, now+181); err == nil {
		t.Fatal("invented missing clip")
	}
}
func TestRecordingIndexBounds(t *testing.T) {
	for _, size := range []uint32{0, 3, 2<<20 + 1, 0xffffffff} {
		var r recordingIndex
		header := make([]byte, 8)
		binary.LittleEndian.PutUint32(header, 6)
		binary.LittleEndian.PutUint32(header[4:], size)
		if _, _, err := r.feed(header); err == nil {
			t.Fatal("unsafe index size accepted")
		}
	}
	var r recordingIndex
	header := make([]byte, 13)
	binary.LittleEndian.PutUint32(header, 6)
	binary.LittleEndian.PutUint32(header[4:], 4)
	if _, _, err := r.feed(header); err == nil {
		t.Fatal("overflow ignored")
	}
}
