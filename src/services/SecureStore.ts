/**
 * SecureStore wrapper.
 *
 * Centralizes all reads/writes for app secrets and configuration.
 *
 * Why a wrapper?
 * - Keeps `expo-secure-store` out of UI components.
 * - Provides a single seam to mock for tests.
 * - Documents the *secure* vs *plain* boundary.
 *
 * NOTE on storage choice:
 *   `expo-secure-store` uses Keychain on iOS, EncryptedSharedPreferences on
 *   Android, and IndexedDB-backed encryption on web. The API key is stored
 *   exclusively via `setItemAsync` so it remains encrypted at rest.
 */

import * as SecureStore from 'expo-secure-store';

const KEYS = {
  apiKey: 'mio.apiKey',
  baseUrl: 'mio.baseUrl',
  selectedModel: 'mio.selectedModel',
  selectedModelIsCustom: 'mio.selectedModelIsCustom',
} as const;

export type SecureStoreAPI = {
  getApiKey(): Promise<string | null>;
  setApiKey(value: string): Promise<void>;
  clearApiKey(): Promise<void>;

  getBaseUrl(): Promise<string | null>;
  setBaseUrl(value: string): Promise<void>;

  getSelectedModel(): Promise<string | null>;
  setSelectedModel(value: string, isCustom: boolean): Promise<void>;
  clearSelectedModel(): Promise<void>;
};

class SecureStoreImpl implements SecureStoreAPI {
  async getApiKey(): Promise<string | null> {
    try {
      return await SecureStore.getItemAsync(KEYS.apiKey);
    } catch {
      return null;
    }
  }
  async setApiKey(value: string): Promise<void> {
    await SecureStore.setItemAsync(KEYS.apiKey, value, {
      keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
    });
  }
  async clearApiKey(): Promise<void> {
    await SecureStore.deleteItemAsync(KEYS.apiKey);
  }

  async getBaseUrl(): Promise<string | null> {
    return await SecureStore.getItemAsync(KEYS.baseUrl);
  }
  async setBaseUrl(value: string): Promise<void> {
    await SecureStore.setItemAsync(KEYS.baseUrl, value);
  }

  async getSelectedModel(): Promise<string | null> {
    return await SecureStore.getItemAsync(KEYS.selectedModel);
  }
  async setSelectedModel(value: string, isCustom: boolean): Promise<void> {
    await SecureStore.setItemAsync(KEYS.selectedModel, value);
    await SecureStore.setItemAsync(KEYS.selectedModelIsCustom, isCustom ? '1' : '0');
  }
  async clearSelectedModel(): Promise<void> {
    await SecureStore.deleteItemAsync(KEYS.selectedModel);
    await SecureStore.deleteItemAsync(KEYS.selectedModelIsCustom);
  }
}

export const secureStore: SecureStoreAPI = new SecureStoreImpl();

/**
 * Exposed for tests so they can swap in an in-memory implementation
 * without depending on the native module.
 */
export const __testing = { SecureStoreImpl };
