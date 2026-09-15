import {
  ModelService,
  InMemoryModelCache,
  normalizeModels,
  classifyError,
  redactApiKey,
} from '../src/services/ModelService';
import { HttpClient, HttpError } from '../src/services/HttpClient';

class FakeHttp implements HttpClient {
  responses: Array<
    | { ok: true; json: unknown }
    | { ok: false; status: number; message?: string }
  > = [];
  recordedHeaders: Record<string, string>[] = [];
  recordedUrls: string[] = [];
  queuedResponses: any[] = [];

  enqueueJson(json: unknown) {
    this.responses.push({ ok: true, json });
  }
  enqueueError(status: number, message?: string) {
    this.responses.push({ ok: false, status, message });
  }

  async get<T>(opts: any): Promise<T> {
    this.recordedUrls.push(opts.url);
    this.recordedHeaders.push(opts.headers || {});
    const next = this.responses.shift();
    if (!next) throw new Error('No queued response');
    if (!next.ok) {
      throw new HttpError(next.message || `HTTP ${next.status}`, next.status);
    }
    return next.json as T;
  }
  async post<T>(opts: any): Promise<T> {
    return this.get<T>(opts);
  }
}

const makeService = (http: FakeHttp, cache?: InMemoryModelCache) =>
  new ModelService({
    http,
    cache: cache ?? new InMemoryModelCache(),
    timeoutMs: 1000,
  });

describe('ModelService.buildModelsUrl', () => {
  const svc = new ModelService({ http: new FakeHttp() });
  it('appends /models to a bare host', () => {
    expect(svc.buildModelsUrl('https://api.b.ai/v1')).toBe(
      'https://api.b.ai/v1/models',
    );
  });
  it('strips trailing slashes', () => {
    expect(svc.buildModelsUrl('https://api.b.ai/v1///')).toBe(
      'https://api.b.ai/v1/models',
    );
  });
  it('rejects non-http protocols', () => {
    expect(svc.buildModelsUrl('ftp://example.com')).toBeNull();
    expect(svc.buildModelsUrl('javascript:alert(1)')).toBeNull();
  });
  it('rejects empty / unparseable URLs', () => {
    expect(svc.buildModelsUrl('')).toBeNull();
    expect(svc.buildModelsUrl('not a url')).toBeNull();
  });
});

describe('ModelService.fetchModels — successful flow', () => {
  it('parses the standard OpenAI { data: [...] } shape', async () => {
    const http = new FakeHttp();
    http.enqueueJson({
      object: 'list',
      data: [
        { id: 'gpt-6-astra', owned_by: 'openai' },
        { id: 'gpt-5-mini', owned_by: 'openai' },
      ],
    });
    const svc = makeService(http);
    const models = await svc.fetchModels('https://api.b.ai/v1', 'sk-test');
    expect(models).toHaveLength(2);
    expect(models[0].id).toBe('gpt-6-astra');
    expect(models[0].ownedBy).toBe('openai');
    expect(models[1].id).toBe('gpt-5-mini');
  });

  it('sends Bearer auth and never logs it', async () => {
    const http = new FakeHttp();
    http.enqueueJson({ data: [{ id: 'm1' }] });
    const svc = makeService(http);
    await svc.fetchModels('https://api.b.ai/v1', 'sk-supersecret-1234567890');
    expect(http.recordedUrls[0]).toBe('https://api.b.ai/v1/models');
    expect(http.recordedHeaders[0].Authorization).toBe(
      'Bearer sk-supersecret-1234567890',
    );
    // Redaction helper must scrub the key.
    expect(redactApiKey('sk-supersecret-1234567890')).not.toContain(
      'supersecret',
    );
  });

  it('caches a successful response so it can be served offline', async () => {
    const http = new FakeHttp();
    http.enqueueJson({ data: [{ id: 'm1' }, { id: 'm2' }] });
    const cache = new InMemoryModelCache();
    const svc = makeService(http, cache);
    await svc.fetchModels('https://api.b.ai/v1', 'sk-test');
    const cached = await svc.getCachedModels('https://api.b.ai/v1');
    expect(cached?.map((m) => m.id)).toEqual(['m1', 'm2']);
  });

  it('extracts capabilities from provider metadata', async () => {
    const http = new FakeHttp();
    http.enqueueJson({
      data: [
        {
          id: 'vision-pro',
          modalities: ['text', 'image'],
          context_length: 128000,
          supports_reasoning: true,
          owned_by: 'example',
        },
        {
          id: 'chat-fast',
          modalities: ['text'],
          context_window: 8000,
        },
      ],
    });
    const svc = makeService(http);
    const models = await svc.fetchModels('https://api.b.ai/v1', 'sk-test');
    expect(models[0].capabilities.vision).toBe(true);
    expect(models[0].capabilities.modalities).toContain('image');
    expect(models[0].capabilities.reasoning).toBe(true);
    expect(models[0].contextLength).toBe(128000);
    expect(models[1].capabilities.vision).toBe(false);
    expect(models[1].contextLength).toBe(8000);
  });
});

describe('ModelService.fetchModels — error handling', () => {
  it('rejects with INVALID_URL on bad base URL', async () => {
    const svc = makeService(new FakeHttp());
    await expect(svc.fetchModels('not-a-url', 'sk-test')).rejects.toMatchObject(
      { code: 'INVALID_URL' },
    );
  });

  it('rejects with MISSING_API_KEY when no key', async () => {
    const svc = makeService(new FakeHttp());
    await expect(
      svc.fetchModels('https://api.b.ai/v1', ''),
    ).rejects.toMatchObject({ code: 'MISSING_API_KEY' });
  });

  it('classifies 401 as UNAUTHORIZED with a safe message', async () => {
    const http = new FakeHttp();
    http.enqueueError(401);
    const svc = makeService(http);
    try {
      await svc.fetchModels('https://api.b.ai/v1', 'sk-test');
      fail('should have thrown');
    } catch (e: any) {
      expect(e.code).toBe('UNAUTHORIZED');
      // The error must not contain the API key.
      expect(JSON.stringify(e)).not.toContain('sk-test');
      expect(e.message).not.toContain('sk-test');
    }
  });

  it('classifies 403 as FORBIDDEN', async () => {
    const http = new FakeHttp();
    http.enqueueError(403);
    const svc = makeService(http);
    await expect(
      svc.fetchModels('https://api.b.ai/v1', 'sk-test'),
    ).rejects.toMatchObject({ code: 'FORBIDDEN' });
  });

  it('classifies 404 as NOT_FOUND', async () => {
    const http = new FakeHttp();
    http.enqueueError(404);
    const svc = makeService(http);
    await expect(
      svc.fetchModels('https://api.b.ai/v1', 'sk-test'),
    ).rejects.toMatchObject({ code: 'NOT_FOUND' });
  });

  it('classifies empty list as EMPTY_LIST', async () => {
    const http = new FakeHttp();
    http.enqueueJson({ object: 'list', data: [] });
    const svc = makeService(http);
    await expect(
      svc.fetchModels('https://api.b.ai/v1', 'sk-test'),
    ).rejects.toMatchObject({ code: 'EMPTY_LIST' });
  });

  it('classifies malformed JSON as MALFORMED_RESPONSE', async () => {
    const http = new FakeHttp();
    http.enqueueError(200, 'Malformed JSON response');
    const svc = makeService(http);
    await expect(
      svc.fetchModels('https://api.b.ai/v1', 'sk-test'),
    ).rejects.toMatchObject({ code: 'MALFORMED_RESPONSE' });
  });
});

describe('ModelService.testConnection', () => {
  it('returns ok with modelCount on success', async () => {
    const http = new FakeHttp();
    http.enqueueJson({ data: [{ id: 'a' }, { id: 'b' }] });
    const svc = makeService(http);
    const result = await svc.testConnection('https://api.b.ai/v1', 'sk-test');
    expect(result.ok).toBe(true);
    expect(result.modelCount).toBe(2);
    expect(result.status).toBe(200);
  });

  it('returns ok=false with safe reason on 401', async () => {
    const http = new FakeHttp();
    http.enqueueError(401);
    const svc = makeService(http);
    const result = await svc.testConnection('https://api.b.ai/v1', 'sk-test');
    expect(result.ok).toBe(false);
    expect(result.errorCode).toBe('UNAUTHORIZED');
    expect(result.error).toBeDefined();
    expect(result.error).not.toContain('sk-test');
  });

  it('treats empty list as failure', async () => {
    const http = new FakeHttp();
    http.enqueueJson({ data: [] });
    const svc = makeService(http);
    const result = await svc.testConnection('https://api.b.ai/v1', 'sk-test');
    expect(result.ok).toBe(false);
    expect(result.errorCode).toBe('EMPTY_LIST');
  });
});

describe('normalizeModels — parser resilience', () => {
  it('handles standard { data: [...] }', () => {
    expect(normalizeModels({ data: [{ id: 'a' }, { id: 'b' }] }).map((m) => m.id)).toEqual(['a', 'b']);
  });
  it('handles { models: [...] }', () => {
    expect(
      normalizeModels({ models: [{ id: 'x' }, { id: 'y' }] }).map((m) => m.id),
    ).toEqual(['x', 'y']);
  });
  it('handles a top-level array', () => {
    expect(normalizeModels([{ id: '1' }, { id: '2' }]).map((m) => m.id)).toEqual(['1', '2']);
  });
  it('handles a single object', () => {
    expect(normalizeModels({ id: 'solo' }).map((m) => m.id)).toEqual(['solo']);
  });
  it('returns [] for non-object input', () => {
    expect(normalizeModels(null)).toEqual([]);
    expect(normalizeModels('string')).toEqual([]);
    expect(normalizeModels(42)).toEqual([]);
  });
  it('drops entries without an id', () => {
    expect(normalizeModels({ data: [{ id: 'ok' }, { foo: 'bar' }] }).map((m) => m.id)).toEqual(['ok']);
  });
  it('infers displayName from name/display_name/id', () => {
    const m = normalizeModels({
      data: [{ id: 'm-1', display_name: 'Model One' }],
    })[0];
    expect(m.displayName).toBe('Model One');
    const m2 = normalizeModels({
      data: [{ id: 'm-2', name: 'Model Two' }],
    })[0];
    expect(m2.displayName).toBe('Model Two');
    const m3 = normalizeModels({ data: [{ id: 'm-3' }] })[0];
    expect(m3.displayName).toBe('m-3');
  });
});

describe('classifyError', () => {
  it('maps status codes', () => {
    expect(classifyError({ status: 401 })).toBe('UNAUTHORIZED');
    expect(classifyError({ status: 403 })).toBe('FORBIDDEN');
    expect(classifyError({ status: 404 })).toBe('NOT_FOUND');
  });
  it('maps message keywords', () => {
    expect(classifyError({ message: 'Request timed out' })).toBe('TIMEOUT');
    expect(classifyError({ message: 'Network error' })).toBe('NETWORK_ERROR');
    expect(classifyError({ message: 'Malformed JSON response' })).toBe(
      'MALFORMED_RESPONSE',
    );
    expect(classifyError({ message: 'No models returned' })).toBe('EMPTY_LIST');
  });
});

describe('redactApiKey', () => {
  it('redacts short or empty keys', () => {
    expect(redactApiKey('')).toBe('***');
    expect(redactApiKey('abc')).toBe('***');
  });
  it('keeps the first 3 and last 3 characters', () => {
    expect(redactApiKey('sk-abcdefghijk')).toBe('sk-…ijk');
  });
});
