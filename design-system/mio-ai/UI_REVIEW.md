# Mio AI — UI/UX Pro Max Review Audit

> Post-implementation review of the v2 interface using the installed skill
> (`.claude/skills/ui-ux-pro-max/`). Every finding below was fixed in the same
> pass unless marked Accepted.

## Method

Skill queries executed (all via `scripts/search.py`):

| # | Query | Domain | Used for |
|---|---|---|---|
| 1 | "mobile voice assistant loading feedback error states cancel action" | `ux` | Async feedback, error recovery |
| 2 | "dark mode contrast touch targets font scaling accessibility" | `ux` | Contrast, targets, scaling |
| 3 | "minimal premium dark elegant restrained" | `style` | Single-accent restraint, 200–250ms motion |
| 4 | "AI assistant mobile app interface" | `product` | **No match** — assistant patterns are original (`[ORIGINAL]`) |
| 5 | "button hierarchy disabled state primary secondary" | `ux` | Disabled-state clarity, back-stack behavior |
| 6 | "chat message readability list design" | `ux` | Body line-height 1.5–1.75 |

Static checks: `Color(0x…)` grep outside tokens · raw-`dp` census · WCAG contrast math (scripted)
· 48dp target audit · fixed-height text container scan · animation-OFF path read-through.

## Findings & fixes

| # | Severity | Finding (skill rule) | Location | Fix | Status |
|---|---|---|---|---|---|
| 1 | High | Violet `#8B5CF6` on surface = **4.47:1**, under the 4.5:1 text rule (`ux`) | `MioColors` | Violet → `#A78BFA` (**6.95:1**) | Fixed |
| 2 | High | Muted `#5D6B84` = 3.52:1 — too low even for captions (`ux`) | `MioColors` | Muted → `#7585A0`/`#7E8EA8` (**≥5:1**), still never sole carriers | Fixed |
| 3 | Medium | Body line-height 1.46–1.47 below skill 1.5–1.75 (`ux`) | `Type.kt` | body 15/23 (1.53), bodySmall 13/20 (1.54) | Fixed |
| 4 | Medium | Raw colors in theme scheme (`compose` token rule) | `Theme.kt` | `onPrimary`/`onError` → `mio.void` (8.9:1 on accent) | Fixed |
| 5 | Medium | Off-scale gaps/radii: 10/9/7/6/26dp literals, 24dp field radius | Buttons, QuickActions, Status, Timeline, PermissionCard, SettingRow, Home, Conversation | All mapped to `MioDimens` (`md/xs/sm/touchMin/radiusXl`); icons → 24/32 | Fixed |
| 6 | Medium | Replay button shrunk to 32dp target (`ux` touch rule) | `ChatBubble` | Full 48dp default target restored | Fixed |
| 7 | Medium | Success "pop" animated 1→1 (dead code, no feedback) | `ActionTimeline` | Real 0.55→1 appear-pop, motion-aware | Fixed |
| 8 | Medium | Home could clip on 640dp-tall phones (fixed 208dp orb) | `HomeScreen` | Height-aware orb: `min(58% w, h−500)`, 140–248dp | Fixed |
| 9 | Low | Sloppy trailing spacer; stale imports | Conversation, Settings, AssistantStatus, Theme, VM | Cleaned; `width`/`height` imports corrected | Fixed |
| 10 | Low | VM `runPlan` finally-block no-op | `AssistantViewModel` | Removed | Fixed |
| 11 | Info | Overlay permission requested by brief §Accessibility | Permissions | **Accepted (omitted):** Mio genuinely doesn't need draw-over; footer states this explicitly. Adding it would be dishonest scope. | Accepted |
| 12 | Info | `product` domain has no assistant patterns | Design system | Original patterns flagged `[ORIGINAL]` in DESIGN_SYSTEM.md | Accepted |

## Verified contrast pairs (final tokens)

| Pair | Ratio | Verdict |
|---|---|---|
| textPrimary `#F1F5F9` / surface `#0D1117` | 17.27:1 | ✅ body |
| textSecondary `#9AA7BD` / surface | 7.78:1 | ✅ body |
| textMuted `#7585A0` / surface | 5.06:1 | ✅ captions |
| accent `#22D3EE` / void `#05070A` | 11.16:1 | ✅ labels/graphics |
| violet `#A78BFA` / surface | 6.95:1 | ✅ thinking label |
| success `#34D399` / surface | 9.84:1 | ✅ |
| warning `#FBBF24` / surface | 11.34:1 | ✅ |
| danger `#F87171` / surface | 6.84:1 | ✅ |
| void `#05070A` on accent (button text) | ~11:1 | ✅ |

## Target / motion / hierarchy checklist

- [x] All interactive targets ≥ 48dp (M3 `minimumInteractiveComponentSize` + explicit heights; replay verified 48)
- [x] 8dp+ gaps between targets (quick grid 12dp, chips n/a, steppers 4→`xs` internal + 48dp buttons)
- [x] No fixed-height text containers except single-line ellipsized labels (quick grid, log rows)
- [x] Font scaling: sp everywhere; bubbles/timelines wrap; buttons keep 52dp rhythm at 1.3×
- [x] Animation OFF: static orb/waveform/dot, no enter motion, instant state colors (read-through verified)
- [x] Disabled states distinct: `surfaceHigh` + muted text (skill: "reduce opacity… don't confuse")
- [x] Back stack linear: Home → {Conversation, Actions, Settings → {Permissions, Licenses}}; Fix buttons deep-link with highlight
- [x] No bare spinners: `LoadingState(label)`; every error pairs message + recovery (`ErrorState`, Fix buttons, STOP)
- [x] Hierarchy: one display title per screen (24sp), section titles 17sp, single accent, brackets only on live/important panels
- [x] Decoration budget: zero emoji icons, no gradients on text, background = flat gradient + aura + vignette
