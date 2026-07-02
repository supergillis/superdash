# Contributing to superdash

Thanks for your interest in contributing! superdash is a Home Assistant kiosk
app for Android tablets. This document covers the practical basics.

## Prerequisites

- **JDK 17**
- **Android SDK** (the NDK/CMake components are resolved by the build as
  needed; native code is part of the build)
- Git LFS is **not** required

Note: `whisper.cpp` is fetched on demand and is optional — CMake builds a stub
when it is absent, so a plain checkout builds fine without it.

## Building

The project is a multi-module Gradle build; the app module is `:packages:app`.

```sh
./gradlew :packages:app:assembleDebug
```

## Running tests

```sh
./gradlew testDebugUnitTest
```

## Lint

Kotlin code is linted with ktlint:

```sh
./gradlew ktlintCheck    # check
./gradlew ktlintFormat   # auto-format
```

## Code style

See [AGENTS.md](AGENTS.md) for the project's code conventions.

## Pull requests

1. Create a branch from `main`.
2. Make your changes; keep each PR focused on a single topic.
3. Make sure `./gradlew ktlintCheck testDebugUnitTest` passes before opening
   the PR.

## License

superdash is licensed under the GPL-3.0 (see [LICENSE](LICENSE)). By
contributing, you agree that your contributions are licensed under the same
terms.
