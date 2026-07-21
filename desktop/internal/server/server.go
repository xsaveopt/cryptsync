package server

import (
	"archive/zip"
	"context"
	"encoding/json"
	"io"
	"io/fs"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/xsaveopt/cryptsync/desktop/internal/rclonecli"
	"github.com/xsaveopt/cryptsync/desktop/internal/rcloneconf"
	"github.com/xsaveopt/cryptsync/desktop/internal/restic"
	"github.com/xsaveopt/cryptsync/desktop/internal/tools"
)

type Server struct {
	bins    tools.Set
	workDir string

	mu     sync.Mutex
	engine *restic.Engine
}

func New(bins tools.Set, workDir string) *Server {
	return &Server{bins: bins, workDir: workDir}
}

func (s *Server) Handler(webFS fs.FS) http.Handler {
	mux := http.NewServeMux()
	mux.Handle("/", http.FileServer(http.FS(webFS)))
	mux.HandleFunc("/api/session", s.handleSession)
	mux.HandleFunc("/api/oauth/drive", s.handleOAuthDrive)
	mux.HandleFunc("/api/connect", s.handleConnect)
	mux.HandleFunc("/api/tree", s.handleTree)
	mux.HandleFunc("/api/file", s.handleFile)
	mux.HandleFunc("/api/archive", s.handleArchive)
	mux.HandleFunc("/api/disconnect", s.handleDisconnect)
	return mux
}

func (s *Server) handleSession(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, map[string]any{"connected": s.current() != nil})
}

func (s *Server) handleOAuthDrive(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Minute)
	defer cancel()
	token, err := rclonecli.Authorize(ctx, s.bins.Rclone)
	if err != nil {
		writeError(w, http.StatusBadGateway, "Google sign-in did not complete")
		return
	}
	writeJSON(w, map[string]any{"token": token})
}

type connectRequest struct {
	Mode     string `json:"mode"`
	Token    string `json:"token"`
	Body     string `json:"body"`
	Remote   string `json:"remote"`
	Prefix   string `json:"prefix"`
	Password string `json:"password"`
}

func (s *Server) handleConnect(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	var req connectRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid request")
		return
	}
	if req.Password == "" {
		writeError(w, http.StatusBadRequest, "a repository password is required")
		return
	}
	remote := firstNonEmpty(strings.TrimSpace(req.Remote), "gdrive")
	prefix := firstNonEmpty(strings.TrimSpace(req.Prefix), "cryptsync")

	var conf string
	switch req.Mode {
	case "raw":
		if strings.TrimSpace(req.Body) == "" {
			writeError(w, http.StatusBadRequest, "paste the rclone remote configuration")
			return
		}
		conf = rcloneconf.RawRemote(remote, req.Body)
	default:
		if strings.TrimSpace(req.Token) == "" {
			writeError(w, http.StatusBadRequest, "paste the Google Drive token")
			return
		}
		conf = rcloneconf.Drive(remote, req.Token)
	}

	confPath := filepath.Join(s.workDir, "rclone.conf")
	if err := os.WriteFile(confPath, []byte(conf), 0o600); err != nil {
		writeError(w, http.StatusInternalServerError, "could not write rclone config")
		return
	}
	cacheDir := filepath.Join(s.workDir, "cache")
	if err := os.MkdirAll(cacheDir, 0o755); err != nil {
		writeError(w, http.StatusInternalServerError, "could not create cache dir")
		return
	}

	engine := restic.New(s.bins.Restic, s.bins.Rclone, confPath, cacheDir, remote, prefix, req.Password)
	snaps, err := engine.Snapshots(r.Context())
	if err != nil {
		writeError(w, http.StatusBadGateway, "could not open the repository: check the password and connection")
		return
	}

	s.mu.Lock()
	s.engine = engine
	s.mu.Unlock()

	writeJSON(w, map[string]any{"snapshots": snaps})
}

func (s *Server) handleTree(w http.ResponseWriter, r *http.Request) {
	engine := s.current()
	if engine == nil {
		writeError(w, http.StatusConflict, "not connected")
		return
	}
	snapshot := firstNonEmpty(r.URL.Query().Get("snapshot"), "latest")
	nodes, err := engine.Tree(r.Context(), snapshot)
	if err != nil {
		writeError(w, http.StatusBadGateway, "could not read the snapshot")
		return
	}
	writeJSON(w, map[string]any{"nodes": nodes})
}

func (s *Server) handleFile(w http.ResponseWriter, r *http.Request) {
	engine := s.current()
	if engine == nil {
		writeError(w, http.StatusConflict, "not connected")
		return
	}
	snapshot := firstNonEmpty(r.URL.Query().Get("snapshot"), "latest")
	path := r.URL.Query().Get("path")
	if path == "" {
		writeError(w, http.StatusBadRequest, "missing path")
		return
	}
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Disposition", "attachment; filename=\""+sanitizeName(filepath.Base(path))+"\"")
	if err := engine.DumpFile(r.Context(), snapshot, path, w); err != nil {
		return
	}
}

type archiveRequest struct {
	Snapshot string   `json:"snapshot"`
	Paths    []string `json:"paths"`
}

func (s *Server) handleArchive(w http.ResponseWriter, r *http.Request) {
	engine := s.current()
	if engine == nil {
		writeError(w, http.StatusConflict, "not connected")
		return
	}
	var req archiveRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil || len(req.Paths) == 0 {
		writeError(w, http.StatusBadRequest, "select at least one item")
		return
	}
	snapshot := firstNonEmpty(req.Snapshot, "latest")

	tmp, err := os.MkdirTemp(s.workDir, "export-")
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not create export dir")
		return
	}
	defer func() { _ = os.RemoveAll(tmp) }()

	if err := engine.Restore(r.Context(), snapshot, tmp, req.Paths); err != nil {
		writeError(w, http.StatusBadGateway, "could not restore the selection")
		return
	}

	w.Header().Set("Content-Type", "application/zip")
	w.Header().Set("Content-Disposition", "attachment; filename=\"cryptsync-export.zip\"")
	if err := zipDir(tmp, w); err != nil {
		return
	}
}

func (s *Server) handleDisconnect(w http.ResponseWriter, _ *http.Request) {
	s.mu.Lock()
	s.engine = nil
	s.mu.Unlock()
	writeJSON(w, map[string]any{"ok": true})
}

func (s *Server) current() *restic.Engine {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.engine
}

func zipDir(root string, w io.Writer) error {
	zw := zip.NewWriter(w)
	err := filepath.WalkDir(root, func(path string, d os.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() {
			return nil
		}
		rel, err := filepath.Rel(root, path)
		if err != nil {
			return err
		}
		name := strings.TrimPrefix(restic.DisplayPath("/"+filepath.ToSlash(rel)), "/")
		entry, err := zw.Create(name)
		if err != nil {
			return err
		}
		f, err := os.Open(path)
		if err != nil {
			return err
		}
		defer func() { _ = f.Close() }()
		_, err = io.Copy(entry, f)
		return err
	})
	if err != nil {
		_ = zw.Close()
		return err
	}
	return zw.Close()
}

func firstNonEmpty(a, b string) string {
	if a != "" {
		return a
	}
	return b
}

func sanitizeName(name string) string {
	name = strings.ReplaceAll(name, "\"", "")
	name = strings.ReplaceAll(name, "\n", "")
	name = strings.ReplaceAll(name, "\r", "")
	if name == "" {
		return "download"
	}
	return name
}

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]string{"error": msg})
}
