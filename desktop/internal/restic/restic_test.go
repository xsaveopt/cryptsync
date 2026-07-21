package restic

import "testing"

func TestParseSnapshots(t *testing.T) {
	out := []byte(`[{"id":"abc123def456","short_id":"abc123de","time":"2026-07-20T10:00:00Z","paths":["/storage/emulated/0/DCIM"],"hostname":"phone"}]`)
	snaps, err := parseSnapshots(out)
	if err != nil {
		t.Fatalf("parseSnapshots: %v", err)
	}
	if len(snaps) != 1 {
		t.Fatalf("want 1 snapshot, got %d", len(snaps))
	}
	if snaps[0].ShortID != "abc123de" {
		t.Errorf("short id = %q", snaps[0].ShortID)
	}
	if len(snaps[0].Paths) != 1 || snaps[0].Paths[0] != "/storage/emulated/0/DCIM" {
		t.Errorf("paths = %v", snaps[0].Paths)
	}
}

func TestParseSnapshotsEmpty(t *testing.T) {
	snaps, err := parseSnapshots([]byte(`[]`))
	if err != nil {
		t.Fatalf("parseSnapshots: %v", err)
	}
	if len(snaps) != 0 {
		t.Fatalf("want 0 snapshots, got %d", len(snaps))
	}
}

func TestParseNodes(t *testing.T) {
	out := []byte(`{"time":"2026-07-20T10:00:00Z","struct_type":"snapshot","id":"abc"}
{"name":"DCIM","type":"dir","path":"/storage/emulated/0/DCIM","struct_type":"node"}
{"name":"photo.heic","type":"file","path":"/storage/emulated/0/DCIM/photo.heic","size":204800,"struct_type":"node"}
garbage line that is not json
{"name":"noise","type":"other","path":"/x"}`)
	nodes := parseNodes(out)
	if len(nodes) != 2 {
		t.Fatalf("want 2 nodes, got %d: %+v", len(nodes), nodes)
	}
	if nodes[0].Type != "dir" || nodes[0].Path != "/storage/emulated/0/DCIM" {
		t.Errorf("dir node = %+v", nodes[0])
	}
	if nodes[1].Type != "file" || nodes[1].Size != 204800 {
		t.Errorf("file node = %+v", nodes[1])
	}
	if nodes[1].Display != "DCIM/photo.heic" {
		t.Errorf("display should be relative to shared storage, got %q", nodes[1].Display)
	}
}

func TestDisplayPath(t *testing.T) {
	cases := map[string]string{
		"/storage/emulated/0/Android/data/io.github.xsaveopt.cryptsync/files/media_cache/storage/emulated/0/Pictures/x.heic": "Pictures/x.heic",
		"/storage/emulated/0/Download/doc.pdf":                                  "Download/doc.pdf",
		"/storage/emulated/0/Android/data/com.whatsapp/files/note.txt":          "Android/data/com.whatsapp/files/note.txt",
		"/data/user/0/io.github.xsaveopt.cryptsync/files/cryptsync-config.json": "cryptsync-config.json",
	}
	for in, want := range cases {
		if got := DisplayPath(in); got != want {
			t.Errorf("DisplayPath(%q) = %q, want %q", in, got, want)
		}
	}
}
