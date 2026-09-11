# Mio AI — Design System MASTER (v2)

> Generated with **UI/UX Pro Max** (`ui-ux-pro-max` skill at `.claude/skills/ui-ux-pro-max/`).
> v2 restrains v1's neon-HUD: near-black foundation, **single** electric-cyan accent, glow only on
> key interactive elements, minimal premium quietness (skill: `minimalism-and-swiss-style` —
> "single primary only", subtle 200–250ms transitions).
> Full spec: [`DESIGN_SYSTEM.md`](DESIGN_SYSTEM.md) · audit: [`UI_REVIEW.md`](UI_REVIEW.md).

## 1. Direction

**Restrained futuristic minimalism.** Mio feels like a real next-generation personal AI: cinematic
and technical, calm and confident. Voice-first with minimal chrome; quiet surfaces; motion with
meaning. Never a ChatGPT clone, never a gaming HUD.

## 2. Color tokens

| Token | Midnight | Abyss | Use |
|---|---|---|---|
| `void` / `base` | `#05070A` / `#080B10` | `#020304` / `#05070A` | Backgrounds |
| `surface` / `surfaceHigh` | `#0D1117` / `#121821` | `#0A0D12` / `#10151D` | Surfaces, user bubbles |
| `line` | white 8% | white 7% | 1px borders |
| `accent` | `#22D3EE` | `#22D3EE` | The one accent: interactive + listening/speaking |
| `violet` | `#A78BFA` (rare) | same | Thinking state only (6.9:1) |
| `textPrimary` / `textSecondary` / `textMuted` | `#F1F5F9` / `#9AA7BD` / `#7585A0` | brighter set | ≥7:1 / ≥4.5:1 / ≥5:1 |
| `success` / `warning` / `danger` | `#34D399` / `#FBBF24` / `#F87171` | same | Status only |

Status colors: IDLE muted · LISTENING cyan · THINKING violet · SPEAKING cyan · EXECUTING cyan +
success checks. Accent-intensity setting scales glow (never hue).

## 3. Typography (2 families)

Sans for UI/conversation (technical via weight + tracking); Mono for status/timestamps/steps.
`display 24 Bold` · `title 17 SemiBold` · `body 15` · `bodySmall 13` · `label 12 Medium +1`
· `caption 11 Mono +1.5` (minimum; never sole carrier).

## 4. Layout & components

Home = orb + status + waveform + 5 quick actions + mic — spacious, nothing competes.
Conversation / Actions / Settings one tap from the top bar; **no bottom nav** (would feel social).
Components: MioOrb · VoiceWaveform · AssistantStatus · GlassSurface · Primary/SecondaryButton ·
ChatBubble · ActionTimeline · ActionCard · PermissionCard · SettingRow/Toggle · SectionHeader ·
Loading/Error/EmptyState. Spacing 4/8/12/16/24/32 · radius 8/12/16/24/full · flat-first, 48dp
targets, 8dp gaps · motion 120/220/350ms, Full/Reduced/Off + system animator-scale respected.

## 5. Stack rules (skill `jetpack-compose`)

`MioColors`/`MioDimens`/`MioMotion` composition locals — no raw values in components. Sealed
`AssistantStatus`; public Route+State+Events contracts per screen. UI logic in components/screens,
business logic in core/system/VM.

## 6. Pre-delivery checklist

- [ ] Material symbols only, no emoji icons
- [ ] Body contrast ≥ 4.5:1 (see UI_REVIEW.md pairs)
- [ ] 48dp targets, 8dp+ gaps, portrait 360–460dp verified
- [ ] Loading → progress → success/error on every async surface; errors carry recovery
- [ ] Animation OFF path verified (static orb/waveform, no enter motion)
- [ ] TalkBack labels on orb, mic, steps, toggles
