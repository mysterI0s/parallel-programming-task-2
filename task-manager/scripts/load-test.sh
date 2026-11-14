#!/usr/bin/env bash
#
# Sends a mix of the new task (TaskPrimeCount) and the original tasks to the
# server all at the same moment, then prints how long the whole batch took next
# to the execution times of the individual tasks added together.
#
# Usage: scripts/load-test.sh [host:port]        (default: localhost:8000)
#
# Each request below is "TaskName input". curl turns "-d TaskName -d input"
# into the body "TaskName&input", which is the format the server expects.

TARGET="${1:-localhost:8000}"
export LC_ALL=C   # "." as the decimal separator for printf and awk

REQUESTS=(
  "TaskPrimeCount 1-40000000"
  "TaskFiboRecursive 44"
  "TaskPrimeCount 40000001-65000000"
  "TaskBubbleSort 9,4,7,1,8,2,6,3,5"
  "TaskPrimeCount 1-30000000"
  "TaskFiboRecursive 43"
  "TaskPrimeCount 65000001-85000000"
  "TaskFiboRecursive 42"
)

# Sends one request and, when its response arrives, prints the request and the
# server's reply on two lines (a single printf, so replies never interleave).
send() {
  local number="$1" task="$2" input="$3" reply waited
  # -w appends the total request time in seconds on a last line of its own.
  reply=$(curl -s -m 300 -d "$task" -d "$input" -w '\n%{time_total}' "http://$TARGET/")
  waited=${reply##*$'\n'}
  reply=${reply%%$'\n'*}
  printf '#%d %s %s   (client waited %.2fs)\n   %s\n' \
         "$number" "$task" "$input" "$waited" "${reply:-no reply}"
}

# Starts every request in the background (&) so they overlap, then waits for all.
send_all_at_once() {
  local number=0 request
  for request in "${REQUESTS[@]}"; do
    number=$((number + 1))
    # shellcheck disable=SC2086  # the request is split on purpose: "task input"
    send "$number" $request &
  done
  wait
}

echo "Sending ${#REQUESTS[@]} requests to http://$TARGET/ at the same time..."
echo

replies=$(mktemp)
trap 'rm -f "$replies"' EXIT

# `time` reports on stderr; fd 3 keeps the replies going to the terminal.
TIMEFORMAT='%2R'
exec 3>&1
batch_seconds=$( { time send_all_at_once 2>&3 | tee "$replies" >&3; } 2>&1 )
exec 3>&-

# Add up the "Executed in: 1.23s" figures the server reported for each task.
task_seconds=$(awk -F'Executed in: ' \
  'NF > 1 { sub(/s.*/, "", $2); sum += $2 } END { printf "%.2f", sum }' "$replies")

echo
echo "Whole batch, sent together     : ${batch_seconds}s"
echo "Task execution times, added up : ${task_seconds}s"
