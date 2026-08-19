#!/usr/bin/env bash
#
# Carries a release back into develop, which is step 3 of CONTRIBUTING.md's
# "Releasing".
#
#   scripts/merge-back.sh
#
# Merges origin/main into a branch off origin/develop, restores the empty
# [Unreleased] heading the release consumed, and opens the next snapshot.
# Leaves the branch committed; pushing it and opening the PR is the caller's job.
#
# The merge always conflicts, because main carries the release as one squashed
# commit while develop carries the same work as its original commits. Exactly
# two hunks are predictable, the changelog heading and VERSION_NAME, and this
# script resolves those and nothing else. Anything unexpected is left conflicted
# for a human, since a release is the wrong place to guess.
set -euo pipefail

die() { echo "error: $*" >&2; exit 1; }

cd "$(git rev-parse --show-toplevel)"
dirty=$(git status --porcelain)
[ -z "$dirty" ] || die "working tree is dirty:
$dirty"

RELEASED=$(git show origin/main:gradle.properties | sed -nE 's/^VERSION_NAME=(.*)$/\1/p')
[ -n "$RELEASED" ] || die "cannot read VERSION_NAME from origin/main"
case "$RELEASED" in
  *-SNAPSHOT) die "origin/main is at $RELEASED. There is no release to carry back." ;;
esac

CURRENT=$(git show origin/develop:gradle.properties | sed -nE 's/^VERSION_NAME=(.*)$/\1/p')
[ -n "$CURRENT" ] || die "cannot read VERSION_NAME from origin/develop"

if git merge-base --is-ancestor origin/main origin/develop; then
  echo "develop already contains origin/main. Nothing to carry back."
  exit 0
fi

# The next snapshot is the released minor plus one, unless develop is already
# further ahead, because a patch release must not drag the snapshot backwards.
NEXT=$(awk -v rel="$RELEASED" -v cur="${CURRENT%-SNAPSHOT}" 'BEGIN {
  split(rel, r, "."); candidate = r[1] "." (r[2] + 1) ".0"
  split(candidate, c, "."); split(cur, u, ".")
  for (i = 1; i <= 3; i++) {
    if ((u[i] + 0) > (c[i] + 0)) { print cur "-SNAPSHOT"; exit }
    if ((u[i] + 0) < (c[i] + 0)) { print candidate "-SNAPSHOT"; exit }
  }
  print candidate "-SNAPSHOT"
}')

BRANCH="chore/merge-$RELEASED-back"
git rev-parse -q --verify "refs/heads/$BRANCH" >/dev/null \
  && die "branch $BRANCH already exists"

git switch -q -c "$BRANCH" origin/develop
git merge --no-ff --no-commit origin/main >/dev/null 2>&1 || true

# The changelog: keep the dated section main wrote and put an empty
# [Unreleased] back above it, which is what every merge-back so far has done.
if git diff --name-only --diff-filter=U | grep -qx CHANGELOG.md; then
  awk '
    /^<<<<<<< / { inblock = 1; nours = 0; ntheirs = 0; side = "ours"; next }
    inblock && /^=======$/ { side = "theirs"; next }
    inblock && /^>>>>>>> / {
      inblock = 0
      if (nours == 1 && ntheirs == 1 && ours[1] == "## [Unreleased]" &&
          theirs[1] ~ /^## \[[0-9]+\.[0-9]+\.[0-9]+\] - [0-9]{4}-[0-9]{2}-[0-9]{2}$/) {
        print "## [Unreleased]"; print ""; print theirs[1]
      } else {
        bad = 1
        print "<<<<<<< develop"
        for (i = 1; i <= nours; i++) print ours[i]
        print "======="
        for (i = 1; i <= ntheirs; i++) print theirs[i]
        print ">>>>>>> main"
      }
      next
    }
    inblock && side == "ours" { ours[++nours] = $0; next }
    inblock && side == "theirs" { theirs[++ntheirs] = $0; next }
    { print }
    END { exit (bad ? 1 : 0) }
  ' CHANGELOG.md > CHANGELOG.md.tmp || {
    mv CHANGELOG.md.tmp CHANGELOG.md
    die "CHANGELOG.md conflicts in a way this script will not guess at.
   Resolve it by hand on $BRANCH, then commit."
  }
  mv CHANGELOG.md.tmp CHANGELOG.md
  git add CHANGELOG.md
fi

# VERSION_NAME: neither side is right. main holds the version just released and
# develop the snapshot it is replacing; what belongs here is the next one.
if git diff --name-only --diff-filter=U | grep -qx gradle.properties; then
  awk -v next_version="$NEXT" '
    /^<<<<<<< / { inblock = 1; nlines = 0; next }
    inblock && /^=======$/ { next }
    inblock && /^>>>>>>> / { inblock = 0; print "VERSION_NAME=" next_version; next }
    inblock {
      nlines++
      if ($0 !~ /^VERSION_NAME=/) bad = 1
      next
    }
    { print }
    END { exit (bad ? 1 : 0) }
  ' gradle.properties > gradle.properties.tmp || {
    rm -f gradle.properties.tmp
    die "gradle.properties conflicts beyond VERSION_NAME. Resolve it by hand."
  }
  mv gradle.properties.tmp gradle.properties
  git add gradle.properties
else
  awk -v next_version="$NEXT" '
    /^VERSION_NAME=/ { print "VERSION_NAME=" next_version; next }
    { print }
  ' gradle.properties > gradle.properties.tmp && mv gradle.properties.tmp gradle.properties
  git add gradle.properties
fi

still_conflicted=$(git diff --name-only --diff-filter=U)
[ -z "$still_conflicted" ] || die "unresolved conflicts, resolve them on $BRANCH:
$still_conflicted"

git commit -q -m "chore: merge $RELEASED back into develop and open $NEXT"

echo "Prepared $BRANCH: $RELEASED carried back, develop reopens at $NEXT"
git --no-pager show --stat --format='  %h %s' HEAD
