import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, getToken, setUnauthorizedHandler, storeToken } from './api';

export interface User {
  sub: string;
  name: string;
  markets: string[];
  locale: string;
  roles: string[];
}

interface AuthState {
  user: User | null;
  login: (username: string) => Promise<void>;
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

  const login = useCallback(async (username: string) => {
    const res = await api.login(username);
    if (res.role !== 'CLAIMANT') {
      throw new Error('This portal is for customers. Staff use the staff workbench.');
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
