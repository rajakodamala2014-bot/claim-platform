import { useEffect, useState } from 'react';
import { Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom';
import { api } from './api';
import { isClaimant, isStaff, isSupervisor, useAuth } from './auth';
import { LoginPage } from './pages/LoginPage';
import { ClaimsPage } from './pages/ClaimsPage';
import { NewClaimPage } from './pages/NewClaimPage';
import { ClaimDetailPage } from './pages/ClaimDetailPage';
import { NotificationsPage } from './pages/NotificationsPage';
import { PreferencesPage } from './pages/PreferencesPage';
import { WorkQueuePage } from './pages/WorkQueuePage';
import { StaffClaimPage } from './pages/StaffClaimPage';
import { ApprovalsPage } from './pages/ApprovalsPage';
import { InsightsPage } from './pages/InsightsPage';

export function App() {
  const { user } = useAuth();
  if (!user) {
    return (
      <Routes>
        <Route path="*" element={<LoginPage />} />
      </Routes>
    );
  }
  if (isClaimant(user)) {
    return (
      <Shell home="/claims" brand="My Claims">
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
  if (isStaff(user)) {
    const supervisor = isSupervisor(user);
    return (
      <Shell home="/work" brand="Claims desk" messages={false}>
        <Routes>
          <Route path="/" element={<Navigate to="/work" replace />} />
          <Route path="/work" element={<WorkQueuePage />} />
          <Route path="/work/:claimId" element={<StaffClaimPage />} />
          {supervisor && <Route path="/approvals" element={<ApprovalsPage />} />}
          {supervisor && <Route path="/insights" element={<InsightsPage />} />}
          <Route path="*" element={<Navigate to="/work" replace />} />
        </Routes>
      </Shell>
    );
  }
  return (
    <Routes>
      <Route path="*" element={<LoginPage />} />
    </Routes>
  );
}

function Shell({ children, home, brand, messages = true }: { children: React.ReactNode; home: string; brand: string; messages?: boolean }) {
  const { user, logout } = useAuth();
  const supervisor = isSupervisor(user);
  const unread = useUnreadCount(messages);
  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar-inner">
          <NavLink to={home} className="brand">
            <img src="/favicon.svg" alt="" width={24} height={24} />
            <span>{brand}</span>
          </NavLink>
          <nav className="nav">
            {messages ? (
              <>
                <NavLink to="/claims" end>Claims</NavLink>
                <NavLink to="/notifications">
                  Messages{unread > 0 && <span className="count">{unread}</span>}
                </NavLink>
                <NavLink to="/settings">Settings</NavLink>
              </>
            ) : (
              <>
                <NavLink to="/work" end>Queue</NavLink>
                {supervisor && <NavLink to="/approvals">Approvals</NavLink>}
                {supervisor && <NavLink to="/insights">Insights</NavLink>}
              </>
            )}
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

/** Unread message count, refreshed on navigation and every 15 s. Customers only. */
function useUnreadCount(enabled: boolean): number {
  const [count, setCount] = useState(0);
  const location = useLocation();
  useEffect(() => {
    if (!enabled) return;
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
  }, [location.pathname, enabled]);
  return count;
}
