# CryptSync

CryptSync is an Android app that keeps a live, encrypted copy of your chosen folders in your own cloud, and puts everything back when you set up a new phone.
It is not a cold backup.
It is a live mirror, so a photo you delete on the phone disappears from the next sync too, which means you should never delete originals thinking they are safe in here.
The point is recovery after a lost, stolen, or broken phone, not freeing up space.

## How it works

You pick a set of folders, and every file inside them is backed up whatever the type.
Media gets the one bit of special treatment, because photos and videos are where the real savings are, so they are re-encoded to shrink them while everything else is stored as it is.
Videos are transcoded with hardware acceleration through Media3 Transformer and images are recompressed to a modern format, both with quality settings you control, and each re-encoded file is cached on the device so it never has to be redone on the next run.
Your remaining files, the documents and archives and app databases, go in untouched and lean on restic's own lossless compression, since re-encoding them would either corrupt them or save nothing.
Everything is then handed to [restic](https://restic.net), which deduplicates, compresses, and encrypts it with AES-256 before uploading.
Uploads go to your Google Drive through restic's rclone backend, using rclone's built in Drive access, so there is nothing to register as a developer.

Reading the non-media files means CryptSync needs all files access, which you grant on the Sources tab.
Without it the app can still back up your photos and videos through the media permission, but not the documents and other files sitting alongside them.

Because restic is a standard, cross-platform tool, you can recover your data on a computer without this app at all.
You install restic, point it at the same repository, enter your password, and run `restic restore`.

## Connecting Google Drive

CryptSync uses rclone's built in Google Drive access, so you never register anything with Google, and the tradeoff is that there is no in-app sign in button.
Instead you authorize once on a computer that has rclone installed, then paste the result into the app.

```sh
rclone authorize "drive"
```

Sign in when the browser opens, then copy the whole token it prints and paste it into the Connect Google Drive screen.

## License

GPL-2.0.
See [LICENSE](LICENSE).
