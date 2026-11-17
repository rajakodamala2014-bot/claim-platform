import { describe, expect, it } from 'vitest';
import { decodeToken, isClaimant, isStaff, isSupervisor, type User } from './auth';

function token(claims: Record<string, unknown>): string {
  const payload = btoa(JSON.stringify(claims)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
  return `header.${payload}.signature`;
}

const later = Math.floor(Date.now() / 1000) + 3600;

function user(roles: string[]): User {
  return { sub: 'id', name: 'Name', markets: ['SG'], locale: 'en', roles, team: null };
}

describe('decodeToken', () => {
  it('reads the role, markets and team the portal uses to choose a screen', () => {
    const decoded = decodeToken(token({
      sub: 'h-arun', name: 'Arun Kumar', roles: ['CLAIMS_ADJUSTER'], markets: ['SG'], team: 'team-sg-motor', locale: 'en-SG', exp: later,
    }));

    expect(decoded).toMatchObject({ sub: 'h-arun', roles: ['CLAIMS_ADJUSTER'], markets: ['SG'], team: 'team-sg-motor' });
    expect(isStaff(decoded)).toBe(true);
    expect(isClaimant(decoded)).toBe(false);
    expect(isSupervisor(decoded)).toBe(true);
  });

  it('treats an expired token as signed out', () => {
    expect(decodeToken(token({ sub: 'cust-priya', roles: ['CUSTOMER'], exp: 1 }))).toBeNull();
    expect(isClaimant(user(['CUSTOMER']))).toBe(true);
    expect(isStaff(user(['CUSTOMER']))).toBe(false);
    expect(isSupervisor(user(['CUSTOMER']))).toBe(false);
    expect(isSupervisor(user(['CLAIMS_ADJUSTER']))).toBe(true);
  });
});
