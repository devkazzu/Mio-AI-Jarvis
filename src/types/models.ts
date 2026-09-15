/**
 * Type definitions for OpenAI-compatible /models endpoints.
 *
 * These interfaces intentionally use safe optional fields so we can parse
 * responses from a wide variety of providers (b.ai, OpenAI, Ollama, etc.)
 * without crashing on unknown shapes.
 */

export type Modality = 'text' | 'image' | 'audio' | 'video' | 'file';

/**
 * Raw shape returned by `/v1/models`.
 *
 * Different providers add different fields. We treat everything as optional
 * except `id` and parse defensively.
 */
export interface RawModelEntry {
  id?: unknown;
  object?: unknown;
  created?: unknown;
  owned_by?: unknown;
  // Some providers (e.g. vLLM, OpenRouter) attach metadata:
  display_name?: unknown;
  name?: unknown;
  description?: unknown;
  context_length?: unknown;
  max_context_length?: unknown;
  context_window?: unknown;
  capabilities?: unknown;
  modalities?: unknown;
  input_modalities?: unknown;
  output_modalities?: unknown;
  supports_vision?: unknown;
  supports_tools?: unknown;
  supports_reasoning?: unknown;
  // Generic catch-all (not iterated, just tolerated)
  [key: string]: unknown;
}

export interface RawModelsResponse {
  object?: unknown;
  data?: unknown;
  // Some providers put models at the top level
  models?: unknown;
}

/**
 * Normalized model entry used internally and shown in the UI.
 */
export interface ModelInfo {
  /** Stable identifier sent in chat completion requests. */
  id: string;
  /** Human-readable display name. Falls back to `id`. */
  displayName: string;
  /** Owner / provider string when reported. */
  ownedBy?: string;
  /** Free-text description when reported. */
  description?: string;
  /** Optional context window length in tokens. */
  contextLength?: number;
  /** Optional model capabilities derived from the response. */
  capabilities: ModelCapabilities;
}

export interface ModelCapabilities {
  modalities: Modality[];
  vision: boolean;
  tools: boolean;
  reasoning: boolean;
}

export interface ConnectionTestResult {
  ok: boolean;
  status?: number;
  modelCount?: number;
  error?: string;
  errorCode?: ConnectionErrorCode;
}

export type ConnectionErrorCode =
  | 'INVALID_URL'
  | 'MISSING_API_KEY'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'NETWORK_ERROR'
  | 'TIMEOUT'
  | 'EMPTY_LIST'
  | 'MALFORMED_RESPONSE'
  | 'UNKNOWN';
