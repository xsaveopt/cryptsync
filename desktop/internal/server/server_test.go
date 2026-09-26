package server

import (
	"archive/zip"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"
	"testing/fstest"

	"github.com/xsaveopt/cryptsync/desktop/internal/rcloneconf"
	"github.com/xsaveopt/cryptsync/desktop/internal/tools"
)

const fakeEnv = "CRYPTSYNC_FAKE_TOOL"

func TestMain(m *testing.M) {
	if os.Getenv(fakeEnv) != "" {
		os.Exit(fakeTool(os.Args[1:]))
	}
	os.Exit(m.Run())
}

func fakeTool(args []string) int {
	if os.Getenv("FAKE_FAIL") != "" {
		fmt.Fprintln(os.Stderr, "boom")
		return 1
	}
	if len(args) > 0 && args[0] == "authorize" {
		fmt.Println("Paste the following --->")
		fmt.Println(`{"access_token":"tok"}`)
		fmt.Println("<---End paste")
		return 0
	}
	if len(args) < 3 {
		return 2
	}
	if log := os.Getenv("FAKE_LOG"); log != "" {
		_ = os.WriteFile(log, []byte(os.Getenv("RESTIC_REPOSITORY")), 0o600)
	}
	sub := args[2:]
	switch sub[0] {
	case "snapshots":
		fmt.Print(`[{"id":"full","short_id":"abc12345","time":"t","paths":["/p"],"hostname":"phone"}]`)
	case "ls":
		fmt.Printf(`{"struct_type":"snapshot"}`+"\n"+`{"type":"file","path":"/storage/emulated/0/%s.txt","size":5}`+"\n", sub[1])
	case "dump":
		fmt.Printf("dump:%s:%s", sub[1], sub[2])
	case "restore":
		target, include := "", ""
		for i := 0; i+1 < len(sub); i++ {
			switch sub[i] {
			case "--target":
				target = sub[i+1]
			case "--include-file":
				include = sub[i+1]
			}
		}
		data, err := os.ReadFile(include)
		if err != nil {
			return 3
		}
		for _, p := range strings.Split(string(data), "\n") {
			dest := filepath.Join(target, filepath.FromSlash(p))
			if err := os.MkdirAll(filepath.Dir(dest), 0o755); err != nil {
				return 3
			}
			if err := os.WriteFile(dest, []byte("restored:"+p), 0o600); err != nil {
				return 3
			}
		}
	default:
		return 2
	}
	return 0
}

type harness struct {
	t       *testing.T
	workDir string
	log     string
	handler http.Handler
}

func newHarness(t *testing.T) *harness {
	t.Helper()
	t.Setenv(fakeEnv, "1")
	t.Setenv("FAKE_FAIL", "")
	log := filepath.Join(t.TempDir(), "repo.txt")
	t.Setenv("FAKE_LOG", log)
	workDir := t.TempDir()
	s := New(tools.Set{Restic: os.Args[0], Rclone: os.Args[0]}, workDir)
	webFS := fstest.MapFS{"index.html": {Data: []byte("<h1>cryptsync</h1>")}}
	return &harness{t: t, workDir: workDir, log: log, handler: s.Handler(webFS)}
}

func (h *harness) do(method, target string, body string) *httptest.ResponseRecorder {
	h.t.Helper()
	var r io.Reader
	if body != "" {
		r = strings.NewReader(body)
	}
	req := httptest.NewRequest(method, target, r)
	rec := httptest.NewRecorder()
	h.handler.ServeHTTP(rec, req)
	return rec
}

func (h *harness) connect() {
	h.t.Helper()
	rec := h.do(http.MethodPost, "/api/connect", `{"mode":"drive","token":"{\"a\":1}","password":"pw"}`)
	if rec.Code != http.StatusOK {
		h.t.Fatalf("connect status = %d body = %s", rec.Code, rec.Body.String())
	}
}

func decode(t *testing.T, rec *httptest.ResponseRecorder) map[string]any {
	t.Helper()
	var out map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &out); err != nil {
		t.Fatalf("decode %q: %v", rec.Body.String(), err)
	}
	return out
}

func expectError(t *testing.T, rec *httptest.ResponseRecorder, status int, msg string) {
	t.Helper()
	if rec.Code != status {
		t.Fatalf("status = %d, want %d, body = %s", rec.Code, status, rec.Body.String())
	}
	if ct := rec.Header().Get("Content-Type"); ct != "application/json" {
		t.Errorf("content type = %q", ct)
	}
	if got := decode(t, rec)["error"]; got != msg {
		t.Errorf("error = %q, want %q", got, msg)
	}
}

func TestStaticFiles(t *testing.T) {
	h := newHarness(t)
	rec := h.do(http.MethodGet, "/", "")
	if rec.Code != http.StatusOK || !strings.Contains(rec.Body.String(), "cryptsync") {
		t.Fatalf("status = %d body = %q", rec.Code, rec.Body.String())
	}
}

func TestSessionLifecycle(t *testing.T) {
	h := newHarness(t)
	if got := decode(t, h.do(http.MethodGet, "/api/session", ""))["connected"]; got != false {
		t.Fatalf("connected before connect = %v", got)
	}
	h.connect()
	if got := decode(t, h.do(http.MethodGet, "/api/session", ""))["connected"]; got != true {
		t.Fatalf("connected after connect = %v", got)
	}
	rec := h.do(http.MethodPost, "/api/disconnect", "")
	if got := decode(t, rec)["ok"]; got != true {
		t.Fatalf("disconnect ok = %v", got)
	}
	if got := decode(t, h.do(http.MethodGet, "/api/session", ""))["connected"]; got != false {
		t.Fatalf("connected after disconnect = %v", got)
	}
	expectError(t, h.do(http.MethodGet, "/api/tree", ""), http.StatusConflict, "not connected")
}

func TestOAuthDrive(t *testing.T) {
	h := newHarness(t)
	rec := h.do(http.MethodGet, "/api/oauth/drive", "")
	if rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("GET status = %d", rec.Code)
	}
	rec = h.do(http.MethodPost, "/api/oauth/drive", "")
	if rec.Code != http.StatusOK {
		t.Fatalf("POST status = %d body = %s", rec.Code, rec.Body.String())
	}
	if got := decode(t, rec)["token"]; got != `{"access_token":"tok"}` {
		t.Errorf("token = %v", got)
	}
}

func TestOAuthDriveFailure(t *testing.T) {
	h := newHarness(t)
	t.Setenv("FAKE_FAIL", "1")
	expectError(t, h.do(http.MethodPost, "/api/oauth/drive", ""), http.StatusBadGateway, "Google sign-in did not complete")
}

func TestConnectValidation(t *testing.T) {
	cases := []struct {
		name   string
		method string
		body   string
		status int
		msg    string
	}{
		{"invalid json", http.MethodPost, "{", http.StatusBadRequest, "invalid request"},
		{"missing password", http.MethodPost, `{"mode":"drive","token":"x"}`, http.StatusBadRequest, "a repository password is required"},
		{"raw without body", http.MethodPost, `{"mode":"raw","body":"  ","password":"pw"}`, http.StatusBadRequest, "paste the rclone remote configuration"},
		{"drive without token", http.MethodPost, `{"mode":"drive","token":" ","password":"pw"}`, http.StatusBadRequest, "paste the Google Drive token"},
		{"unknown mode needs token", http.MethodPost, `{"mode":"other","password":"pw"}`, http.StatusBadRequest, "paste the Google Drive token"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			h := newHarness(t)
			expectError(t, h.do(tc.method, "/api/connect", tc.body), tc.status, tc.msg)
		})
	}
}

func TestConnectMethodNotAllowed(t *testing.T) {
	h := newHarness(t)
	if rec := h.do(http.MethodGet, "/api/connect", ""); rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("status = %d", rec.Code)
	}
}

func TestConnectDriveDefaults(t *testing.T) {
	h := newHarness(t)
	rec := h.do(http.MethodPost, "/api/connect", `{"mode":"drive","token":"{\"a\":1}","password":"pw"}`)
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d body = %s", rec.Code, rec.Body.String())
	}
	var resp struct {
		Snapshots []struct {
			ShortID string `json:"short_id"`
		} `json:"snapshots"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatal(err)
	}
	if len(resp.Snapshots) != 1 || resp.Snapshots[0].ShortID != "abc12345" {
		t.Fatalf("snapshots = %+v", resp.Snapshots)
	}

	confPath := filepath.Join(h.workDir, "rclone.conf")
	conf, err := os.ReadFile(confPath)
	if err != nil {
		t.Fatalf("read conf: %v", err)
	}
	if string(conf) != rcloneconf.Drive("gdrive", `{"a":1}`) {
		t.Errorf("conf = %q", conf)
	}
	if info, err := os.Stat(filepath.Join(h.workDir, "cache")); err != nil || !info.IsDir() {
		t.Errorf("cache dir missing: %v", err)
	}
	repo, _ := os.ReadFile(h.log)
	if string(repo) != "rclone:gdrive:cryptsync" {
		t.Errorf("repository = %q", repo)
	}
}

func TestConnectRawCustomRemote(t *testing.T) {
	h := newHarness(t)
	body := `{"mode":"raw","body":"[old]\ntype = s3\n","remote":" mine ","prefix":" backups ","password":"pw"}`
	rec := h.do(http.MethodPost, "/api/connect", body)
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d body = %s", rec.Code, rec.Body.String())
	}
	conf, err := os.ReadFile(filepath.Join(h.workDir, "rclone.conf"))
	if err != nil {
		t.Fatal(err)
	}
	if string(conf) != rcloneconf.RawRemote("mine", "[old]\ntype = s3\n") {
		t.Errorf("conf = %q", conf)
	}
	repo, _ := os.ReadFile(h.log)
	if string(repo) != "rclone:mine:backups" {
		t.Errorf("repository = %q", repo)
	}
}

func TestConnectRepositoryFailure(t *testing.T) {
	h := newHarness(t)
	t.Setenv("FAKE_FAIL", "1")
	rec := h.do(http.MethodPost, "/api/connect", `{"token":"x","password":"pw"}`)
	expectError(t, rec, http.StatusBadGateway, "could not open the repository: check the password and connection")
	if got := decode(t, h.do(http.MethodGet, "/api/session", ""))["connected"]; got != false {
		t.Errorf("connected after failure = %v", got)
	}
}

func TestConnectUnwritableWorkDir(t *testing.T) {
	t.Setenv(fakeEnv, "1")
	s := New(tools.Set{Restic: os.Args[0], Rclone: os.Args[0]}, filepath.Join(t.TempDir(), "missing"))
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/connect", strings.NewReader(`{"token":"x","password":"pw"}`))
	s.Handler(fstest.MapFS{}).ServeHTTP(rec, req)
	expectError(t, rec, http.StatusInternalServerError, "could not write rclone config")
}

func TestTree(t *testing.T) {
	h := newHarness(t)
	expectError(t, h.do(http.MethodGet, "/api/tree", ""), http.StatusConflict, "not connected")
	h.connect()

	var resp struct {
		Nodes []struct {
			Path    string `json:"path"`
			Display string `json:"display"`
		} `json:"nodes"`
	}
	rec := h.do(http.MethodGet, "/api/tree", "")
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatal(err)
	}
	if len(resp.Nodes) != 1 || resp.Nodes[0].Display != "latest.txt" {
		t.Fatalf("nodes = %+v", resp.Nodes)
	}

	rec = h.do(http.MethodGet, "/api/tree?snapshot=abc", "")
	if err := json.Unmarshal(rec.Body.Bytes(), &resp); err != nil {
		t.Fatal(err)
	}
	if len(resp.Nodes) != 1 || resp.Nodes[0].Path != "/storage/emulated/0/abc.txt" {
		t.Fatalf("nodes = %+v", resp.Nodes)
	}

	t.Setenv("FAKE_FAIL", "1")
	expectError(t, h.do(http.MethodGet, "/api/tree", ""), http.StatusBadGateway, "could not read the snapshot")
}

func TestFile(t *testing.T) {
	h := newHarness(t)
	expectError(t, h.do(http.MethodGet, "/api/file?path=/a", ""), http.StatusConflict, "not connected")
	h.connect()
	expectError(t, h.do(http.MethodGet, "/api/file", ""), http.StatusBadRequest, "missing path")

	rec := h.do(http.MethodGet, "/api/file?snapshot=s1&path=/dir/we%22ird.txt", "")
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d", rec.Code)
	}
	if got := rec.Body.String(); got != `dump:s1:/dir/we"ird.txt` {
		t.Errorf("body = %q", got)
	}
	if ct := rec.Header().Get("Content-Type"); ct != "application/octet-stream" {
		t.Errorf("content type = %q", ct)
	}
	if cd := rec.Header().Get("Content-Disposition"); cd != `attachment; filename="weird.txt"` {
		t.Errorf("content disposition = %q", cd)
	}

	rec = h.do(http.MethodGet, "/api/file?path=/a.txt", "")
	if got := rec.Body.String(); got != "dump:latest:/a.txt" {
		t.Errorf("default snapshot body = %q", got)
	}
}

func TestArchive(t *testing.T) {
	h := newHarness(t)
	expectError(t, h.do(http.MethodPost, "/api/archive", `{"paths":["/a"]}`), http.StatusConflict, "not connected")
	h.connect()
	expectError(t, h.do(http.MethodPost, "/api/archive", "{"), http.StatusBadRequest, "select at least one item")
	expectError(t, h.do(http.MethodPost, "/api/archive", `{"paths":[]}`), http.StatusBadRequest, "select at least one item")

	body := `{"paths":["/storage/emulated/0/DCIM/a.jpg","/data/app/media_cache/storage/emulated/0/Pictures/b.heic"]}`
	rec := h.do(http.MethodPost, "/api/archive", body)
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d body = %s", rec.Code, rec.Body.String())
	}
	if ct := rec.Header().Get("Content-Type"); ct != "application/zip" {
		t.Errorf("content type = %q", ct)
	}
	if cd := rec.Header().Get("Content-Disposition"); cd != `attachment; filename="cryptsync-export.zip"` {
		t.Errorf("content disposition = %q", cd)
	}
	got := readZip(t, rec.Body.Bytes())
	want := map[string]string{
		"DCIM/a.jpg":      "restored:/storage/emulated/0/DCIM/a.jpg",
		"Pictures/b.heic": "restored:/data/app/media_cache/storage/emulated/0/Pictures/b.heic",
	}
	if len(got) != len(want) {
		t.Fatalf("entries = %v", got)
	}
	for name, content := range want {
		if got[name] != content {
			t.Errorf("entry %q = %q, want %q", name, got[name], content)
		}
	}

	entries, err := os.ReadDir(h.workDir)
	if err != nil {
		t.Fatal(err)
	}
	for _, e := range entries {
		if strings.HasPrefix(e.Name(), "export-") {
			t.Errorf("export dir %s was not cleaned up", e.Name())
		}
	}

	t.Setenv("FAKE_FAIL", "1")
	expectError(t, h.do(http.MethodPost, "/api/archive", `{"paths":["/a"]}`), http.StatusBadGateway, "could not restore the selection")
}

func readZip(t *testing.T, data []byte) map[string]string {
	t.Helper()
	zr, err := zip.NewReader(bytes.NewReader(data), int64(len(data)))
	if err != nil {
		t.Fatalf("open zip: %v", err)
	}
	out := map[string]string{}
	for _, f := range zr.File {
		rc, err := f.Open()
		if err != nil {
			t.Fatal(err)
		}
		content, err := io.ReadAll(rc)
		_ = rc.Close()
		if err != nil {
			t.Fatal(err)
		}
		out[f.Name] = string(content)
	}
	return out
}

func TestZipDir(t *testing.T) {
	root := t.TempDir()
	files := map[string]string{
		"storage/emulated/0/Music/song.mp3": "song",
		"top.txt":                           "top",
		"deep/nested/cryptsync-config.json": "{}",
	}
	for name, content := range files {
		p := filepath.Join(root, filepath.FromSlash(name))
		if err := os.MkdirAll(filepath.Dir(p), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(p, []byte(content), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	if err := os.MkdirAll(filepath.Join(root, "empty"), 0o755); err != nil {
		t.Fatal(err)
	}
	var buf bytes.Buffer
	if err := zipDir(root, &buf); err != nil {
		t.Fatalf("zipDir: %v", err)
	}
	got := readZip(t, buf.Bytes())
	names := make([]string, 0, len(got))
	for n := range got {
		names = append(names, n)
	}
	sort.Strings(names)
	want := []string{"Music/song.mp3", "cryptsync-config.json", "top.txt"}
	if strings.Join(names, ",") != strings.Join(want, ",") {
		t.Fatalf("names = %v, want %v", names, want)
	}
	if got["Music/song.mp3"] != "song" {
		t.Errorf("content = %q", got["Music/song.mp3"])
	}
}

func TestZipDirMissingRoot(t *testing.T) {
	if err := zipDir(filepath.Join(t.TempDir(), "missing"), io.Discard); err == nil {
		t.Fatal("expected error")
	}
}

func TestSanitizeName(t *testing.T) {
	cases := map[string]string{
		"plain.txt":     "plain.txt",
		`a"b.txt`:       "ab.txt",
		"line\r\nx.txt": "linex.txt",
		`""`:            "download",
		"":              "download",
	}
	for in, want := range cases {
		if got := sanitizeName(in); got != want {
			t.Errorf("sanitizeName(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestFirstNonEmpty(t *testing.T) {
	if got := firstNonEmpty("a", "b"); got != "a" {
		t.Errorf("got %q", got)
	}
	if got := firstNonEmpty("", "b"); got != "b" {
		t.Errorf("got %q", got)
	}
}
