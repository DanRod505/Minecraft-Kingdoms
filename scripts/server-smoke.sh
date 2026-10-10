#!/usr/bin/env bash
# Dedicated server smoke test: start runServer headless, wait for "Done", send "stop",
# and fail on timeout, a non-zero exit, or any ERROR/exception logged by the kingdoms mod.
# Accepting the EULA here only affects the local dev run directory (run/).
set -euo pipefail
cd "$(dirname "$0")/.."

LOG=${1:-build/server-smoke.log}
TIMEOUT=${SERVER_SMOKE_TIMEOUT:-600}
mkdir -p run "$(dirname "$LOG")"
echo "eula=true" > run/eula.txt

FIFO=$(mktemp -u)
mkfifo "$FIFO"
./gradlew runServer --console=plain < "$FIFO" > "$LOG" 2>&1 &
PID=$!
exec 3> "$FIFO"
rm "$FIFO"

for _ in $(seq 1 "$TIMEOUT"); do
    if grep -q 'Done (' "$LOG"; then break; fi
    if ! kill -0 "$PID" 2>/dev/null; then break; fi
    sleep 1
done

if ! grep -q 'Done (' "$LOG"; then
    echo "Server did not reach Done within ${TIMEOUT}s" >&2
    kill "$PID" 2>/dev/null || true
    tail -50 "$LOG" >&2
    exit 1
fi

echo stop >&3
STATUS=0
wait "$PID" || STATUS=$?
exec 3>&-

grep -E 'Done \(|\[kingdoms/' "$LOG" || true
if [ "$STATUS" -ne 0 ]; then
    echo "runServer exited with $STATUS" >&2
    exit "$STATUS"
fi
if grep -E '\[kingdoms/\]: .*(ERROR|Exception)|/ERROR\] \[kingdoms' "$LOG"; then
    echo "kingdoms logged errors" >&2
    exit 1
fi
if grep -E 'dev\.kingdoms\.' "$LOG" | grep -qiE 'exception|at dev\.kingdoms'; then
    echo "Stack trace from kingdoms code" >&2
    exit 1
fi
echo "Server smoke test passed"
