# Mio AI Jarvis

A professional AI chat client built with **Expo + React Native + TypeScript**. The app connects to any OpenAI-compatible provider (e.g. `b.ai`, OpenAI, vLLM, Ollama) and ships with a production-ready model selector that dynamically fetches models from the provider's `/models` endpoint.

> **Note:** this repository started empty; the full app was scaffolded as a single, self-contained Expo project so the requested features could be implemented from scratch.

## Features

- 🔐 **Encrypted API key storage** via `expo-secure-store` (Keychain / EncryptedSharedPreferences / IndexedDB-encrypted on web)
- 🌐 **Configurable Base URL** for any OpenAI-compatible endpoint
- 🔄 **Dynamic model fetching** from `GET {BASE_URL}/models`
- 🔎 **Polished model picker** with search, capability metadata, refresh, and a custom-model fallback
- 🧪 **42 unit tests** covering parsing, error handling, caching, persistence, and offline behaviour
- 🎨 **Dark theme** matching Mio's visual language
- 💬 **Minimal chat screen** that uses the selected model
- 📱 **Runs on iOS, Android, and Web** (Expo)

## Quick start

```bash
npm install
npm run web        # opens the app in your browser (or)
npm run android    # runs on Android (requires Android SDK)
npm run ios        # runs on iOS (requires macOS + Xcode)
```

### Scripts

| Script              | Purpose                                                       |
| ------------------- | ------------------------------------------------------------- |
| `npm start`         | Start the Expo dev server                                     |
| `npm run web`       | Start the web build in dev mode                               |
| `npm run typecheck` | Run TypeScript strict typecheck                                |
| `npm test`          | Run the Jest test suite (42 tests)                            |
| `npm run build:web` | Produce a production web bundle in `dist/`                    |

## Architecture

```
src/
├── components/
│   ├── ModelPicker.tsx          # Compact row + refresh icon
│   └── ModelPickerSheet.tsx     # Bottom sheet with search + list
├── screens/
│   ├── ChatScreen.tsx           # AI chat surface
│   └── SettingsScreen.tsx       # AI settings + model selector
├── services/                    # Pure logic — no UI imports
│   ├── HttpClient.ts            # fetch wrapper, timeouts, error normalization
│   ├── ModelService.ts          # fetchModels / testConnection / normalize / cache
│   ├── ChatService.ts           # POST /chat/completions
│   └── SecureStore.ts           # Encrypted persistence wrapper
├── state/
│   ├── settingsStore.ts         # Tiny observable settings store
│   └── useSettings.ts           # React hook for the store
├── theme.ts                     # Color / spacing / typography tokens
└── types/
    └── models.ts                # Type-safe interfaces for /models responses
```

The **ModelService** is the heart of the app:

| Method             | Purpose                                                   |
| ------------------ | --------------------------------------------------------- |
| `fetchModels()`    | Fetch `/models`, normalize, classify errors, cache        |
| `testConnection()` | Call `fetchModels` and return `{ ok, modelCount, error }` |
| `getCachedModels()`| Read the last successful list for the configured URL      |
| `clearCache()`     | Drop a cached list                                         |
| `normalizeModels()`| Pure helper: parse `{ data: [...] }` / `{ models: [...] }`/ array / single object |

## Provider compatibility

The implementation is intentionally generic:

- ✅ `{ "data": [{ "id": "..." }, ...] }` — OpenAI standard
- ✅ `{ "models": [...] }` — alternative providers
- ✅ `[...]` — top-level arrays
- ✅ `{ "id": "..." }` — single object
- ✅ Capability metadata: `modalities`, `input_modalities`, `output_modalities`, `supports_vision`, `supports_tools`, `supports_reasoning`, `capabilities`
- ✅ Context length: `context_length`, `max_context_length`, `context_window`
- ✅ Custom-model fallback when `/models` is unavailable

## Security

- The API key is **never** logged, never displayed in plain text, and never sent anywhere except the configured `BASE_URL`.
- The `Authorization: Bearer ...` header is added only by `ModelService`/`ChatService` and never reaches the UI layer.
- Error messages scrub the API key (verified by tests).
- URLs are validated to `http(s)` only.

## Testing

```bash
npm test
```

Covers successful fetches, empty lists, 401/403/404 errors, malformed responses, network failures, model persistence, cache hits, selected-model restoration, custom-model fallback, API-key redaction, and HTTP client behaviour.
