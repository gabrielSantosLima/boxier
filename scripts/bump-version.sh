#!/usr/bin/env bash
# Bumps `mod_version` in gradle.properties and prints the new version.
# Usage: scripts/bump-version.sh <bump> [preid] [explicit-version]
#   bump: major | minor | patch | premajor | preminor | prepatch | prerelease
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPERTIES="$ROOT/gradle.properties"

BUMP="${1:-patch}"
PREID="${2:-alpha}"
VERSION_INPUT="${3:-}"

CURRENT="$(grep -E '^mod_version=' "$PROPERTIES" | head -n1 | cut -d= -f2- | tr -d '\r')"
if [ -z "$CURRENT" ]; then
  echo "mod_version not found in $PROPERTIES" >&2
  exit 1
fi

if [ -n "$VERSION_INPUT" ]; then
  NEW="$VERSION_INPUT"
else
  CORE="${CURRENT%%-*}"
  PRE=""
  [ "$CORE" != "$CURRENT" ] && PRE="${CURRENT#*-}"

  IFS='.' read -r MAJOR MINOR PATCH <<< "$CORE"
  MAJOR="${MAJOR:-0}"; MINOR="${MINOR:-0}"; PATCH="${PATCH:-0}"

  PRENUM=""
  if [ -n "$PRE" ]; then
    case "$PRE" in
      *.*) PRENUM="${PRE##*.}" ;;
    esac
  fi

  bump_pre() {
    if [ -n "$PRE" ] && { [ "$PRE" = "$PREID" ] || [[ "$PRE" == "$PREID".* ]]; }; then
      NEW_PRE="$PREID.$(( ${PRENUM:-0} + 1 ))"
    else
      NEW_PRE="$PREID.0"
    fi
  }

  case "$BUMP" in
    major)
      NEW="$((MAJOR+1)).0.0" ;;
    minor)
      NEW="$MAJOR.$((MINOR+1)).0" ;;
    patch)
      NEW="$MAJOR.$MINOR.$((PATCH+1))" ;;
    premajor)
      MAJOR=$((MAJOR+1)); MINOR=0; PATCH=0; PRE=""; PRENUM=""
      bump_pre; NEW="$MAJOR.$MINOR.$PATCH-$NEW_PRE" ;;
    preminor)
      MINOR=$((MINOR+1)); PATCH=0; PRE=""; PRENUM=""
      bump_pre; NEW="$MAJOR.$MINOR.$PATCH-$NEW_PRE" ;;
    prepatch)
      PATCH=$((PATCH+1)); PRE=""; PRENUM=""
      bump_pre; NEW="$MAJOR.$MINOR.$PATCH-$NEW_PRE" ;;
    prerelease)
      [ -z "$PRE" ] && PATCH=$((PATCH+1))
      bump_pre; NEW="$MAJOR.$MINOR.$PATCH-$NEW_PRE" ;;
    *)
      echo "Unknown bump type: $BUMP" >&2
      exit 1 ;;
  esac
fi

awk -v v="$NEW" 'BEGIN { FS = OFS = "=" } /^mod_version=/ { print "mod_version=" v; next } { print }' \
  "$PROPERTIES" > "$PROPERTIES.tmp"
mv "$PROPERTIES.tmp" "$PROPERTIES"

printf '%s\n' "$NEW"
