#!/bin/sh
if [ "$1" = "-o" ]; then
  shift 2
fi
state=$(dirname "$RCLONE_CONFIG")
count=$(cat "$state/fake-count" 2>/dev/null || echo 0)
count=$((count + 1))
echo "$count" > "$state/fake-count"
echo "$*" >> "$state/fake-calls"
prev=""
for arg in "$@"; do
  if [ "$prev" = "--files-from" ] || [ "$prev" = "--new-password-file" ]; then
    cat "$arg" > "$state/fake-seen-$count"
  fi
  prev="$arg"
done
response="$state/fake-responses/$count"
if [ -f "$response" ]; then
  code=$(head -n 1 "$response")
  tail -n +2 "$response"
  exit "$code"
fi
exit 0
