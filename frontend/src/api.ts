// Cliente da API da Carteira Digital: tipos, chamadas HTTP e tratamento de erros.

export type TransactionType = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER';
export type Direction = 'CREDIT' | 'DEBIT';

export interface RegisteredUser {
  id: string;
  name: string;
  email: string;
  walletId: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface WalletInfo {
  id: string;
  balance: number;
}

export interface OperationResult {
  id: string;
  type: TransactionType;
  status: string;
  amount: number;
  createdAt: string;
}

export interface StatementEntry {
  id: string;
  type: TransactionType;
  status: string;
  direction: Direction;
  amount: number;
  sourceWalletId: string | null;
  targetWalletId: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface StatementFilters {
  type?: TransactionType;
  from?: string;
  to?: string;
  page: number;
  size: number;
}

// ---------------------------------------------------------------- token

const TOKEN_KEY = 'carteira-digital.token';

// O token fica no sessionStorage: sobrevive ao F5, mas some ao fechar a aba.
let authToken: string | null = sessionStorage.getItem(TOKEN_KEY);
let unauthorizedHandler: (() => void) | null = null;

export function getAuthToken(): string | null {
  return authToken;
}

export function setAuthToken(token: string | null): void {
  authToken = token;
  if (token) {
    sessionStorage.setItem(TOKEN_KEY, token);
  } else {
    sessionStorage.removeItem(TOKEN_KEY);
  }
}

/** Chamado quando a API responde 401 a uma requisição autenticada (token expirado ou inválido). */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

// ---------------------------------------------------------------- erros

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: Record<string, string> | null;

  constructor(status: number, message: string, fieldErrors: Record<string, string> | null = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

const FIELD_LABELS: Record<string, string> = {
  name: 'Nome',
  email: 'E-mail',
  password: 'Senha',
  amount: 'Valor',
  targetWalletId: 'Carteira de destino',
};

const FALLBACK_MESSAGES: Record<number, string> = {
  401: 'Sua sessão expirou. Entre novamente.',
  403: 'Você não tem permissão para fazer isso.',
  404: 'Recurso não encontrado.',
  409: 'Conflito: tente novamente.',
  500: 'Erro interno no servidor. Tente novamente em instantes.',
};

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.fieldErrors) {
      const parts = Object.entries(error.fieldErrors).map(
        ([field, message]) => `${FIELD_LABELS[field] ?? field}: ${message}`,
      );
      if (parts.length > 0) {
        return parts.join(' · ');
      }
    }
    return error.message;
  }
  return 'Ocorreu um erro inesperado.';
}

async function toApiError(response: Response): Promise<ApiError> {
  let detail: string | undefined;
  let fieldErrors: Record<string, string> | null = null;
  try {
    const data = (await response.json()) as {
      detail?: string;
      title?: string;
      errors?: Record<string, string>;
    };
    detail = data.detail ?? data.title;
    fieldErrors = data.errors ?? null;
  } catch {
    // resposta sem corpo JSON (por exemplo, o 401 do filtro de segurança)
  }
  const message = detail ?? FALLBACK_MESSAGES[response.status] ?? `Erro ${response.status}`;
  return new ApiError(response.status, message, fieldErrors);
}

// ---------------------------------------------------------------- requisições

const API_BASE = '/api';

interface RequestOptions {
  body?: unknown;
  idempotencyKey?: string;
  authenticated?: boolean;
}

async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const authenticated = options.authenticated ?? true;
  const headers: Record<string, string> = {};
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (options.idempotencyKey) {
    headers['Idempotency-Key'] = options.idempotencyKey;
  }
  if (authenticated && authToken) {
    headers['Authorization'] = `Bearer ${authToken}`;
  }

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new ApiError(0, 'Não foi possível conectar à API. Confira se o back-end está rodando na porta 8080.');
  }

  if (!response.ok) {
    if (response.status === 401 && authenticated) {
      unauthorizedHandler?.();
    }
    throw await toApiError(response);
  }
  return (await response.json()) as T;
}

export const api = {
  register(name: string, email: string, password: string): Promise<RegisteredUser> {
    return request('POST', '/users', { body: { name, email, password }, authenticated: false });
  },

  login(email: string, password: string): Promise<TokenResponse> {
    return request('POST', '/auth/login', { body: { email, password }, authenticated: false });
  },

  myWallet(): Promise<WalletInfo> {
    return request('GET', '/wallets/me');
  },

  deposit(walletId: string, amount: number, idempotencyKey: string): Promise<OperationResult> {
    return request('POST', `/wallets/${walletId}/deposit`, { body: { amount }, idempotencyKey });
  },

  withdraw(walletId: string, amount: number, idempotencyKey: string): Promise<OperationResult> {
    return request('POST', `/wallets/${walletId}/withdraw`, { body: { amount }, idempotencyKey });
  },

  transfer(
    walletId: string,
    targetWalletId: string,
    amount: number,
    idempotencyKey: string,
  ): Promise<OperationResult> {
    return request('POST', `/wallets/${walletId}/transfer`, {
      body: { targetWalletId, amount },
      idempotencyKey,
    });
  },

  statement(walletId: string, filters: StatementFilters): Promise<Page<StatementEntry>> {
    const params = new URLSearchParams();
    if (filters.type) params.set('type', filters.type);
    if (filters.from) params.set('from', filters.from);
    if (filters.to) params.set('to', filters.to);
    params.set('page', String(filters.page));
    params.set('size', String(filters.size));
    return request('GET', `/wallets/${walletId}/transactions?${params.toString()}`);
  },
};
