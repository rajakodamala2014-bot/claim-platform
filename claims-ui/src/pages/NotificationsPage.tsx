import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { ErrorBox, Loading, useLoad } from '../components/ui';
import { formatDateTime } from '../format';

/** N1 inbox, N2 mark as read. */
export function NotificationsPage() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const inbox = useLoad(() => api.notifications(unreadOnly), [unreadOnly]);
  const [error, setError] = useState<unknown>(null);

  async function markRead(id: string) {
    try {
      await api.markRead(id);
      inbox.setData((d) => d && { items: d.items.map((n) => (n.id === id ? { ...n, read: true } : n)) });
    } catch (e) {
      setError(e);
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Messages</h1>
          <p className="muted">Updates about your claims.</p>
        </div>
        <div className="row">
          <label className="toggle">
            <input type="checkbox" checked={unreadOnly} onChange={(e) => setUnreadOnly(e.target.checked)} /> Unread only
          </label>
          <button className="btn btn-secondary" onClick={() => void inbox.reload()}>Refresh</button>
        </div>
      </div>
      <ErrorBox error={inbox.error ?? error} />
      {inbox.loading && !inbox.data && <Loading />}
      {inbox.data && inbox.data.items.length === 0 && <div className="empty"><p>No messages.</p></div>}
      <ul className="inbox">
        {inbox.data?.items.map((n) => (
          <li key={n.id} className={n.read ? 'read' : 'unread'}>
            <div className="inbox-head">
              <strong>{n.title}</strong>
              <span className="muted small">{formatDateTime(n.at)}</span>
            </div>
            <p>{n.message}</p>
            <div className="row">
              {n.claimId && <Link to={`/claims/${n.claimId}`} className="link">{n.claimNumber ?? 'View claim'}</Link>}
              {!n.read && (
                <button className="link" onClick={() => void markRead(n.id)}>Mark as read</button>
              )}
            </div>
          </li>
        ))}
      </ul>
    </>
  );
}
