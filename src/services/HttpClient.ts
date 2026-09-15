/**
 * Tiny HTTP client used by service-layer code.
 *
 * We deliberately do NOT depend on axios/etc — `fetch` is sufficient for the
 * simple GETs we need and keeps the bundle small.
 *
 * Important: This module never logs the `Authorization` header. Errors are
 * normalized so callers don't have to know whether the failure was a timeout,
 * network error, or HTTP status.
 */

export class HttpError extends Error {
  readonly status?: number;
  readonly cause?: unknown;
  constructor(message: string, status?: number, cause?: unknown) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.cause = cause;
  }
}

export interface HttpRequestOptions {
  url: string;
  headers?: Record<string, string>;
  /** ms; default 15s. */
  timeoutMs?: number;
  /** AbortSignal to compose with parent aborts. */
  signal?: AbortSignal;
  /** Optional JSON body (POST/PUT). */
  body?: unknown;
  /** Override HTTP method (defaults to GET, or POST if body provided). */
  method?: string;
}

export interface HttpClient {
  get<T>(options: HttpRequestOptions): Promise<T>;
  post<T>(options: HttpRequestOptions): Promise<T>;
}

class FetchHttpClient implements HttpClient {
  async get<T>(options: HttpRequestOptions): Promise<T> {
    return this.request<T>({ ...options, method: 'GET' });
  }
  async post<T>(options: HttpRequestOptions): Promise<T> {
    return this.request<T>({
      ...options,
      method: options.method ?? 'POST',
    });
  }

  private async request<T>({
    url,
    headers = {},
    timeoutMs = 15_000,
    signal,
    body,
    method = 'GET',
  }: HttpRequestOptions): Promise<T> {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
    const onParentAbort = () => controller.abort();
    if (signal) {
      if (signal.aborted) controller.abort();
      else signal.addEventListener('abort', onParentAbort);
    }
    try {
      const res = await fetch(url, {
        method,
        headers: {
          Accept: 'application/json',
          ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
          ...headers,
        },
        body: body !== undefined ? JSON.stringify(body) : undefined,
        signal: controller.signal,
      });
      if (!res.ok) {
        throw new HttpError(`HTTP ${res.status}`, res.status);
      }
      const text = await res.text();
      if (!text) return undefined as unknown as T;
      try {
        return JSON.parse(text) as T;
      } catch (e) {
        throw new HttpError('Malformed JSON response', res.status, e);
      }
    } catch (e: any) {
      if (e instanceof HttpError) throw e;
      if (e?.name === 'AbortError') {
        throw new HttpError('Request timed out', undefined, e);
      }
      throw new HttpError(e?.message || 'Network error', undefined, e);
    } finally {
      clearTimeout(timeoutId);
      if (signal) signal.removeEventListener('abort', onParentAbort);
    }
  }
}

export const httpClient: HttpClient = new FetchHttpClient();
export const __testing = { FetchHttpClient };
