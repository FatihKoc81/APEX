# APEX Architecture

## Components
- Android client submits instructions and polls task status.
- TypeScript server stores tasks and events in SQLite.
- Worker orchestrates a primary OpenRouter model and a Gemini reviewer.
- GitHub tools allow approved file reads/writes and workflow dispatches.

## Provider configuration
- `OPENROUTER_API_KEY`: required primary gateway credential.
- `OPENROUTER_MODEL`: defaults to `openrouter/free`; never set this to a paid model if operating at a zero budget.
- `GEMINI_API_KEY`: existing Gemini API credential used for independent review.
- `GEMINI_MODEL`: defaults to `gemini-2.5-flash`.

The application must not silently fall back to paid models. Provider quota/rate-limit errors should be surfaced and the task paused for operator action.

## Current limitations
- Free-model routing, tool calling, and quotas depend on OpenRouter's current model availability.
- SQLite needs persistent storage on the deployment host.
- A successful server TypeScript build does not prove Android APK compilation or real-device behavior.
- Generated code changes should be reviewed before merging to `main`.
