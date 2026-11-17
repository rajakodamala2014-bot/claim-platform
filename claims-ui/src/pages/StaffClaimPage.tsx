import { Fragment, useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router-dom';
import { staffApi, type PolicySnapshot, type StaffClaim, type StaffIncident } from '../api';
import { isSupervisor, useAuth } from '../auth';
import { Card, ErrorBox, Loading, useLoad } from '../components/ui';
import { formatDate, formatDateTime, formatMinor, formatMoney, humanize } from '../format';
import { personName, usePeople } from '../people';

export function StaffClaimPage() {
  const { claimId = '' } = useParams();
  const { user } = useAuth();
  const supervisor = isSupervisor(user);
  const people = usePeople();
  const loaded = useLoad(() => staffApi.claim(claimId), [claimId]);
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const workspace = loaded.data;
  const claim = workspace?.claim;

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
  if (!claim || !workspace) return <ErrorBox error={loaded.error} />;

  const currency = claim.claimedAmount?.currency || claim.approvedAmount?.currency || 'SGD';
  const waitingForApproval = claim.decision?.status === 'AWAITING_APPROVAL';
  return (
    <>
      <Link className="back" to="/work">← Work queue</Link>
      <div className="status-panel">
        <div className="row">
          <h1 className="claim-number">{claim.claimNumber}</h1>
          <span className="badge tone-info">{humanize(claim.status)}</span>
          <span className="chip">{claim.market} · {humanize(claim.lineOfBusiness)}</span>
        </div>
        <p className="muted">
          Policy {claim.policyNumber} · {personName(people, claim.claimantId)} · cover {humanize(claim.coverStatus)}
          {claim.handlerId ? ` · assigned to ${personName(people, claim.handlerId)}` : ' · unassigned'}
        </p>
        {claim.status === 'REJECTED' && (
          <p className="next-step">This claim is rejected.</p>
        )}
        {waitingForApproval && claim.status !== 'REJECTED' && (
          <p className="next-step">Waiting for another claim manager to approve this decision.</p>
        )}
      </div>

      <div className="summary-grid">
        <Summary label="Claimed" value={formatMoney(claim.claimedAmount)} />
        <Summary label="Covered" value={formatMoney(claim.assessment?.assessedAmount)} />
        <Summary label="Approved" value={formatMoney(claim.approvedAmount)} />
        <Summary label="SLA" value={claim.sla.paused ? 'Paused' : `Due ${formatDateTime(claim.sla.dueAt)}`} />
      </div>

      <ErrorBox error={error ?? loaded.error} />

      <div className="grid-main">
        <div className="stack">
          <IncidentCard incident={workspace.incident} currency={currency} />
          <Card title="People">
            {workspace.parties.length === 0 ? <p className="muted">No people recorded.</p> : (
              <ul className="doc-list">
                {workspace.parties.map((party) => (
                  <li key={party.role + party.name}>
                    <span><strong>{humanize(party.role)}</strong> · {party.name}</span>
                    <span className="muted small">{[party.phone, party.email, party.vehicleRegistration, party.insurer].filter(Boolean).join(' · ') || '—'}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          <PolicyCard policy={workspace.policy} />
          <Card title="Documents">
            {(claim.documents ?? []).length === 0 ? <p className="muted">No documents.</p> : (
              <ul className="doc-list">
                {claim.documents!.map((doc) => (
                  <li key={doc.fileName + doc.uploadedAt}>
                    <span>{doc.fileName}</span>
                    <span className="muted small">{humanize(doc.status)} · {formatDateTime(doc.uploadedAt)}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          <Card title="Information requests">
            {(claim.informationRequests ?? []).length === 0 ? <p className="muted">None sent.</p> : (
              <ul className="doc-list">
                {claim.informationRequests!.map((request) => (
                  <li key={request.requestId}>
                    <span>{request.items.join(', ')}</span>
                    <span className="muted small">{humanize(request.status)} · due {formatDate(request.dueAt)}{request.answer ? ` · ${request.answer}` : ''}</span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>

        <div className="stack">
          {claim.handlerId == null && claim.status === 'SUBMITTED' && (
            <Card title="Take this claim">
              <button className="btn btn-primary" disabled={busy} onClick={() => void run((c) => staffApi.pickup(c.claimId, c.version))}>
                Pick up
              </button>
            </Card>
          )}

          {supervisor && claim.handlerId != null && !['REJECTED', 'SETTLED', 'WITHDRAWN', 'CLOSED'].includes(claim.status) && (
            <AssignForm busy={busy} team={user?.team ?? claim.teamId ?? ''} people={people.filter((person) => person.role === 'CLAIMS_ADJUSTER')} onSubmit={(assigneeId, teamId) => void run((c) => staffApi.assign(c.claimId, c.version, assigneeId, teamId))} />
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
              <InfoForm busy={busy} onSubmit={(item) => void run((c) => staffApi.requestInfo(c.claimId, c.version, [item]))} />
              <CoverForm
                currency={currency}
                claimed={claim.claimedAmount?.amount ?? ''}
                busy={busy}
                onSubmit={(outcome, coverage, amount, reason) => void run((c) => staffApi.decide(c.claimId, c.version, outcome, coverage, amount, currency, reason))}
              />
            </>
          )}

          {claim.status === 'DECISION_PENDING' && claim.decision && supervisor && claim.decision.proposedBy !== user?.sub && (
            <ReviewForm busy={busy} decisionId={claim.decision.decisionId} onSubmit={(action, comment) => void run((c) => staffApi.review(c.claimId, c.decision!.decisionId, c.version, action, comment))} />
          )}

          {claim.status === 'APPROVED' && claim.decision?.proposedBy === user?.sub && (
            <SettleForm
              currency={claim.approvedAmount?.currency ?? currency}
              suggested={claim.approvedAmount?.amount ?? ''}
              busy={busy}
              onSubmit={(name, bank, account, amount) => void run((c) => staffApi.settle(c.claimId, c.version, name, bank, account, amount, c.approvedAmount?.currency ?? currency))}
            />
          )}

          {claim.status === 'APPROVED' && claim.decision && claim.decision.proposedBy !== user?.sub && (
            <Card title="Payment">
              <p>Waiting for {personName(people, claim.decision.proposedBy)} to instruct payment.</p>
            </Card>
          )}

          {claim.settlement?.status === 'FAILED' && claim.decision?.proposedBy === user?.sub && (
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

          {claim.assessment && (
            <Card title="Cover">
              <Facts rows={[
                ['Cover', claim.assessment.liability === 'PARTIAL' ? 'Partially covered' : 'Fully covered'],
                ['Amount', formatMoney(claim.assessment.assessedAmount)],
              ]} />
            </Card>
          )}

          {claim.decision && (
            <Card title="Decision">
              <Facts rows={[
                ['Outcome', humanize(claim.decision.outcome)],
                ['Amount', formatMoney(claim.decision.amount)],
                ['Status', waitingForApproval ? 'Waiting for approval' : humanize(claim.decision.status)],
                ['Reason', claim.decision.reasonCode ? humanize(claim.decision.reasonCode) : '—'],
                ['Proposed by', personName(people, claim.decision.proposedBy)],
                ['Decided by', claim.decision.decidedBy ? personName(people, claim.decision.decidedBy) : '—'],
              ]} />
            </Card>
          )}

          {claim.settlement && (
            <Card title="Payment">
              <Facts rows={[
                ['Status', humanize(claim.settlement.status)],
                ['Amount', formatMoney(claim.settlement.amount)],
                ['Payee', claim.settlement.payeeName],
                ['Account', claim.settlement.accountNumberMasked],
                ['Failure', claim.settlement.failureReason || '—'],
              ]} />
            </Card>
          )}
        </div>
      </div>
    </>
  );
}

function Summary({ label, value }: { label: string; value: string }) {
  return <div><span>{label}</span><strong className="amount">{value}</strong></div>;
}

function Facts({ rows }: { rows: [string, ReactNode][] }) {
  return (
    <dl className="facts">
      {rows.map(([label, value]) => (
        <Fragment key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </Fragment>
      ))}
    </dl>
  );
}

function codeOf(value: string | { value: string }): string {
  return typeof value === 'string' ? value : value.value;
}

function IncidentCard({ incident, currency }: { incident: StaffIncident; currency: string }) {
  return (
    <Card title="What happened">
      <Facts rows={[
        ['Peril', humanize(incident.peril)],
        ['Where', incident.location || incident.propertyAddress || '—'],
        ['What the customer said', incident.description],
        ['Vehicle', incident.vehicleRegistration],
        ['Driver', incident.driverName],
        ['Damage', incident.damageDescription],
        ['Police report', incident.policeReportNumber],
        ['Property', incident.propertyAddress],
      ].filter((row): row is [string, string] => Boolean(row[1]))} />
      {(incident.thirdParties ?? []).length > 0 && (
        <ul className="doc-list">
          {incident.thirdParties!.map((party) => (
            <li key={party.name}><span>Other party · {party.name}</span><span className="muted small">{[party.vehicleRegistration, party.insurer, party.phone].filter(Boolean).join(' · ')}</span></li>
          ))}
        </ul>
      )}
      {(incident.damagedItems ?? []).length > 0 && (
        <ul className="doc-list">
          {incident.damagedItems!.map((item) => (
            <li key={item.description}>
              <span>{item.description}</span>
              <span className="muted small">{item.estimatedValueMinor == null ? '—' : formatMinor(item.estimatedValueMinor, currency)}</span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function PolicyCard({ policy }: { policy: PolicySnapshot }) {
  return (
    <Card title="Policy at the time of the claim">
      <Facts rows={[
        ['Policy', policy.policyNumber],
        ['Holder', policy.holderName],
        ['Product', policy.product],
        ['Status', humanize(policy.status)],
        ['Market', `${codeOf(policy.market)} · ${humanize(policy.lineOfBusiness)}`],
        ['Cover', `${formatDate(policy.coverageStart)} – ${formatDate(policy.coverageEnd)}`],
        ['Sum insured', policy.sumInsuredMinor == null ? '—' : formatMinor(policy.sumInsuredMinor, policy.currency)],
        ['Excess', policy.excessMinor == null ? '—' : formatMinor(policy.excessMinor, policy.currency)],
        ['Perils', policy.coveredPerils.map(humanize).join(', ') || '—'],
      ]} />
    </Card>
  );
}

function AssignForm({ busy, team, people, onSubmit }: { busy: boolean; team: string; people: { id: string; name: string }[]; onSubmit: (assigneeId: string, teamId: string) => void }) {
  const [assigneeId, setAssigneeId] = useState('');
  const [teamId, setTeamId] = useState(team);
  return (
    <Card title="Reassign">
      <p className="muted small">You can reassign while the claim is being assessed, waiting for information, waiting for approval, or being paid. Reassignment stops once it is rejected, settled, withdrawn, or closed.</p>
      <form className="grid-2" onSubmit={(e) => { e.preventDefault(); onSubmit(assigneeId, teamId); }}>
        <label>Claim manager
          <select value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)} required>
            <option value="">Select</option>
            {people.map((person) => <option key={person.id} value={person.id}>{person.name}</option>)}
          </select>
        </label>
        <label>Team id<input value={teamId} onChange={(e) => setTeamId(e.target.value)} required /></label>
        <button className="btn btn-secondary" disabled={busy}>Assign</button>
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

function CoverForm({ currency, claimed, busy, onSubmit }: {
  currency: string;
  claimed: string;
  busy: boolean;
  onSubmit: (outcome: 'APPROVE' | 'REJECT', coverage: 'FULL' | 'PARTIAL' | '', amount: string, reason: string) => void;
}) {
  const [choice, setChoice] = useState<'FULL' | 'PARTIAL' | 'REJECT'>('FULL');
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  const partialTooHigh = choice === 'PARTIAL' && claimed !== '' && Number(amount) > Number(claimed);
  const rejecting = choice === 'REJECT';
  return (
    <Card title={rejecting ? 'Reject this claim' : 'Send for approval'}>
      <p className="muted small">
        {rejecting
          ? 'Rejecting finishes the claim now. It is shown as rejected and does not go to another claim manager.'
          : `Another claim manager has to confirm a covered amount. Fully covered uses the amount the customer submitted (${claimed || '—'} ${currency}). A partial amount cannot be higher than that.`}
      </p>
      <form className="grid-2" onSubmit={(e) => {
        e.preventDefault();
        if (choice === 'REJECT') onSubmit('REJECT', '', '', reason);
        else if (choice === 'FULL') onSubmit('APPROVE', 'FULL', claimed, '');
        else onSubmit('APPROVE', 'PARTIAL', amount, '');
      }}>
        <label>Decision
          <select value={choice} onChange={(e) => setChoice(e.target.value as 'FULL' | 'PARTIAL' | 'REJECT')}>
            <option value="FULL">Fully covered</option>
            <option value="PARTIAL">Partially covered</option>
            <option value="REJECT">Reject</option>
          </select>
        </label>
        {choice === 'FULL' && <label>Amount ({currency})<input value={claimed} readOnly /></label>}
        {choice === 'PARTIAL' && (
          <label>Amount ({currency})
            <input value={amount} onChange={(e) => setAmount(e.target.value)} inputMode="decimal" required />
          </label>
        )}
        {choice === 'REJECT' && <label>Reason<input value={reason} onChange={(e) => setReason(e.target.value)} required /></label>}
        {partialTooHigh && <p className="alert alert-bad">Enter an amount up to {claimed} {currency}.</p>}
        <button className="btn btn-primary" disabled={busy || !claimed || partialTooHigh}>{rejecting ? 'Reject claim' : 'Send for approval'}</button>
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
