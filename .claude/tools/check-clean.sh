#!/usr/bin/env bash
#
# The Clean Code gate. ONE command, run identically by the hardener before it
# reports a task finished and by the orchestrator to verify that report.
#
#   bash .claude/tools/check-clean.sh            full gate
#   bash .claude/tools/check-clean.sh --fast     skip the verify command (inner loop only, NOT the gate)
#   bash .claude/tools/check-clean.sh --base <ref>
#
# Why one command for both sides: a subagent reporting "done" and the
# orchestrator bouncing it is expensive when the two sides check different
# things. Same script, same rules, same output closes that gap by construction.
#
# Stack-neutral: the stack-specific parts are commands read from
# .claude/tools/clean.env (environment variables override it):
#   VERIFY_CMD      required. Build + every test suite + coverage gates.
#   LINT_CMD        optional. Static analysis / formatter check, must exit
#                   non-zero on a violation (eslint, ruff, PMD, spotless...).
#   CLEAN_BASE_REF  branch to diff against (default develop).
#   CLEAN_SOURCE_GLOBS  space-separated pathspecs the text checks scan
#                   (default: everything except docs, specs and .claude/).
#
# Exit: 0 = mechanical checks pass (judgment checklist still owed)
#       1 = at least one blocking section failed
#       2 = could not run (not a git repo, VERIFY_CMD unset)

set -uo pipefail

ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "ERROR: not inside a git repository." >&2; exit 2; }
cd "$ROOT" || exit 2

CONFIG=".claude/tools/clean.env"
if [ -f "$CONFIG" ]; then
  # Environment wins over the file, so a one-off override needs no edit.
  saved_verify="${VERIFY_CMD-}"; saved_lint="${LINT_CMD-}"
  saved_base="${CLEAN_BASE_REF-}"; saved_globs="${CLEAN_SOURCE_GLOBS-}"
  # shellcheck disable=SC1090
  . "$CONFIG"
  [ -n "$saved_verify" ] && VERIFY_CMD="$saved_verify"
  [ -n "$saved_lint" ] && LINT_CMD="$saved_lint"
  [ -n "$saved_base" ] && CLEAN_BASE_REF="$saved_base"
  [ -n "$saved_globs" ] && CLEAN_SOURCE_GLOBS="$saved_globs"
fi

VERIFY_CMD="${VERIFY_CMD:-}"
LINT_CMD="${LINT_CMD:-}"
BASE_REF="${CLEAN_BASE_REF:-develop}"
SOURCE_GLOBS="${CLEAN_SOURCE_GLOBS:-. :(exclude)docs :(exclude)specs :(exclude).claude :(exclude)*.md}"
RUN_VERIFY=1

while [ $# -gt 0 ]; do
  case "$1" in
    --fast) RUN_VERIFY=0 ;;
    --base) shift; BASE_REF="${1:?--base needs a ref}" ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
  shift
done

hr() { echo "------------------------------------------------------------------------"; }
blocking=0
sections_failed=""
fail_section() { blocking=$((blocking + 1)); sections_failed="$sections_failed $1"; }

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

# ---------------------------------------------------------------------------
# 1. Verify command: build, tests, coverage, structural gates
# ---------------------------------------------------------------------------
hr
echo "1. Build, tests, coverage (verify command)"
hr
if [ "$RUN_VERIFY" -eq 0 ]; then
  echo "  SKIP  --fast: verify command not run. This is NOT a passing gate."
  fail_section "verify(skipped)"
elif [ -z "$VERIFY_CMD" ]; then
  echo "  ERROR VERIFY_CMD is not set. Configure it in $CONFIG" >&2
  echo "        (and the matching 'Verify command' row in .claude/workflow.md)." >&2
  exit 2
else
  log="$WORK/verify.log"
  echo "  \$ $VERIFY_CMD"
  if bash -c "$VERIFY_CMD" > "$log" 2>&1; then
    echo "  PASS"
  else
    echo "  FAIL  verify command failed. Last 40 lines:"
    tail -n 40 "$log" | sed 's/^/    /'
    fail_section "verify"
  fi
fi

# ---------------------------------------------------------------------------
# 2. Lint / static analysis (optional)
# ---------------------------------------------------------------------------
hr
echo "2. Lint / static analysis (LINT_CMD)"
hr
if [ -z "$LINT_CMD" ]; then
  echo "  SKIP  LINT_CMD not configured."
else
  log="$WORK/lint.log"
  echo "  \$ $LINT_CMD"
  if bash -c "$LINT_CMD" > "$log" 2>&1; then
    echo "  PASS"
  else
    echo "  FAIL  lint command failed. Last 40 lines:"
    tail -n 40 "$log" | sed 's/^/    /'
    fail_section "lint"
  fi
fi

# ---------------------------------------------------------------------------
# 3. Commented-out code, deferred work, suppressions (diff-scoped)
#    Only lines this change added are checked, so existing debt is not
#    attributed to whoever touches the file next.
# ---------------------------------------------------------------------------
hr
echo "3. Commented-out code, deferred work, suppressions (vs $BASE_REF)"
hr

merge_base=$(git merge-base HEAD "$BASE_REF" 2>/dev/null || git merge-base HEAD "origin/$BASE_REF" 2>/dev/null || true)
if [ -z "$merge_base" ]; then
  echo "  ERROR cannot find a merge-base with '$BASE_REF'. Pass --base <ref>." >&2
  exit 2
fi

# shellcheck disable=SC2086
git diff -U0 "$merge_base" -- $SOURCE_GLOBS > "$WORK/diff" 2>/dev/null
awk '
  /^\+\+\+ b\// { file = substr($0, 7); next }
  /^@@ / { split($3, a, ","); ln = a[1]; sub(/^\+/, "", ln); next }
  /^\+/ && !/^\+\+\+/ { print file "|" ln "|" substr($0, 2); ln++ }
' "$WORK/diff" > "$WORK/added.txt"
# Untracked files are entirely new: without this, a brand-new file escapes.
# shellcheck disable=SC2086
git ls-files --others --exclude-standard -- $SOURCE_GLOBS 2>/dev/null | while IFS= read -r f; do
  [ -f "$f" ] && awk -v F="$f" '{ print F "|" FNR "|" $0 }' "$f"
done >> "$WORK/added.txt"

text_fail=0
report_text_smell() {
  local label="$1" pattern="$2" advice="$3" hits
  hits=$(grep -E "$pattern" "$WORK/added.txt" 2>/dev/null || true)
  if [ -n "$hits" ]; then
    echo "  FAIL  $label:"
    printf '%s\n' "$hits" | awk -F'|' '{ printf "    %s:%s\n      %s\n", $1, $2, substr($3,1,100) }'
    echo "      -> $advice"
    text_fail=1
  fi
}

# Detected by SHAPE, not keywords: a // comment whose body ends in ';', '{'
# or '}' is a statement or a block; prose almost never ends that way.
report_text_smell "commented-out code" \
  '\|[0-9]+\|[[:space:]]*//[[:space:]]*(([^[:space:]].*)?[;{}][[:space:]]*$|(if|for|while|switch|catch|else)[[:space:]]*\()' \
  "Delete it. Version control is the record of code that used to exist."

report_text_smell "deferred-work marker" \
  '\|[0-9]+\|.*(//|/\*|\*|#|<!--)[[:space:]]*(TODO|FIXME|XXX|HACK)' \
  "Do it now, or open an issue and reference it by number instead."

# Decompose, never disable a check (.claude/workflow.md, 'Constraints').
report_text_smell "new lint/type-check suppression" \
  '\|[0-9]+\|.*(@SuppressWarnings|eslint-disable|@ts-ignore|@ts-nocheck|# *noqa|# *type: *ignore|# *pylint: *disable|NOSONAR|istanbul ignore|c8 ignore|nolint)' \
  "\"Fixed\" means decomposed, not suppressed. The one allowed exception is a parameter-count suppression on a library override (.claude/workflow.md)."

if [ "$text_fail" -eq 0 ]; then
  echo "  PASS  no commented-out code, deferred-work markers, or new suppressions"
else
  fail_section "text-smells"
fi

# ---------------------------------------------------------------------------
# Verdict
# ---------------------------------------------------------------------------
echo
hr
if [ "$blocking" -gt 0 ]; then
  echo "RESULT: FAIL"
  hr
  echo
  echo "Blocking sections:$sections_failed"
  echo
  echo "You may NOT report this task as finished. Fix the violations above and"
  echo "rerun this exact command. Do not suppress a rule to get past it, and do"
  echo "not report the task done with a caveat - if a violation cannot be fixed,"
  echo "stop and report the blocker to the orchestrator instead."
  exit 1
fi

echo "RESULT: MECHANICAL CHECKS PASS"
hr
echo
cat <<'EOF'
The machine has checked what it can. These rules cannot be automated and are
still owed - answer every line, in the completion report, with PASS or FAIL and
one clause of evidence. "All good" is not an answer; name the file.

  [ ] SLAP        Every function I added does its work at ONE level of
                  abstraction - no function mixes orchestration (calling named
                  steps) with detail (index arithmetic, string building).
                  Evidence: name the longest function you added and its level.
  [ ] SRP         Every class/module I touched can be described in one sentence
                  with no "and". Evidence: give that sentence for each new one.
  [ ] Naming      Every name I introduced can be understood without reading its
                  implementation, and no name needs a comment to explain it.
  [ ] Why-not-what Every comment I added explains WHY, not WHAT. A comment that
                  restates the code below it has been deleted or the code
                  renamed instead. Evidence: quote one comment you kept.
  [ ] Test intent Each test I added fails for exactly ONE reason, and its name
                  says which. Evidence: name one test and the single reason.
  [ ] AAA         Each test has visible arrange / act / assert phases, in that
                  order, with no assertion before the act.
  [ ] No new debt I introduced no code path that exists only for tests, and no
                  abstraction with a single caller added "for later".
  [ ] Single answer For each thing a changed UI/presentation unit shows or
                  decides, the domain function that supplies it - the UI
                  translates, it doesn't re-derive a rule the domain owns.
                  Evidence: name one UI function and the domain function it calls.

Design lens behind these: the devflow:uncle-bob-craft skill.
EOF
exit 0
