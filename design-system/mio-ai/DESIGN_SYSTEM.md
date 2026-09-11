# Mio AI — Complete Design System (v2)

> Source of truth for all Mio AI UI. Derived from the installed **UI/UX Pro Max** skill
> (`style`: minimalism + ai-native-ui + hud restraint · `ux`: async feedback, error recovery,
> touch targets, 4.5:1 contrast · `stack`: jetpack-compose). `product` domain had **no database
> match** for "AI assistant mobile" — assistant-specific patterns below are original Mio decisions,
> flagged `[ORIGINAL]`.
>
> Direction shift from v1: less neon-HUD decoration, more restraint — near-black foundation,
> single electric-cyan accent, glow only on key interactive elements.

## 1. Design principles

1. **Voice first, text second.** The orb + mic dominate Home; typing is a quiet alternative.
2. **Always answer six questions:** what Mio heard · what it's thinking · what it's doing ·
   whether it worked · how to stop it · why a permission is needed.
3. **Restrained futurism.** One accent, subtle motion, no decoration without a job.
4. **Never a bare spinner.** Every wait has a label (`LoadingState` with context text).
5. **Every error has a way out.** `ErrorState` always pairs message + recovery action.

## 2. Color tokens (`MioColors`)

| Token | Midnight (default) | Abyss (deep) | Usage |
|---|---|---|---|
| `void` | `#05070A` | `#020304` | App background |
| `base` | `#080B10` | `#05070A` | Gradient end / grouped bg |
| `surface` | `#0D1117` | `#0A0D12` | Cards, sheets |
| `surfaceHigh` | `#121821` | `#10151D` | Raised / user bubbles |
| `line` | white @ 8% | white @ 7% | 1px borders, dividers |
| `accent` | `#22D3EE` cyan | `#22D3EE` | Primary interactive + LISTENING |
| `accentDeep` | `#0284C7` | `#0284C7` | Gradient end, pressed states |
| `violet` | `#A78BFA` | `#A78BFA` | **Sparingly:** THINKING only (6.9:1, was 4.47 — see UI_REVIEW) |
| `textPrimary` | `#F1F5F9` | `#F4F7FB` | Body (≥ 7:1 on surface) |
| `textSecondary` | `#9AA7BD` | `#A4B0C6` | Secondary (≥ 4.5:1) |
| `textMuted` | `#7585A0` | `#7E8EA8` | Captions (≥ 5:1; still never sole carriers — see UI_REVIEW) |
| `success` | `#34D399` | `#34D399` | Done, granted |
| `warning` | `#FBBF24` | `#FBBF24` | Confirmations, pending |
| `danger` | `#F87171` | `#F87171` | Errors, destructive, stop |

Status → color: IDLE `textMuted` · LISTENING `accent` · THINKING `violet` · SPEAKING `accent`
(at 80% + waveform) · EXECUTING `warning`-tinted cyan? **No** — EXECUTING uses `accent` + timeline
checkmarks in `success` (`[ORIGINAL]`: keeps one-accent discipline; violet reserved for thinking).

Accent intensity setting (0.3–1.0, default 0.85) scales glow/bracket alphas via `LocalMioFx`.

## 3. Typography (2 families max)

- **Sans** (`FontFamily.SansSerif`): UI + conversation. Technical feel via weight/tracking, not novelty.
- **Mono** (`FontFamily.Monospace`): status readouts, timestamps, step labels, captions-as-data.

| Style | Size / lh / weight / tracking | Use |
|---|---|---|
| `display` | 24 / 30 / Bold / +4sp | "MIO" wordmark, screen titles (never larger) |
| `title` | 17 / 24 / SemiBold / 0 | Section headers, card titles |
| `body` | 15 / 22 / Normal / 0 | Conversation, descriptions |
| `bodySmall` | 13 / 19 / Normal / 0 | Secondary text, step detail |
| `label` | 12 / 16 / Medium / +1sp | Buttons, chips (uppercase via code) |
| `caption` | 11 / 15 / Medium / +1.5sp Mono | Timestamps, status, micro-labels (min size) |

Body never below 13sp; captions never carry meaning alone (always adjacent to a ≥13sp label or icon).

## 4. Spacing / radius / elevation (`MioDimens`)

- Spacing scale: `xs 4 · sm 8 · md 12 · lg 16 · xl 24 · xxl 32` (dp).
- Screen gutter: 16dp (`lg`); section gap 24dp (`xl`); related-item gap 8dp (`sm`).
- Radius: `sm 8 · md 12 · lg 16 · xl 24 · full 50%`. Bubbles 18dp w/ 4dp tail corner.
- Elevation: flat-first. `level0` none (default) · `level1` 2dp (menus) · `level2` 8dp (dialogs).
  Depth comes from 1px `line` borders, not shadows.
- Touch targets: ≥ 48dp, ≥ 8dp gaps (skill `ux`).

## 5. Motion (`MioMotion`)

Durations: `fast 120ms` (press) · `normal 220ms` (appear, skill range 200–250) · `slow 350ms`
(orb state blend) · `pulse 1800ms` · `orbit 16000ms`.
Easings: `FastOutSlowIn` (enter), `Linear` (rotation), spring(soft) for mic press scale.

Levels (`Appearance → Animation`, + system animator-scale respected):
`FULL` everything · `REDUCED` no rotation/particles/pulse, fades kept · `OFF` static.
System animator duration scale = 0 forces OFF.

| Interaction | FULL | REDUCED/OFF |
|---|---|---|
| Orb state change | 350ms color blend + ring morph | instant color |
| Orb idle | slow orbit + breath pulse | static rings |
| Listening | amplitude-reactive core + energy ring | static accent ring |
| Press (buttons/mic) | 0.96 scale spring | none (keep ripple) |
| List enter | 220ms fade + 8dp rise | none |
| Step complete | checkmark pop 220ms | instant |
| Thinking | slow violet shimmer sweep | static violet |

## 6. Component inventory (all in `ui/components/`)

| Component | File | Contract |
|---|---|---|
| `MioOrb` | `Orb.kt` | `(status, amplitude01, motion, fx, onTap)` — 5 states, amplitude-reactive |
| `VoiceWaveform` | `VoiceWaveform.kt` | `(level, status, motion)` — 28 bars, idle shimmer |
| `AssistantStatus` | `AssistantStatus.kt` | `(status, detail?)` — dot + label pill, used top bar + under orb |
| `GlassSurface` | `GlassSurface.kt` | `(accent?: Accent, content)` — subtle surface + 1px line; brackets **only** when accent set |
| `PrimaryButton` | `Buttons.kt` | press-scale, full-width default, loading slot |
| `SecondaryButton` | `Buttons.kt` | outlined, press-scale |
| `ChatBubble` | `ChatBubble.kt` | `(text, isUser, time, onReplay?)` — replay affordance on Mio msgs |
| `ActionTimeline` | `ActionTimeline.kt` | `(steps, running)` — vertical rail, real-time states |
| `ActionCard` | `ActionCard.kt` | `(entry, onStop, onConfirm, onDismiss)` — "MIO IS WORKING" panel |
| `PermissionCard` | `PermissionCard.kt` | `(title, why, granted, ctaLabel, onCta, highlighted)` |
| `SettingRow` | `SettingRow.kt` | `(title, desc, trailing)` + `MioToggle`, `SegmentedOptions`, `StepperRow`, `MioTextField` |
| `SectionHeader` | `SectionHeader.kt` | 17sp semibold + optional action |
| `LoadingState` | `States.kt` | `(label)` — contextual wait, never bare |
| `ErrorState` | `States.kt` | `(message, actionLabel, onAction)` |
| `EmptyState` | `States.kt` | `(icon, title, hint)` |
| `QuickActionGrid` | `QuickActions.kt` | 5 large targets: Camera, Flashlight, Alarm, Apps, Settings |

## 7. Screen hierarchy

```
home (Assistant — primary, no bottom nav)
├── conversation (full history + replay + action cards)
├── actions (execution log: running now + past runs, stop/clear)
├── settings
│   ├── permissions (hero: accessibility why + enable; runtime rows)
│   └── licenses (open-source notices)
└── permissions (also reachable from contextual Fix buttons)
```

`[ORIGINAL]` navigation decision: **no bottom navigation** — a bottom tab bar would make Mio feel
like a social app. Home owns the voice experience; Conversation / Actions are one tap away in the
top bar (icons with disciplined 48dp targets); Settings via gear. Back stack is linear and shallow.

## 8. Screen specs

### Home
Top: `MIO` wordmark + `AssistantStatus` pill (dot + Ready/Listening…/cloud-or-offline) + actions icon
+ conversation icon + settings gear. Center: `MioOrb` (state-reactive) → status line ("Ready",
"Heard: …", step ticker) → `VoiceWaveform` compact. Below: `QuickActionGrid` (5). Bottom: text
field (quiet rooms) + 72dp mic FAB. Generous whitespace; nothing competes with the orb.

### Conversation
App bar (back + "Conversation" + clear). `LazyColumn`: date-agnostic time captions, `ChatBubble`
(user right / Mio left + replay), `ActionCard` inline for plans (collapsed steps when done, live
when running). Composer at bottom mirrors Home input. Empty → `EmptyState` ("Say hello…").

### Actions
App bar (back + "Activity" + clear-all). Live card pinned while executing (with STOP). Past runs:
summary + time + ✓/✗ + step count, expandable to `ActionTimeline`. Empty → `EmptyState`.

### Permissions
Hero `GlassSurface(accent)`: "Why Mio needs access" + accessibility explainer + [Enable
Accessibility]. Then `PermissionCard` rows (Mic, Notifications, Camera, Bluetooth, Contacts, System
settings, DND). Footer: honest notes (no overlay need, Wi-Fi/call OS limits).

### Settings (sections per brief)
AI: provider (fixed "OpenAI-compatible" + base URL) · model · API key (encrypted) · response style
(Concise/Balanced/Detailed) · conversation memory (toggle + depth 5/10/20).
Voice: voice (device TTS list) · speech speed · wake word · listening mode (Tap / Continuous).
Automation: accessibility status+open · confirmations · action timeout (5–30s).
Appearance: theme (Midnight/Abyss) · accent intensity · animation (Full/Reduced/Off).
Privacy: keep history toggle · local data (sizes) · clear history.
About: version · developer · licenses.

## 9. Interaction states (voice loop)

`IDLE → LISTENING (partial transcript live) → THINKING ("Understanding…") → SPEAKING/EXECUTING`
Executing adds per-step `pending → running → done/fail`, cancellable (STOP button + "stop").
Confirmation is a blocking inline state (Confirm/Cancel buttons + yes/no voice).
Mic during EXECUTING opens a listen pass; "stop" cancels, anything else defers politely.

## 10. Responsive & a11y

- Portrait phones 360–460dp wide; orb scales (200–248dp); grid 5 → wraps on <360dp.
- `statusBarsPadding`/`navigationBarsPadding` everywhere; IME-aware composers (`adjustResize`).
- Font scaling: sp everywhere; no fixed-height text containers; 48dp targets preserved.
- TalkBack: content descriptions on orb/mic/steps; state changes announced via status text.
- Contrast: body ≥ 4.5:1, captions ≥ 5:1 (verified pairs in UI_REVIEW.md); captions still never sole carriers.
