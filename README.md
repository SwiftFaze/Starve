# Starter template: spec-first agentic workflow

A stack-neutral GitHub template for building with [Claude Code](https://claude.com/claude-code)
and the [`devflow`](https://github.com/SwiftFaze/claude-lib) plugin: issue →
intent doc → Gherkin spec → test-first implementation (coder agent) → human
playtest → hardening (hardener agent) → PR → automatic release.

It contains no application code and no build tool. Use it for Java, Angular,
Node, Python or anything else: the stack-specific parts are a handful of
commands you fill in once.

## What's in it

| Path | Purpose |
|---|---|
| `CLAUDE.md` | Always-loaded agent instructions: hard rules, entry points, doc map |
| `.claude/workflow.md` | The pipeline, plus the **Project configuration** table every devflow skill reads |
| `.claude/settings.json` | Declares the `SwiftFaze/claude-lib` marketplace and enables `devflow` |
| `.claude/tools/check-clean.sh` | The Clean Code gate: verify + lint commands, diff-scoped smell checks, judgment checklist |
| `.claude/tools/clean.env` | The commands the gate runs (`VERIFY_CMD`, `LINT_CMD`) |
| `.claude/tools/check-instruction-budget.sh` | Line budgets for instruction files (CI-enforced) |
| `.claude/tools/set-project-field.sh` | Sets a GitHub project-board field and reads it back |
| `.claude/tools/check-qa-coverage.sh` | Optional: every changed `.feature` has a QA procedure |
| `specs/intent/`, `specs/features/` | Intent docs (gitignored scratch) and executable Gherkin specs |
| `docs/` | Architecture, testing, release and instruction-file docs (stubs to fill) |
| `.github/workflows/` | CI, repo hygiene, auto-close of linked issues, Release Please |

## Where `.claude/` comes from

The workflow kit is initialised from
[SwiftFaze/claude-lib](https://github.com/SwiftFaze/claude-lib), which holds
two halves:

- **The `devflow` plugin** (skills + `coder`/`hardener` agents). Not copied
  here: `.claude/settings.json` enables it, so it updates with the plugin.
- **The per-project kit in its `lib/`**, copied into this repo:
  `lib/workflow.md` → `.claude/workflow.md`, `lib/specs/` → `specs/`, and
  `lib/tools/set-project-field.sh`, `check-qa-coverage.sh` and
  `check-instruction-budget.sh` → `.claude/tools/`.

Two deliberate differences from `lib/`: `check-clean.sh` here is a
stack-neutral rewrite (lib's version assumes Maven + PMD/SpotBugs; copy that one
instead for a Java project that wants the full gate), and the Java-only tools
(`check-quality-gates.sh`, `check-jqwik-canary.sh`) are left out. To pick up
later changes to the kit, re-copy those files from claude-lib and keep your
filled-in Project configuration table.

## Set up a new project from this template

1. **Create the repo** with "Use this template". The release branch is
   `master`: if GitHub named the first branch `main`, rename it
   (`gh api -X POST repos/<owner>/<repo>/branches/main/rename -f new_name=master`).
   Then create the base branch:
   `git switch -c develop && git push -u origin develop`.
2. **Install devflow** if you don't have it: Claude Code offers it on first
   trust of the folder (from `.claude/settings.json`), or run
   `/plugin install devflow@claude-lib`.
3. **Fill in `.claude/workflow.md`**: every `<placeholder>` in the Project
   configuration table, and the values under Constraints.
4. **Fill in `.claude/tools/clean.env`** (`VERIFY_CMD`, optional `LINT_CMD`).
5. **Fill in `CLAUDE.md`**: the project description, Build & run, Docs.
6. **Make CI build the project**: replace the placeholder step in
   `.github/workflows/ci.yml` `build-and-test`.
7. **Project board** (optional): set `PROJECT_NUMBER` / `PROJECT_OWNER` in
   `.claude/tools/set-project-field.sh` or your environment. The board needs
   single-select fields `Status` (`Backlog`, `Ready`, `In progress`,
   `In review`, `Done`) and `Priority` (`P0`, `P1`, `P2`).
8. **Releases**: follow "One-time setup" in `docs/release.md`
   (`RELEASE_PLEASE_TOKEN` secret, allow auto-merge).
9. **Branch protection** on `master` and `develop`: require PRs and the CI and
   Repo hygiene checks; on `master`, enable "Include administrators".
10. **Docs**: replace the stubs in `docs/architecture.md` and `docs/testing.md`,
    then delete this section of the README and describe your project instead.

Every spot to fill is marked `TEMPLATE` or holds a `<placeholder>`;
`grep -rn TEMPLATE CLAUDE.md .claude docs .github` finds the marked ones.

## Day-to-day

| Step | Skill |
|---|---|
| Think out loud, file an issue (or a milestone of them) | `/devflow:brainstorm-issue`, `/devflow:brainstorm-milestone` |
| Start an issue: branch, board status, intent doc | `/devflow:spec-intent <n>` |
| Intent → `.feature` spec | `/devflow:spec-feature` |
| Coder → playtest → hardener → PR | `/devflow:implement-issue` |
| Whole standard path unattended, up to the playtest | `/devflow:spec-intent-auto <n>` |
| Pick up a dead session | `/devflow:resume-issue <n>` |
| Ship a milestone (develop → master) | `/devflow:close-milestone` |
| Health-check the board | `/devflow:audit-planning` |

**Prerequisites:** `gh` authenticated with the `project` scope
(`gh auth refresh -s project`), `jq`, and `bash` (Git Bash on Windows).
