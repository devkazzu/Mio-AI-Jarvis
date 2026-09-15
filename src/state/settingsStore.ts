/**
 * SettingsStore
 *
 * Lightweight observable store for AI settings. We deliberately avoid adding
 * Redux / Zustand etc. for an MVP — a small pub-sub is enough and keeps the
 * dependency surface minimal.
 *
 * State:
 *   - baseUrl
 *   - hasApiKey (boolean only — actual key stays in SecureStore)
 *   - selectedModel
 *   - selectedModelIsCustom
 *   - models (last fetched)
 *   - modelsFetchedAt
 *   - modelsFromCache (true if shown from cache)
 *
 * UI components subscribe via `subscribe(listener)` and read via `getState()`.
 */

import { ModelInfo } from '../types/models';

export interface SettingsState {
  baseUrl: string;
  hasApiKey: boolean;
  selectedModel: string;
  selectedModelIsCustom: boolean;
  models: ModelInfo[];
  modelsFetchedAt: number | null;
  modelsFromCache: boolean;
  /** Transient status used to render banners. */
  status: SettingsStatus;
}

export type SettingsStatus =
  | { kind: 'idle' }
  | { kind: 'loading'; message: string }
  | { kind: 'success'; message: string }
  | { kind: 'error'; message: string; code?: string };

export type Listener = (state: SettingsState) => void;

const initialState: SettingsState = {
  baseUrl: 'https://api.b.ai/v1',
  hasApiKey: false,
  selectedModel: '',
  selectedModelIsCustom: false,
  models: [],
  modelsFetchedAt: null,
  modelsFromCache: false,
  status: { kind: 'idle' },
};

class SettingsStore {
  private state: SettingsState = initialState;
  private listeners = new Set<Listener>();

  getState(): SettingsState {
    return this.state;
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  setState(patch: Partial<SettingsState>): void {
    this.state = { ...this.state, ...patch };
    for (const l of this.listeners) l(this.state);
  }

  setStatus(status: SettingsStatus): void {
    this.setState({ status });
  }
}

export const settingsStore = new SettingsStore();
