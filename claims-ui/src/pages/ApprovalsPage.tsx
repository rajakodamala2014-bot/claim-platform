import { Link } from 'react-router-dom';
import { staffApi } from '../api';
import { ErrorBox, Loading, useLoad } from '../components/ui';
import { formatDateTime, formatMoney } from '../format';

export function ApprovalsPage() {
  const loaded = useLoad(() => staffApi.approvals(), []);
  if (loaded.loading) return <Loading />;
  if (loaded.error) return <ErrorBox error={loaded.error} />;
  const items = loaded.data?.items ?? [];
  return (
    <>
      <h1>Approvals</h1>
      <p className="muted">Decisions above a handler’s limit. Your own proposals are left out.</p>
      {items.length === 0 ? <div className="empty">Nothing is waiting for you.</div> : (
        <ul className="claim-list">
          {items.map((item) => (
            <li key={item.decisionId}>
              <Link className="claim-row" to={`/work/${item.claimId}`}>
                <span className="claim-main">
                  <span className="claim-number">{item.claimNumber}</span>
                  <span className="muted small">{item.outcome} · {formatMoney(item.amount)} · {item.proposedBy}</span>
                </span>
                <span className="small muted">{formatDateTime(item.proposedAt)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
