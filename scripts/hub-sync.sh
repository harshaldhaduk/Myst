#!/usr/bin/env bash
# Show what landed on origin/main since this branch's base, and flag files to re-read before writing more code.
# Read-only: it fetches but never rebases, merges, or edits anything. See hub/BUILDING.md.
set -euo pipefail

git fetch --quiet origin
base=$(git merge-base HEAD origin/main)
behind=$(git rev-list --count "$base"..origin/main)

if [ "$behind" -eq 0 ]; then
  echo "Up to date with origin/main ($(git rev-parse --short origin/main))."
  exit 0
fi

echo "$behind new commit(s) on origin/main since your base $(git rev-parse --short "$base"):"
git log --format='  %h  %an, %ar: %s' "$base"..origin/main

changed=$(git diff --name-only "$base" origin/main)

shared='^(AGENTS\.md|CLAUDE\.md|GEMINI\.md|PROJECT_CONTEXT\.md|hub/|package\.json|pyproject\.toml)'
flagged=$(printf '%s\n' "$changed" | grep -E "$shared" || true)
if [ -n "$flagged" ]; then
  echo
  echo "Shared or hub files changed. Re-read these before continuing:"
  printf '%s\n' "$flagged" | sed 's/^/  /'
fi

mine=$( { git diff --name-only "$base" HEAD; git diff --name-only; git diff --name-only --cached; } | sort -u)
overlap=$(comm -12 <(printf '%s\n' "$changed" | sort -u) <(printf '%s\n' "$mine") || true)
if [ -n "$overlap" ]; then
  echo
  echo "You changed these too, so expect conflicts:"
  printf '%s\n' "$overlap" | sed 's/^/  /'
fi

echo
echo "Next: commit or stash your work, then run: git rebase origin/main"
