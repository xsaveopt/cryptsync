package rcloneconf

import "strings"

func Drive(remote, tokenJSON string) string {
	var b strings.Builder
	b.WriteString("[" + remote + "]\n")
	b.WriteString("type = drive\n")
	b.WriteString("scope = drive\n")
	b.WriteString("token = " + strings.TrimSpace(tokenJSON) + "\n")
	return b.String()
}

func RawRemote(remote, body string) string {
	lines := strings.Split(strings.TrimSpace(body), "\n")
	for len(lines) > 0 && strings.TrimSpace(lines[0]) == "" {
		lines = lines[1:]
	}
	if len(lines) > 0 && strings.HasPrefix(strings.TrimSpace(lines[0]), "[") {
		lines = lines[1:]
	}
	var b strings.Builder
	b.WriteString("[" + remote + "]\n")
	for _, l := range lines {
		b.WriteString(l + "\n")
	}
	return b.String()
}
