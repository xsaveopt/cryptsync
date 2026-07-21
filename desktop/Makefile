BINARY  = bin/cryptsync-browser

VERSION ?= $(shell git describe --tags --always --dirty 2>/dev/null || echo "0.1.0")
LDFLAGS  = -ldflags "-s -w -X main.version=$(VERSION)"

.PHONY: build web clean

build: web
	go build -trimpath $(LDFLAGS) -o $(BINARY) .

web:
	pnpm --dir web install --frozen-lockfile
	pnpm --dir web build

clean:
	rm -f $(BINARY)
