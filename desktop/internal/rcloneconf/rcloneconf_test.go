package rcloneconf

import (
	"strings"
	"testing"
)

func TestDrive(t *testing.T) {
	got := Drive("gdrive", `  {"access_token":"x"}  `)
	want := "[gdrive]\ntype = drive\nscope = drive\ntoken = {\"access_token\":\"x\"}\n"
	if got != want {
		t.Errorf("Drive() =\n%q\nwant\n%q", got, want)
	}
}

func TestRawRemoteStripsHeader(t *testing.T) {
	body := "[old]\ntype = s3\nprovider = Minio\n"
	got := RawRemote("gdrive", body)
	if !strings.HasPrefix(got, "[gdrive]\n") {
		t.Errorf("missing renamed header: %q", got)
	}
	if strings.Contains(got, "[old]") {
		t.Errorf("original header not stripped: %q", got)
	}
	if !strings.Contains(got, "type = s3") || !strings.Contains(got, "provider = Minio") {
		t.Errorf("body lost: %q", got)
	}
}

func TestRawRemoteNoHeader(t *testing.T) {
	got := RawRemote("gdrive", "type = webdav\nurl = https://example.com\n")
	want := "[gdrive]\ntype = webdav\nurl = https://example.com\n"
	if got != want {
		t.Errorf("RawRemote() =\n%q\nwant\n%q", got, want)
	}
}
