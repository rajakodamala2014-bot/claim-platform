import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { staffApi, type QueueItem } from '../api';
import { isSupervisor, useAuth } from '../auth';
import { ErrorBox, Loading, useLoad } from '../components/ui';
import { formatDateTime, humanize } from '../format';

const OPEN_VIEWS = [
  { id: 'unassigned', label: 'Unassigned' },
  { id: 'mine', label: 'Mine' },
  { id: 'team', label: 'Team' },
] as const;

const PROCESSED = [
  { id: 'paid', title: 'Paid' },
  { id: 'rejected', title: 'Rejected' },
] as const;

export function WorkQueuePage() {
  const { user } = useAuth();
  const supervisor = isSupervisor(user);
  const [view, setView] = useState('unassigned');
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

  const paid = useLoad(() => staffApi.queue('paid'), []);
  const rejected = useLoad(() => staffApi.queue('rejected'), []);
  const processed = { paid, rejected };
  const items = found ?? queue.data?.items ?? [];
  const showProcessed = view === 'mine' && !found;
  const openViews = OPEN_VIEWS.filter((option) => option.id !== 'team' || supervisor);
  return (
    <>
      <div className="page-head">
        <div>
          <h1>Work queue</h1>
          <p className="muted">Open claims stay in this list. Your paid and rejected claims are under Mine.</p>
        </div>
      </div>
      <div className="row">
        {openViews.map((option) => (
          <button
            key={option.id}
            className={view === option.id && !found ? 'btn btn-primary' : 'btn btn-secondary'}
            onClick={() => {
              setFound(null);
              setView(option.id);
            }}
          >
            {option.label}
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
                  <span className="small muted">Due {formatDateTime(item.slaDueAt)}</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
      {showProcessed && <div className="stack" style={{ marginTop: '1.5rem' }}>
        {PROCESSED.map((section) => {
          const loaded = processed[section.id];
          const rows = loaded.data?.items ?? [];
          return (
            <section key={section.id}>
              <h2>{section.title}</h2>
              {loaded.loading ? <Loading /> : loaded.error ? <ErrorBox error={loaded.error} /> : rows.length === 0 ? (
                <p className="muted">None yet.</p>
              ) : (
                <ul className="claim-list">
                  {rows.map((item) => (
                    <li key={item.claimId}>
                      <Link className="claim-row" to={`/work/${item.claimId}`}>
                        <span className="claim-main">
                          <span className="claim-number">{item.claimNumber}</span>
                          <span className="muted small">{item.market} · {item.lineOfBusiness} · {item.policyNumber}</span>
                        </span>
                        <span className="badge tone-info">{humanize(item.status)}</span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          );
        })}
      </div>}
    </>
  );
}
