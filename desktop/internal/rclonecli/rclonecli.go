package rclonecli

import (
	"bytes"
	"context"
	"fmt"
	"os/exec"
	"strings"
)

func Authorize(ctx context.Context, rcloneBin string) (string, error) {
	cmd := exec.CommandContext(ctx, rcloneBin, "authorize", "drive")
	var out bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = &out
	if err := cmd.Run(); err != nil {
		return "", fmt.Errorf("rclone authorize: %w: %s", err, tail(out.String()))
	}
	return extractToken(out.String())
}

func extractToken(output string) (string, error) {
	const startMarker = "--->"
	const endMarker = "<---"
	start := strings.Index(output, startMarker)
	end := strings.Index(output, endMarker)
	if start < 0 || end < 0 || end <= start {
		return "", fmt.Errorf("no token found in rclone output")
	}
	token := strings.TrimSpace(output[start+len(startMarker) : end])
	if !strings.HasPrefix(token, "{") {
		return "", fmt.Errorf("unexpected token format from rclone")
	}
	return token, nil
}

func tail(s string) string {
	lines := strings.Split(strings.TrimSpace(s), "\n")
	if len(lines) > 5 {
		lines = lines[len(lines)-5:]
	}
	return strings.Join(lines, "\n")
}
