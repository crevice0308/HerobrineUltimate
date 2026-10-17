#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
test_output="$(mktemp -d)"
trap 'rm -rf -- "$test_output"' EXIT
java com.sun.tools.javac.Main -encoding UTF-8 -d "$test_output" \
  "$project_dir/common/src/main/java/com/yutadigital/herobrine/core/Ability.java" \
  "$project_dir/common/src/main/java/com/yutadigital/herobrine/core/PlayerSettings.java" \
  "$project_dir/tests/PlayerSettingsTest.java"
java -ea -cp "$test_output" PlayerSettingsTest
