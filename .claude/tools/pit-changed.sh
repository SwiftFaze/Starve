#!/usr/bin/env bash
#
# Runs PIT only on the Kotlin classes this branch changed, against the unit and
# integration tests only. Mutating the whole project, or re-running the
# Cucumber suite (a Spring boot per scenario) for every mutant, made one run
# take ~45 min for a few changed files.
#
#   bash .claude/tools/pit-changed.sh            run PIT on the changed classes
#   bash .claude/tools/pit-changed.sh --print    only print the targetClasses value
#
# PIT_BASE_REF: branch to diff against (default origin/develop).
# Scope is per class (open-source PIT can't mutate only changed lines); survivors
# on lines the change didn't touch need no action (hardener "Done when").
# Exit: PIT's exit code; 0 with a message when no main Kotlin file changed.

set -uo pipefail

ROOT=$(git rev-parse --show-toplevel) || exit 64
cd "$ROOT" || exit 64
BASE="${PIT_BASE_REF:-origin/develop}"
merge_base=$(git merge-base "$BASE" HEAD) || { echo "ERROR: cannot find merge base with $BASE" >&2; exit 64; }

# A Kotlin file can declare several classes, plus a <File>Kt facade for its
# top-level functions, so read the declarations rather than trusting file names.
targets=()
while IFS= read -r file; do
  [ -f "$file" ] || continue
  pkg=$(sed -n 's/^package \([A-Za-z0-9_.]*\).*/\1/p' "$file" | head -1)
  [ -n "$pkg" ] || continue
  base=$(basename "$file" .kt)
  targets+=("$pkg.${base}Kt*")
  while IFS= read -r name; do
    targets+=("$pkg.$name*")
  done < <(sed -n -E 's/^([a-z]+ )*(class|interface|object) ([A-Za-z0-9_]+).*/\3/p' "$file")
done < <(git diff --name-only --diff-filter=AMR "$merge_base" -- 'src/main/kotlin/*.kt')

if [ "${#targets[@]}" -eq 0 ]; then
  echo "No main Kotlin source changed since $BASE: nothing to mutate."
  exit 0
fi

target_classes=$(printf '%s\n' "${targets[@]}" | sort -u | paste -sd, -)

if [ "${1:-}" = "--print" ]; then
  echo "$target_classes"
  exit 0
fi

# clean + test-compile in the same invocation: the goal alone runs stale test
# classes, and an incremental build can leave stale nested classes that crash
# coverage ("disagree on InnerClasses attribute").
./mvnw -B clean test-compile org.pitest:pitest-maven:mutationCoverage \
  "-DtargetClasses=$target_classes" \
  "-DexcludedTestClasses=dev.starve.acceptance.*" \
  -Dthreads=4
