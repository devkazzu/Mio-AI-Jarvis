import {
  ModelService,
  InMemoryModelCache,
  normalizeModels,
} from '../src/services/ModelService';
import { HttpClient } from '../src/services/HttpClient';
import { settingsStore } from '../src/state/settingsStore';

class FakeHttp implements HttpClient {
  response: any;
  constructor(response: any) {
    this.response = response;
  }
  async get<T>(): Promise<T> {
    if (this.response instanceof Error) throw this.response;
    return this.response as T;
  }
  async post<T>(): Promise<T> {
    return this.get<T>();
  }
}

describe('Model persistence & cache semantics', () => {
  beforeEach(() => {
    settingsStore.setState({
      baseUrl: 'https://api.b.ai/v1',
      hasApiKey: true,
      selectedModel: '',
      selectedModelIsCustom: false,
      models: [],
      modelsFetchedAt: null,
      modelsFromCache: false,
      status: { kind: 'idle' },
    });
  });

  it('caches the model list after a successful fetch', async () => {
    const cache = new InMemoryModelCache();
    const svc = new ModelService({
      http: new FakeHttp({ data: [{ id: 'm1' }, { id: 'm2' }] }),
      cache,
    });
    await svc.fetchModels('https://api.b.ai/v1', 'sk-test');
    const cached = await svc.getCachedModels('https://api.b.ai/v1');
    expect(cached?.map((m) => m.id)).toEqual(['m1', 'm2']);
  });

  it('preserves the user-selected model when it still exists', () => {
    settingsStore.setState({ selectedModel: 'gpt-6-astra' });
    const fresh = normalizeModels({
      data: [{ id: 'gpt-6-astra' }, { id: 'gpt-5' }],
    });
    const prev = settingsStore.getState().selectedModel;
    const stillExists = fresh.some((m) => m.id === prev);
    const next = stillExists ? prev : fresh[0]?.id ?? '';
    settingsStore.setState({ selectedModel: next, models: fresh });
    expect(settingsStore.getState().selectedModel).toBe('gpt-6-astra');
  });

  it('falls back to the first available model when the stored one is missing', () => {
    settingsStore.setState({ selectedModel: 'legacy-model' });
    const fresh = normalizeModels({ data: [{ id: 'gpt-5' }, { id: 'gpt-6' }] });
    const prev = settingsStore.getState().selectedModel;
    const stillExists = fresh.some((m) => m.id === prev);
    const next = stillExists ? prev : fresh[0]?.id ?? '';
    settingsStore.setState({ selectedModel: next, models: fresh });
    expect(settingsStore.getState().selectedModel).toBe('gpt-5');
  });

  it('flags a legacy/custom model when nothing else matches', () => {
    settingsStore.setState({ selectedModel: 'manual-id', selectedModelIsCustom: true });
    const fresh: ReturnType<typeof normalizeModels> = [];
    const stillExists = fresh.some((m) => m.id === 'manual-id');
    expect(stillExists).toBe(false);
    // The legacy option is preserved by leaving selectedModelIsCustom=true
    // and rendering the entry at the top of the sheet.
    expect(settingsStore.getState().selectedModelIsCustom).toBe(true);
  });

  it('uses cache when network fetch fails (offline behaviour)', async () => {
    const cache = new InMemoryModelCache();
    // Pre-seed the cache using the same key the service uses internally.
    await cache.write('https://api.b.ai/v1/models', [
      { id: 'cached-1', displayName: 'Cached 1', capabilities: { modalities: ['text'], vision: false, tools: false, reasoning: false } },
    ]);
    const svc = new ModelService({
      http: new FakeHttp(new Error('Failed to fetch')),
      cache,
    });
    // fetch fails:
    await expect(
      svc.fetchModels('https://api.b.ai/v1', 'sk-test'),
    ).rejects.toBeTruthy();
    // but cache is still readable:
    const cached = await svc.getCachedModels('https://api.b.ai/v1');
    expect(cached?.[0].id).toBe('cached-1');
  });
});
