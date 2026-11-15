import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, getToken, setUnauthorizedHandler, storeToken } from './api';

export interface User {
  sub: string;
  name: string;
  markets: string[];
  locale: string;
  roles: string[];
  team: string | null;
}

const STAFF_ROLES = ['HANDLER', 'TEAM_LEAD', 'MANAGER'];

export function isClaimant(user: User | null): boolean {
  return !!user?.roles.includes('CLAIMANT');
}

export function isStaff(user: User | null): boolean {
  return !!user?.roles.some((role) => STAFF_ROLES.includes(role));
}

/** Team leads and managers may approve decisions and open the team dashboards. */
export function isSupervisor(user: User | null): boolean {
  return !!user && (user.roles.includes('TEAM_LEAD') || user.roles.includes('MANAGER'));
}

interface AuthState {
  user: User | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

/** Reads the (unverified) JWT payload for display only; every service validates the token itself. */
export function decodeToken(token: string): User | null {
  try {
    const part = token.split('.')[1];
    const json = decodeURIComponent(
      atob(part.replace(/-/g, '+').replace(/_/g, '/'))
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join(''),
    );
    const claims = JSON.parse(json);
    if (typeof claims.exp === 'number' && claims.exp * 1000 < Date.now()) return null;
    return {
      sub: claims.sub,
      name: claims.name ?? claims.sub,
      markets: claims.markets ?? [],
      locale: claims.locale ?? 'en',
      roles: claims.roles ?? [],
      team: claims.team ?? null,
    };
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => {
    const token = getToken();
    return token ? decodeToken(token) : null;
  });

  const logout = useCallback(() => {
    storeToken(null);
    setUser(null);
  }, []);

  useEffect(() => setUnauthorizedHandler(logout), [logout]);

  const login = useCallback(async (username: string, password: string) => {
    const res = await api.login(username, password);
    if (res.role !== 'CLAIMANT' && !STAFF_ROLES.includes(res.role)) {
      throw new Error('This sign-in is not a customer or a member of staff.');
    }
    storeToken(res.access_token);
    setUser(decodeToken(res.access_token));
  }, []);

  const value = useMemo(() => ({ user, login, logout }), [user, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}
