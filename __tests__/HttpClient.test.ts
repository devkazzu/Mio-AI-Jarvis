import { __testing } from '../src/services/HttpClient';
const { FetchHttpClient } = __testing;

describe('FetchHttpClient', () => {
  let originalFetch: typeof fetch;

  beforeEach(() => {
    originalFetch = global.fetch;
  });
  afterEach(() => {
    global.fetch = originalFetch;
  });

  it('parses JSON on a 2xx response', async () => {
    global.fetch = jest.fn(async () =>
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    ) as any;
    const c = new FetchHttpClient();
    const r = await c.get<{ ok: boolean }>({ url: 'https://x.test/a' });
    expect(r.ok).toBe(true);
  });

  it('throws HttpError with status on non-2xx', async () => {
    global.fetch = jest.fn(async () => new Response('', { status: 401 })) as any;
    const c = new FetchHttpClient();
    await expect(c.get({ url: 'https://x.test/a' })).rejects.toMatchObject({
      status: 401,
      name: 'HttpError',
    });
  });

  it('throws HttpError on malformed JSON', async () => {
    global.fetch = jest.fn(
      async () =>
        new Response('not json{', {
          status: 200,
          headers: { 'content-type': 'application/json' },
        }),
    ) as any;
    const c = new FetchHttpClient();
    await expect(c.get({ url: 'https://x.test/a' })).rejects.toMatchObject({
      name: 'HttpError',
    });
  });

  it('serializes a JSON body for POST', async () => {
    const fetchMock = jest.fn(
      async () =>
        new Response(JSON.stringify({ ok: true }), { status: 200 }),
    );
    global.fetch = fetchMock as any;
    const c = new FetchHttpClient();
    await c.post({ url: 'https://x.test/a', body: { hello: 'world' } });
    const callArgs = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(callArgs[0]).toBe('https://x.test/a');
    expect(callArgs[1].method).toBe('POST');
    expect(JSON.parse(String(callArgs[1].body))).toEqual({ hello: 'world' });
  });
});
