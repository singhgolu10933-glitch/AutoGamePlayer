Auto Game Player

A modular Android-first automation platform for games that you own or control.

AutoGamePlayer is designed around a reusable automation core, game-specific modules, replaceable AI policies, deterministic game-state interfaces, testing, and performance instrumentation.

Current Target

Game 001 — 9×9 Block Puzzle

The current implementation focuses on a 9×9 block puzzle as the first game module while keeping the core engine independent from the specific game.

Architecture

AutoGamePlayer separates the reusable automation engine from game-specific logic.

AutoGamePlayer
│
├── Core Engine
│   ├── Automation loop
│   ├── Game state handling
│   ├── Action execution
│   └── Performance instrumentation
│
├── Game Modules
│   └── Block Puzzle
│       ├── Game state
│       ├── Board representation
│       └── Game-specific actions
│
├── AI Policies
│   └── Replaceable decision policies
│
├── Android UI
│
├── Configuration
│
└── Tests

Design Principles

1. The game exposes exact state through a local "GameState" API.
2. When direct game state is available, the automation engine does not need to infer state from screenshots.
3. Every supported game follows the same "GameModule" contract.
4. The core engine remains independent from any particular game.
5. AI policies can be replaced without changing the game module.
6. Performance instrumentation is part of the automation loop.

Repository Structure

app/
├── src/main/java/com/autogameplayer/
│   ├── core/
│   ├── blockpuzzle/
│   └── ui/
│
└── src/test/

config/
docs/

.github/
└── workflows/

README.md
build.gradle.kts
settings.gradle.kts
gradle.properties

Core

"app/src/main/java/com/autogameplayer/core"

Contains the reusable automation engine and core interfaces.

Block Puzzle

"app/src/main/java/com/autogameplayer/blockpuzzle"

Contains the Game 001 implementation for the 9×9 block puzzle.

UI

"app/src/main/java/com/autogameplayer/ui"

Contains the Android user interface.

Tests

"app/src/test"

Contains engine and AI-related tests.

Configuration

"config"

Contains tunable game and AI configuration.

Requirements

- Android Studio
- Android SDK
- JDK 17 or newer
- An Android device or emulator suitable for development/testing

Build

Open the repository in Android Studio.

Allow Android Studio to synchronize the Gradle project and configure the required Android SDK/JDK.

The repository contains Gradle project configuration. The Gradle wrapper JAR is intentionally not included in the generated source package.

Testing

The project contains tests under:

app/src/test

Tests are intended to validate core engine behavior and AI/game logic independently from the Android UI.

Development Status

AutoGamePlayer is an actively developed open-source project.

Current development is focused on:

- Core automation architecture
- Game module interfaces
- 9×9 block puzzle automation
- AI policy integration
- Runtime validation
- Automated testing
- Performance instrumentation

Features and APIs may continue to evolve as development progresses.

Safety and Scope

AutoGamePlayer is intended for games that you own or control, or for environments where automation is explicitly permitted.

The project does not attempt to bypass anti-cheat systems, DRM, access controls, authentication controls, or other protections of third-party games.

Contributing

Contributions, bug reports, documentation improvements, testing, and technical feedback are welcome.

Before submitting a change:

1. Keep changes focused.
2. Add or update tests when appropriate.
3. Document behavior that affects public interfaces.
4. Avoid introducing unnecessary game-specific dependencies into the core engine.

License

See the repository license file for the current licensing terms.

Project Goals

The long-term goal is to provide a clean, reusable foundation for Android game automation where:

- game logic is isolated from the automation engine,
- AI policies remain replaceable,
- behavior is testable,
- performance can be measured,
- and new supported games can be implemented through the common module contract.
