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
  login: (username: string, password: string) =>
    request<{ access_token: string; sub: string; role: string }>('POST', '/auth/login', { username, password }),

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

export interface QueueItem {
  claimId: string;
  claimNumber: string;
  market: string;
  lineOfBusiness: string;
  policyNumber: string;
  status: string;
  coverDeclined: boolean;
  handlerId: string | null;
  teamId: string | null;
  submittedAt: string;
  slaDueAt: string;
  slaState: string;
  reserve: Money;
}

export interface ApprovalItem {
  claimId: string;
  claimNumber: string;
  market: string;
  lineOfBusiness: string;
  decisionId: string;
  outcome: 'APPROVE' | 'REJECT';
  amount: Money;
  proposedBy: string;
  proposedAt: string;
  teamId: string;
}

export interface WorkloadItem {
  handlerId: string;
  openClaims: number;
  byStatus: Record<string, number>;
  oldestSubmittedAt: string;
}

export interface PerformanceItem {
  handlerId: string;
  closedClaims: number;
  averageCycleHours: number;
  slaMetPercent: number | null;
  reopenRatePercent: number;
}

export interface ExposureItem {
  market: string;
  lineOfBusiness: string;
  outstandingClaims: number;
  totalReserve: Money;
}

export interface StaffClaim {
  claimId: string;
  claimNumber: string;
  market: string;
  lineOfBusiness: string;
  policyNumber: string;
  claimantId: string;
  status: string;
  coverStatus: string;
  handlerId: string | null;
  teamId: string | null;
  submittedAt: string;
  claimedAmount: Money | null;
  sla: { dueAt: string; paused: boolean };
  reserve: { outstanding: Money };
  assessment: { liability: string; assessedAmount: Money; notes: string | null } | null;
  decision: {
    decisionId: string;
    outcome: string;
    amount: Money;
    reasonCode: string | null;
    proposedBy: string;
    approvalRequired: boolean;
    status: string;
  } | null;
  approvedAmount: Money | null;
  settlement: {
    settlementId: string;
    amount: Money;
    payeeName: string;
    status: string;
    failureReason: string | null;
    accountNumberMasked: string;
  } | null;
  version: number;
}

export interface CommandResult<T = unknown> {
  claimId: string;
  status: string;
  version: number;
  result: T;
}

function match(version: number): Record<string, string> {
  return { 'If-Match': `"${version}"` };
}

/** Staff and supervisor calls. The gateway allows these only for HANDLER, TEAM_LEAD and MANAGER. */
export const staffApi = {
  queue: (view: string, cursor?: string | null) =>
    request<{ items: QueueItem[]; nextCursor: string | null }>(
      'GET',
      `/staff/v1/work-queue?view=${encodeURIComponent(view)}${cursor ? `&cursor=${encodeURIComponent(cursor)}` : ''}`,
    ),
  search: (q: string) => request<{ items: QueueItem[] }>('GET', `/staff/v1/search?q=${encodeURIComponent(q)}`),
  approvals: () => request<{ items: ApprovalItem[] }>('GET', '/staff/v1/approvals'),
  workload: () => request<{ items: WorkloadItem[] }>('GET', '/staff/v1/insights/workload'),
  performance: () => request<{ items: PerformanceItem[] }>('GET', '/staff/v1/insights/performance'),
  sla: () => request<{ items: QueueItem[] }>('GET', '/staff/v1/insights/sla'),
  exposure: () => request<{ items: ExposureItem[] }>('GET', '/staff/v1/insights/exposure'),
  claim: (id: string) => request<{ claim: StaffClaim }>('GET', `/staff/v1/claims/${id}`),
  pickup: (id: string, version: number) =>
    request<CommandResult<string>>('POST', `/staff/v1/claims/${id}/assignment`, {}, match(version)),
  assign: (id: string, version: number, assigneeId: string, teamId: string) =>
    request<CommandResult<string>>('POST', `/staff/v1/claims/${id}/assignment`, { assigneeId, teamId }, match(version)),
  startAssessment: (id: string, version: number) =>
    request<CommandResult>('POST', `/staff/v1/claims/${id}/start-assessment`, undefined, match(version)),
  setReserve: (id: string, version: number, amount: string, currency: string, reason: string) =>
    request<CommandResult>('POST', `/staff/v1/claims/${id}/reserves`, { amount: { amount, currency }, reason }, match(version)),
  requestInfo: (id: string, version: number, items: string[]) =>
    request<CommandResult>('POST', `/staff/v1/claims/${id}/info-requests`, { items }, match(version)),
  assess: (id: string, version: number, liability: string, amount: string, currency: string, notes: string) =>
    request<CommandResult>(
      'POST',
      `/staff/v1/claims/${id}/assessments`,
      { liability, assessedAmount: { amount, currency }, notes },
      match(version),
    ),
  decide: (id: string, version: number, outcome: 'APPROVE' | 'REJECT', amount: string, currency: string, reasonCode: string) =>
    request<CommandResult>('POST', `/staff/v1/claims/${id}/decisions`, {
      outcome,
      amount: amount ? { amount, currency } : null,
      reasonCode: reasonCode || null,
    }, match(version)),
  review: (id: string, decisionId: string, version: number, action: 'APPROVE' | 'RETURN', comment: string) =>
    request<CommandResult>(
      'POST',
      `/staff/v1/claims/${id}/decisions/${decisionId}/approval`,
      { action, comment: comment || null },
      match(version),
    ),
  settle: (id: string, version: number, name: string, bankCode: string, accountNumber: string, amount: string, currency: string) =>
    request<CommandResult>('POST', `/staff/v1/claims/${id}/settlements`, {
      payee: { name, bankCode, accountNumber },
      amount: { amount, currency },
    }, match(version)),
  retrySettlement: (id: string, settlementId: string, version: number, name: string, bankCode: string, accountNumber: string) =>
    request<CommandResult>(
      'POST',
      `/staff/v1/claims/${id}/settlements/${settlementId}/retry`,
      name ? { payee: { name, bankCode, accountNumber } } : {},
      match(version),
    ),
};
