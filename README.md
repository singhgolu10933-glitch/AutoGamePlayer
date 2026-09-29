# Auto Game Player — Full V1

A modular Android-first automation platform for games owned/developed by you.

## Current target
Game 001: 9x9 Block Puzzle.

## Design principles
1. The game exposes exact state through a local GameState API.
2. The Auto Player never needs to infer state from screenshots when direct state is available.
3. Every game implements the same GameModule contract.
4. The core engine is independent from any particular game.
5. AI policies can be replaced without changing the game.
6. Performance instrumentation is built into the loop.

## Project
- `app/src/main/java/com/autogameplayer/core` — generic automation core
- `app/src/main/java/com/autogameplayer/blockpuzzle` — Game 001
- `app/src/main/java/com/autogameplayer/ui` — Android UI
- `app/src/test` — engine and AI tests
- `config` — tunable AI/game settings

## Build
Open the project in Android Studio with an Android SDK and JDK 17+.

The repository contains Gradle configuration, but the Gradle wrapper JAR is intentionally not bundled by this generated source package. Android Studio can generate/sync it.

## Scope
This is for games you own/control. It does not attempt to bypass anti-cheat, DRM, access controls, or protections of third-party games.
