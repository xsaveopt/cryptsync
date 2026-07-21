package tools

import (
	"crypto/sha256"
	"encoding/hex"
	"testing"
)

func TestVerifyMatches(t *testing.T) {
	data := []byte("hello restic")
	sum := sha256.Sum256(data)
	sums := "0000  other_file\n" + hex.EncodeToString(sum[:]) + "  restic_0.19.0_linux_amd64.bz2\n"
	if err := verify(data, []byte(sums), "restic_0.19.0_linux_amd64.bz2"); err != nil {
		t.Fatalf("verify: %v", err)
	}
}

func TestVerifyStarPrefix(t *testing.T) {
	data := []byte("rclone bytes")
	sum := sha256.Sum256(data)
	sums := hex.EncodeToString(sum[:]) + " *rclone-v1.74.3-osx-arm64.zip\n"
	if err := verify(data, []byte(sums), "rclone-v1.74.3-osx-arm64.zip"); err != nil {
		t.Fatalf("verify with star prefix: %v", err)
	}
}

func TestVerifyMismatch(t *testing.T) {
	sums := "deadbeef  restic_0.19.0_linux_amd64.bz2\n"
	if err := verify([]byte("data"), []byte(sums), "restic_0.19.0_linux_amd64.bz2"); err == nil {
		t.Fatal("expected mismatch error")
	}
}

func TestVerifyMissing(t *testing.T) {
	if err := verify([]byte("data"), []byte("abc  something_else\n"), "restic_0.19.0_linux_amd64.bz2"); err == nil {
		t.Fatal("expected missing-checksum error")
	}
}
