# HackerTerminalQuest Android

> An Android game that teaches terminal/command-line hacking concepts by having you actually type commands to progress through levels.

HackerTerminalQuest Android is a terminal-hacking educational game: a custom command parser reads what you type, a game engine advances state and levels based on it, and a terminal-style UI renders the output line by line — turning basic command-line literacy into a level-based game.

## Features
- **Custom command parser** (`CommandParser.kt`) that interprets typed terminal commands.
- **Game engine** (`GameEngine.kt`) driving level and state progression from parsed commands.
- **MVVM structure** with a dedicated `GameViewModel` separating game logic from the UI.
- **Terminal-style UI** with a line-by-line adapter (`TerminalAdapter.kt`) for authentic terminal output.
- **Splash + main activity flow** for app startup and the core game screen.

## Stack
Kotlin, Android Gradle Plugin 8.1, Kotlin Gradle plugin 1.9, min SDK 24 / target SDK 34.

## Getting started
**Requirements** — Android Studio (or the Gradle wrapper) with SDK 34 installed.

**Run**
The Gradle wrapper isn't committed (no `gradlew`), though `build.gradle`/`settings.gradle` are present.
```bash
# open the project in Android Studio and run the app module directly
# (Android Studio will generate the missing wrapper on sync)
# — or, with Gradle installed: gradle wrapper && ./gradlew assembleDebug
```

## Status
**Unmaintained / archived.** Personal project, published as-is — fork it, adapt it, take it over. No support or guarantees.

## License
[MIT](LICENSE) — free to use, fork, and build on.
