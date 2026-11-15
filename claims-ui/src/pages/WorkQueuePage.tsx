import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { staffApi, type QueueItem } from '../api';
import { isSupervisor, useAuth } from '../auth';
import { ErrorBox, Loading, useLoad } from '../components/ui';
import { formatDateTime, formatMoney } from '../format';

export function WorkQueuePage() {
  const { user } = useAuth();
  const supervisor = isSupervisor(user);
  const [view, setView] = useState(supervisor ? 'unassigned' : 'unassigned');
  const [query, setQuery] = useState('');
  const [searching, setSearching] = useState(false);
  const [found, setFound] = useState<QueueItem[] | null>(null);
  const [searchError, setSearchError] = useState<unknown>(null);
  const queue = useLoad(() => staffApi.queue(view), [view]);

  async function search(event: FormEvent) {
    event.preventDefault();
    if (!query.trim()) {
      setFound(null);
      return;
    }
    setSearching(true);
    setSearchError(null);
    try {
      setFound((await staffApi.search(query.trim())).items);
    } catch (e) {
      setSearchError(e);
    } finally {
      setSearching(false);
    }
  }

  const items = found ?? queue.data?.items ?? [];
  return (
    <>
      <div className="page-head">
        <div>
          <h1>Work queue</h1>
          <p className="muted">Claims in your markets, oldest deadline first.</p>
        </div>
      </div>
      <div className="row">
        {['unassigned', 'mine', ...(supervisor ? ['team'] : [])].map((option) => (
          <button
            key={option}
            className={view === option && !found ? 'btn btn-primary' : 'btn btn-secondary'}
            onClick={() => {
              setFound(null);
              setView(option);
            }}
          >
            {option}
          </button>
        ))}
      </div>
      <form className="row" onSubmit={(e) => void search(e)}>
        <label className="field" style={{ flex: 1 }}>
          Find a claim or policy number
          <input value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
        <button className="btn btn-secondary" disabled={searching}>Search</button>
      </form>
      <ErrorBox error={searchError ?? queue.error} />
      {queue.loading && !found ? <Loading /> : items.length === 0 ? (
        <div className="empty">Nothing in this list.</div>
      ) : (
        <ul className="claim-list">
          {items.map((item) => (
            <li key={item.claimId}>
              <Link className="claim-row" to={`/work/${item.claimId}`}>
                <span className="claim-main">
                  <span className="claim-number">{item.claimNumber}</span>
                  <span className="muted small">{item.market} · {item.lineOfBusiness} · {item.policyNumber}</span>
                </span>
                <span className="claim-side">
                  <span className="badge tone-info">{item.status}</span>
                  <span className="chip">{item.slaState}</span>
                  <span className="small muted">{formatMoney(item.reserve)} · due {formatDateTime(item.slaDueAt)}</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
