# Keel (Under Development)

Keel is a privacy-first Android financial agent built for Nigerian bank alerts. It captures SMS and push notifications, parses transactions on-device, stores them locally in an encrypted database, and uses an on-device LLM to generate useful financial insights without sending user data to the cloud.

This project is designed for offline-first thinking, background job orchestration, deterministic safety systems, and an agent workflow layered on top of a real mobile product.

## Overview

The core idea behind Keel is simple: personal finance software should be useful without requiring users to hand over sensitive banking data to a remote service.

Keel approaches that by combining:

- local SMS and notification ingestion
- structured transaction parsing for Nigerian bank formats
- encrypted storage with Room and SQLCipher
- a tool-using on-device agent for reviews and chat-style financial questions
- deterministic guardrails for urgent cases like low balance or suspicious repeat charges

Rather than acting like a chatbot wrapper, the app is structured like a real assistant that can observe, reason, store memory, and act within clear boundaries.

## What Currently Works

The project already has substantial implementation across the core product layers.

### App foundation

- Multi-module Android project with clear separation between app, core, and feature modules
- Kotlin, Jetpack Compose, Hilt, WorkManager, Room, SQLCipher, and LiteRT-LM integration
- Application-level wiring for workers, notifications, and dependency injection

### Data ingestion

- SMS broadcast receiver for incoming bank alerts
- Notification listener for supported bank app notifications
- One-time SMS inbox backfill for recent messages
- Raw event storage before parsing so data is captured early in the pipeline

### Parsing and transaction pipeline

- Parser registry driven by JSON bank configs in app assets
- Amount parsing for common naira/kobo text formats
- Transaction parsing coverage for:
  - GTBank
  - Access Bank
  - First Bank
  - UBA
  - Kuda
  - OPay
  - PalmPay
  - Zenith
  - Moniepoint
- Deduplication logic across raw events and parsed transactions
- LLM fallback path for messages that do not match regex parsing

### Storage and domain layer

- Shared domain models in `core-model`
- Encrypted Room database in `core-database`
- Repositories for transactions, raw events, insights, memory, and agent runs
- DataStore-backed app state for onboarding and background flow support

### Agent system

- ReAct-style loop for agent execution
- Context builder for assembling recent transactions, memory, and tool definitions
- Tool registry for transaction queries, summaries, memory search, and insight creation
- Agent run logging for inspectable execution history
- Memory consolidation worker for long-term memory maintenance

### Guardrails and background processing

- Guardrail engine for:
  - low balance alerts
  - possible duplicate charge alerts
  - unusually large debit alerts
- WorkManager-based periodic and event-driven execution
- Separate workers for parsing, LLM parsing, SMS backfill, memory consolidation, and agent runs

### User interface

- Onboarding flow
- Dashboard screen
- Chat screen
- Transaction detail screen
- Settings screen
- Agent debug screen
- Navigation wired through a Compose nav host


## Repository Structure

The repo is organized into focused modules:

```text
keel-v2/
├── app/                  # Android app entry point, workers, app wiring
├── core-model/           # Shared Kotlin domain models
├── core-database/        # Room, SQLCipher, DAOs, repositories, migrations
├── core-datastore/       # DataStore-backed app and onboarding state
├── feature-ingestion/    # SMS + notification capture
├── feature-parser/       # Parser registry, amount parsing, bank configs
├── feature-agent/        # ReAct loop, tools, context, guardrails, embeddings
├── feature-llm/          # Backend abstraction, on-device model integration, downloads
├── feature-ui/           # Compose UI, navigation, view models, screens
├── metadata/             # F-Droid metadata
├── gradle/               # Version catalog and wrapper
└── LICENSE
```

### Module responsibilities

- `app`
  Owns the Android application shell and cross-feature orchestration. This is where the workers live as they coordinate multiple modules.

- `core-model`
  Contains the domain language of the app: transactions, raw events, insights, agent runs, memory, and related types.

- `core-database`
  Handles persistence, encryption, DAOs, entities, mappers, and repositories. This is the data backbone of the app.

- `core-datastore`
  Stores lightweight app state such as onboarding progress and background-processing flags.

- `feature-ingestion`
  Captures external signals from the device, specifically bank SMS and bank app notifications.

- `feature-parser`
  Turns raw alert text into structured transaction data using bank-specific configs and parsing logic.

- `feature-agent`
  Contains the actual agent behavior: prompt/context construction, tool execution, guardrails, memory, and agent orchestration.

- `feature-llm`
  Provides the LLM abstraction layer, including the on-device backend and model download flow.

- `feature-ui`
  Holds the Compose screens and app-facing presentation logic.


## Tech Stack

- Kotlin
- Jetpack Compose
- Hilt
- WorkManager
- Room
- SQLCipher
- Kotlinx Serialization
- LiteRT-LM
- Android DataStore

## Notes

- `minSdk` is 31
- the project targets Android 12+ arm64 devices
- the current on-device model flow is built around Gemma 3 1B via LiteRT-LM
- the implementation plan in [`keel_implementation_plan.md`](./keel_implementation_plan.md) remains the main design reference for the broader roadmap

## Licence

AGPL-3.0-only. See [`LICENSE`](./LICENSE).
