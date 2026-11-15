import { useEffect, useState } from 'react';
import { Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom';
import { api } from './api';
import { useAuth } from './auth';
import { LoginPage } from './pages/LoginPage';
import { ClaimsPage } from './pages/ClaimsPage';
import { NewClaimPage } from './pages/NewClaimPage';
import { ClaimDetailPage } from './pages/ClaimDetailPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { PreferencesPage } from './pages/PreferencesPage';

export function App() {
  const { user } = useAuth();
  if (!user) {
    return (
      <Routes>
        <Route path="*" element={<LoginPage />} />
      </Routes>
    );
  }
  return (
    <Shell>
      <Routes>
        <Route path="/" element={<Navigate to="/claims" replace />} />
        <Route path="/claims" element={<ClaimsPage />} />
        <Route path="/claims/new" element={<NewClaimPage />} />
        <Route path="/claims/:claimId" element={<ClaimDetailPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
        <Route path="/settings" element={<PreferencesPage />} />
        <Route path="*" element={<Navigate to="/claims" replace />} />
      </Routes>
    </Shell>
  );
}

function Shell({ children }: { children: React.ReactNode }) {
  const { user, logout } = useAuth();
  const unread = useUnreadCount();
  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar-inner">
          <NavLink to="/claims" className="brand">
            <img src="/favicon.svg" alt="" width={24} height={24} />
            <span>My Claims</span>
          </NavLink>
          <nav className="nav">
            <NavLink to="/claims" end>Claims</NavLink>
            <NavLink to="/notifications">
              Messages{unread > 0 && <span className="count">{unread}</span>}
            </NavLink>
            <NavLink to="/settings">Settings</NavLink>
          </nav>
          <div className="who">
            <span className="muted small">{user?.name}</span>
            <button className="link" onClick={logout}>Sign out</button>
          </div>
        </div>
      </header>
      <main className="content">{children}</main>
    </div>
  );
}

/** Unread message count, refreshed on navigation and every 15 s. */
function useUnreadCount(): number {
  const [count, setCount] = useState(0);
  const location = useLocation();
  useEffect(() => {
    let cancelled = false;
    const load = () =>
      api.notifications(true).then(
        (r) => !cancelled && setCount(r.items.length),
        () => {},
      );
    void load();
    const timer = setInterval(load, 15000);
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
  }, [location.pathname]);
  return count;
}
