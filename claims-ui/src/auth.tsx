import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ApiError, api, getToken, setUnauthorizedHandler, storeToken } from './api';

export interface User {
  sub: string;
  name: string;
  markets: string[];
  locale: string;
  roles: string[];
  team: string | null;
}

const CUSTOMER = 'CUSTOMER';
const ADJUSTER = 'CLAIMS_ADJUSTER';

export function isClaimant(user: User | null): boolean {
  return !!user?.roles.includes(CUSTOMER);
}

export function isStaff(user: User | null): boolean {
  return !!user?.roles.includes(ADJUSTER);
}

/** Every claims adjuster may approve decisions and open the team screens. */
export function isSupervisor(user: User | null): boolean {
  return isStaff(user);
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
    let last: unknown;
    for (let attempt = 0; attempt < 6; attempt++) {
      try {
        const res = await api.login(username, password);
        if (res.role !== CUSTOMER && res.role !== ADJUSTER) {
          throw new Error('This sign-in is not a customer or a claim manager.');
        }
        storeToken(res.access_token);
        const next = decodeToken(res.access_token);
        if (!next) throw new Error('Sign-in did not return a usable session.');
        setUser(next);
        return;
      } catch (error) {
        last = error;
        const starting = error instanceof ApiError && (error.status === 502 || error.status === 503);
        if (!starting || attempt === 5) break;
        await new Promise((resolve) => setTimeout(resolve, 1500));
      }
    }
    throw last;
  }, []);

  const value = useMemo(() => ({ user, login, logout }), [user, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth outside AuthProvider');
  return ctx;
}
