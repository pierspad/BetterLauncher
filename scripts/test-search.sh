#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
task_tmp=$(mktemp -d)
trap 'rm -rf "$task_tmp"' EXIT
kotlinc app/src/main/java/app/olauncher/helper/FuzzySearch.kt app/src/androidTest/java/app/olauncher/SearchMatchingRegression.kt -include-runtime -d "$task_tmp/search-tests.jar"
java -jar "$task_tmp/search-tests.jar"
