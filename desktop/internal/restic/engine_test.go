package restic

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

const fakeEnv = "CRYPTSYNC_FAKE_RESTIC"

type fakeCall struct {
	Args    []string          `json:"args"`
	Env     map[string]string `json:"env"`
	Include string            `json:"include"`
}

func TestMain(m *testing.M) {
	if os.Getenv(fakeEnv) != "" {
		os.Exit(fakeRestic(os.Args[1:]))
	}
	os.Exit(m.Run())
}

func fakeRestic(args []string) int {
	call := fakeCall{Args: args, Env: map[string]string{}}
	for _, key := range []string{"RESTIC_PASSWORD", "RESTIC_REPOSITORY", "RESTIC_CACHE_DIR", "RCLONE_CONFIG"} {
		call.Env[key] = os.Getenv(key)
	}
	for i, a := range args {
		if a == "--include-file" && i+1 < len(args) {
			data, _ := os.ReadFile(args[i+1])
			call.Include = string(data)
		}
	}
	if log := os.Getenv("FAKE_LOG"); log != "" {
		data, _ := json.Marshal(call)
		_ = os.WriteFile(log, data, 0o600)
	}
	if os.Getenv("FAKE_FAIL") != "" {
		for i := 1; i <= 7; i++ {
			fmt.Fprintf(os.Stderr, "stderr line %d\n", i)
		}
		return 1
	}
	fmt.Print(os.Getenv("FAKE_STDOUT"))
	return 0
}

func fakeEngine(t *testing.T) (*Engine, string) {
	t.Helper()
	t.Setenv(fakeEnv, "1")
	log := filepath.Join(t.TempDir(), "call.json")
	t.Setenv("FAKE_LOG", log)
	t.Setenv("FAKE_FAIL", "")
	t.Setenv("FAKE_STDOUT", "")
	return New(os.Args[0], "/opt/rclone", "/work/rclone.conf", "/work/cache", "gdrive", "backup", "s3cret"), log
}

func readCall(t *testing.T, log string) fakeCall {
	t.Helper()
	data, err := os.ReadFile(log)
	if err != nil {
		t.Fatalf("fake restic was not invoked: %v", err)
	}
	var call fakeCall
	if err := json.Unmarshal(data, &call); err != nil {
		t.Fatalf("decode call: %v", err)
	}
	return call
}

func TestNewBuildsRepository(t *testing.T) {
	e := New("restic", "rclone", "conf", "cache", "remote", "prefix", "pw")
	if e.repo != "rclone:remote:prefix" {
		t.Errorf("repo = %q", e.repo)
	}
}

func TestSnapshotsRunsResticWithEnvironment(t *testing.T) {
	e, log := fakeEngine(t)
	t.Setenv("FAKE_STDOUT", `[{"id":"full","short_id":"abcd1234","time":"t","paths":["/p"],"hostname":"h"}]`)

	snaps, err := e.Snapshots(context.Background())
	if err != nil {
		t.Fatalf("Snapshots: %v", err)
	}
	if len(snaps) != 1 || snaps[0].ShortID != "abcd1234" || snaps[0].Hostname != "h" {
		t.Fatalf("snapshots = %+v", snaps)
	}

	call := readCall(t, log)
	want := []string{"-o", "rclone.program=/opt/rclone", "snapshots", "--json"}
	if strings.Join(call.Args, " ") != strings.Join(want, " ") {
		t.Errorf("args = %v, want %v", call.Args, want)
	}
	wantEnv := map[string]string{
		"RESTIC_PASSWORD":   "s3cret",
		"RESTIC_REPOSITORY": "rclone:gdrive:backup",
		"RESTIC_CACHE_DIR":  "/work/cache",
		"RCLONE_CONFIG":     "/work/rclone.conf",
	}
	for k, v := range wantEnv {
		if call.Env[k] != v {
			t.Errorf("env %s = %q, want %q", k, call.Env[k], v)
		}
	}
}

func TestSnapshotsInvalidJSON(t *testing.T) {
	e, _ := fakeEngine(t)
	t.Setenv("FAKE_STDOUT", "not json")
	if _, err := e.Snapshots(context.Background()); err == nil || !strings.Contains(err.Error(), "parse snapshots") {
		t.Fatalf("expected parse error, got %v", err)
	}
}

func TestSnapshotsFailureIncludesStderrTail(t *testing.T) {
	e, _ := fakeEngine(t)
	t.Setenv("FAKE_FAIL", "1")
	_, err := e.Snapshots(context.Background())
	if err == nil {
		t.Fatal("expected error")
	}
	msg := err.Error()
	if !strings.Contains(msg, "restic snapshots failed") {
		t.Errorf("error = %q", msg)
	}
	if !strings.Contains(msg, "stderr line 7") || !strings.Contains(msg, "stderr line 3") {
		t.Errorf("error should carry the last stderr lines: %q", msg)
	}
	if strings.Contains(msg, "stderr line 2") {
		t.Errorf("error should drop early stderr lines: %q", msg)
	}
}

func TestTreeParsesNodes(t *testing.T) {
	e, log := fakeEngine(t)
	t.Setenv("FAKE_STDOUT", `{"struct_type":"snapshot"}
{"type":"file","path":"/storage/emulated/0/a.txt","size":3}
`)
	nodes, err := e.Tree(context.Background(), "abcd")
	if err != nil {
		t.Fatalf("Tree: %v", err)
	}
	if len(nodes) != 1 || nodes[0].Display != "a.txt" || nodes[0].Size != 3 {
		t.Fatalf("nodes = %+v", nodes)
	}
	call := readCall(t, log)
	if got := strings.Join(call.Args[2:], " "); got != "ls abcd --json" {
		t.Errorf("args = %q", got)
	}
}

func TestTreeFailure(t *testing.T) {
	e, _ := fakeEngine(t)
	t.Setenv("FAKE_FAIL", "1")
	if _, err := e.Tree(context.Background(), "latest"); err == nil {
		t.Fatal("expected error")
	}
}

func TestDumpFileStreamsStdout(t *testing.T) {
	e, log := fakeEngine(t)
	t.Setenv("FAKE_STDOUT", "file bytes")
	var buf bytes.Buffer
	if err := e.DumpFile(context.Background(), "latest", "/dir/a.txt", &buf); err != nil {
		t.Fatalf("DumpFile: %v", err)
	}
	if buf.String() != "file bytes" {
		t.Errorf("output = %q", buf.String())
	}
	call := readCall(t, log)
	if got := strings.Join(call.Args[2:], " "); got != "dump latest /dir/a.txt" {
		t.Errorf("args = %q", got)
	}
}

func TestDumpFileFailure(t *testing.T) {
	e, _ := fakeEngine(t)
	t.Setenv("FAKE_FAIL", "1")
	err := e.DumpFile(context.Background(), "latest", "/dir/a.txt", &bytes.Buffer{})
	if err == nil || !strings.Contains(err.Error(), "dump /dir/a.txt") {
		t.Fatalf("expected dump error, got %v", err)
	}
}

func TestRestoreWritesIncludeFile(t *testing.T) {
	e, log := fakeEngine(t)
	target := t.TempDir()
	if err := e.Restore(context.Background(), "snap1", target, []string{"/a/one.txt", "/b/two.txt"}); err != nil {
		t.Fatalf("Restore: %v", err)
	}
	call := readCall(t, log)
	if len(call.Args) != 8 {
		t.Fatalf("args = %v", call.Args)
	}
	if got := strings.Join(call.Args[2:6], " "); got != "restore snap1 --target "+target {
		t.Errorf("args = %q", got)
	}
	if call.Args[6] != "--include-file" {
		t.Errorf("args = %v", call.Args)
	}
	if call.Include != "/a/one.txt\n/b/two.txt" {
		t.Errorf("include = %q", call.Include)
	}
	if _, err := os.Stat(call.Args[7]); !os.IsNotExist(err) {
		t.Errorf("include file should be removed, stat err = %v", err)
	}
}

func TestRestoreFailure(t *testing.T) {
	e, _ := fakeEngine(t)
	t.Setenv("FAKE_FAIL", "1")
	if err := e.Restore(context.Background(), "latest", t.TempDir(), []string{"/a"}); err == nil {
		t.Fatal("expected error")
	}
}

func TestRunMissingBinary(t *testing.T) {
	e := New(filepath.Join(t.TempDir(), "missing"), "rclone", "c", "d", "r", "p", "pw")
	if _, err := e.Snapshots(context.Background()); err == nil {
		t.Fatal("expected error for missing binary")
	}
}

func TestTail(t *testing.T) {
	if got := tail("  one\ntwo\n  "); got != "one\ntwo" {
		t.Errorf("tail = %q", got)
	}
	if got := tail("1\n2\n3\n4\n5\n6\n7"); got != "3\n4\n5\n6\n7" {
		t.Errorf("tail = %q", got)
	}
}
