# APEX

Android + backend starter for a multi-provider AI orchestration system.

## Architecture
- `android/`: Kotlin + Jetpack Compose Android client.
- `server/`: Node.js/TypeScript API and durable orchestration worker.
- `docs/`: architecture and provider setup notes.

## Providers
- OpenRouter: primary model gateway. Default model is `openrouter/free`; set `OPENROUTER_MODEL` to another explicitly free model only if desired.
- Google Gemini: reviewer/second opinion using the existing `GEMINI_API_KEY`.

APEX does not require an OpenAI API key. Do not configure paid model IDs or enable provider billing. Free model availability, rate limits, and tool-call support are controlled by the provider and may change; when a free route is unavailable, the task should be paused rather than silently switching to a paid model.

## Secrets
Set `OPENROUTER_API_KEY` and the existing `GEMINI_API_KEY` in the backend host's secret/environment settings. Never put provider API keys in the Android app or commit them to GitHub.

Optional:
- `OPENROUTER_MODEL` (default: `openrouter/free`)
- `GEMINI_MODEL` (default: `gemini-2.5-flash`)
- `APP_URL`

## Run server
```bash
cd server
cp .env.example .env
npm install
npm run dev
```

## Android
Open `android/` in Android Studio, set `APEX_API_BASE_URL` in `android/local.properties`, then build and run on a device. Android Studio or a compatible CI runner is required to build/sign APK.

## Safety / limitations
This is a development starter, not a production-hardened autonomous agent. Review generated changes before merging them into `main`. Free quotas are limited. The current SQLite worker requires persistent storage for reliable hosted operation.
