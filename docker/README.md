# Docker Local Run

## What `start-local.sh` does

1. Ensures `docker/.env` exists (copies from `docker/.env.example` if missing).
2. Checks whether image `cicd-local-validator:local` exists.
3. If missing, builds it with `docker/Dockerfile` (which compiles the Java app during image build and installs Docker/Podman CLIs for local Job execution).
4. Starts the container via `docker compose up -d`.

`start-local.sh` chooses the container CLI from `CUSTOM_DOCKER_COMMAND` (shell env first, then `docker/.env`, then default `docker`).
Set it to `podman` to run the same flow via Podman.

## Socket passthrough

The app container receives a container runtime socket through compose bind-mount:

- `CONTAINER_SOCKET_HOST_PATH` - socket on host
- `CONTAINER_SOCKET_CONTAINER_PATH` - socket path inside container
- `LOCAL_WORKSPACE_PATH` - absolute host path for local Job workspace (must be Docker-shared)

`DOCKER_HOST` is set to `unix://${CONTAINER_SOCKET_CONTAINER_PATH}`.

Examples in `docker/.env`:

```dotenv
# Docker
CONTAINER_SOCKET_HOST_PATH=/var/run/docker.sock
CONTAINER_SOCKET_CONTAINER_PATH=/var/run/docker.sock
LOCAL_WORKSPACE_PATH=/Users/your-user/cicd-local-validator-jobs

# Podman rootless
# CONTAINER_SOCKET_HOST_PATH=/run/user/1000/podman/podman.sock
# CONTAINER_SOCKET_CONTAINER_PATH=/var/run/docker.sock
# LOCAL_WORKSPACE_PATH=/Users/your-user/cicd-local-validator-jobs
```

If you see `mounts denied ... The path /app is not shared from the host`, set `LOCAL_WORKSPACE_PATH` to a shared host path under `/Users/...` and restart with `./docker/start-local.sh`.

## Run

```sh
./docker/start-local.sh
```

If this looks "stuck" on first run, it is usually building `cicd-local-validator:local` (Gradle + package installs). The script prints timestamps and build progress.

## Stop

```sh
cd docker
${CUSTOM_DOCKER_COMMAND:-docker} compose down
```
