# CLAUDE.md

<!-- TEMPLATE: replace this paragraph. One or two sentences: what the project
is, its stack, and anything an agent would get wrong without being told
(e.g. "no ORM, raw SQL only"). -->
`<Project name>` is a `<one-line description>` built with `<stack>`.

## Editing this file

Rules here load into context on every task, so a bad one costs more than a bad
line of code. Before adding:

- **Only add what will matter across many future tasks.** A one-off mistake gets
  fixed in the code or that PR — a standing instruction is the most expensive
  possible fix for it.
- **State, not rules, belongs in the issue.** "X is broken until #N lands" rots
  the moment #N lands, and nothing prompts anyone to remove it.
- **Tag every rule** `<!-- added YYYY-MM-DD: why -->`. Without the reason, nobody
  can later tell a load-bearing rule from a dead one, so nothing is ever deleted.
- **One canonical home per rule.** Other files link to it, never restate it.
- **If it is mechanically checkable, write the check, not the rule.**
- **At or over budget, adding requires removing.** Budgets are CI-enforced:
  `bash .claude/tools/check-instruction-budget.sh`.

Re-audit monthly — checklist in `docs/instruction-files.md`.

## Never do these

- **Never commit or push directly to `master`.** Only two PRs may target it:
  from `develop` (release promotion) or from `hotfix/*`. Enforced by the
  `master-source-check` CI job (plus branch protection with `enforce_admins`).
- **Never merge a feature/fix/docs PR into `develop` with a merge commit** —
  always `gh pr merge <n> --squash --delete-branch`. A merge commit duplicates
  every `CHANGELOG.md` entry (see `docs/release.md`). The one exception is the
  `develop` → `master` promotion PR, which must stay a true merge
  (`gh pr merge --merge`) — see the `devflow:close-milestone` skill.
- **Never hand-edit the project version or `CHANGELOG.md`.** Release Please
  derives both from Conventional Commits — `docs/release.md`.
- **Always put `Closes #N` in the PR body.** GitHub's own auto-close never fires
  here (PRs merge into `develop`, not the default branch), so
  `.github/workflows/close-linked-issues.yml` does it on merge instead — but
  only if the reference is there. CI-enforced by `repo-hygiene.yml`.

## Build & run

<!-- TEMPLATE: replace with the project's real commands, and keep them in step
with the Project configuration table in .claude/workflow.md. -->

- `<build command>` — build. `<test command>` — unit + acceptance tests.
  `<verify command>` — everything CI runs.
- Single test: `<command to run one test / one scenario>`.
- Run the app: `<run command>`.
- Clean Code gate: `bash .claude/tools/check-clean.sh` (commands in
  `.claude/tools/clean.env`).
- Test-layer rationale and troubleshooting: `docs/testing.md`.

## Docs

`docs/README.md` is the index. Read before writing code in these areas:

- Any test work → `docs/testing.md`.
- Architecture / module boundaries → `docs/architecture.md`.
<!-- TEMPLATE: add one line per area doc, e.g. "Any UI component → docs/ui-styling.md". -->

## Spec-first workflow

`.claude/workflow.md` has the pipeline; the skills and agents come from the
`devflow` plugin (enabled in `.claude/settings.json`). Read it when starting or
resuming a pipeline step, not every session. Repo layout:

- `specs/intent/<slug>.md` — gitignored scratch; copy `TEMPLATE.md`, or use the
  `devflow:spec-intent` skill to derive one from a GitHub issue.
- `specs/features/<slug>.feature` — run by the project's acceptance runner.
  **One `.feature` file per distinct concept**, never a bundled file. Each
  file's `Feature:` description block is the single source of truth for what it
  covers, supersedes, and excludes — keep that current, not a separate index.

Two pipeline steps are mandatory and easy to skip:

- **Step 4.5 — human playtest.** After the coder's commit, before the hardener:
  the human runs the app (the run command) and tries the changed behavior.
  Tests prove the code does what the spec says, not whether it *feels* right.
  For a multi-area change, playtest each area as it lands. Record what was
  tested in the PR description. No feature is done without this.
- **Step 7.5 — close the linked issue.** Automatic on merge via `Closes #N`
  above. Still manual for an issue with no PR of its own:
  `gh issue close <n> --reason completed`, and set the board's `Status` to Done.
