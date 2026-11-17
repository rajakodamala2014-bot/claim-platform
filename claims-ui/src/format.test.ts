import { describe, expect, it } from 'vitest';
import { sameOriginUrl } from './api';
import { decodeToken } from './auth';
import { formatMinor, humanize, localInputToIso, statusTone, timelineAmount } from './format';

describe('format', () => {
  it('formats minor units with the currency digits', () => {
    expect(formatMinor(600000, 'SGD', 'en-SG')).toContain('6,000.00');
    expect(formatMinor(1050, 'THB', 'en')).toContain('10.50');
  });

  it('humanizes codes', () => {
    expect(humanize('WATER_DAMAGE')).toBe('Water damage');
    expect(humanize('MOTOR')).toBe('Motor');
  });

  it('maps statuses to tones', () => {
    expect(statusTone('AWAITING_INFO')).toBe('warn');
    expect(statusTone('SETTLED')).toBe('good');
    expect(statusTone('REJECTED')).toBe('bad');
    expect(statusTone('SUBMITTED')).toBe('info');
  });

  it('finds the amount in timeline details', () => {
    expect(timelineAmount({ approvedAmountMinor: 1200000, currency: 'SGD' }, 'en-SG')).toContain('12,000.00');
    expect(timelineAmount({ claimNumber: 'X' })).toBeNull();
  });

  it('converts datetime-local input to an ISO instant', () => {
    expect(localInputToIso('')).toBeNull();
    expect(localInputToIso('2026-11-10T09:00')).toMatch(/^2026-11-\d\dT\d\d:\d\d:00\.000Z$/);
  });
});

describe('api helpers', () => {
  it('keeps pre-signed upload URLs same-origin', () => {
    expect(sameOriginUrl('http://localhost:8000/claimant/v1/uploads/abc/content?expires=1&signature=x'))
      .toBe('/claimant/v1/uploads/abc/content?expires=1&signature=x');
    expect(sameOriginUrl('/already/relative')).toBe('/already/relative');
  });
});

describe('auth', () => {
  const encode = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');

  it('reads display claims from the JWT payload', () => {
    const token = `x.${encode({ sub: 'cust-priya', name: 'Priya Nair', roles: ['CUSTOMER'], markets: ['SG'], exp: 4102444800 })}.y`;
    expect(decodeToken(token)).toMatchObject({ sub: 'cust-priya', name: 'Priya Nair', markets: ['SG'] });
  });

  it('treats an expired token as signed out', () => {
    expect(decodeToken(`x.${encode({ sub: 'a', exp: 1 })}.y`)).toBeNull();
  });
});
