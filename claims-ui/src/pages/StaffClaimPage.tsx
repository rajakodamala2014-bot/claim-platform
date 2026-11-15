import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { staffApi, type StaffClaim } from '../api';
import { isSupervisor, useAuth } from '../auth';
import { Card, ErrorBox, Loading, useLoad } from '../components/ui';
import { formatMoney } from '../format';

export function StaffClaimPage() {
  const { claimId = '' } = useParams();
  const { user } = useAuth();
  const supervisor = isSupervisor(user);
  const loaded = useLoad(() => staffApi.claim(claimId), [claimId]);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const claim = loaded.data?.claim;

  async function run(action: (claim: StaffClaim) => Promise<unknown>) {
    if (!claim) return;
    setBusy(true);
    setError(null);
    try {
      await action(claim);
      await loaded.reload();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  if (loaded.loading && !claim) return <Loading />;
  if (!claim) return <ErrorBox error={loaded.error} />;

  const currency = claim.reserve.outstanding.currency || claim.claimedAmount?.currency || 'SGD';
  return (
    <>
      <Link className="back" to="/work">← Work queue</Link>
      <div className="status-panel">
        <div className="row">
          <h1 className="claim-number">{claim.claimNumber}</h1>
          <span className="badge tone-info">{claim.status}</span>
          <span className="chip">{claim.market} · {claim.lineOfBusiness}</span>
        </div>
        <p className="muted">
          Policy {claim.policyNumber} · claimant {claim.claimantId} · cover {claim.coverStatus}
          {claim.handlerId ? ` · handler ${claim.handlerId}` : ' · unassigned'}
        </p>
        <p>Reserve {formatMoney(claim.reserve.outstanding)} · claimed {formatMoney(claim.claimedAmount)}</p>
        <p className="small muted">SLA {claim.sla.paused ? 'paused' : 'running'} · version {claim.version}</p>
      </div>
      <ErrorBox error={error ?? loaded.error} />

      {claim.handlerId == null && (claim.status === 'TRIAGED' || claim.status === 'SUBMITTED') && (
        <Card title="Take this claim">
          <button className="btn btn-primary" disabled={busy} onClick={() => void run((c) => staffApi.pickup(c.claimId, c.version))}>
            Pick up
          </button>
        </Card>
      )}

      {supervisor && claim.handlerId != null && (
        <AssignForm busy={busy} team={user?.team ?? claim.teamId ?? ''} onSubmit={(assigneeId, teamId) => void run((c) => staffApi.assign(c.claimId, c.version, assigneeId, teamId))} />
      )}

      {claim.status === 'ASSIGNED' && (
        <Card title="Assessment">
          <button className="btn btn-primary" disabled={busy} onClick={() => void run((c) => staffApi.startAssessment(c.claimId, c.version))}>
            Start assessment
          </button>
        </Card>
      )}

      {claim.status === 'UNDER_ASSESSMENT' && (
        <>
          <ReserveForm currency={currency} busy={busy} onSubmit={(amount, reason) => void run((c) => staffApi.setReserve(c.claimId, c.version, amount, currency, reason))} />
          <InfoForm busy={busy} onSubmit={(item) => void run((c) => staffApi.requestInfo(c.claimId, c.version, [item]))} />
          <AssessForm currency={currency} busy={busy} onSubmit={(liability, amount, notes) => void run((c) => staffApi.assess(c.claimId, c.version, liability, amount, currency, notes))} />
          <DecisionForm currency={currency} busy={busy} onSubmit={(outcome, amount, reason) => void run((c) => staffApi.decide(c.claimId, c.version, outcome, amount, currency, reason))} />
        </>
      )}

      {claim.status === 'DECISION_PENDING' && claim.decision && supervisor && claim.decision.proposedBy !== user?.sub && (
        <ReviewForm busy={busy} decisionId={claim.decision.decisionId} onSubmit={(action, comment) => void run((c) => staffApi.review(c.claimId, c.decision!.decisionId, c.version, action, comment))} />
      )}

      {claim.status === 'APPROVED' && (
        <SettleForm
          currency={claim.approvedAmount?.currency ?? currency}
          suggested={claim.approvedAmount?.amount ?? ''}
          busy={busy}
          onSubmit={(name, bank, account, amount) => void run((c) => staffApi.settle(c.claimId, c.version, name, bank, account, amount, c.approvedAmount?.currency ?? currency))}
        />
      )}

      {claim.settlement?.status === 'FAILED' && (
        <Card title="Payment failed">
          <p>{claim.settlement.failureReason}</p>
          <button
            className="btn btn-primary"
            disabled={busy}
            onClick={() => void run((c) => staffApi.retrySettlement(c.claimId, c.settlement!.settlementId, c.version, '', '', ''))}
          >
            Retry the same payee
          </button>
        </Card>
      )}

      {claim.decision && (
        <Card title="Decision">
          <p>{claim.decision.outcome} {formatMoney(claim.decision.amount)} · {claim.decision.status}</p>
          <p className="small muted">Proposed by {claim.decision.proposedBy}{claim.decision.reasonCode ? ` · ${claim.decision.reasonCode}` : ''}</p>
        </Card>
      )}
    </>
  );
}

function AssignForm({ busy, team, onSubmit }: { busy: boolean; team: string; onSubmit: (assigneeId: string, teamId: string) => void }) {
  const [assigneeId, setAssigneeId] = useState('');
  const [teamId, setTeamId] = useState(team);
  return (
    <Card title="Reassign">
      <form className="grid-2" onSubmit={(e) => { e.preventDefault(); onSubmit(assigneeId, teamId); }}>
        <label>Handler id<input value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)} required /></label>
        <label>Team id<input value={teamId} onChange={(e) => setTeamId(e.target.value)} required /></label>
        <button className="btn btn-secondary" disabled={busy}>Assign</button>
      </form>
    </Card>
  );
}

function ReserveForm({ currency, busy, onSubmit }: { currency: string; busy: boolean; onSubmit: (amount: string, reason: string) => void }) {
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('Initial reserve');
  return (
    <Card title={`Set reserve (${currency})`}>
      <form className="grid-2" onSubmit={(e) => { e.preventDefault(); onSubmit(amount, reason); }}>
        <label>Total amount<input value={amount} onChange={(e) => setAmount(e.target.value)} required /></label>
        <label>Reason<input value={reason} onChange={(e) => setReason(e.target.value)} required /></label>
        <button className="btn btn-secondary" disabled={busy}>Save reserve</button>
      </form>
    </Card>
  );
}

function InfoForm({ busy, onSubmit }: { busy: boolean; onSubmit: (item: string) => void }) {
  const [item, setItem] = useState('');
  return (
    <Card title="Ask the customer">
      <form className="row" onSubmit={(e) => { e.preventDefault(); onSubmit(item); }}>
        <label className="field" style={{ flex: 1 }}>What you need<input value={item} onChange={(e) => setItem(e.target.value)} required /></label>
        <button className="btn btn-secondary" disabled={busy}>Request</button>
      </form>
    </Card>
  );
}

function AssessForm({ currency, busy, onSubmit }: { currency: string; busy: boolean; onSubmit: (liability: string, amount: string, notes: string) => void }) {
  const [liability, setLiability] = useState('FULL');
  const [amount, setAmount] = useState('');
  const [notes, setNotes] = useState('');
  return (
    <Card title="Record assessment">
      <form className="grid-2" onSubmit={(e) => { e.preventDefault(); onSubmit(liability, amount, notes); }}>
        <label>Liability
          <select value={liability} onChange={(e) => setLiability(e.target.value)}>
            <option value="FULL">FULL</option>
            <option value="PARTIAL">PARTIAL</option>
            <option value="NONE">NONE</option>
          </select>
        </label>
        <label>Assessed amount ({currency})<input value={amount} onChange={(e) => setAmount(e.target.value)} required /></label>
        <label>Notes<input value={notes} onChange={(e) => setNotes(e.target.value)} /></label>
        <button className="btn btn-secondary" disabled={busy}>Save assessment</button>
      </form>
    </Card>
  );
}

function DecisionForm({ currency, busy, onSubmit }: { currency: string; busy: boolean; onSubmit: (outcome: 'APPROVE' | 'REJECT', amount: string, reason: string) => void }) {
  const [outcome, setOutcome] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  return (
    <Card title="Propose a decision">
      <form className="grid-2" onSubmit={(e) => { e.preventDefault(); onSubmit(outcome, amount, reason); }}>
        <label>Outcome
          <select value={outcome} onChange={(e) => setOutcome(e.target.value as 'APPROVE' | 'REJECT')}>
            <option value="APPROVE">APPROVE</option>
            <option value="REJECT">REJECT</option>
          </select>
        </label>
        <label>Amount ({currency})<input value={amount} onChange={(e) => setAmount(e.target.value)} required={outcome === 'APPROVE'} /></label>
        <label>Reason code<input value={reason} onChange={(e) => setReason(e.target.value)} required={outcome === 'REJECT'} /></label>
        <button className="btn btn-primary" disabled={busy}>Propose</button>
      </form>
    </Card>
  );
}

function ReviewForm({ busy, onSubmit }: { busy: boolean; decisionId: string; onSubmit: (action: 'APPROVE' | 'RETURN', comment: string) => void }) {
  const [comment, setComment] = useState('');
  return (
    <Card title="This decision is waiting for you">
      <label>Comment (required to send it back)<input value={comment} onChange={(e) => setComment(e.target.value)} /></label>
      <div className="row" style={{ marginTop: '.75rem' }}>
        <button className="btn btn-primary" disabled={busy} onClick={() => onSubmit('APPROVE', comment)}>Approve</button>
        <button className="btn btn-secondary" disabled={busy || !comment.trim()} onClick={() => onSubmit('RETURN', comment)}>Return</button>
      </div>
    </Card>
  );
}

function SettleForm({ currency, suggested, busy, onSubmit }: { currency: string; suggested: string; busy: boolean; onSubmit: (name: string, bank: string, account: string, amount: string) => void }) {
  const [name, setName] = useState('');
  const [bank, setBank] = useState('');
  const [account, setAccount] = useState('');
  const [amount, setAmount] = useState(suggested);
  return (
    <Card title={`Pay the customer (${currency})`}>
      <form className="grid-2" onSubmit={(e) => { e.preventDefault(); onSubmit(name, bank, account, amount); }}>
        <label>Payee name<input value={name} onChange={(e) => setName(e.target.value)} required /></label>
        <label>Bank code<input value={bank} onChange={(e) => setBank(e.target.value)} required /></label>
        <label>Account number<input value={account} onChange={(e) => setAccount(e.target.value)} required /></label>
        <label>Amount<input value={amount} onChange={(e) => setAmount(e.target.value)} required /></label>
        <button className="btn btn-primary" disabled={busy}>Instruct payment</button>
      </form>
    </Card>
  );
}
