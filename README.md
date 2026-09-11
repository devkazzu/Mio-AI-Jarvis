# Mio AI — JARVIS-style Android Voice Assistant

A **real, buildable, runnable** Android app (Kotlin + Jetpack Compose) that understands natural voice
commands, talks back, and operates the phone: apps, flashlight, Wi-Fi, Bluetooth, volume, alarms,
calls, UI automation inside other apps, and multi-step sequences like
*“Open Instagram, search for cats, and open the first result.”*

Built with the [`ui-ux-pro-max`](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill) skill —
installed in this repo at [`.claude/skills/ui-ux-pro-max/`](.claude/skills/ui-ux-pro-max/) (mirrored to
`.agent/skills/ui-ux-pro-max/`). The persisted design system is
[`design-system/mio-ai/MASTER.md`](design-system/mio-ai/MASTER.md).

---

## 1. What Mio can do

| Say… | Mio does |
|---|---|
| “Turn on the flashlight” | Torch on/off (needs Camera perm) |
| “Turn on Wi-Fi / Bluetooth” | Toggles; on Android 10+ opens the system panel (OS restriction — Mio tells you) |
| “Volume up / mute / set volume to 40” | Media volume control |
| “Turn on Do Not Disturb” | DND on/off (one-time access grant) |
| “Brightness to 70 / brighter” | Screen brightness (write-settings grant) |
| “Set an alarm for 7 AM”, “timer 5 minutes” | Clock intents (no permission needed) |
| “Open YouTube / Spotify / …” | Fuzzy app launch (150+ curated apps + full launcher search) |
| “Call mom / text mom saying I'll be late” | Opens dialer / SMS pre-filled — **you** tap send (Android rule) |
| “Search for cats on Instagram” | Opens the app, taps Search, types, submits |
| “Open Instagram, search for cats, open the first result” | Full multi-step UI automation (needs UI Control) |
| “Tap Search / scroll down / type hello / go back” | Accessibility UI primitives |
| “Navigate to Mumbai airport” | Google Maps navigation |
| “What time is it / battery / device status” | Instant readouts |
| “Call me Neo” | Mio remembers your name |
| Anything else | Cloud LLM (if configured) or the offline personality brain |

Every action narrates itself: the HUD shows **Listening / Thinking / Speaking / Executing**, a live
action ticker, and per-step checklists. Failures name the exact missing permission with a **Fix** button.

---

## 2. Setup

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer, with JDK 17 (bundled).
- Android SDK **API 34**, min supported device/emulator **API 26** (Android 8.0).
- A physical device is strongly recommended (emulators lack a real mic/speech engine).

### Build & install

```bash
git clone https://github.com/devkazzu/Mio-AI-Jarvis.git
cd Mio-AI-Jarvis
cp local.properties.example local.properties   # then edit (SDK path is auto-added by Studio)
./gradlew :app:assembleDebug
./gradlew :app:installDebug                     # device connected, USB debugging on
```

Or just **Open the folder in Android Studio** → let Gradle sync → **Run ▶**.

### Run the unit tests (no device needed)

```bash
./gradlew :app:testDebugUnitTest
```

Covers the command parser (30+ utterances), LLM plan validation, JSON codec and app catalog.

---

## 3. AI brain configuration (keys stay out of source)

Mio works **fully offline** out of the box (rule parser + personality brain). For open-ended
conversation and LLM-planned actions, point it at any OpenAI-compatible endpoint:

**Defaults** (non-secret, `local.properties` → build config, git-ignored):

```properties
MIO_AI_BASE_URL=https://api.openai.com/v1
MIO_AI_MODEL=gpt-4o-mini
```

**API key — in the app only** (Settings → AI): your key is kept in encrypted
on-device storage (`EncryptedSharedPreferences`) and is never baked into the APK.
In-app URL/model values override the build defaults. Only a masked hint
(`sk-••••1234`) is ever displayed.

**Free local LLM** (no key at all): run Ollama on your computer, then in the *emulator*
set base URL `http://10.0.2.2:11434/v1`, model `llama3.1` (or any pulled model), empty key.
(Cleartext is allow-listed for emulator loopback only — see `network_security_config.xml`.)

Home and Settings always show the same brain state: `OFFLINE BRAIN`, `CLOUD BRAIN`,
`CLOUD SETUP REQUIRED`, or `CLOUD BRAIN ERROR`.

---

## 4. Permissions (each asked in context, all explained in-app)

| Permission | Where | Why |
|---|---|---|
| Microphone | Runtime | Voice commands |
| Camera | Runtime | Flashlight torch only (never photos) |
| Bluetooth Connect | Runtime (API 31+) | BT toggles |
| Contacts | Runtime | “Call mom” name → number |
| Notifications | Runtime (API 33+) | Wake-word service status |
| Modify system settings | System Settings | Brightness |
| Do Not Disturb access | System Settings | Silence by voice |
| **UI Control (Accessibility)** | System Settings | Tap/scroll/type in other apps — only on your command |
| Internet | Install-time | Cloud brain only |

Open **Permissions** (lock icon) any time for live status + one-tap fixes. Failed actions deep-link
there automatically (`TAP TO FIX →`).

### Enabling UI Control (for “tap / search / first result” commands)

1. Permissions → **UI Control** → **OPEN** (or system Settings → Accessibility).
2. Enable **Mio AI UI Control** → allow.
3. The HUD header dot turns green: `UI CONTROL ON`.

---

## 5. Testing checklist

1. **Voice loop**: tap mic → “what time is it” → Mio speaks the time, chat shows the exchange.
2. **Device**: “turn on the flashlight” → torch lights; “mute” → volume mutes.
3. **Apps**: “open YouTube” → launches; “go home” → returns.
4. **Confirmation**: “call mom” (or any number) → Mio asks first; say “yes” → dialer opens pre-filled.
5. **Multi-step** (enable UI Control first): “open YouTube, search for lo-fi beats” → timeline
   streams; tap **Stop** mid-run → card marks remaining steps cancelled.
6. **Failure path**: revoke Camera → “flashlight on” → Mio explains + offers Fix.
7. **Offline**: airplane mode, no API key → commands + “tell me a joke” still work.
8. **Cloud** (optional): set key → ask anything open-ended → contextual reply; follow-ups use history.
9. **Conversation/Activity**: open Chat → replay a Mio message; open Activity → expand a past run.
10. **Appearance**: Settings → try Abyss theme, accent intensity, and Animation Off (orb/wave go static).
11. **Voice picker + continuous mode**: Settings → Voice → pick a device voice; Listening mode →
    Continuous → replies auto re-listen (says “stop” handling included).
12. **Wake word** (optional): Settings → enable → background the app → say “Hey Mio”.
13. **Unit tests**: `./gradlew :app:testDebugUnitTest` → all green.

---

## 6. Project structure

```
app/src/main/
├── AndroidManifest.xml                    # permissions, queries, services
├── res/xml/{accessibility_service_config,network_security_config}.xml
├── java/com/mio/ai/
│   ├── MainActivity.kt                    # single-activity host + wake intent
│   ├── MioApplication.kt                  # composition root (settings/keys/AI/engine)
│   ├── core/
│   │   ├── actions/Action.kt              # structured action model + Plan/StepOutcome
│   │   ├── commands/CommandParser.kt      # offline NL → Plan (pure Kotlin)
│   │   ├── commands/CommandRouter.kt      # rules → LLM planner + safety policy
│   │   ├── commands/AppCatalog.kt         # 150+ apps for voice launch (pure)
│   │   ├── ai/AiClient.kt + OpenAiCompatibleClient.kt
│   │   ├── ai/LlmPlanner.kt               # strict JSON planner, fail-closed validation
│   │   ├── ai/LocalBrain.kt               # offline personality (pure)
│   │   ├── ai/Conversation.kt             # persisted conversation memory (pure)
│   │   ├── engine/ActionEngine.kt         # executes plans, streams progress
│   │   └── util/MiniJson.kt               # dependency-free JSON (pure)
│   ├── system/SystemActions.kt            # torch/wifi/bt/volume/dnd/brightness/alarms/calls…
│   ├── system/AppLauncher.kt              # fuzzy launch + close-current-app
│   ├── system/ContactsResolver.kt         # contact name → number
│   ├── accessibility/MioAccessibilityService.kt + AccessibilityController.kt
│   ├── voice/SpeechListener.kt            # SpeechRecognizer + mic level + partials
│   ├── voice/MioTts.kt                    # TTS personality, chunking, callbacks
│   ├── voice/WakeWordService.kt           # optional “Hey Mio” foreground service
│   ├── data/SettingsRepository.kt         # DataStore settings
│   ├── data/SecureKeyStore.kt             # encrypted API-key override
│   └── ui/
│       ├── theme/                         # MioColors/Type/Theme (per MASTER.md)
│       ├── components/                    # Orb, Waveform, ChatList, QuickActions, HUD panels
│       ├── screens/                       # Home, Permissions, Settings
│       ├── navigation/MioNav.kt
│       └── vm/AssistantViewModel.kt        # listen→think→speak→execute orchestrator
app/src/test/…                              # JVM unit tests (parser, planner, JSON, catalog)
design-system/mio-ai/MASTER.md              # persisted UI/UX Pro Max design system
.claude/skills/ui-ux-pro-max/               # installed skill (searchable design intelligence)
```

**Skill usage:** `python3 .claude/skills/ui-ux-pro-max/scripts/search.py "<query>" --domain <style|color|typography|ux|…> [--stack jetpack-compose] [--design-system]`

---

## 7. Honest limitations (by Android design)

- **Calls/texts**: Mio opens the dialer/composer pre-filled — only the user (or the default
  dialer/SMS app) can place/send. This is stated in the confirmation prompt.
- **Wi-Fi (Android 10+)**: silent toggles are system-only; Mio opens the Wi-Fi panel instead.
- **Bluetooth off (newer Android)**: may be refused for third-party apps; Mio falls back gracefully.
- **UI automation**: depends on the target app's view tree; heavily canvas-rendered screens may not
  expose tappable text — Mio reports exactly which step failed.
- **Wake word**: pattern-matching over the speech engine (no dedicated hotword chip) — works, but
  uses more battery; off by default.

---

## License

MIT — see [LICENSE](LICENSE) (add one if distributing).
