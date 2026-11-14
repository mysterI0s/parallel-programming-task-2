#!/usr/bin/env bash
#
# Compiles the server together with its tests into a temporary folder and runs
# every test class. Needs only a JDK (8 or newer); there are no dependencies.
#
# Usage: scripts/run-tests.sh

set -euo pipefail
cd "$(dirname "$0")/.."

out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT

javac -nowarn -d "$out" -sourcepath src src/*.java src/task/*.java test/*.java

for test in test/*Test.java; do
  name=$(basename "$test" .java)
  echo "== $name"
  java -cp "$out" "$name"
  echo
done
