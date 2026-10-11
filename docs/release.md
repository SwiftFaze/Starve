# Releases

Versioning and changelog generation are automatic — you should not hand-edit
the project version or write `CHANGELOG.md` entries by hand.

## Two channels: stable (`master`) and beta (`develop`)

- **`master` → stable releases** (`vX.Y.Z`). This is what most users
  should install.
- **`develop` → beta prereleases** (`vX.Y.Z-beta.N`). GitHub marks these
  releases as a "Pre-release" automatically (Release Please's
  `prerelease: true` config). Merging `develop` into `master` is what
  "promotes" accumulated beta work into the next real stable release.

Both are handled by the same `.github/workflows/release.yml`, via two
independent Release Please jobs (`release-please-stable`,
`release-please-beta`) gated on which branch was pushed to, each with its
own config/manifest (`release-please-config.json` +
`.release-please-manifest.json` for stable, the `-beta` variants of both
for beta) so the two version lines and changelogs don't collide.

## How a release happens

1. Commits land on `master` or `develop` (via PR, per branch protection)
   using [Conventional Commits](https://www.conventionalcommits.org/)
   (`feat:`, `fix:`, `feat!:`/`BREAKING CHANGE:`, etc.) — the same style
   already used in this repo.
2. [Release Please](https://github.com/googleapis/release-please) watches
   both branches and keeps a standing `chore(<branch>): release x.y.z`
   pull request up to date on each, computing the next version from those
   commits (`fix:` → patch, `feat:` → minor, a `!` or `BREAKING CHANGE:`
   footer → major) and drafting `CHANGELOG.md` from their messages.
3. Merging that release PR is the actual release trigger: Release Please
   tags the merge commit (`vX.Y.Z` on `master`, `vX.Y.Z-beta.N` on
   `develop`), updates the version files it manages, and creates the
   GitHub Release with the changelog as its body.

The merge in step 3 happens automatically — no human click needed. Right
after Release Please creates/updates the PR, the same job asks GitHub to
auto-merge it (`gh pr merge --auto`), which waits for the branch's
required status checks to pass and then merges it for you. This exists
because leaving the release PR sitting open was actively harmful: every
other PR that merged into `develop`/`master` in the meantime kept
updating that same standing PR, and if it sat unmerged for a while before
finally being merged, it was easy to lose track of which commits were and
weren't actually reflected in `CHANGELOG.md` for that version. Auto-merge
means each release goes out the moment the commit that should trigger it
lands and CI is green, so there's no window where the release PR can go
stale.

**Why this needs a PAT, not just `GITHUB_TOKEN`**: GitHub suppresses
further workflow-triggering events from a push/merge made with the
default `GITHUB_TOKEN`, to prevent recursive workflow runs. If the
auto-merge step used `GITHUB_TOKEN`, the merge would land the release
commit on `master`/`develop` but never fire the follow-up `push` event
that lets `release-please-action` notice it and actually cut the tag +
GitHub Release — the PR merges, but nothing downstream runs (this
happened in practice; see the `RELEASE_PLEASE_TOKEN` secret and the
comment above the `jobs:` key in `release.yml`). Both
`release-please-action` and the auto-merge step instead use
`RELEASE_PLEASE_TOKEN`, a fine-grained PAT scoped to this repo's
`Contents` and `Pull requests` permissions (matching this workflow's own
`permissions:` block, nothing broader) — a PAT-driven merge isn't subject
to that suppression, and also isn't caught by GitHub's fork-PR
workflow-approval gate, which the bot-authored release PR was otherwise
triggering.

Nothing about this requires a human to decide "what's the next version
number" — that's entirely derived from commit messages. If a change
shouldn't trigger a release at all, use a non-triggering type (`chore:`,
`docs:`, `test:`, `style:`) or `fix:`/`feat:` as appropriate — Release
Please only reacts to types that map to a version bump.

## Publishing build artifacts

Not configured: the template's `release.yml` only cuts tags, GitHub Releases
and `CHANGELOG.md`. To attach build output (jars, installers, bundles) to a
release, add a job to `release.yml` as described in its closing comment, and
document what it produces here.

## One-time setup

1. Create the `RELEASE_PLEASE_TOKEN` repository secret: a fine-grained PAT on
   this repo with `Contents` and `Pull requests` read/write.
2. Enable "Allow auto-merge" in the repo settings, so the release PRs can merge
   themselves once checks pass.
3. If the version also lives in a build file (`pom.xml`, `package.json`...),
   either switch `release-type` in both configs to that ecosystem (`maven`,
   `node`...) or add an `extra-files` entry pointing at it, and extend the
   `no-manual-release-edits` job in `repo-hygiene.yml` to guard that file.

## Resetting the release history

The `no-manual-release-edits` check blocks any PR that touches `CHANGELOG.md`
or the manifests. For a deliberate reset (for example, wiping inherited history
after cutting a project from a template, or restarting at a new version), add
the `release-reset` label to that PR: the check passes with a notice and
re-runs as soon as the label changes. Never use it to get an ordinary change
past the check.
