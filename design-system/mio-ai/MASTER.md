# Mio AI — Design System MASTER

> Generated with **UI/UX Pro Max** (`ui-ux-pro-max` skill, installed at `.claude/skills/ui-ux-pro-max/`).
> Skill queries used: `style: futuristic dark AI assistant voice interface` → **ai-native-ui, zero-interface, cyberpunk-ui, hud-sci-fi-fui** ·
> `color: dark futuristic AI assistant cyan` → **AI/Chatbot Platform** · `typography: futuristic technical monospace display` →
> **Kinetic Motion (Syncopate + Space Mono)** · `stack: jetpack-compose` · `ux: voice interface touch feedback dark mode loading`.
> Source of truth for all Mio AI UI work. Page overrides (if any) live in `design-system/mio-ai/pages/`.

## 1. Design Direction

**JARVIS-style HUD + AI-native conversational UI.** Dark-first (dark mode only by design), voice-first with minimal
visible chrome, progressive disclosure of controls. Thin 1px technical lines, corner brackets, subtle scanline/grid
texture, neon-on-black glow used sparingly for status and the central orb.

- **Styles (skill IDs):** `hud-sci-fi-fui` (primary), `ai-native-ui` (chat/conversation patterns),
  `zero-interface` (voice-first, hide controls until needed), `cyberpunk-ui` (neon accents, restrained — no glitch).
- **Never:** light mode, emoji-as-icons, fixed px container widths, hover-only affordances, one animation duration
  for everything, animating width/height, missing loading feedback.

## 2. Color Tokens (Compose `MioColors` — theme-based, never fixed colors in components)

| Token | Value | Usage |
|---|---|---|
| `void` (background) | `#05070D` | App background |
| `abyss` | `#0A0F1A` | Surface / cards base |
| `glass` | `#0E1626` @ 72% | Glass panels, chat bubbles (AI) |
| `hudLine` | `#00E5FF` @ 28% | 1px borders, brackets, dividers |
| `primary` (signal cyan) | `#00E5FF` | Primary actions, listening state, links |
| `secondary` (AI violet) | `#7C3AED` | AI accent, thinking state, gradient end |
| `userBubble` | `#13233B` | User chat bubble |
| `textPrimary` | `#E8F1FF` | Body text (≥ 4.5:1 on `void`) |
| `textMuted` | `#8B98B8` | Secondary text, timestamps |
| `success` | `#34D399` | Done states, connected, permission granted |
| `warning` | `#FBBF24` | Confirmations, pending |
| `danger` | `#F87171` | Errors, destructive, permission denied |
| `speaking` | `#22D3EE` | Speaking state |
| `executing` | `#A78BFA` | Executing state |

Status → color mapping: `IDLE=textMuted, LISTENING=primary, THINKING=secondary, SPEAKING=speaking, EXECUTING=executing, ERROR=danger`.
Gradients: orb core `primary → secondary`; background radial vignette `secondary @ 8% → transparent`.

## 3. Typography (skill pairing: Kinetic Motion)

- **Display:** Syncopate (wide futuristic) for the "MIO" wordmark / hero numerals. Android: system fallback stack —
  implemented as `FontFamily.SansSerif` + `letterSpacing(0.35em)` + `Uppercase`, with a documented hook for the
  downloadable Google Font (`Syncopate`) in `Type.kt`.
- **Body/mono:** Space Mono mood for status readouts, timestamps, action cards — implemented as
  `FontFamily.Monospace` + `letterSpacing(0.12em)` uppercase micro-labels (10–11sp).
- **Chat body:** system sans 15sp, line-height 1.5. Base sizes ≥ 12sp for body, micro-labels never carry meaning alone.
- Rules: sentence case in conversation; UPPERCASE + tracking only for HUD labels/status.

## 4. Layout & Components

- **Home (HUD):** status readout top → central orb (Canvas, ~240dp) → waveform → live transcript line → action-ticker
  card (current step) → quick-action chips (horizontal scroll) → chat/history bottom sheet panel.
- **Touch targets:** ≥ 48dp; chips 48dp tall with 8dp gaps. Haptic tick on mic press and confirmations only.
- **Cards:** 16dp radius, 1dp `hudLine` border, glass fill; corner-bracket accents on hero panels.
- **Chat:** AI left / glass, user right / `userBubble`; action cards embed per-step checklist (pending → running →
  done/error) so "what Mio is doing" is always visible.
- **Feedback:** every async op shows state (skeleton shimmer for history load, spinner + step text for execution);
  errors name the missing permission with a deep-link button. No frozen UI, ever.
- **Motion:** orb rotation 12s linear (disabled when Reduce Motion is on), pulse 1.6s ease-in-out, message enter
  220ms fade+rise 8dp, waveform bars driven by live mic RMS. `prefers-reduced-motion` ≅ in-app Reduce Motion setting.

## 5. Jetpack Compose Stack Rules (skill `jetpack-compose`)

- Theme via `MaterialTheme.colorScheme` extended by `MioColors` composition local — no raw hex in components.
- UI state as sealed interface (`AssistantUiState` / `AssistantStatus`), never boolean-flag hell.
- Public UI contracts per screen (Route + State + Events); keep implementation details internal.

## 6. Pre-Delivery Checklist

- [ ] No emojis as icons (Material symbols only)
- [ ] Text contrast ≥ 4.5:1 on dark surfaces
- [ ] 48dp touch targets, 8dp+ spacing
- [ ] Loading/empty/error states for every async surface
- [ ] Reduce-motion path verified (orb static, no pulse)
- [ ] Tested on 360×640 (small phone) and 412×915 (large phone)
