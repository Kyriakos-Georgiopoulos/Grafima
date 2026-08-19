#!/usr/bin/env bash
#
# Prepares a release branch, which is step 1 of CONTRIBUTING.md's "Releasing".
#
#   scripts/prepare-release.sh 1.3.0
#
# Creates release/1.3.0 off develop, bumps VERSION_NAME, dates the changelog's
# [Unreleased] section, points the README's install snippets at the new version,
# and commits it. Pushing the branch and opening the PR is deliberately left to
# the caller, so the same script serves a local dry run and the CI workflow.
#
# Set RELEASE_DATE to override the changelog date (used by the tests).
# Set ALLOW_ANY_BRANCH=1 to prepare from somewhere other than develop.
set -euo pipefail

die() { echo "error: $*" >&2; exit 1; }

VERSION="${1:-}"
[ -n "$VERSION" ] || die "usage: $0 <version>   e.g. $0 1.3.0"
case "$VERSION" in
  *-SNAPSHOT) die "$VERSION is a snapshot. Release a fixed version." ;;
esac
echo "$VERSION" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$' \
  || die "$VERSION is not x.y.z"

cd "$(git rev-parse --show-toplevel)"

BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != "develop" ] && [ "${ALLOW_ANY_BRANCH:-}" != "1" ]; then
  die "on $BRANCH, not develop. Release branches come off develop."
fi
dirty=$(git status --porcelain)
[ -z "$dirty" ] || die "working tree is dirty:
$dirty"
git rev-parse -q --verify "refs/tags/v$VERSION" >/dev/null \
  && die "tag v$VERSION already exists"
git rev-parse -q --verify "refs/heads/release/$VERSION" >/dev/null \
  && die "branch release/$VERSION already exists"

# A squash-merged merge-back leaves develop without main in its history, and
# the symptom shows up much later as a release PR that cannot satisfy main's
# up-to-date requirement. Catch it here, where the fix is still cheap.
if git rev-parse -q --verify refs/remotes/origin/main >/dev/null; then
  git merge-base --is-ancestor origin/main HEAD || die \
    "develop does not contain origin/main. The last merge-back was squashed
   rather than merged. Recover with: git merge --no-ff origin/main"
fi

# The version this one follows, taken from the changelog rather than from git
# tags, so a local clone with stale tags cannot produce a wrong compare link.
PREV=$(grep -m1 -E '^## \[[0-9]+\.[0-9]+\.[0-9]+\]' CHANGELOG.md \
  | sed -E 's/^## \[([0-9.]+)\].*/\1/') || true
[ -n "$PREV" ] || die "no released section found in CHANGELOG.md"

newer=$(awk -v a="$VERSION" -v b="$PREV" 'BEGIN {
  split(a, x, "."); split(b, y, ".")
  for (i = 1; i <= 3; i++) {
    if ((x[i] + 0) > (y[i] + 0)) { print "yes"; exit }
    if ((x[i] + 0) < (y[i] + 0)) { print "no"; exit }
  }
  print "no"
}')
[ "$newer" = "yes" ] || die "$VERSION is not newer than the released $PREV"

grep -q '^## \[Unreleased\]$' CHANGELOG.md || die "no [Unreleased] section"
# An [Unreleased] section with no entries under it means someone is releasing
# nothing, or the last merge-back left the heading in the wrong place.
entries=$(awk '/^## \[Unreleased\]$/ { flag = 1; next }
               flag && /^## \[/ { exit }
               flag && /^- / { n++ }
               END { print n + 0 }' CHANGELOG.md)
[ "$entries" -gt 0 ] || die "[Unreleased] has no entries. Nothing to release."

# Derived rather than hardcoded, so a fork gets its own compare links.
REPO=$(grep -m1 -E '^\[Unreleased\]: ' CHANGELOG.md \
  | sed -E 's#^\[Unreleased\]: (https://github.com/[^/]+/[^/]+)/compare/.*#\1#')
[ -n "$REPO" ] || die "cannot read the repository URL from CHANGELOG.md"

DATE="${RELEASE_DATE:-$(date -u +%Y-%m-%d)}"

git switch -c "release/$VERSION" >/dev/null

awk -v v="$VERSION" '
  /^VERSION_NAME=/ { print "VERSION_NAME=" v; next }
  { print }
' gradle.properties > gradle.properties.tmp && mv gradle.properties.tmp gradle.properties

awk -v v="$VERSION" '
  { gsub(/io\.grafima:grafima:[0-9]+\.[0-9]+\.[0-9]+/, "io.grafima:grafima:" v); print }
' README.md > README.md.tmp && mv README.md.tmp README.md

awk -v v="$VERSION" -v d="$DATE" -v p="$PREV" -v repo="$REPO" '
  !dated && $0 == "## [Unreleased]" { print "## [" v "] - " d; dated = 1; next }
  /^\[Unreleased\]: / {
    print "[Unreleased]: " repo "/compare/v" v "...HEAD"
    print "[" v "]: " repo "/compare/v" p "...v" v
    next
  }
  { print }
' CHANGELOG.md > CHANGELOG.md.tmp && mv CHANGELOG.md.tmp CHANGELOG.md

git commit -q -m "chore(release): $VERSION" CHANGELOG.md README.md gradle.properties

echo "Prepared release/$VERSION (was $PREV):"
git --no-pager show --stat --format='  %h %s' HEAD
