import type { Money } from './api';

/** ISO 4217 minor-unit digits, matching java.util.Currency on the server (Intl agrees for these markets). */
export function fractionDigits(currency: string): number {
  try {
    return new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits ?? 2;
  } catch {
    return 2;
  }
}

export function formatMinor(amountMinor: number, currency: string, locale?: string): string {
  const digits = fractionDigits(currency);
  const major = amountMinor / 10 ** digits;
  try {
    return new Intl.NumberFormat(locale, { style: 'currency', currency, minimumFractionDigits: digits }).format(major);
  } catch {
    return `${currency} ${major.toFixed(digits)}`;
  }
}

export function formatMoney(money: Money | null | undefined, locale?: string): string {
  return money ? formatMinor(money.amountMinor, money.currency, locale) : '—';
}

export function formatDate(iso: string | null | undefined, locale?: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString(locale, { day: 'numeric', month: 'short', year: 'numeric' });
}

export function formatDateTime(iso: string | null | undefined, locale?: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  return Number.isNaN(d.getTime())
    ? iso
    : d.toLocaleString(locale, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

/** "WATER_DAMAGE" → "Water damage" */
export function humanize(code: string): string {
  const words = code.toLowerCase().split('_').join(' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export type Tone = 'neutral' | 'info' | 'warn' | 'good' | 'bad';

export function statusTone(status: string): Tone {
  switch (status) {
    case 'AWAITING_INFO':
      return 'warn';
    case 'APPROVED':
    case 'SETTLEMENT_IN_PROGRESS':
    case 'SETTLED':
      return 'good';
    case 'REJECTED':
      return 'bad';
    case 'WITHDRAWN':
    case 'CLOSED':
      return 'neutral';
    default:
      return 'info';
  }
}

/** Money mentioned in a timeline entry's details (`amountMinor`, `approvedAmountMinor`, `totalPaidMinor`). */
export function timelineAmount(details: Record<string, unknown>, locale?: string): string | null {
  const currency = typeof details.currency === 'string' ? details.currency : null;
  const minor = ['approvedAmountMinor', 'totalPaidMinor', 'amountMinor']
    .map((k) => details[k])
    .find((v): v is number => typeof v === 'number');
  return currency && minor !== undefined ? formatMinor(minor, currency, locale) : null;
}

/** `<input type="datetime-local">` value → ISO instant, or null when empty/invalid. */
export function localInputToIso(value: string): string | null {
  if (!value) return null;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

export function nowAsLocalInput(): string {
  const d = new Date();
  d.setSeconds(0, 0);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}
