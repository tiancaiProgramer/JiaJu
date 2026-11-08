package main

import "testing"

func TestCameraModelScope(t *testing.T) {
	for _, model := range []string{"chuangmi.camera.069a01", "chuangmi.camera.039c01", "xiaomi.camera.c302"} {
		if !supportedCameraModel(model) {
			t.Fatalf("supported model rejected: %s", model)
		}
	}
	for _, model := range []string{"", "other.camera", "chuangmi.camera.039a01", "chuangmi.camera.029a02", "xiaomi.camera.c302n", "chuangmi.camera.039c01.extra"} {
		if supportedCameraModel(model) {
			t.Fatalf("unverified model accepted: %s", model)
		}
	}
}
