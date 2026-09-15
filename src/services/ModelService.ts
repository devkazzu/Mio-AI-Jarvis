/**
 * ModelService
 * ============
 *
 * The single source of truth for talking to the OpenAI-compatible provider's
 * `/models` endpoint. The UI never calls `fetch` directly.
 *
 * Responsibilities:
 *   - Build a sanitized request URL from a user-supplied Base URL.
 *   - Attach `Authorization: Bearer ...` (NEVER logged).
 *   - Parse common `/models` response shapes.
 *   - Cache the latest successful response (memory + secure-store mirror).
 *   - Return a typed `ModelInfo[]`.
 *   - Distinguish empty / malformed / unauthorized / network failures.
 *
 * The service is intentionally framework-agnostic: no React, no React Native
 * imports. That makes it trivial to unit-test with plain Jest.
 */

import {
  HttpClient,
  HttpError,
  httpClient,
} from './HttpClient';
import {
  ConnectionErrorCode,
  ConnectionTestResult,
  ModelCapabilities,
  ModelInfo,
  Modality,
  RawModelEntry,
  RawModelsResponse,
} from '../types/models';

/** Sentinel token used in logs/error messages in place of real keys. */
const REDACTED = '***';

export interface ModelServiceOptions {
  http?: HttpClient;
  /** Overridable cache adapter; default is in-memory. */
  cache?: ModelCache;
  /** Default timeout for network calls. */
  timeoutMs?: number;
}

export interface ModelCache {
  read(baseUrl: string): Promise<ModelInfo[] | null>;
  write(baseUrl: string, models: ModelInfo[]): Promise<void>;
  clear(baseUrl: string): Promise<void>;
}

interface InMemoryCacheEntry {
  baseUrl: string;
  models: ModelInfo[];
  fetchedAt: number;
}

export class InMemoryModelCache implements ModelCache {
  private entries = new Map<string, InMemoryCacheEntry>();

  async read(baseUrl: string): Promise<ModelInfo[] | null> {
    return this.entries.get(baseUrl)?.models ?? null;
  }
  async write(baseUrl: string, models: ModelInfo[]): Promise<void> {
    this.entries.set(baseUrl, {
      baseUrl,
      models,
      fetchedAt: Date.now(),
    });
  }
  async clear(baseUrl: string): Promise<void> {
    this.entries.delete(baseUrl);
  }
}

export class ModelService {
  private http: HttpClient;
  private cache: ModelCache;
  private timeoutMs: number;

  constructor(opts: ModelServiceOptions = {}) {
    this.http = opts.http ?? httpClient;
    this.cache = opts.cache ?? new InMemoryModelCache();
    this.timeoutMs = opts.timeoutMs ?? 15_000;
  }

  // ------------------------------------------------------------------
  // Public API
  // ------------------------------------------------------------------

  /**
   * Fetch models from the configured provider.
   *
   * Throws an Error with a `.code` property matching `ConnectionErrorCode`
   * and a user-safe `message` that never contains the API key.
   */
  async fetchModels(baseUrl: string, apiKey: string): Promise<ModelInfo[]> {
    const url = this.buildModelsUrl(baseUrl);
    if (!url) {
      throw this.makeError('Invalid Base URL', 'INVALID_URL');
    }
    if (!apiKey) {
      throw this.makeError('API key is required', 'MISSING_API_KEY');
    }
    try {
      const res = await this.http.get<RawModelsResponse>({
        url,
        headers: { Authorization: `Bearer ${apiKey}` },
        timeoutMs: this.timeoutMs,
      });
      const models = normalizeModels(res);
      if (models.length === 0) {
        throw this.makeError('No models returned by provider', 'EMPTY_LIST');
      }
      await this.cache.write(url, models);
      return models;
    } catch (e: any) {
      // Re-normalize network errors thrown by HttpClient.
      const code = classifyError(e);
      throw this.makeError(
        safeMessage(e, code),
        code,
        e instanceof HttpError ? e.status : undefined,
      );
    }
  }

  /**
   * Test the connection by calling `/models` and verifying it returns at
   * least one model. Returns a structured result (does not throw).
   */
  async testConnection(
    baseUrl: string,
    apiKey: string,
  ): Promise<ConnectionTestResult> {
    try {
      const models = await this.fetchModels(baseUrl, apiKey);
      return {
        ok: true,
        status: 200,
        modelCount: models.length,
      };
    } catch (e: any) {
      return {
        ok: false,
        status: e?.status,
        error: e?.message ?? 'Connection failed',
        errorCode: e?.code as ConnectionErrorCode,
      };
    }
  }

  /**
   * Read the cached model list for a base URL, if any.
   * Returns `null` if nothing is cached.
   */
  async getCachedModels(baseUrl: string): Promise<ModelInfo[] | null> {
    const url = this.buildModelsUrl(baseUrl);
    if (!url) return null;
    return this.cache.read(url);
  }

  /**
   * Clear the cache entry for a base URL.
   */
  async clearCache(baseUrl: string): Promise<void> {
    const url = this.buildModelsUrl(baseUrl);
    if (!url) return;
    await this.cache.clear(url);
  }

  /**
   * Build the absolute URL to GET /models. Public for tests.
   */
  buildModelsUrl(baseUrl: string): string | null {
    const trimmed = (baseUrl || '').trim().replace(/\/+$/, '');
    if (!trimmed) return null;
    // Quick validation: must be http(s) and contain a host.
    let parsed: URL;
    try {
      parsed = new URL(trimmed);
    } catch {
      return null;
    }
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return null;
    }
    if (!parsed.hostname) return null;
    return `${parsed.toString().replace(/\/+$/, '')}/models`;
  }

  // ------------------------------------------------------------------
  // Helpers
  // ------------------------------------------------------------------

  private makeError(
    message: string,
    code: ConnectionErrorCode,
    status?: number,
  ): Error {
    const err: any = new Error(message);
    err.code = code;
    if (status !== undefined) err.status = status;
    return err;
  }
}

// ----------------------------------------------------------------------
// Pure helpers (exported so tests can hit them directly)
// ----------------------------------------------------------------------

/**
 * Parse and normalize a `/models` response into our internal shape.
 * Tolerates `data`, top-level `models`, or single-object responses.
 */
export function normalizeModels(input: unknown): ModelInfo[] {
  if (!input || typeof input !== 'object') return [];
  const resp = input as RawModelsResponse;

  // Standard OpenAI shape: { data: [...] }
  if (Array.isArray(resp.data)) {
    return resp.data.map(parseEntry).filter(isValidModel);
  }

  // Some providers: { models: [...] }
  if (Array.isArray(resp.models)) {
    return resp.models.map(parseEntry).filter(isValidModel);
  }

  // Some providers: top-level array
  if (Array.isArray(input)) {
    return (input as unknown[]).map(parseEntry).filter(isValidModel);
  }

  // Some providers: a single model object
  const single = parseEntry(input as RawModelEntry);
  return isValidModel(single) ? [single] : [];
}

function parseEntry(raw: unknown): ModelInfo {
  const r = (raw ?? {}) as RawModelEntry;
  const id = typeof r.id === 'string' ? r.id : '';
  const displayName =
    pickString(r.display_name, r.name, r.id) || id;
  const ownedBy = pickString(r.owned_by);
  const description = pickString(r.description);
  const contextLength = pickNumber(
    r.context_length,
    r.max_context_length,
    r.context_window,
  );
  const capabilities = extractCapabilities(r);
  return {
    id,
    displayName,
    ownedBy,
    description,
    contextLength,
    capabilities,
  };
}

function isValidModel(m: ModelInfo): boolean {
  return typeof m.id === 'string' && m.id.length > 0;
}

function pickString(...candidates: unknown[]): string | undefined {
  for (const c of candidates) {
    if (typeof c === 'string' && c.trim().length > 0) return c.trim();
  }
  return undefined;
}

function pickNumber(...candidates: unknown[]): number | undefined {
  for (const c of candidates) {
    if (typeof c === 'number' && Number.isFinite(c)) return c;
    if (typeof c === 'string') {
      const n = Number(c);
      if (Number.isFinite(n)) return n;
    }
  }
  return undefined;
}

function extractCapabilities(r: RawModelEntry): ModelCapabilities {
  const modalities = collectModalities(r);
  const vision =
    boolOr(r.supports_vision) ||
    modalities.includes('image');
  const tools = boolOr(r.supports_tools);
  const reasoning = boolOr(r.supports_reasoning);
  return { modalities, vision, tools, reasoning };
}

function collectModalities(r: RawModelEntry): Modality[] {
  const out = new Set<Modality>();
  const lists: unknown[] = [
    r.modalities,
    r.input_modalities,
    r.output_modalities,
  ];
  // Some providers put modalities inside `capabilities`
  if (r.capabilities && typeof r.capabilities === 'object') {
    const cap = r.capabilities as Record<string, unknown>;
    lists.push(cap.modalities, cap.input, cap.output);
  }
  for (const list of lists) {
    if (Array.isArray(list)) {
      for (const item of list) {
        const m = normalizeModality(item);
        if (m) out.add(m);
      }
    } else if (typeof list === 'string') {
      const m = normalizeModality(list);
      if (m) out.add(m);
    }
  }
  // If nothing was reported but the model explicitly supports vision, mark it.
  if (out.size === 0) {
    if (boolOr(r.supports_vision)) out.add('image');
  }
  // Default to text if absolutely nothing was inferred.
  if (out.size === 0) out.add('text');
  return Array.from(out);
}

function normalizeModality(value: unknown): Modality | null {
  if (typeof value !== 'string') return null;
  const v = value.toLowerCase().trim();
  if (v === 'text' || v === 'text-only' || v === 'chat') return 'text';
  if (v === 'image' || v === 'vision' || v === 'images') return 'image';
  if (v === 'audio' || v === 'audio_input' || v === 'audio_output')
    return 'audio';
  if (v === 'video') return 'video';
  if (v === 'file' || v === 'pdf') return 'file';
  return null;
}

function boolOr(value: unknown): boolean {
  return value === true;
}

export function classifyError(e: any): ConnectionErrorCode {
  if (!e) return 'UNKNOWN';
  const status = typeof e.status === 'number' ? e.status : undefined;
  if (status === 401) return 'UNAUTHORIZED';
  if (status === 403) return 'FORBIDDEN';
  if (status === 404) return 'NOT_FOUND';
  const msg = String(e.message || '').toLowerCase();
  if (msg.includes('timed out') || msg.includes('timeout')) return 'TIMEOUT';
  if (
    msg.includes('network') ||
    msg.includes('failed to fetch') ||
    msg.includes('econnrefused') ||
    msg.includes('enotfound')
  ) {
    return 'NETWORK_ERROR';
  }
  if (msg.includes('malformed')) return 'MALFORMED_RESPONSE';
  if (msg.includes('no models')) return 'EMPTY_LIST';
  return 'UNKNOWN';
}

function safeMessage(e: any, code: ConnectionErrorCode): string {
  switch (code) {
    case 'UNAUTHORIZED':
      return 'Invalid API key or unauthorized request.';
    case 'FORBIDDEN':
      return 'Access forbidden. Check your API key permissions.';
    case 'NOT_FOUND':
      return 'The /models endpoint was not found on this Base URL.';
    case 'NETWORK_ERROR':
      return 'Could not reach the server. Check your network and Base URL.';
    case 'TIMEOUT':
      return 'The request timed out. Try again or increase the timeout.';
    case 'EMPTY_LIST':
      return 'The provider returned no models.';
    case 'MALFORMED_RESPONSE':
      return 'The provider returned an unexpected response format.';
    case 'INVALID_URL':
      return 'The Base URL is not valid.';
    case 'MISSING_API_KEY':
      return 'API key is required.';
    default:
      return 'Could not fetch models. Please try again.';
  }
}

/** Expose a redaction helper for callers that want to scrub API keys. */
export function redactApiKey(apiKey: string): string {
  if (!apiKey) return REDACTED;
  if (apiKey.length <= 6) return REDACTED;
  return `${apiKey.slice(0, 3)}…${apiKey.slice(-3)}`;
}

export const __testing = {
  ModelService,
  parseEntry,
  extractCapabilities,
  collectModalities,
  normalizeModality,
};
