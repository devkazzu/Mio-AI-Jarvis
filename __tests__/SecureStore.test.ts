import * as SecureStore from 'expo-secure-store';
import { secureStore } from '../src/services/SecureStore';

describe('SecureStore wrapper', () => {
  beforeEach(() => {
    (SecureStore as any).__reset?.();
  });

  it('stores and retrieves an API key', async () => {
    await secureStore.setApiKey('sk-test-1234567890');
    expect(await secureStore.getApiKey()).toBe('sk-test-1234567890');
  });

  it('clears an API key', async () => {
    await secureStore.setApiKey('sk-test');
    await secureStore.clearApiKey();
    expect(await secureStore.getApiKey()).toBeNull();
  });

  it('stores and retrieves a Base URL', async () => {
    await secureStore.setBaseUrl('https://api.b.ai/v1');
    expect(await secureStore.getBaseUrl()).toBe('https://api.b.ai/v1');
  });

  it('stores and retrieves a selected model with the custom flag', async () => {
    await secureStore.setSelectedModel('gpt-6', false);
    expect(await secureStore.getSelectedModel()).toBe('gpt-6');
    await secureStore.clearSelectedModel();
    expect(await secureStore.getSelectedModel()).toBeNull();
  });
});
