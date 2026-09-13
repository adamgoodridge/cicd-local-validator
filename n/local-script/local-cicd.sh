#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/jobs-output"
SERVER_URL="${VALIDATOR_URL:-http://localhost:8080}"
TEMP_PAYLOAD_FILE=""

cleanup() {
  if [ -n "$TEMP_PAYLOAD_FILE" ] && [ -f "$TEMP_PAYLOAD_FILE" ]; then
    rm -f "$TEMP_PAYLOAD_FILE"
  fi
}
trap cleanup EXIT

load_env_file() {
  if [ -f "$SCRIPT_DIR/.env" ]; then
    set -a
    source "$SCRIPT_DIR/.env"
    set +a
  fi
}

resolve_ci_file() {
  local workspace="${LOCAL_WORKSPACE_PATH:-$SCRIPT_DIR}"
  local ci_file="$workspace/.gitlab-ci.yml"

  if [ ! -f "$ci_file" ] && [ -f "$SCRIPT_DIR/.gitlab-ci.yml" ]; then
    ci_file="$SCRIPT_DIR/.gitlab-ci.yml"
  fi

  if [ ! -f "$ci_file" ]; then
    echo "Error: .gitlab-ci.yml not found at $ci_file" >&2
    exit 1
  fi

  echo "$ci_file"
}

convert_format2_to_format1() {
  local ci_file="$1"
  TEMP_PAYLOAD_FILE=$(mktemp)

  python3 - "$ci_file" "$TEMP_PAYLOAD_FILE" << 'EOF'
import sys, os, re

ci_file = sys.argv[1]
out_file = sys.argv[2]
base_dir = os.path.dirname(ci_file)

with open(ci_file, 'r', encoding='utf-8') as f:
    content = f.read()

lines = content.splitlines()
main_lines = []
included_files = []
in_include = False

for line in lines:
    if re.match(r'^\s*include\s*:', line):
        in_include = True
        continue
    if in_include:
        m = re.search(r'local:\s*[\'"]?([^\'"\s]+)[\'"]?', line)
        if m:
            included_files.append(m.group(1))
            continue
        m_simple = re.search(r'^\s*-\s*[\'"]?([^\'"\s]+\.(?:yml|yaml))[\'"]?', line)
        if m_simple:
            included_files.append(m_simple.group(1))
            continue
        if line.strip() and not line.startswith(' ') and not line.startswith('\t') and not line.startswith('-'):
            in_include = False
            main_lines.append(line)
    else:
        main_lines.append(line)

out_content = '\n'.join(main_lines).strip() + '\n\n'
for inc_rel in included_files:
    inc_path = os.path.join(base_dir, inc_rel)
    if os.path.exists(inc_path):
        with open(inc_path, 'r', encoding='utf-8') as inc_f:
            out_content += inc_f.read().strip() + '\n\n'

with open(out_file, 'w', encoding='utf-8') as f:
    f.write(out_content)
EOF

  echo "$TEMP_PAYLOAD_FILE"
}

prepare_payload_file() {
  local ci_file="$1"

  if grep -q "include:" "$ci_file"; then
    convert_format2_to_format1 "$ci_file"
  else
    echo "$ci_file"
  fi
}

clean_output_dir() {
  mkdir -p "$OUTPUT_DIR"
  find "$OUTPUT_DIR" -mindepth 1 ! -name 'README.md' -exec rm -rf {} +
}

submit_validation_run() {
  local payload_file="$1"
  local response
  local run_id

  response=$(curl -sS -X POST "$SERVER_URL/api/validation/run/local" \
    -H "Content-Type: text/plain" \
    --data-binary "@$payload_file")

  run_id=$(printf '%s\n' "$response" | jq -r '.id // empty')

  if [ -z "$run_id" ] || [ "$run_id" = "null" ]; then
    echo "Error: Validation failed or invalid response from server." >&2
    echo "Server response: $response" >&2
    exit 1
  fi

  echo "$run_id"
}

fetch_job_logs() {
  local run_id="$1"
  local jobs_response

  jobs_response=$(curl -sS "$SERVER_URL/api/validation/runs/$run_id/jobs")

  printf '%s\n' "$jobs_response" | jq -r 'if type == "array" then .[].name else empty end' | while IFS= read -r job_name; do
    [ -n "$job_name" ] || continue
    curl -L -sS "$SERVER_URL/api/validation/runs/$run_id/jobs/$job_name/log" -o "$OUTPUT_DIR/$job_name.log"
  done
}

main() {
  load_env_file

  local ci_file
  ci_file=$(resolve_ci_file)

  local payload_file
  payload_file=$(prepare_payload_file "$ci_file")

  clean_output_dir

  local run_id
  run_id=$(submit_validation_run "$payload_file")
  echo "$run_id"

  fetch_job_logs "$run_id"
}

main "$@"