/**
 * Minimal chat completion client used by the demo Chat screen.
 *
 * Mirrors the same fetch plumbing as ModelService but talks to
 * `POST /chat/completions`. Kept separate so each service has a single
 * responsibility.
 */

import { HttpClient, HttpError, httpClient } from './HttpClient';

export interface ChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

export interface ChatOptions {
  baseUrl: string;
  apiKey: string;
  model: string;
  messages: ChatMessage[];
  signal?: AbortSignal;
  timeoutMs?: number;
}

export interface ChatResult {
  text: string;
  raw: unknown;
}

export class ChatService {
  private http: HttpClient;
  constructor(http: HttpClient = httpClient) {
    this.http = http;
  }

  async complete(opts: ChatOptions): Promise<ChatResult> {
    const url = this.buildChatUrl(opts.baseUrl);
    if (!url) throw new Error('Invalid Base URL');
    try {
      const data = await this.http.post<any>({
        url,
        timeoutMs: opts.timeoutMs ?? 30_000,
        signal: opts.signal,
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${opts.apiKey}`,
        },
        body: {
          model: opts.model,
          messages: opts.messages,
        },
      });
      const text = extractText(data);
      return { text, raw: data };
    } catch (e: any) {
      if (e instanceof HttpError) {
        throw new Error(`Chat failed: ${e.message}`);
      }
      throw e;
    }
  }

  private buildChatUrl(baseUrl: string): string | null {
    const trimmed = (baseUrl || '').trim().replace(/\/+$/, '');
    if (!trimmed) return null;
    try {
      const u = new URL(trimmed);
      if (u.protocol !== 'http:' && u.protocol !== 'https:') return null;
      return `${u.toString().replace(/\/+$/, '')}/chat/completions`;
    } catch {
      return null;
    }
  }
}

function extractText(data: any): string {
  if (!data) return '';
  // Standard OpenAI shape:
  const choice = data?.choices?.[0];
  if (choice?.message?.content) return String(choice.message.content);
  if (typeof choice?.text === 'string') return choice.text;
  // Some providers return a top-level `output_text`.
  if (typeof data?.output_text === 'string') return data.output_text;
  return '';
}
