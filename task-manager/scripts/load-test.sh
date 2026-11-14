#!/usr/bin/env bash
#
# Sends a mix of the new task (TaskPrimeCount) and the original tasks to the
# server all at the same moment, then compares how long the whole batch took
# with how long the tasks took individually.
#
# Usage: scripts/load-test.sh [host:port]        (default: localhost:8000)
#
# Each request below is "TaskName input". curl turns "-d TaskName -d input"
# into the body "TaskName&input", which is the format the server expects.

TARGET="${1:-localhost:8000}"

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

# Sends one request and prints one line when its response arrives.
send() {
  local number="$1" task="$2" input="$3" reply
  # curl reports the wait in microseconds; sed trims it to two decimals.
  reply=$(curl -s -m 300 -d "$task" -d "$input" \
               -w ' | client waited %{time_total}' "http://$TARGET/" \
          | tr -d '\n' | sed -E 's/(waited [0-9]+\.[0-9]{2})[0-9]*$/\1s/')
  printf '#%d %-18s %-18s => %s\n' "$number" "$task" "$input" "$reply"
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
TIMEFORMAT='%R'
exec 3>&1
batch_seconds=$( { time send_all_at_once 2>&3 | tee "$replies" >&3; } 2>&1 )
exec 3>&-

# Add up the "Executed in: 1.23s" figures the server reported for each task.
task_seconds=$(awk -F'Executed in: ' \
  'NF > 1 { sub(/s.*/, "", $2); sum += $2 } END { printf "%.2f", sum }' "$replies")

echo
echo "Whole batch, sent together : ${batch_seconds}s"
echo "Sum of the task times      : ${task_seconds}s  (what running them one after another costs)"
