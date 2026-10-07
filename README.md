# Git and Conquer — server

Built for the Git & Conquer talk. The Quarkus (Kotlin) sidecar behind the Unity client. It owns the world's git
repo, turns gameplay intents into real `git` CLI commands, and pushes events
to the client over a WebSocket.

## Requirements

- JDK 21 (Gradle provisions it via the toolchain if missing)
- `git` on the `PATH`

## Run

```shell
./gradlew quarkusDev
```

Listens on `http://127.0.0.1:8080` (localhost only, no auth). On first start
it creates and seeds the world repo at `./world` (git-ignored).

## Merge drivers

The custom `map.grid` / `wallet.res` merge and diff drivers are a native
binary built from the `git-drivers` subproject:

```shell
./gradlew :git-drivers:nativeCompile
```

Build it once before starting the server; it registers the binary in the
world repo's `.git/config` on startup and logs a warning if it's missing.

## Configuration

`src/main/resources/application.yml`:

| Key               | Default    | Meaning                                         |
|-------------------|------------|-------------------------------------------------|
| `world.repo.path` | `./world`  | Where the world repo lives                      |
| `world.seed`      | `0`        | Map seed; `0` = random, anything else = same map |

## Reset the world

Stop the server, delete `world/`, start it again. Set `world.seed` to get
the same map back (the last seed used is in `world/meta/map.json`).

## Test

```shell
./gradlew test                  # server
./gradlew :git-drivers:test     # merge/diff drivers, against real git
```

## API

- REST: `/repo/*` (reads), `/actions/*` (gameplay, working tree only),
  `/git/*` (branch, checkout, merge, revert, cherry-pick, rebase, reset, tag,
  gc, conflict resolution), `/explain/*`
- WebSocket: `/ws/events` — HEAD moves, conflicts, tags, and every git
  command the server runs

Example requests live in `http/` (IntelliJ HTTP client).
