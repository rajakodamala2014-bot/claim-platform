// Typed client for the claimant surface. All calls are same-origin (the gateway, or Vite's dev proxy),
// so there is no CORS and no API base URL to configure.

export interface Money {
  amount: string;
  amountMinor: number;
  currency: string;
}

export interface Policy {
  policyNumber: string;
  product: string;
  lineOfBusiness: 'MOTOR' | 'PROPERTY';
  market: string;
  coverageStart: string;
  coverageEnd: string;
  excess: Money | null;
  coveredPerils: string[];
}

export interface ClaimListItem {
  claimId: string;
  claimNumber: string;
  status: string;
  statusLabel: string;
  lineOfBusiness: string;
  policyNumber: string;
  submittedAt: string;
  lastUpdatedAt: string;
  actionNeeded: boolean;
}

export interface Page<T> {
  items: T[];
  nextCursor: string | null;
}

export interface Claim {
  claimId: string;
  claimNumber: string;
  status: string;
  statusLabel: string;
  nextStep: string;
  lineOfBusiness: string;
  policyNumber: string;
  incidentAt: string;
  submittedAt: string;
  lastUpdatedAt: string;
  actionNeeded: { requestId: string; items: string[]; dueAt: string } | null;
  decision: { outcome: 'APPROVED' | 'REJECTED'; approvedAmount: Money | null; reasonCode: string | null; decidedAt: string } | null;
  payment: { status: 'PROCESSING' | 'PAID'; amount: Money; paidAt: string | null; paymentRef: string | null } | null;
  documents: { documentId: string; fileName: string; contentType: string; uploadedAt: string }[];
  canWithdraw: boolean;
  canAppeal: boolean;
}

export interface TimelineEntry {
  at: string;
  type: string;
  title: string;
  details: Record<string, unknown>;
}

export interface InfoRequest {
  requestId: string;
  items: string[];
  dueAt: string;
  requestedAt: string;
  status: string;
  answeredAt: string | null;
}

export interface SubmitResult {
  claimId: string;
  claimNumber: string;
  status: string;
  coverStatus: string;
  replayed: boolean;
}

export interface InboxItem {
  id: string;
  claimId: string | null;
  claimNumber: string | null;
  type: string;
  title: string;
  message: string;
  at: string;
  read: boolean;
}

export interface ChannelSetting {
  enabled: boolean;
  address: string | null;
}

export interface Preferences {
  language: string;
  email: ChannelSetting;
  sms: ChannelSetting;
  push: ChannelSetting;
}

export interface SubmitClaimBody {
  policyNumber: string;
  lineOfBusiness: string;
  incidentAt: string;
  incident: Record<string, unknown>;
  claimedAmount?: { amount: string; currency: string };
  uploadIds: string[];
  contact?: { phone?: string; email?: string };
}

/** RFC 9457 problem+json as returned by every service; `code` is the stable contract. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly detail: string,
    readonly fieldErrors: { field: string; message: string }[] = [],
    readonly correlationId?: string,
  ) {
    super(detail);
  }
}

const TOKEN_KEY = 'claims-ui.token';
let onUnauthorized: () => void = () => {};

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

export function getToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function storeToken(token: string | null) {
  try {
    if (token) sessionStorage.setItem(TOKEN_KEY, token);
    else sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage unavailable: the session just won't survive a reload */
  }
}

async function toApiError(res: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {};
  try {
    body = await res.json();
  } catch {
    /* not problem+json */
  }
  const errors = Array.isArray(body.errors)
    ? (body.errors as Record<string, unknown>[]).map((e) => ({ field: String(e.field ?? ''), message: String(e.message ?? '') }))
    : [];
  return new ApiError(
    res.status,
    String(body.code ?? `HTTP_${res.status}`),
    String(body.detail ?? body.title ?? res.statusText ?? 'Request failed'),
    errors,
    (body.correlationId as string | undefined) ?? res.headers.get('X-Correlation-Id') ?? undefined,
  );
}

async function request<T>(method: string, path: string, body?: unknown, headers: Record<string, string> = {}): Promise<T> {
  const token = getToken();
  const res = await fetch(path, {
    method,
    headers: {
      Accept: 'application/json, application/problem+json',
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.status === 401) {
    onUnauthorized();
  }
  if (!res.ok) {
    throw await toApiError(res);
  }
  if (res.status === 204 || res.headers.get('Content-Length') === '0') {
    return undefined as T;
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/**
 * Pre-signed upload URLs are absolute (claims.documents.public-base-url). Keep only path + query so the PUT is
 * same-origin whether the app runs behind the gateway or on Vite's dev server.
 */
export function sameOriginUrl(url: string): string {
  try {
    const parsed = new URL(url);
    return parsed.pathname + parsed.search;
  } catch {
    return url;
  }
}

export const api = {
  login: (username: string) =>
    request<{ access_token: string; sub: string; role: string }>('POST', '/dev/token', { username }),

  policies: () => request<{ items: Policy[] }>('GET', '/claimant/v1/policies'),

  /** C2 then the direct PUT to storage. Returns the uploadId to attach to a claim or an information response. */
  async upload(file: File): Promise<string> {
    const contentType = file.type || 'application/octet-stream';
    const target = await request<{ uploadId: string; uploadUrl: string; method: string; headers: Record<string, string> }>(
      'POST',
      '/claimant/v1/uploads',
      { fileName: file.name, contentType, sizeBytes: file.size },
    );
    const res = await fetch(sameOriginUrl(target.uploadUrl), {
      method: target.method || 'PUT',
      headers: { 'Content-Type': contentType, ...target.headers },
      body: file,
    });
    if (!res.ok) throw await toApiError(res);
    return target.uploadId;
  },

  submitClaim: (idempotencyKey: string, body: SubmitClaimBody) =>
    request<SubmitResult>('POST', '/claimant/v1/claims', body, { 'Idempotency-Key': idempotencyKey }),

  claims: (cursor?: string | null) =>
    request<Page<ClaimListItem>>('GET', `/claimant/v1/claims${cursor ? `?cursor=${encodeURIComponent(cursor)}` : ''}`),

  claim: (id: string) => request<Claim>('GET', `/claimant/v1/claims/${id}`),

  timeline: (id: string) => request<TimelineEntry[]>('GET', `/claimant/v1/claims/${id}/timeline`),

  infoRequests: (id: string) => request<{ items: InfoRequest[] }>('GET', `/claimant/v1/claims/${id}/info-requests`),

  respond: (id: string, requestId: string, answer: string, uploadIds: string[]) =>
    request<{ status: string }>('POST', `/claimant/v1/claims/${id}/info-requests/${requestId}/responses`, { answer, uploadIds }),

  withdraw: (id: string, reason: string) =>
    request<{ status: string }>('POST', `/claimant/v1/claims/${id}/withdraw`, { reason: reason || null }),

  appeal: (id: string, reason: string) => request<{ status: string }>('POST', `/claimant/v1/claims/${id}/appeal`, { reason }),

  notifications: (unreadOnly = false) =>
    request<{ items: InboxItem[] }>('GET', `/claimant/v1/notifications${unreadOnly ? '?unreadOnly=true' : ''}`),

  markRead: (id: string) => request<void>('POST', `/claimant/v1/notifications/${id}/read`),

  preferences: () => request<Preferences>('GET', '/claimant/v1/notification-preferences'),

  savePreferences: (p: Preferences) => request<Preferences>('PUT', '/claimant/v1/notification-preferences', p),
};
