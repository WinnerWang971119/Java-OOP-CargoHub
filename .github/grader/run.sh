#!/usr/bin/env bash
# Driver for the CargoHub grader. The workflow and the local simulator both call this
# file, so a local run does exactly what the job does. It must always be the copy from
# the trusted checkout, never the one from a submission.
#
#   run.sh assemble   build the work dir: the submission's src/ with the original model classes
#   run.sh data       copy the test data to a temporary directory
#   run.sh compile    compile the work dir and the Grader
#   run.sh grade      run the Grader (with a time limit)
#   run.sh report     write the summary and exit 1 unless it is "Passed"
#   run.sh all        all of the above in order, like the job does
#
# Environment:
#   GRADER_SUBMISSION    the student's checkout (default: $GITHUB_WORKSPACE/submission)
#   GRADER_TMP           scratch directory (default: $RUNNER_TEMP/grader)
#   COMPILE_OUTCOME      report only: the outcome of the compile step (success, failure, ...)
#   GITHUB_STEP_SUMMARY  report only: file the summary is appended to (optional)
#
# Works with bash 3.2 (macOS) and the usual BSD or GNU tools.

set -eu

BASE=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
SUBMISSION=${GRADER_SUBMISSION:-${GITHUB_WORKSPACE:+$GITHUB_WORKSPACE/submission}}
TMP=${GRADER_TMP:-${RUNNER_TEMP:+$RUNNER_TEMP/grader}}

die() {
  echo "run.sh: $*" >&2
  exit 2
}

[ -n "$TMP" ] || die "set GRADER_TMP"
case "$TMP" in
  /?*) ;;
  *) die "GRADER_TMP must be an absolute path" ;;
esac

WORK=$TMP/work
BUILD=$TMP/build
DATA=$TMP/data
RESULT=$TMP/result.txt

# Runs a command with a time limit: coreutils timeout, else perl's alarm (macOS has no timeout).
run_with_timeout() {
  local seconds=$1
  shift
  if command -v timeout >/dev/null 2>&1; then
    timeout -k 10 "$seconds" "$@"
  elif command -v gtimeout >/dev/null 2>&1; then
    gtimeout -k 10 "$seconds" "$@"
  else
    perl -e 'alarm shift; exec @ARGV or die "cannot run $ARGV[0]: $!\n"' "$seconds" "$@"
  fi
}

assemble() {
  [ -n "$SUBMISSION" ] || die "set GRADER_SUBMISSION"
  rm -rf "$WORK"
  mkdir -p "$WORK/src"
  # A src that is a link could point anywhere, so it counts as missing (the compile then fails).
  if [ -d "$SUBMISSION/src" ] && [ ! -L "$SUBMISSION/src" ]; then
    cp -RP "$SUBMISSION/src/." "$WORK/src/"
  fi
  find "$WORK/src" -type l -exec rm -f {} +
  # Provided classes always come from the trusted checkout.
  rm -rf "$WORK/src/org/amped/model"
  mkdir -p "$WORK/src/org/amped"
  cp -RP "$BASE/src/org/amped/model" "$WORK/src/org/amped/model"
}

data() {
  rm -rf "$DATA"
  mkdir -p "$DATA"
  cp "$BASE"/.github/grader/*.txt "$DATA/"
}

compile() {
  unset CLASSPATH
  rm -rf "$BUILD" || return 1
  mkdir -p "$BUILD" || return 1
  # Annotation processing is off: compiling must never run code from the submission.
  find "$WORK/src" -type f -name '*.java' -print0 |
    xargs -0 javac -proc:none -encoding UTF-8 -d "$BUILD" ||
    return 1
  # The Grader lives in the default package. Drop any default-package class from the submission
  # so it cannot shadow java.lang types inside the Grader, then compile the Grader on its own.
  find "$BUILD" -maxdepth 1 -type f -name '*.class' -delete || return 1
  javac -proc:none -encoding UTF-8 -cp "$BUILD" -d "$BUILD" "$BASE/.github/grader/Grader.java" || return 1
}

grade() {
  unset CLASSPATH
  rm -f "$RESULT"
  # The student code runs inside this JVM. Keep the runner's file-command variables away from it.
  run_with_timeout 300 env -u GITHUB_ENV -u GITHUB_PATH -u GITHUB_OUTPUT -u GITHUB_STATE -u GITHUB_STEP_SUMMARY \
    java -cp "$BUILD" Grader "$DATA" "$RESULT" </dev/null
}

# True if the result file says this part passed. $1 = label in the result file (PART1, ...).
part_passed() {
  grep -qxF "$1 PASSED" "$RESULT"
}

# One summary line for a part: $1 = label in the result file, $2 = text if it passed, $3 = text if not.
part_line() {
  if part_passed "$1"; then
    printf '%s' "$2"
  else
    printf '%s' "$3"
  fi
}

report() {
  local summary
  local passed=0
  local outcome=${COMPILE_OUTCOME:-}
  if [ "$outcome" = "failure" ]; then
    summary='❌ Failed: your code does not compile. Open the "Compile" step to see why.'
  elif [ "$outcome" = "success" ] && [ -f "$RESULT" ]; then
    local headline='❌ Failed'
    # Parts 1 and 2 decide the check. Part 3 is a bonus and never changes the colour.
    if part_passed PART1 && part_passed PART2; then
      passed=1
      headline='✅ Passed'
    fi
    # One line per part, in the result file's order. Add a part_line call for every new part.
    # Blank lines between them: the job summary is markdown.
    summary=$(printf '%s\n\n%s\n\n%s\n\n%s' "$headline" \
      "$(part_line PART1 'Part 1 · processBatch: ✅ Passed' 'Part 1 · processBatch: ❌ Failed')" \
      "$(part_line PART2 'Part 2 · optimizeInbound: ✅ Passed' 'Part 2 · optimizeInbound: ❌ Failed')" \
      "$(part_line PART3 '⭐ Bonus Part 3 · planTrucks: Passed' 'Bonus Part 3 · planTrucks: not passed yet')")
  else
    summary='❌ Failed'
  fi

  printf '%s\n' "$summary"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    printf '%s\n' "$summary" >>"$GITHUB_STEP_SUMMARY"
  fi
  [ "$passed" -eq 1 ] || exit 1
}

all() {
  assemble
  data
  if compile; then
    COMPILE_OUTCOME=success
    grade || true
  else
    COMPILE_OUTCOME=failure
  fi
  export COMPILE_OUTCOME
  report
}

case "${1:-}" in
  assemble | data | compile | grade | report | all) "$1" ;;
  *) die "usage: run.sh assemble|data|compile|grade|report|all" ;;
esac
