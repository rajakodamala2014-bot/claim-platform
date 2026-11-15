import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type ClaimListItem } from '../api';
import { useAuth } from '../auth';
import { ErrorBox, Loading, StatusBadge, useLoad } from '../components/ui';
import { formatDate, humanize } from '../format';

/** C4: my claims, newest first, cursor-paginated. */
export function ClaimsPage() {
  const { user } = useAuth();
  const first = useLoad(() => api.claims(), []);
  const [more, setMore] = useState<ClaimListItem[]>([]);
  const [cursor, setCursor] = useState<string | null | undefined>(undefined);
  const [loadingMore, setLoadingMore] = useState(false);
  const [moreError, setMoreError] = useState<unknown>(null);

  const nextCursor = cursor === undefined ? first.data?.nextCursor : cursor;
  const items = [...(first.data?.items ?? []), ...more];

  async function loadMore() {
    if (!nextCursor) return;
    setLoadingMore(true);
    try {
      const page = await api.claims(nextCursor);
      setMore((m) => [...m, ...page.items]);
      setCursor(page.nextCursor);
      setMoreError(null);
    } catch (e) {
      setMoreError(e);
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Your claims</h1>
          <p className="muted">Hello {user?.name.split(' ')[0]}. Here is where every claim you've made stands.</p>
        </div>
        <Link to="/claims/new" className="btn btn-primary">Report an incident</Link>
      </div>

      {first.loading && !first.data && <Loading />}
      <ErrorBox error={first.error} />

      {first.data && items.length === 0 && (
        <div className="empty">
          <p>You haven't made any claims yet.</p>
          <Link to="/claims/new" className="btn btn-primary">Report an incident</Link>
        </div>
      )}

      {items.length > 0 && (
        <ul className="claim-list">
          {items.map((c) => (
            <li key={c.claimId}>
              <Link to={`/claims/${c.claimId}`} className="claim-row">
                <div className="claim-main">
                  <span className="claim-number">{c.claimNumber}</span>
                  <span className="muted small">
                    {humanize(c.lineOfBusiness)} · {c.policyNumber} · submitted {formatDate(c.submittedAt)}
                  </span>
                </div>
                <div className="claim-side">
                  {c.actionNeeded && <span className="badge tone-warn">Action needed</span>}
                  <StatusBadge status={c.status} label={c.statusLabel} />
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}

      <ErrorBox error={moreError} />
      {nextCursor && (
        <div className="center">
          <button className="btn btn-secondary" disabled={loadingMore} onClick={() => void loadMore()}>
            {loadingMore ? 'Loading…' : 'Show more'}
          </button>
        </div>
      )}
    </>
  );
}
