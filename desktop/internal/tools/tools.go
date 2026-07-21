package tools

import (
	"archive/zip"
	"bytes"
	"compress/bzip2"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

const (
	ResticVersion = "0.19.0"
	RcloneVersion = "1.74.3"
)

type Set struct {
	Restic string
	Rclone string
}

type Logf func(format string, args ...any)

func Ensure(ctx context.Context, log Logf) (Set, error) {
	dir, err := binDir()
	if err != nil {
		return Set{}, err
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return Set{}, err
	}

	restic := filepath.Join(dir, "restic-"+ResticVersion+exeSuffix())
	if !executable(restic) {
		log("Downloading restic %s for %s/%s…", ResticVersion, runtime.GOOS, runtime.GOARCH)
		if err := fetchRestic(ctx, restic); err != nil {
			return Set{}, fmt.Errorf("restic: %w", err)
		}
		log("restic ready.")
	}

	rclone := filepath.Join(dir, "rclone-"+RcloneVersion+exeSuffix())
	if !executable(rclone) {
		log("Downloading rclone %s for %s/%s…", RcloneVersion, runtime.GOOS, runtime.GOARCH)
		if err := fetchRclone(ctx, rclone); err != nil {
			return Set{}, fmt.Errorf("rclone: %w", err)
		}
		log("rclone ready.")
	}

	return Set{Restic: restic, Rclone: rclone}, nil
}

func binDir() (string, error) {
	base, err := os.UserCacheDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(base, "cryptsync-browser", "bin"), nil
}

func exeSuffix() string {
	if runtime.GOOS == "windows" {
		return ".exe"
	}
	return ""
}

func executable(path string) bool {
	info, err := os.Stat(path)
	if err != nil {
		return false
	}
	return !info.IsDir() && info.Size() > 0
}

func fetchRestic(ctx context.Context, dest string) error {
	ext := ".bz2"
	if runtime.GOOS == "windows" {
		ext = ".zip"
	}
	asset := fmt.Sprintf("restic_%s_%s_%s%s", ResticVersion, runtime.GOOS, runtime.GOARCH, ext)
	base := "https://github.com/restic/restic/releases/download/v" + ResticVersion
	archive, err := download(ctx, base+"/"+asset)
	if err != nil {
		return err
	}
	sums, err := download(ctx, base+"/SHA256SUMS")
	if err != nil {
		return err
	}
	if err := verify(archive, sums, asset); err != nil {
		return err
	}

	if runtime.GOOS == "windows" {
		return extractZipEntry(archive, dest, func(name string) bool {
			return strings.HasSuffix(name, ".exe")
		})
	}
	return writeExecutable(dest, bzip2.NewReader(bytes.NewReader(archive)))
}

func fetchRclone(ctx context.Context, dest string) error {
	osName := runtime.GOOS
	if osName == "darwin" {
		osName = "osx"
	}
	asset := fmt.Sprintf("rclone-v%s-%s-%s.zip", RcloneVersion, osName, runtime.GOARCH)
	base := "https://downloads.rclone.org/v" + RcloneVersion
	archive, err := download(ctx, base+"/"+asset)
	if err != nil {
		return err
	}
	sums, err := download(ctx, base+"/SHA256SUMS")
	if err != nil {
		return err
	}
	if err := verify(archive, sums, asset); err != nil {
		return err
	}

	binName := "rclone" + exeSuffix()
	return extractZipEntry(archive, dest, func(name string) bool {
		return filepath.Base(name) == binName
	})
}

func download(ctx context.Context, url string) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	client := &http.Client{Timeout: 10 * time.Minute}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("GET %s: %s", url, resp.Status)
	}
	return io.ReadAll(resp.Body)
}

func verify(data, sums []byte, asset string) error {
	want := ""
	for _, line := range strings.Split(string(sums), "\n") {
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}
		name := strings.TrimPrefix(fields[len(fields)-1], "*")
		if name == asset {
			want = fields[0]
			break
		}
	}
	if want == "" {
		return fmt.Errorf("no checksum listed for %s", asset)
	}
	got := sha256.Sum256(data)
	if !strings.EqualFold(hex.EncodeToString(got[:]), want) {
		return fmt.Errorf("checksum mismatch for %s", asset)
	}
	return nil
}

func extractZipEntry(archive []byte, dest string, match func(name string) bool) error {
	zr, err := zip.NewReader(bytes.NewReader(archive), int64(len(archive)))
	if err != nil {
		return err
	}
	for _, f := range zr.File {
		if f.FileInfo().IsDir() || !match(f.Name) {
			continue
		}
		rc, err := f.Open()
		if err != nil {
			return err
		}
		defer func() { _ = rc.Close() }()
		return writeExecutable(dest, rc)
	}
	return fmt.Errorf("no matching entry in archive")
}

func writeExecutable(dest string, r io.Reader) error {
	tmp := dest + ".tmp"
	f, err := os.OpenFile(tmp, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o755)
	if err != nil {
		return err
	}
	if _, err := io.Copy(f, r); err != nil {
		_ = f.Close()
		_ = os.Remove(tmp)
		return err
	}
	if err := f.Close(); err != nil {
		_ = os.Remove(tmp)
		return err
	}
	return os.Rename(tmp, dest)
}
