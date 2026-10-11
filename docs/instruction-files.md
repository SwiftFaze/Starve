# Instruction files: budgets, review, and audits

`CLAUDE.md` and the files under `.claude/` are not documentation — they are
loaded into an agent's context and spend budget on every task that reads them.
Left alone they decay in a predictable way: a task goes wrong, a rule gets
appended to prevent a recurrence, and nothing is ever removed. After enough
rounds the agent carries a page of stale, overlapping advice into work none of
it applies to, and the rules that genuinely matter are harder to find.

This file is the system that prevents that. `CLAUDE.md`'s "Editing this file"
section is the short version an agent reads; this is the full rationale, the
review checklist, and the audit procedure.

## Budgets

Budgets are tiered by **how often a file is loaded**, because that is what it
actually costs — a line in `CLAUDE.md` is paid on every task, a line in a
`SKILL.md` only when that skill runs.

| File | Loaded | Budget |
|---|---|---|
| `CLAUDE.md` | every session | 100 lines |
| `.claude/workflow*.md` | every pipeline step | 170 lines |
| `.claude/agents/*.md` (project-local, if any) | every agent run | 60 lines |
| `.claude/skills/*/SKILL.md` (project-local, if any) | on skill use | 215 lines |
| `docs/*.md` | on demand | 250 lines |

The devflow plugin's own skills and agents live in the plugin repo and are
budgeted there, not here. Vendored third-party skills (`impeccable`, under
`.claude/skills/impeccable/` and `.claude/agents/impeccable-*.md`) are exempt
(`VENDORED` in the script): their installer rewrites them, so trimming them here
would be lost on the next update.

These numbers are **baselines, not targets** — set at each file's size when the
template was cut, so the gate stops growth immediately and can be tightened
afterwards. A gate that is red on the day it arrives teaches everyone to ignore
it. **When you trim a file, lower its budget in the same commit** — that is the
ratchet, and without it the baseline just becomes the new floor.

Enforced by `.claude/tools/check-instruction-budget.sh`, run in CI by
`.github/workflows/repo-hygiene.yml`. Run it locally any time:

```
bash .claude/tools/check-instruction-budget.sh --verbose
```

The budget is a forcing function, not a quality measure. Being under it does not
make a file good; hitting it forces the trade that keeps the file honest.

### When a file is over budget

In preference order:

1. **Delete a rule that no longer applies.** `git blame` the line — if it was
   added to patch an incident that is long resolved, or names a file or flag
   that no longer exists, it goes.
2. **Relocate it** to the file whose subject it is, leaving a link rather than a
   copy. One canonical home per rule (see below).
3. **Mechanize it** — a test, a CI job, a lint rule — then delete the prose. A
   rule a tool enforces does not need to be remembered.
4. **Only then, raise the budget**, and say in the commit message why the file
   genuinely needs to be bigger.

## One canonical home per rule

Every rule lives in exactly one file. Every other mention is a link, never a
restatement — two copies drift, and the reader cannot tell which is current.

| Subject | Canonical home |
|---|---|
| Pipeline sequencing, gates, thresholds, project configuration | `.claude/workflow.md` |
| Test layers, runners, test mechanics | `docs/testing.md` |
| Delegating Steps 4-7 to subagents | `devflow:implement-issue` skill; each role in the plugin's `agents/` |
| Release, versioning, changelog | `docs/release.md` |
| Architecture, module boundaries | `docs/architecture.md` |
| Repo-wide hard prohibitions, entry points | `CLAUDE.md` |

## Review checklist for changes to CLAUDE.md or .claude/*.md

Edits to these files change agent behaviour on every future task, so they get
the same scrutiny as code. Work through this on any PR touching them.

**Necessity**
- [ ] Would this rule matter across **many** future tasks? If it exists to
      prevent one specific mistake that already happened, fix that in code or in
      the PR instead — a standing instruction is the most expensive possible fix.
- [ ] Is it a rule, or is it a **current state**? Transient facts ("X is broken
      until #N lands") belong in the issue, not a rules file. They are the single
      biggest source of staleness, because nothing prompts anyone to remove them.

**Redundancy**
- [ ] Does this rule already exist elsewhere? `grep` a distinctive phrase across
      `CLAUDE.md .claude/*.md docs/*.md` before adding.
- [ ] If a related rule exists in another file, does this **link** to it rather
      than restate it?

**Contradiction**
- [ ] Does it conflict with an existing rule, including in tone ("always" here
      vs "prefer" there)? Two rules that disagree mean the agent follows
      whichever it read last.
- [ ] If it narrows or supersedes an existing rule, is that rule **updated or
      deleted** in the same commit?

**Scope**
- [ ] Is it in the right file per the canonical-home table above?
- [ ] Is it general, or does it belong in a `SKILL.md` for the one workflow that
      needs it?

**Mechanizability**
- [ ] Could a test, CI job, or lint rule enforce this instead? If yes, do that
      and do not add the prose. See "Rules that became tooling" below.

**Staleness**
- [ ] Does it carry `<!-- added YYYY-MM-DD: reason -->`?
- [ ] Do the files, commands, flags, and issue numbers it names still exist?

**Budget**
- [ ] Does `bash .claude/tools/check-instruction-budget.sh` pass? If the file is
      at or over budget, something must be removed or consolidated in the same
      commit.

## Monthly audit

Takes about twenty minutes. Open an issue titled "Instruction file audit
<YYYY-MM>" so it is visible and skippable-with-a-reason rather than silently
dropped.

1. **Budget** — run the script. Anything over, or above 90%, gets trimmed now
   rather than at the next emergency.
2. **Staleness sweep** — for every rule carrying a date older than six months,
   ask whether the incident it prevents can still happen. Rules that patched a
   since-deleted subsystem go.
3. **Reference check** — every file path, command, class name, flag, and issue
   number mentioned still resolves:
   ```
   grep -ohE '`[a-zA-Z0-9_./-]+\.(md|sh|json|yml|feature)`' CLAUDE.md .claude/*.md docs/*.md \
     | tr -d '`' | sort -u | while read -r f; do
       [ -e "$f" ] || git ls-files --error-unmatch "$f" >/dev/null 2>&1 || echo "MISSING: $f"
     done
   ```
4. **Duplication sweep** — for each rule in `CLAUDE.md`, grep its key phrase
   across the other instruction files. More than one hit means one is a copy
   that should become a link.
5. **Closed-issue sweep** — every issue number referenced in a rules file:
   ```
   grep -ohE '#[0-9]+' CLAUDE.md .claude/*.md | tr -d '#' | sort -u | while read -r n; do
     state=$(gh issue view "$n" --json state -q .state 2>/dev/null)
     [ "$state" = "CLOSED" ] && echo "Issue #$n is CLOSED - is the rule referencing it still needed?"
   done
   ```
   A closed issue behind a "this is broken until #N lands" note means that note
   is now actively misleading.
6. **Contradiction spot-check** — re-read `CLAUDE.md` top to bottom in one sitting.
   Contradictions are invisible when you only ever read one section at a time.

## Rules that became tooling

Kept as a record so the same rule does not get re-added as prose later.

| Was prose in | Now enforced by |
|---|---|
| Branch naming convention | `ci.yml` `branch-name` job |
| Only `develop`/`hotfix/*` may target `master` | `ci.yml` `master-source-check` job + branch protection |
| "Always put `Closes #N` in the PR body" | `repo-hygiene.yml` `pr-body-closes-issue` job |
| "Never hand-edit the version or `CHANGELOG.md`" | `repo-hygiene.yml` `no-manual-release-edits` job |
| Instruction file size | `check-instruction-budget.sh` |
| Commented-out code, TODOs, lint suppressions in a diff | `check-clean.sh` section 3 |

Add a row whenever a rule moves from prose to a check (complexity limits to a
linter config, layering to an architecture test, and so on).

**What tooling cannot do here**, so it stays prose: the Step 4.5 human playtest,
"one `.feature` file per distinct concept", and judgement calls about scope. Do
not try to mechanize these — a check that approximates a judgement call is worse
than the prose, because it gets trusted.

A note on limits: a lint rule that a test contains *an* assertion does not
catch a test whose assertion is vacuous (`assertNotNull` on a freshly
constructed object passes every gate). Only mutation testing catches that class
of problem — Step 6 of `.claude/workflow.md`. Prefer the check that can
actually fail for the right reason.
