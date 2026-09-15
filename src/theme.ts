/**
 * Mio dark theme tokens. Kept minimal and stable so other files can import
 * individual constants instead of hard-coding colors.
 */

export const colors = {
  // Backgrounds
  bg: '#0B0D12',
  bgElevated: '#14171F',
  bgSheet: '#101319',
  bgInput: '#1A1E29',
  bgChip: '#1F2433',

  // Borders / dividers
  border: '#252A38',
  borderStrong: '#323849',

  // Text
  text: '#E8ECF5',
  textSecondary: '#A0A6B8',
  textMuted: '#6B7286',

  // Accent (Mio blue/cyan)
  accent: '#5EB1FF',
  accentSoft: '#1F2D44',

  // Status
  success: '#3DDC97',
  successSoft: '#133528',
  danger: '#FF6B6B',
  dangerSoft: '#3A1A1F',
  warning: '#FFC857',

  // Overlay
  overlay: 'rgba(0,0,0,0.6)',
};

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
};

export const radius = {
  sm: 6,
  md: 10,
  lg: 14,
  xl: 20,
};

export const typography = {
  h1: { fontSize: 22, fontWeight: '700' as const, color: colors.text },
  h2: { fontSize: 17, fontWeight: '600' as const, color: colors.text },
  body: { fontSize: 15, fontWeight: '400' as const, color: colors.text },
  small: { fontSize: 13, fontWeight: '400' as const, color: colors.textSecondary },
  tiny: { fontSize: 11, fontWeight: '500' as const, color: colors.textMuted },
};
