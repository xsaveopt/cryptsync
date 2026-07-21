# CryptSync Browser

A small desktop companion to the CryptSync Android app.
Open the same encrypted backup on a computer, browse the files, and download what you want.

## Run it

Download the binary and open it.
On first run it fetches restic and rclone for your platform, then opens `http://127.0.0.1:7777` in your browser.

Flags: `-port`, `-no-browser`, `-version`.

## Connect

You need your storage access and your backup password.
For Google Drive, click Sign in with Google Drive and complete the consent in the browser.
For S3, MinIO, or WebDAV, paste the rclone remote config.

## Build

`make build` produces `bin/cryptsync-browser`.
Rerun `make web` after changing anything under `web/`.
