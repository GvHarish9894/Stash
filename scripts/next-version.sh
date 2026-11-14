#!/usr/bin/env bash
# Works out the next release version from the commits since the last release tag and writes it to
# iosApp/Configuration/Config.xcconfig, the one file both the iOS and the Android build read.
#
#   scripts/next-version.sh [auto|patch|minor|major]   prints version=, build= and tag= lines
#   scripts/next-version.sh --self-test
#
# auto: a commit marked breaking ("type!:" or "BREAKING CHANGE") gives a major bump, a "feat:" commit a
# minor bump, anything else a patch bump. With no release tag yet, the version in the file is released as it is.
set -euo pipefail

CONFIG="$(cd "$(dirname "$0")/.." && pwd)/iosApp/Configuration/Config.xcconfig"

# major, minor or patch for the commit messages (subjects and bodies) on stdin.
bump_for() {
  local log
  log=$(cat)
  if grep -qE '^[a-z]+(\([^)]*\))?!:|BREAKING CHANGE' <<<"$log"; then
    echo major
  elif grep -qE '^feat(\([^)]*\))?:' <<<"$log"; then
    echo minor
  else
    echo patch
  fi
}

# next <x.y[.z]> <major|minor|patch|none>
next() {
  local major minor patch
  IFS=. read -r major minor patch <<<"$1"
  minor=${minor:-0}
  patch=${patch:-0}
  case "$2" in
    major) echo "$((major + 1)).0.0" ;;
    minor) echo "$major.$((minor + 1)).0" ;;
    patch) echo "$major.$minor.$((patch + 1))" ;;
    none) echo "$major.$minor.$patch" ;;
  esac
}

if [ "${1:-}" = "--self-test" ]; then
  [ "$(printf 'Fix a typo\nAdd a screen\n' | bump_for)" = patch ]
  [ "$(printf 'fix: a typo\nfeat(shelf): thumbnails\n' | bump_for)" = minor ]
  [ "$(printf 'feat!: new storage\n' | bump_for)" = major ]
  [ "$(printf 'Change storage\n\nBREAKING CHANGE: old data is dropped\n' | bump_for)" = major ]
  [ "$(next 1.0 none)" = 1.0.0 ]
  [ "$(next 1.0 patch)" = 1.0.1 ]
  [ "$(next 1.4.2 minor)" = 1.5.0 ]
  [ "$(next 1.4.2 major)" = 2.0.0 ]
  echo "self-test passed"
  exit 0
fi

mode=${1:-auto}
case "$mode" in auto | patch | minor | major) ;; *) echo "Unknown bump '$mode'" >&2; exit 2 ;; esac

current=$(sed -n 's/^MARKETING_VERSION *= *//p' "$CONFIG")
build=$(sed -n 's/^CURRENT_PROJECT_VERSION *= *//p' "$CONFIG")
last=$(git describe --tags --match 'v[0-9]*' --abbrev=0 2>/dev/null || true)

if [ -z "$last" ]; then
  version=$(next "$current" none)
else
  log=$(git log --format='%s%n%b' "$last"..HEAD)
  if [ -z "$log" ]; then
    echo "No commits since $last: nothing to release." >&2
    exit 1
  fi
  if [ "$mode" = auto ]; then mode=$(bump_for <<<"$log"); fi
  version=$(next "$current" "$mode")
  build=$((build + 1))
fi

if git rev-parse -q --verify "refs/tags/v$version" >/dev/null; then
  echo "Tag v$version already exists." >&2
  exit 1
fi

sed -i.bak -e "s/^MARKETING_VERSION *=.*/MARKETING_VERSION=$version/" -e "s/^CURRENT_PROJECT_VERSION *=.*/CURRENT_PROJECT_VERSION=$build/" "$CONFIG"
rm -f "$CONFIG.bak"

echo "version=$version"
echo "build=$build"
echo "tag=v$version"
