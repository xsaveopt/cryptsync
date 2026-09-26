package tools

import (
	"archive/zip"
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

var resticBz2 = []byte{
	0x42, 0x5a, 0x68, 0x39, 0x31, 0x41, 0x59, 0x26, 0x53, 0x59, 0x35, 0x36, 0x87, 0xee, 0x00, 0x00,
	0x04, 0x11, 0x80, 0x00, 0x02, 0x3b, 0x29, 0x1c, 0x20, 0x20, 0x00, 0x31, 0x00, 0xd0, 0x01, 0x02,
	0x69, 0x9a, 0x4f, 0x29, 0xca, 0xe8, 0xac, 0x1a, 0x91, 0x37, 0x93, 0xe4, 0x3e, 0x2e, 0xe4, 0x8a,
	0x70, 0xa1, 0x20, 0x6a, 0x6d, 0x0f, 0xdc,
}

type fakeTransport struct {
	files    map[string][]byte
	requests []string
}

func (f *fakeTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	f.requests = append(f.requests, req.URL.String())
	data, ok := f.files[req.URL.String()]
	status := http.StatusOK
	if !ok {
		status = http.StatusNotFound
	}
	return &http.Response{
		StatusCode: status,
		Status:     fmt.Sprintf("%d %s", status, http.StatusText(status)),
		Body:       io.NopCloser(bytes.NewReader(data)),
		Header:     http.Header{},
		Request:    req,
	}, nil
}

func useTransport(t *testing.T, rt http.RoundTripper) {
	t.Helper()
	orig := http.DefaultTransport
	http.DefaultTransport = rt
	t.Cleanup(func() { http.DefaultTransport = orig })
}

func useCacheDir(t *testing.T) string {
	t.Helper()
	dir := t.TempDir()
	t.Setenv("XDG_CACHE_HOME", dir)
	t.Setenv("HOME", dir)
	t.Setenv("LocalAppData", dir)
	got, err := binDir()
	if err != nil {
		t.Fatalf("binDir: %v", err)
	}
	return got
}

func makeZip(t *testing.T, entries map[string]string) []byte {
	t.Helper()
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	for name, content := range entries {
		w, err := zw.Create(name)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := w.Write([]byte(content)); err != nil {
			t.Fatal(err)
		}
	}
	if err := zw.Close(); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

func sumLine(data []byte, name string) string {
	s := sha256.Sum256(data)
	return hex.EncodeToString(s[:]) + "  " + name + "\n"
}

func releaseFiles(t *testing.T) map[string][]byte {
	t.Helper()
	resticExt := ".bz2"
	resticArchive := resticBz2
	if runtime.GOOS == "windows" {
		resticExt = ".zip"
		resticArchive = makeZip(t, map[string]string{"restic_" + ResticVersion + "_windows_" + runtime.GOARCH + ".exe": "fake-restic-binary"})
	}
	resticAsset := fmt.Sprintf("restic_%s_%s_%s%s", ResticVersion, runtime.GOOS, runtime.GOARCH, resticExt)
	resticBase := "https://github.com/restic/restic/releases/download/v" + ResticVersion

	osName := runtime.GOOS
	if osName == "darwin" {
		osName = "osx"
	}
	rcloneAsset := fmt.Sprintf("rclone-v%s-%s-%s.zip", RcloneVersion, osName, runtime.GOARCH)
	rcloneDir := strings.TrimSuffix(rcloneAsset, ".zip")
	rcloneArchive := makeZip(t, map[string]string{
		rcloneDir + "/README.txt":           "readme",
		rcloneDir + "/rclone" + exeSuffix(): "fake-rclone-binary",
	})
	rcloneBase := "https://downloads.rclone.org/v" + RcloneVersion

	return map[string][]byte{
		resticBase + "/" + resticAsset: resticArchive,
		resticBase + "/SHA256SUMS":     []byte(sumLine(resticArchive, resticAsset)),
		rcloneBase + "/" + rcloneAsset: rcloneArchive,
		rcloneBase + "/SHA256SUMS":     []byte(sumLine(rcloneArchive, rcloneAsset)),
	}
}

func TestEnsureDownloadsAndVerifies(t *testing.T) {
	dir := useCacheDir(t)
	ft := &fakeTransport{files: releaseFiles(t)}
	useTransport(t, ft)

	var logs []string
	set, err := Ensure(context.Background(), func(format string, args ...any) {
		logs = append(logs, fmt.Sprintf(format, args...))
	})
	if err != nil {
		t.Fatalf("Ensure: %v", err)
	}
	if set.Restic != filepath.Join(dir, "restic-"+ResticVersion+exeSuffix()) {
		t.Errorf("restic path = %q", set.Restic)
	}
	if set.Rclone != filepath.Join(dir, "rclone-"+RcloneVersion+exeSuffix()) {
		t.Errorf("rclone path = %q", set.Rclone)
	}
	if data, _ := os.ReadFile(set.Restic); string(data) != "fake-restic-binary" {
		t.Errorf("restic content = %q", data)
	}
	if data, _ := os.ReadFile(set.Rclone); string(data) != "fake-rclone-binary" {
		t.Errorf("rclone content = %q", data)
	}
	if len(ft.requests) != 4 {
		t.Errorf("requests = %v", ft.requests)
	}
	if len(logs) != 4 || !strings.Contains(logs[0], "restic "+ResticVersion) || !strings.Contains(logs[2], "rclone "+RcloneVersion) {
		t.Errorf("logs = %v", logs)
	}

	ft.requests = nil
	logs = nil
	again, err := Ensure(context.Background(), func(format string, args ...any) {
		logs = append(logs, fmt.Sprintf(format, args...))
	})
	if err != nil {
		t.Fatalf("second Ensure: %v", err)
	}
	if again != set {
		t.Errorf("second set = %+v, want %+v", again, set)
	}
	if len(ft.requests) != 0 || len(logs) != 0 {
		t.Errorf("cached tools should not be fetched again: requests=%v logs=%v", ft.requests, logs)
	}
}

func TestEnsureChecksumMismatch(t *testing.T) {
	useCacheDir(t)
	files := releaseFiles(t)
	for url := range files {
		if strings.Contains(url, "restic") && strings.HasSuffix(url, "SHA256SUMS") {
			files[url] = []byte(strings.Replace(string(files[url]), string(files[url][:8]), "00000000", 1))
		}
	}
	useTransport(t, &fakeTransport{files: files})
	_, err := Ensure(context.Background(), func(string, ...any) {})
	if err == nil || !strings.Contains(err.Error(), "restic: checksum mismatch") {
		t.Fatalf("expected restic checksum error, got %v", err)
	}
}

func TestEnsureRcloneMissing(t *testing.T) {
	useCacheDir(t)
	files := releaseFiles(t)
	for url := range files {
		if strings.Contains(url, "rclone.org") && !strings.HasSuffix(url, "SHA256SUMS") {
			delete(files, url)
		}
	}
	useTransport(t, &fakeTransport{files: files})
	_, err := Ensure(context.Background(), func(string, ...any) {})
	if err == nil || !strings.HasPrefix(err.Error(), "rclone: GET ") || !strings.Contains(err.Error(), "404") {
		t.Fatalf("expected rclone download error, got %v", err)
	}
}

func TestDownload(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/ok" {
			_, _ = w.Write([]byte("payload"))
			return
		}
		http.Error(w, "nope", http.StatusForbidden)
	}))
	defer srv.Close()

	data, err := download(context.Background(), srv.URL+"/ok")
	if err != nil || string(data) != "payload" {
		t.Fatalf("download = %q, %v", data, err)
	}
	if _, err := download(context.Background(), srv.URL+"/bad"); err == nil || !strings.Contains(err.Error(), "403") {
		t.Fatalf("expected status error, got %v", err)
	}
	if _, err := download(context.Background(), "://bad"); err == nil {
		t.Fatal("expected request error")
	}
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := download(ctx, srv.URL+"/ok"); !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context canceled, got %v", err)
	}
}

func TestExtractZipEntry(t *testing.T) {
	archive := makeZip(t, map[string]string{
		"pkg/":           "",
		"pkg/README":     "readme",
		"pkg/bin/rclone": "binary",
	})
	dest := filepath.Join(t.TempDir(), "out")
	err := extractZipEntry(archive, dest, func(name string) bool { return filepath.Base(name) == "rclone" })
	if err != nil {
		t.Fatalf("extractZipEntry: %v", err)
	}
	if data, _ := os.ReadFile(dest); string(data) != "binary" {
		t.Errorf("content = %q", data)
	}
}

func TestExtractZipEntryNoMatch(t *testing.T) {
	archive := makeZip(t, map[string]string{"a.txt": "a"})
	dest := filepath.Join(t.TempDir(), "out")
	if err := extractZipEntry(archive, dest, func(string) bool { return false }); err == nil {
		t.Fatal("expected no-match error")
	}
	if _, err := os.Stat(dest); !os.IsNotExist(err) {
		t.Errorf("dest should not exist, stat err = %v", err)
	}
}

func TestExtractZipEntryInvalidArchive(t *testing.T) {
	if err := extractZipEntry([]byte("not a zip"), filepath.Join(t.TempDir(), "out"), func(string) bool { return true }); err == nil {
		t.Fatal("expected error for invalid archive")
	}
}

func TestWriteExecutable(t *testing.T) {
	dest := filepath.Join(t.TempDir(), "bin")
	if err := os.WriteFile(dest, []byte("old"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := writeExecutable(dest, strings.NewReader("new content")); err != nil {
		t.Fatalf("writeExecutable: %v", err)
	}
	info, err := os.Stat(dest)
	if err != nil {
		t.Fatal(err)
	}
	if data, _ := os.ReadFile(dest); string(data) != "new content" {
		t.Errorf("content = %q", data)
	}
	if runtime.GOOS != "windows" && info.Mode().Perm()&0o100 == 0 {
		t.Errorf("mode = %v, want executable", info.Mode())
	}
	if _, err := os.Stat(dest + ".tmp"); !os.IsNotExist(err) {
		t.Errorf("tmp file left behind: %v", err)
	}
}

type failingReader struct{}

func (failingReader) Read([]byte) (int, error) { return 0, errors.New("read failed") }

func TestWriteExecutableReadError(t *testing.T) {
	dest := filepath.Join(t.TempDir(), "bin")
	if err := writeExecutable(dest, failingReader{}); err == nil {
		t.Fatal("expected read error")
	}
	for _, p := range []string{dest, dest + ".tmp"} {
		if _, err := os.Stat(p); !os.IsNotExist(err) {
			t.Errorf("%s should not exist, stat err = %v", p, err)
		}
	}
}

func TestWriteExecutableMissingDir(t *testing.T) {
	if err := writeExecutable(filepath.Join(t.TempDir(), "missing", "bin"), strings.NewReader("x")); err == nil {
		t.Fatal("expected error for missing directory")
	}
}

func TestExecutable(t *testing.T) {
	dir := t.TempDir()
	empty := filepath.Join(dir, "empty")
	full := filepath.Join(dir, "full")
	if err := os.WriteFile(empty, nil, 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(full, []byte("x"), 0o755); err != nil {
		t.Fatal(err)
	}
	cases := map[string]bool{
		filepath.Join(dir, "missing"): false,
		dir:                           false,
		empty:                         false,
		full:                          true,
	}
	for p, want := range cases {
		if got := executable(p); got != want {
			t.Errorf("executable(%q) = %v, want %v", p, got, want)
		}
	}
}

func TestExeSuffix(t *testing.T) {
	want := ""
	if runtime.GOOS == "windows" {
		want = ".exe"
	}
	if got := exeSuffix(); got != want {
		t.Errorf("exeSuffix = %q, want %q", got, want)
	}
}
