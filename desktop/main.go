package main

import (
	"context"
	"embed"
	"flag"
	"fmt"
	"io/fs"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"os/signal"
	"runtime"
	"time"

	"github.com/xsaveopt/cryptsync/desktop/internal/server"
	"github.com/xsaveopt/cryptsync/desktop/internal/tools"
)

//go:embed all:web/dist
var webRoot embed.FS

var version = "dev"

func main() {
	port := flag.Int("port", 7777, "port for the local web interface")
	noBrowser := flag.Bool("no-browser", false, "do not open the browser automatically")
	showVersion := flag.Bool("version", false, "print the version and exit")
	flag.Parse()

	if *showVersion {
		fmt.Println("cryptsync-browser", version)
		return
	}

	if err := run(*port, *noBrowser); err != nil {
		log.Fatal(err)
	}
}

func run(port int, noBrowser bool) error {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()

	log.Printf("CryptSync browser %s", version)

	bins, err := tools.Ensure(ctx, log.Printf)
	if err != nil {
		return fmt.Errorf("preparing tools: %w", err)
	}

	workDir, err := os.MkdirTemp("", "cryptsync-browser-session-")
	if err != nil {
		return err
	}
	defer func() { _ = os.RemoveAll(workDir) }()

	webFS, err := fs.Sub(webRoot, "web/dist")
	if err != nil {
		return err
	}

	srv := server.New(bins, workDir)
	addr := net.JoinHostPort("127.0.0.1", fmt.Sprintf("%d", port))
	httpServer := &http.Server{
		Addr:              addr,
		Handler:           srv.Handler(webFS),
		ReadHeaderTimeout: 10 * time.Second,
	}

	listener, err := net.Listen("tcp", addr)
	if err != nil {
		return fmt.Errorf("listen on %s: %w", addr, err)
	}

	url := "http://" + addr
	log.Printf("Open %s in your browser.", url)
	if !noBrowser {
		openBrowser(url)
	}

	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = httpServer.Shutdown(shutdownCtx)
	}()

	if err := httpServer.Serve(listener); err != nil && err != http.ErrServerClosed {
		return err
	}
	return nil
}

func openBrowser(url string) {
	var cmd *exec.Cmd
	switch runtime.GOOS {
	case "darwin":
		cmd = exec.Command("open", url)
	case "windows":
		cmd = exec.Command("rundll32", "url.dll,FileProtocolHandler", url)
	default:
		cmd = exec.Command("xdg-open", url)
	}
	if err := cmd.Start(); err != nil {
		log.Printf("Could not open the browser automatically: %v", err)
	}
}
