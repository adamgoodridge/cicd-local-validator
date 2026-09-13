#!/usr/bin/env sh
set -eu

IMAGE_NAME="cicd-local-validator:local"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
CONTAINER_CMD=""

log() {
  echo "[$(date '+%H:%M:%S')] $*"
}

ensure_env_file() {
  if [ ! -f "$SCRIPT_DIR/.env" ] && [ -f "$SCRIPT_DIR/.env.example" ]; then
    cp "$SCRIPT_DIR/.env.example" "$SCRIPT_DIR/.env"
    echo "Created docker/.env from docker/.env.example"
  fi
}

load_container_command() {
  if [ -n "${CUSTOM_DOCKER_COMMAND:-}" ]; then
    CONTAINER_CMD="$CUSTOM_DOCKER_COMMAND"
    return
  fi

  if [ -f "$SCRIPT_DIR/.env" ]; then
    CONTAINER_CMD=$(grep -E '^CUSTOM_DOCKER_COMMAND=' "$SCRIPT_DIR/.env" | tail -n 1 | cut -d '=' -f 2- | tr -d '"' | tr -d "'" || true)
  fi

  if [ -z "$CONTAINER_CMD" ]; then
    CONTAINER_CMD="docker"
  fi

  if ! command -v "$CONTAINER_CMD" >/dev/null 2>&1; then
    echo "Container command '$CONTAINER_CMD' was not found in PATH."
    exit 1
  fi
}

ensure_workspace_path() {
  if [ -f "$SCRIPT_DIR/.env" ]; then
    workspace_path=$(grep -E '^LOCAL_WORKSPACE_PATH=' "$SCRIPT_DIR/.env" | tail -n 1 | cut -d '=' -f 2- | tr -d '"' | tr -d "'" || true)
    if [ -n "${workspace_path:-}" ]; then
      mkdir -p "$workspace_path"
      log "Ensured LOCAL_WORKSPACE_PATH exists: $workspace_path"
    fi
  fi
}

build_image_if_missing() {
  if ! "$CONTAINER_CMD" image inspect "$IMAGE_NAME" >/dev/null 2>&1; then
    log "Image $IMAGE_NAME not found. Building (this compiles the Java app)."
    log "First build can take several minutes (JDK image + apt install + Gradle bootJar)."
    if [ "$CONTAINER_CMD" = "docker" ]; then
      "$CONTAINER_CMD" build --progress=plain -f "$SCRIPT_DIR/Dockerfile" -t "$IMAGE_NAME" "$REPO_ROOT"
    else
      "$CONTAINER_CMD" build -f "$SCRIPT_DIR/Dockerfile" -t "$IMAGE_NAME" "$REPO_ROOT"
    fi
  else
    log "Image $IMAGE_NAME already exists. Skipping build."
  fi
}

start_container() {
  log "Starting container via '$CONTAINER_CMD compose up -d'"
  (cd "$SCRIPT_DIR" && "$CONTAINER_CMD" compose up -d)
}

ensure_env_file
ensure_workspace_path
load_container_command
log "Using container command: $CONTAINER_CMD"
build_image_if_missing
start_container

log "cicd-local-validator is starting on http://localhost:8080"

