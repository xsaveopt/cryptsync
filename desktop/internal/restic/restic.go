package restic

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"os/exec"
	"strings"
)

type Engine struct {
	resticBin string
	rcloneBin string
	confPath  string
	cacheDir  string
	repo      string
	password  string
}

type Snapshot struct {
	ID       string   `json:"id"`
	ShortID  string   `json:"short_id"`
	Time     string   `json:"time"`
	Paths    []string `json:"paths"`
	Hostname string   `json:"hostname"`
}

type Node struct {
	Path    string `json:"path"`
	Display string `json:"display"`
	Type    string `json:"type"`
	Size    int64  `json:"size"`
}

func DisplayPath(p string) string {
	const marker = "/media_cache/"
	if i := strings.Index(p, marker); i >= 0 {
		p = p[i+len(marker):]
	}
	if strings.HasSuffix(p, "/cryptsync-config.json") || p == "cryptsync-config.json" {
		return "cryptsync-config.json"
	}
	for _, root := range []string{"/storage/emulated/0/", "storage/emulated/0/"} {
		if strings.HasPrefix(p, root) {
			return strings.TrimPrefix(p, root)
		}
	}
	return strings.TrimPrefix(p, "/")
}

func New(resticBin, rcloneBin, confPath, cacheDir, remote, prefix, password string) *Engine {
	return &Engine{
		resticBin: resticBin,
		rcloneBin: rcloneBin,
		confPath:  confPath,
		cacheDir:  cacheDir,
		repo:      fmt.Sprintf("rclone:%s:%s", remote, prefix),
		password:  password,
	}
}

func (e *Engine) env() []string {
	return append(os.Environ(),
		"RESTIC_PASSWORD="+e.password,
		"RESTIC_REPOSITORY="+e.repo,
		"RESTIC_CACHE_DIR="+e.cacheDir,
		"RCLONE_CONFIG="+e.confPath,
	)
}

func (e *Engine) command(ctx context.Context, args ...string) *exec.Cmd {
	full := append([]string{"-o", "rclone.program=" + e.rcloneBin}, args...)
	cmd := exec.CommandContext(ctx, e.resticBin, full...)
	cmd.Env = e.env()
	return cmd
}

func (e *Engine) run(ctx context.Context, args ...string) ([]byte, error) {
	cmd := e.command(ctx, args...)
	var out, errb bytes.Buffer
	cmd.Stdout = &out
	cmd.Stderr = &errb
	if err := cmd.Run(); err != nil {
		return out.Bytes(), fmt.Errorf("restic %s failed: %w: %s", args[0], err, tail(errb.String()))
	}
	return out.Bytes(), nil
}

func (e *Engine) Snapshots(ctx context.Context) ([]Snapshot, error) {
	out, err := e.run(ctx, "snapshots", "--json")
	if err != nil {
		return nil, err
	}
	return parseSnapshots(out)
}

func parseSnapshots(out []byte) ([]Snapshot, error) {
	var snaps []Snapshot
	if err := json.Unmarshal(out, &snaps); err != nil {
		return nil, fmt.Errorf("parse snapshots: %w", err)
	}
	return snaps, nil
}

func (e *Engine) Tree(ctx context.Context, snapshot string) ([]Node, error) {
	out, err := e.run(ctx, "ls", snapshot, "--json")
	if err != nil {
		return nil, err
	}
	return parseNodes(out), nil
}

func parseNodes(out []byte) []Node {
	var nodes []Node
	for _, line := range strings.Split(string(out), "\n") {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}
		var n Node
		if err := json.Unmarshal([]byte(line), &n); err != nil {
			continue
		}
		if n.Path == "" || (n.Type != "file" && n.Type != "dir" && n.Type != "symlink") {
			continue
		}
		n.Display = DisplayPath(n.Path)
		nodes = append(nodes, n)
	}
	return nodes
}

func (e *Engine) DumpFile(ctx context.Context, snapshot, path string, w io.Writer) error {
	cmd := e.command(ctx, "dump", snapshot, path)
	cmd.Stdout = w
	var errb bytes.Buffer
	cmd.Stderr = &errb
	if err := cmd.Run(); err != nil {
		return fmt.Errorf("dump %s: %w: %s", path, err, tail(errb.String()))
	}
	return nil
}

func (e *Engine) Restore(ctx context.Context, snapshot, target string, paths []string) error {
	include, err := os.CreateTemp("", "cryptsync-include-*.txt")
	if err != nil {
		return err
	}
	defer func() { _ = os.Remove(include.Name()) }()
	if _, err := include.WriteString(strings.Join(paths, "\n")); err != nil {
		_ = include.Close()
		return err
	}
	if err := include.Close(); err != nil {
		return err
	}
	_, err = e.run(ctx, "restore", snapshot, "--target", target, "--include-file", include.Name())
	return err
}

func tail(s string) string {
	lines := strings.Split(strings.TrimSpace(s), "\n")
	if len(lines) > 5 {
		lines = lines[len(lines)-5:]
	}
	return strings.Join(lines, "\n")
}
