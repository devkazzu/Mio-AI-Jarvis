import { useEffect, useState } from 'react';
import { SettingsState, settingsStore } from './settingsStore';

/**
 * Subscribe a React component to the settings store.
 *
 * We intentionally avoid pulling in a third-party state library for this MVP.
 * This hook re-renders only when `useSyncExternalStore` would — but using
 * `useState` here keeps it portable across React versions.
 */
export function useSettings(): SettingsState {
  const [state, setState] = useState<SettingsState>(settingsStore.getState());
  useEffect(() => settingsStore.subscribe(setState), []);
  return state;
}
