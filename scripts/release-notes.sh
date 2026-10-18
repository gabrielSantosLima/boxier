#!/usr/bin/env bash
# Generates release notes from Conventional Commits using docs/RELEASE_TEMPLATE.md.
# Usage: scripts/release-notes.sh [--from <ref>] [--to <ref>] [--out <file>] [--summary <text>]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

FROM=""
TO="HEAD"
OUT=""
SUMMARY="${SUMMARY:-}"

while [ $# -gt 0 ]; do
  case "$1" in
    --from) FROM="${2:-}"; shift 2 ;;
    --to) TO="${2:-}"; shift 2 ;;
    --out) OUT="${2:-}"; shift 2 ;;
    --summary) SUMMARY="${2:-}"; shift 2 ;;
    *) shift ;;
  esac
done

if [ -z "$FROM" ]; then
  FROM="$(git describe --tags --abbrev=0 2>/dev/null || true)"
fi

VERSION="$(grep -E '^mod_version=' gradle.properties | head -n1 | cut -d= -f2- | tr -d '\r')"
DATE="$(date -u +%Y-%m-%d)"

if [ -n "$FROM" ]; then RANGE="$FROM..$TO"; else RANGE="$TO"; fi

FEATURES=""; FIXES=""; CHORES=""; OTHER=""
while IFS=$'\t' read -r HASH SUBJECT; do
  [ -z "$SUBJECT" ] && continue
  case "$SUBJECT" in chore\(release\)*) continue ;; esac
  REF=""
  [ -n "$HASH" ] && REF=" [$HASH]"
  CC_RE='^([a-zA-Z]+)(\([^)]*\))?!?:[[:space:]]*(.+)$'
  if [[ "$SUBJECT" =~ $CC_RE ]]; then
    TYPE="$(printf '%s' "${BASH_REMATCH[1]}" | tr '[:upper:]' '[:lower:]')"
    TEXT="${BASH_REMATCH[3]}"
    case "$TYPE" in
      feat) FEATURES+="- $TEXT$REF"$'\n' ;;
      fix) FIXES+="- $TEXT$REF"$'\n' ;;
      refactor|chore|docs|style|perf|test|build|ci) CHORES+="- $TYPE: $TEXT$REF"$'\n' ;;
      *) OTHER+="- $SUBJECT$REF"$'\n' ;;
    esac
  else
    OTHER+="- $SUBJECT$REF"$'\n'
  fi
done < <(git log --no-merges --pretty=format:'%h%x09%s' "$RANGE")

bullets() {
  if [ -z "$1" ]; then printf '%s' "- None"; else printf '%s' "$1" | sed '/^$/d'; fi
}

FROM_DISPLAY="${FROM:-first release}"
SUMMARY_TEXT="${SUMMARY:-Release v$VERSION.}"

render() {
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      '{features}') printf '%s\n' "$(bullets "$FEATURES")" ;;
      '{fixes}') printf '%s\n' "$(bullets "$FIXES")" ;;
      '{chores}') printf '%s\n' "$(bullets "$CHORES")" ;;
      '{other}') printf '%s\n' "$(bullets "$OTHER")" ;;
      *)
        line="${line//\{version\}/$VERSION}"
        line="${line//\{date\}/$DATE}"
        line="${line//\{summary\}/$SUMMARY_TEXT}"
        line="${line//\{from\}/$FROM_DISPLAY}"
        printf '%s\n' "$line" ;;
    esac
  done < docs/RELEASE_TEMPLATE.md
}

if [ -n "$OUT" ]; then
  render > "$OUT"
else
  render
fi
