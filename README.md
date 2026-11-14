# NEXORA

Standalone Android + backend starter for a four-provider AI orchestration system.

## Architecture
- `android/`: Kotlin + Jetpack Compose Android client.
- `server/`: Node.js/TypeScript API and durable orchestration worker.
- `docs/`: architecture and provider setup notes.

## Important
This is a development starter, not a production APK. Provider calls require your own API keys.
The worker persists tasks and checkpoints in SQLite in this starter. For production multi-instance
deployment, use PostgreSQL + a queue (e.g. Redis/BullMQ) and encrypted secret storage.
Do not put provider API keys in the Android app.

## Providers
- OpenAI (ChatGPT models): `OPENAI_API_KEY`
- Google Gemini: `GEMINI_API_KEY`
- xAI Grok: `XAI_API_KEY`
- Anthropic Claude: `ANTHROPIC_API_KEY`

## Run server
```bash
cd server
cp .env.example .env
npm install
npm run dev
```

## Android
Open `android/` in Android Studio, set `NEXORA_API_BASE_URL` in `android/local.properties`
or edit the BuildConfig field, then run on a device. Android Studio is required to build/sign APK.
