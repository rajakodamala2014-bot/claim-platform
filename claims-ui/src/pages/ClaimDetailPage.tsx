import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, type Claim, type InfoRequest } from '../api';
import { Card, ErrorBox, FileUploader, Loading, StatusBadge, useLoad } from '../components/ui';
import { formatDate, formatDateTime, formatMoney, humanize, timelineAmount } from '../format';

/** C5 detail, C6 timeline, C7/C8 information requests, C9 withdraw, C10 appeal. */
export function ClaimDetailPage() {
  const { claimId = '' } = useParams();
  const claim = useLoad(() => api.claim(claimId), [claimId]);
  const timeline = useLoad(() => api.timeline(claimId), [claimId]);
  const requests = useLoad(() => api.infoRequests(claimId), [claimId]);

  const refresh = () => {
    void claim.reload();
    void timeline.reload();
    void requests.reload();
  };

  if (claim.loading && !claim.data) return <Loading />;
  if (claim.error && !claim.data) {
    return (
      <>
        <Link to="/claims" className="back">← Claims</Link>
        <ErrorBox error={claim.error} />
      </>
    );
  }
  const c = claim.data!;
  const open = requests.data?.items.find((r) => r.requestId === c.actionNeeded?.requestId) ?? null;
  const answered = requests.data?.items.filter((r) => r.status !== 'OPEN') ?? [];

  return (
    <>
      <div className="page-head">
        <div>
          <Link to="/claims" className="back">← Claims</Link>
          <h1 className="claim-number">{c.claimNumber}</h1>
          <p className="muted">
            {humanize(c.lineOfBusiness)} · {c.policyNumber} · incident {formatDate(c.incidentAt)}
          </p>
        </div>
        <button className="btn btn-secondary" onClick={refresh}>Refresh</button>
      </div>

      <section className={`status-panel tone-${c.status === 'AWAITING_INFO' ? 'warn' : 'plain'}`}>
        <StatusBadge status={c.status} label={c.statusLabel} />
        <p className="next-step">{c.nextStep}</p>
        <p className="muted small">Last updated {formatDateTime(c.lastUpdatedAt)}</p>
      </section>

      {c.actionNeeded && <RespondCard claim={c} request={open} onDone={refresh} />}

      <div className="grid-main">
        <div className="stack">
          {(c.decision || c.payment) && <OutcomeCard claim={c} />}

          <Card title="Timeline">
            {timeline.loading && !timeline.data && <Loading />}
            <ErrorBox error={timeline.error} />
            <ol className="timeline">
              {[...(timeline.data ?? [])].reverse().map((t, i) => {
                const amount = timelineAmount(t.details);
                return (
                  <li key={i}>
                    <span className="dot" />
                    <div>
                      <strong>{t.title}</strong>
                      {amount && <span> · {amount}</span>}
                      {Array.isArray(t.details.items) && (
                        <div className="small muted">{(t.details.items as string[]).join(', ')}</div>
                      )}
                      {typeof t.details.reasonCode === 'string' && (
                        <div className="small muted">Reason: {humanize(t.details.reasonCode)}</div>
                      )}
                      <div className="small muted">{formatDateTime(t.at)}</div>
                    </div>
                  </li>
                );
              })}
            </ol>
          </Card>
        </div>

        <div className="stack">
          <Card title="Documents">
            {c.documents.length === 0 ? (
              <p className="muted">No documents yet.</p>
            ) : (
              <ul className="doc-list">
                {c.documents.map((d) => (
                  <li key={d.documentId}>
                    <span>{d.fileName}</span>
                    <span className="muted small">{formatDate(d.uploadedAt)}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>

          {answered.length > 0 && (
            <Card title="Earlier requests">
              <ul className="doc-list">
                {answered.map((r) => (
                  <li key={r.requestId}>
                    <span>{r.items.join(', ')}</span>
                    <span className="muted small">{humanize(r.status)} {r.answeredAt ? formatDate(r.answeredAt) : ''}</span>
                  </li>
                ))}
              </ul>
            </Card>
          )}

          {c.canAppeal && <ReasonAction claimId={c.claimId} kind="appeal" onDone={refresh} />}
          {c.canWithdraw && <ReasonAction claimId={c.claimId} kind="withdraw" onDone={refresh} />}
        </div>
      </div>
    </>
  );
}

function OutcomeCard({ claim }: { claim: Claim }) {
  const { decision, payment } = claim;
  return (
    <Card title="Outcome">
      <dl className="facts">
        {decision && (
          <>
            <dt>Decision</dt>
            <dd>
              <span className={`badge tone-${decision.outcome === 'APPROVED' ? 'good' : 'bad'}`}>{humanize(decision.outcome)}</span>{' '}
              <span className="muted small">{formatDate(decision.decidedAt)}</span>
            </dd>
            {decision.approvedAmount && (
              <>
                <dt>Approved amount</dt>
                <dd className="amount">{formatMoney(decision.approvedAmount)}</dd>
              </>
            )}
            {decision.reasonCode && (
              <>
                <dt>Reason</dt>
                <dd>{humanize(decision.reasonCode)}</dd>
              </>
            )}
          </>
        )}
        {payment && (
          <>
            <dt>Payment</dt>
            <dd>
              {payment.status === 'PAID' ? `Paid ${formatDate(payment.paidAt)}` : 'Processing'} · {formatMoney(payment.amount)}
            </dd>
            {payment.paymentRef && (
              <>
                <dt>Reference</dt>
                <dd><code>{payment.paymentRef}</code></dd>
              </>
            )}
          </>
        )}
      </dl>
    </Card>
  );
}

function RespondCard({ claim, request, onDone }: { claim: Claim; request: InfoRequest | null; onDone: () => void }) {
  const action = claim.actionNeeded!;
  const [answer, setAnswer] = useState('');
  const [uploadIds, setUploadIds] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api.respond(claim.claimId, action.requestId, answer.trim(), uploadIds);
      onDone();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card title="We need some information from you">
      <p>Please send us:</p>
      <ul className="needed">
        {action.items.map((item) => <li key={item}>{item}</li>)}
      </ul>
      <p className="muted small">
        Reply by {formatDate(action.dueAt)}
        {request ? ` · requested ${formatDate(request.requestedAt)}` : ''}
      </p>
      <form onSubmit={(e) => void submit(e)} className="stack">
        <label>
          <span>Your reply</span>
          <textarea rows={3} maxLength={4000} value={answer} onChange={(e) => setAnswer(e.target.value)} />
        </label>
        <FileUploader onChange={setUploadIds} />
        <ErrorBox error={error} />
        <div className="row end">
          <button className="btn btn-primary" disabled={busy || (!answer.trim() && uploadIds.length === 0)}>
            {busy ? 'Sending…' : 'Send'}
          </button>
        </div>
      </form>
    </Card>
  );
}

/** Withdraw (reason optional) or appeal (reason required), with an in-page confirmation step. */
function ReasonAction({ claimId, kind, onDone }: { claimId: string; kind: 'withdraw' | 'appeal'; onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const appeal = kind === 'appeal';

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      await (appeal ? api.appeal(claimId, reason.trim()) : api.withdraw(claimId, reason.trim()));
      setOpen(false);
      onDone();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card title={appeal ? 'Appeal this decision' : 'Withdraw claim'}>
      {!open ? (
        <>
          <p className="muted small">
            {appeal
              ? "If you have new information, we'll reopen the claim and assess it again."
              : "If you no longer want to claim, you can withdraw. This can't be undone."}
          </p>
          <button className={`btn ${appeal ? 'btn-primary' : 'btn-danger-outline'}`} onClick={() => setOpen(true)}>
            {appeal ? 'Start an appeal' : 'Withdraw claim'}
          </button>
        </>
      ) : (
        <div className="stack">
          <label>
            <span>{appeal ? 'Why should we look again?' : 'Reason (optional)'}</span>
            <textarea rows={3} maxLength={appeal ? 2000 : 1000} value={reason} onChange={(e) => setReason(e.target.value)} />
          </label>
          <ErrorBox error={error} />
          <div className="row end">
            <button className="btn btn-secondary" onClick={() => setOpen(false)} disabled={busy}>Cancel</button>
            <button
              className={`btn ${appeal ? 'btn-primary' : 'btn-danger'}`}
              disabled={busy || (appeal && !reason.trim())}
              onClick={() => void confirm()}
            >
              {busy ? 'Sending…' : appeal ? 'Submit appeal' : 'Yes, withdraw'}
            </button>
          </div>
        </div>
      )}
    </Card>
  );
}
