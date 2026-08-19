# Releasing

A release moves `develop` onto `main`, tags it, publishes `io.grafima:grafima`
to Maven Central, and carries the result back to `develop`. Most of that is
automated. Two things are not, on purpose: deciding the version, and pressing
Publish at Central.

## The shape of it

| Step | Who does it |
| ---- | ----------- |
| Decide the version | You |
| Cut `release/x.y.z`, bump, date the changelog, open the PR | **Prepare release** workflow |
| Review the changelog and merge | You |
| Tag `vx.y.z` on `main` | `release-tag.yml` |
| Publish to Central, cut the GitHub release | `release.yml` |
| Open the merge-back PR into `develop` | `release.yml` |
| Press Publish at Central | You |
| Merge the merge-back PR | You |

## Before the first automated release

The chain needs a `RELEASE_TOKEN` repository secret holding a fine-grained
personal access token. GitHub deliberately starts no workflow for an event
raised by the built-in `GITHUB_TOKEN`, so a tag pushed with it would never reach
`release.yml`, and a pull request opened with it would sit with no CI and could
never satisfy a required check. The PAT is what keeps the chain moving.

Give it access to this repository only, and these repository permissions:

| Permission | Why |
| ---------- | --- |
| Contents: read and write | Push the release branch and the tag |
| Pull requests: read and write | Open the release and merge-back PRs |
| Workflows: read and write | Only if a release ever touches `.github/workflows/` |

Every job that needs the token checks for it first and fails with the manual
command to run instead, so a missing or expired PAT stalls a release visibly
rather than half-finishing one.

Publishing also needs `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`,
`SIGNING_IN_MEMORY_KEY` and `SIGNING_IN_MEMORY_KEY_PASSWORD`, which have been
set since 1.0.0.

## 1. Decide the version

[Semantic Versioning](https://semver.org). The question that decides it is what
happened to the public API, and `library/api/` answers it:

```bash
git diff v1.2.0 develop -- library/api
```

A removed or changed signature in that diff is a major bump. Additions with
defaults are a minor. Nothing but behaviour is a patch. Kotlin regenerates a
data class's constructor and `copy` when it gains a parameter, so a "harmless"
defaulted parameter still removes the old signature from the artifact: source
compatible, binary incompatible. Say so in the changelog either way.

## 2. Run **Prepare release**

Actions → Prepare release → Run workflow, and give it `x.y.z`. It creates
`release/x.y.z` off `develop`, sets `VERSION_NAME`, moves `[Unreleased]` to a
dated `[x.y.z]` section with the compare links, points the README's install
snippets at the new version, and opens the PR into `main`.

It refuses, rather than producing a half-right release, when:

- the version is not `x.y.z`, is a snapshot, or is not newer than the last
  released section in the changelog
- `vx.y.z` is already tagged, or `release/x.y.z` already exists
- `[Unreleased]` has no entries, which means there is nothing to release
- `develop` does not contain `origin/main`, which means the last merge-back was
  squashed; recover with `git merge --no-ff origin/main`

## 3. Review and merge

CI runs the full matrix on the PR. The part CI cannot check is the changelog
section, which is what ships as the GitHub release and what consumers read.
Read it as a consumer would: does it name every breaking change, and does the
version match what it describes?

Merge it. Squash or merge commit both work here, since `main` keeps only the
release commit either way.

## 4. Everything else happens on its own

Merging fires `release-tag.yml`, which reads `VERSION_NAME` from `main`, sees it
is not a snapshot and not yet tagged, and pushes an annotated `vx.y.z`. Any
other push to `main` lands there too and is skipped, because the version is
already tagged.

The tag fires `release.yml`, which re-checks that the tag matches
`VERSION_NAME`, refuses a snapshot, checks the API dump has not drifted, runs
the library suites, uploads the deployment to Central, and cuts the GitHub
release from the changelog section. It then opens the merge-back PR into
`develop`.

## 5. Press Publish

https://central.sonatype.com/publishing/deployments

The workflow uploads and validates the deployment; it does not release it. This
is the point of no return, since a version published to Central cannot be
replaced or withdrawn. Nothing else in the process is irreversible.

To let a tag go straight through unattended, swap `publishToMavenCentral` for
`publishAndReleaseToMavenCentral` in `release.yml`.

## 6. Merge the merge-back PR with a merge commit

**Do not squash it.** Squashing drops the merge's second parent and `develop`
stops containing `main`. Nothing breaks that day. It surfaces at the next
release, when the release branch cannot satisfy the up-to-date requirement on
`main`, and `scripts/prepare-release.sh` refuses to start.

The PR restores the empty `[Unreleased]` heading above the dated section and
moves `VERSION_NAME` to the next snapshot: the released minor plus one, unless
`develop` is already further ahead, so a patch release never drags the snapshot
backwards.

## Doing it by hand

Both scripts run locally and do exactly what the workflows do, which is the
point of them being scripts:

```bash
git switch develop && git pull
scripts/prepare-release.sh 1.3.0     # commits release/1.3.0
git push -u origin release/1.3.0
gh pr create --base main --title "chore(release): 1.3.0"
```

After the release is tagged and published:

```bash
git fetch origin
scripts/merge-back.sh                # commits chore/merge-1.3.0-back
git push -u origin chore/merge-1.3.0-back
gh pr create --base develop --title "chore: carry v1.3.0 back into develop"
```

`RELEASE_DATE` overrides the changelog date, and `ALLOW_ANY_BRANCH=1` lets you
rehearse a preparation from somewhere other than `develop`.

## When the merge-back conflicts

It always conflicts. `main` carries the release as one squashed commit while
`develop` carries the same work as its original commits, so git has no shared
history to line them up with. Two hunks are predictable, the changelog heading
and `VERSION_NAME`, and `scripts/merge-back.sh` resolves those two and nothing
else.

If a feature landed on `develop` while the release PR was open, the changelog
hunk is no longer just the heading and the script stops, leaving the conflict on
the branch with both sides intact. Resolve it by keeping the new entries under
`[Unreleased]` and the released entries under their dated heading, then commit
and push the branch yourself.

## Hotfixes

A `hotfix/` branch comes off `main`, not `develop`. Prepare it by hand: the
prepare script insists on `develop`, since that is the right default for
everything else. Everything after the tag is the same, and the merge-back PR
carries the fix into `develop` on its own.

## The moving parts

| File | What it owns |
| ---- | ------------ |
| `scripts/prepare-release.sh` | The version bump, the changelog move, the guards |
| `scripts/merge-back.sh` | The merge, the restored `[Unreleased]`, the next snapshot |
| `.github/workflows/release-prep.yml` | Runs the first script, opens the release PR |
| `.github/workflows/release-tag.yml` | Tags a released `VERSION_NAME` on `main` |
| `.github/workflows/release.yml` | Publishes, cuts the GitHub release, opens the merge-back PR |
