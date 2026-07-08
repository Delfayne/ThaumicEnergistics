#!/usr/bin/env bash
# Bumps modVersion in gradle.properties and prepends a placeholder entry to
# CHANGELOG.md. Edits files only -- review and commit the result yourself.
#
# Usage:
#   scripts/bump-version.sh <major|minor|patch>
#   scripts/bump-version.sh <X.Y.Z>

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

usage() {
  echo "Usage: $0 <major|minor|patch|X.Y.Z>" >&2
  exit 1
}

[[ $# -eq 1 ]] || usage

PROPERTIES_FILE="gradle.properties"
CHANGELOG_FILE="CHANGELOG.md"

CURRENT_VERSION=$(grep -Po '(?<=^modVersion=).*' "$PROPERTIES_FILE") || {
  echo "Could not find modVersion in $PROPERTIES_FILE" >&2
  exit 1
}

if [[ ! $CURRENT_VERSION =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  echo "Current modVersion '$CURRENT_VERSION' is not in X.Y.Z form, can't bump automatically." >&2
  exit 1
fi
CUR_MAJOR=${BASH_REMATCH[1]}
CUR_MINOR=${BASH_REMATCH[2]}
CUR_PATCH=${BASH_REMATCH[3]}

case "$1" in
  major)
    NEW_VERSION="$((CUR_MAJOR + 1)).0.0"
    ;;
  minor)
    NEW_VERSION="$CUR_MAJOR.$((CUR_MINOR + 1)).0"
    ;;
  patch)
    NEW_VERSION="$CUR_MAJOR.$CUR_MINOR.$((CUR_PATCH + 1))"
    ;;
  [0-9]*.[0-9]*.[0-9]*)
    NEW_VERSION="$1"
    ;;
  *)
    usage
    ;;
esac

if [[ "$NEW_VERSION" == "$CURRENT_VERSION" ]]; then
  echo "New version ($NEW_VERSION) is the same as the current version." >&2
  exit 1
fi

if git rev-parse -q --verify "refs/tags/v$NEW_VERSION" >/dev/null 2>&1; then
  echo "Tag v$NEW_VERSION already exists." >&2
  exit 1
fi

if ! git diff --quiet -- "$PROPERTIES_FILE" "$CHANGELOG_FILE" 2>/dev/null; then
  echo "$PROPERTIES_FILE or $CHANGELOG_FILE already has uncommitted changes." >&2
  echo "Commit or stash them first so this bump is easy to review on its own." >&2
  exit 1
fi

sed -i "s/^modVersion=.*/modVersion=$NEW_VERSION/" "$PROPERTIES_FILE"

CHANGELOG_ENTRY="# $(date +%Y/%m/%d) - $NEW_VERSION
## Features
* TODO
## Bugs
* TODO
## Misc
* TODO
"
TMP_CHANGELOG=$(mktemp)
{
  printf '%s\n' "$CHANGELOG_ENTRY"
  cat "$CHANGELOG_FILE"
} > "$TMP_CHANGELOG"
mv "$TMP_CHANGELOG" "$CHANGELOG_FILE"

echo "Bumped modVersion: $CURRENT_VERSION -> $NEW_VERSION"
echo "Prepended a placeholder entry to $CHANGELOG_FILE -- fill in the real notes before merging."
echo
echo "Review with: git diff -- $PROPERTIES_FILE $CHANGELOG_FILE"
echo "Merging this to main will tag and publish v$NEW_VERSION via .github/workflows/release.yml."
