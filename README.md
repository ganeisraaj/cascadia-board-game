# Cascadia Board Game

A digital implementation of the **Cascadia** board game, built in **Kotlin** using the [BoardGameWork (BGW)](https://tudo-aqua.github.io/bgw/) framework as part of a software engineering internship.

---

## About the Project

Cascadia is a tile-laying strategy game where players build habitats and place wildlife tokens to score points. This implementation brings the full board game experience to a desktop application, including network multiplayer and AI opponents.

---

## Features

- 🎮 **Local hotseat** multiplayer (2–4 players)
- 🌐 **Network multiplayer** via BGW-Net
- 🤖 **AI bots** — random and tournament-level difficulty
- ↩️ **Undo/Redo** with full history persistence
- 💾 **Save & Load** with complete game state serialization
- 🔷 **Hex-grid tile placement** with rotation support
- 🦅 **Complex wildlife scoring** — salmon chains, hawk line-of-sight, habitat corridor bonuses, nature token mechanics
- ⚡ **Simulation speed control** for bot games
- 📥 **CSV tile import** for custom tile sets

---

## Architecture

The project follows a strict **3-layer architecture**:

- **Entity layer** — pure data classes (`Game`, `Player`, `HabitatTile`, `WildlifeToken`, etc.)
- **Service layer** — all game logic (`GameService`, `UserActionService`, `BotService`, `NetworkService`)
- **GUI layer** — all scenes and UI components built with BGW

---

## Tech Stack

| Tool | Purpose |
|---|---|
| Kotlin | Primary language |
| BoardGameWork (BGW) | 2D game framework |
| JUnit 5 | Testing |
| detekt | Static analysis |
| KDoc | Documentation |
| Gradle | Build tool |
| Git | Version control |

---

## Development Practices

This project was developed following professional software engineering practices:

- Feature branch Git workflow with merge requests and peer code reviews
- Static analysis enforcement via **detekt**
- Full **KDoc** documentation on all public classes and functions
- Comprehensive **JUnit 5** test suites covering edge cases and service logic

---

## Disclaimer

This project was built as part of a software engineering internship. The original Cascadia board game is designed by Randy Flynn and published by Flatout Games.
