package rclonecli

import "testing"

func TestExtractToken(t *testing.T) {
	output := `2026/07/21 13:00:00 NOTICE: Please go to the following link: http://127.0.0.1:53682/auth
2026/07/21 13:00:00 NOTICE: Waiting for code...
Paste the following into your remote machine --->
{"access_token":"ya29.abc","token_type":"Bearer","refresh_token":"1//xyz","expiry":"2026-07-21T14:00:00Z"}
<---End paste`
	token, err := extractToken(output)
	if err != nil {
		t.Fatalf("extractToken: %v", err)
	}
	want := `{"access_token":"ya29.abc","token_type":"Bearer","refresh_token":"1//xyz","expiry":"2026-07-21T14:00:00Z"}`
	if token != want {
		t.Errorf("token = %q, want %q", token, want)
	}
}

func TestExtractTokenMissing(t *testing.T) {
	if _, err := extractToken("NOTICE: Waiting for code...\nno token here"); err == nil {
		t.Fatal("expected error when no token present")
	}
}

func TestExtractTokenNotJSON(t *testing.T) {
	if _, err := extractToken("---> not-a-json <---"); err == nil {
		t.Fatal("expected error for non-JSON token")
	}
}
