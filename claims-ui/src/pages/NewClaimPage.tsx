import { useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, type Policy, type SubmitResult } from '../api';
import { Card, ErrorBox, FileUploader, Loading, useLoad } from '../components/ui';
import { formatDate, formatMoney, humanize, localInputToIso, nowAsLocalInput } from '../format';

interface Form {
  peril: string;
  incidentAt: string;
  description: string;
  location: string;
  vehicleRegistration: string;
  driverName: string;
  damageDescription: string;
  policeReportNumber: string;
  thirdPartyName: string;
  thirdPartyVehicle: string;
  thirdPartyInsurer: string;
  propertyAddress: string;
  items: { description: string; value: string }[];
  amount: string;
  phone: string;
  email: string;
}

const EMPTY: Form = {
  peril: '',
  incidentAt: nowAsLocalInput(),
  description: '',
  location: '',
  vehicleRegistration: '',
  driverName: '',
  damageDescription: '',
  policeReportNumber: '',
  thirdPartyName: '',
  thirdPartyVehicle: '',
  thirdPartyInsurer: '',
  propertyAddress: '',
  items: [{ description: '', value: '' }],
  amount: '',
  phone: '',
  email: '',
};

/** C1 → C2 → C3: pick a policy, describe the incident, attach evidence, submit. */
export function NewClaimPage() {
  const navigate = useNavigate();
  const policies = useLoad(() => api.policies(), []);
  const [policy, setPolicy] = useState<Policy | null>(null);
  const [form, setForm] = useState<Form>(EMPTY);
  const [uploadIds, setUploadIds] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [result, setResult] = useState<SubmitResult | null>(null);
  // One key per claim being drafted: a retried submit (timeout, double click) replays instead of duplicating.
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID());

  const currency = policy?.excess?.currency ?? currencyFor(policy?.market);
  const set = (field: Exclude<keyof Form, 'items'>) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [field]: e.target.value }));

  const motor = policy?.lineOfBusiness === 'MOTOR';
  const collisionNeedsParty = motor && form.peril === 'COLLISION' && !form.thirdPartyName.trim() && !form.policeReportNumber.trim();
  const setItem = (i: number, patch: Partial<Form['items'][number]>) =>
    setForm((f) => ({ ...f, items: f.items.map((it, j) => (j === i ? { ...it, ...patch } : it)) }));

  function choose(p: Policy) {
    setPolicy(p);
    setForm((f) => ({ ...f, peril: p.coveredPerils[0] ?? '' }));
    setIdempotencyKey(crypto.randomUUID());
    setError(null);
  }

  const body = useMemo(() => {
    if (!policy) return null;
    const incident: Record<string, unknown> = {
      peril: form.peril,
      description: form.description.trim(),
      location: form.location.trim() || null,
    };
    if (policy.lineOfBusiness === 'MOTOR') {
      Object.assign(incident, {
        vehicleRegistration: form.vehicleRegistration.trim() || null,
        driverName: form.driverName.trim() || null,
        damageDescription: form.damageDescription.trim() || null,
        policeReportNumber: form.policeReportNumber.trim() || null,
        thirdParties: form.thirdPartyName.trim()
          ? [{ name: form.thirdPartyName.trim(), vehicleRegistration: form.thirdPartyVehicle.trim() || null, insurer: form.thirdPartyInsurer.trim() || null }]
          : [],
      });
    } else {
      incident.propertyAddress = form.propertyAddress.trim() || null;
      incident.damagedItems = form.items
        .filter((i) => i.description.trim())
        .map((i) => ({
          description: i.description.trim(),
          estimatedValue: i.value.trim() ? { amount: i.value.trim(), currency } : null,
        }));
    }
    const contact = form.phone.trim() || form.email.trim()
      ? { phone: form.phone.trim() || undefined, email: form.email.trim() || undefined }
      : undefined;
    return {
      policyNumber: policy.policyNumber,
      lineOfBusiness: policy.lineOfBusiness,
      incidentAt: localInputToIso(form.incidentAt) ?? '',
      incident,
      claimedAmount: { amount: form.amount.trim(), currency },
      uploadIds,
      contact,
    };
  }, [policy, form, uploadIds, currency]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!body) return;
    setSubmitting(true);
    setError(null);
    try {
      setResult(await api.submitClaim(idempotencyKey, body));
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  if (result) {
    return (
      <Card>
        <div className="success">
          <div className="success-mark" aria-hidden>✓</div>
          <h1>We've received your claim</h1>
          <p>
            Your claim number is <strong className="claim-number">{result.claimNumber}</strong>.
          </p>
          <p className="muted">
            {result.coverStatus === 'PENDING'
              ? "We're still confirming your policy cover. This can take a little while and needs nothing from you."
              : 'A claim manager will review it shortly. We will message you as it progresses.'}
          </p>
          <div className="row">
            <button className="btn btn-primary" onClick={() => navigate(`/claims/${result.claimId}`)}>View claim</button>
            <Link to="/claims" className="btn btn-secondary">Back to claims</Link>
          </div>
        </div>
      </Card>
    );
  }

  return (
    <>
      <div className="page-head">
        <div>
          <Link to="/claims" className="back">← Claims</Link>
          <h1>Report an incident</h1>
        </div>
      </div>

      <Card title="1. Which policy?">
        {policies.loading && <Loading label="Fetching your policies…" />}
        <ErrorBox error={policies.error} />
        {policies.data && policies.data.items.length === 0 && <p className="muted">We couldn't find an active policy for you.</p>}
        <div className="policy-grid">
          {policies.data?.items.map((p) => (
            <button
              type="button"
              key={p.policyNumber}
              className={`policy ${policy?.policyNumber === p.policyNumber ? 'selected' : ''}`}
              onClick={() => choose(p)}
              aria-pressed={policy?.policyNumber === p.policyNumber}
            >
              <span className="chip">{humanize(p.lineOfBusiness)}</span>
              <strong>{p.product}</strong>
              <span className="muted small">{p.policyNumber}</span>
              <span className="muted small">
                {formatDate(p.coverageStart)} – {formatDate(p.coverageEnd)} · excess {formatMoney(p.excess)}
              </span>
            </button>
          ))}
        </div>
      </Card>

      {policy && (
        <form onSubmit={(e) => void submit(e)} className="stack">
          <Card title="2. What happened?">
            <div className="grid-2">
              <label>
                <span>Type of incident</span>
                <select value={form.peril} onChange={set('peril')} required>
                  {policy.coveredPerils.map((p) => (
                    <option key={p} value={p}>{humanize(p)}</option>
                  ))}
                </select>
              </label>
              <label>
                <span>When did it happen?</span>
                <input type="datetime-local" value={form.incidentAt} max={nowAsLocalInput()} onChange={set('incidentAt')} required />
              </label>
            </div>
            <label>
              <span>Describe what happened</span>
              <textarea rows={4} maxLength={2000} value={form.description} onChange={set('description')} required />
            </label>
            <label>
              <span>Where?{!motor && <em className="muted"> optional</em>}</span>
              <input maxLength={300} value={form.location} onChange={set('location')} required={motor} />
            </label>

            {policy.lineOfBusiness === 'MOTOR' ? (
              <>
                <div className="grid-2">
                  <label>
                    <span>Vehicle registration</span>
                    <input maxLength={20} value={form.vehicleRegistration} onChange={set('vehicleRegistration')} required />
                  </label>
                  <label>
                    <span>Who was driving?</span>
                    <input maxLength={200} value={form.driverName} onChange={set('driverName')} required />
                  </label>
                </div>
                <label>
                  <span>Damage to your vehicle</span>
                  <input maxLength={2000} value={form.damageDescription} onChange={set('damageDescription')} required />
                </label>
                <label>
                  <span>Police report number <em className="muted">optional</em></span>
                  <input maxLength={40} value={form.policeReportNumber} onChange={set('policeReportNumber')} />
                </label>
                <fieldset>
                  <legend>Other party {collisionNeedsParty ? '' : <em className="muted">optional</em>}</legend>
                  {collisionNeedsParty && (
                    <p className="small muted">For a collision, give the other party's name or a police report number.</p>
                  )}
                  <div className="grid-3">
                    <label><span>Name</span><input maxLength={200} value={form.thirdPartyName} onChange={set('thirdPartyName')} /></label>
                    <label><span>Vehicle</span><input maxLength={20} value={form.thirdPartyVehicle} onChange={set('thirdPartyVehicle')} /></label>
                    <label><span>Insurer</span><input maxLength={100} value={form.thirdPartyInsurer} onChange={set('thirdPartyInsurer')} /></label>
                  </div>
                </fieldset>
              </>
            ) : (
              <>
                <label>
                  <span>Property address</span>
                  <input maxLength={300} value={form.propertyAddress} onChange={set('propertyAddress')} required />
                </label>
                <fieldset>
                  <legend>What was damaged or lost?</legend>
                  <div className="stack">
                    {form.items.map((item, i) => (
                      <div className="item-row" key={i}>
                        <input aria-label={`Item ${i + 1}`} placeholder="e.g. Sofa" maxLength={300} value={item.description}
                          onChange={(e) => setItem(i, { description: e.target.value })} required={i === 0} />
                        <div className="input-affix">
                          <span>{currency}</span>
                          <input aria-label={`Item ${i + 1} value`} inputMode="decimal" pattern="^\d+(\.\d{1,2})?$" placeholder="Value"
                            value={item.value} onChange={(e) => setItem(i, { value: e.target.value })} />
                        </div>
                        {form.items.length > 1 && (
                          <button type="button" className="link" onClick={() => setForm((f) => ({ ...f, items: f.items.filter((_, j) => j !== i) }))}>
                            Remove
                          </button>
                        )}
                      </div>
                    ))}
                    {form.items.length < 50 && (
                      <button type="button" className="btn btn-secondary" style={{ alignSelf: 'flex-start' }}
                        onClick={() => setForm((f) => ({ ...f, items: [...f.items, { description: '', value: '' }] }))}>
                        Add item
                      </button>
                    )}
                  </div>
                </fieldset>
              </>
            )}
          </Card>

          <Card title="3. Amount and evidence">
            <label>
              <span>Amount you're claiming</span>
              <div className="input-affix">
                <span>{currency}</span>
                <input inputMode="decimal" pattern="^(?!0+(\.0+)?$)\d+(\.\d{1,2})?$" placeholder="0.00" value={form.amount} onChange={set('amount')} required />
              </div>
              <p className="muted small">Enter an amount greater than zero, with up to two decimal places.</p>
            </label>
            <div className="field">
              <span>Photos, receipts, reports</span>
              <FileUploader onChange={setUploadIds} />
            </div>
          </Card>

          <Card title="4. How can we reach you?">
            <div className="grid-2">
              <label><span>Phone</span><input type="tel" maxLength={40} value={form.phone} onChange={set('phone')} /></label>
              <label><span>Email</span><input type="email" maxLength={200} value={form.email} onChange={set('email')} /></label>
            </div>
          </Card>

          <ErrorBox error={error} />
          <div className="row end">
            <Link to="/claims" className="btn btn-secondary">Cancel</Link>
            <button className="btn btn-primary" type="submit" disabled={submitting || !form.description.trim() || !form.amount.trim() || collisionNeedsParty}>
              {submitting ? 'Submitting…' : 'Submit claim'}
            </button>
          </div>
        </form>
      )}
    </>
  );
}

function currencyFor(market?: string): string {
  return { SG: 'SGD', HK: 'HKD', MY: 'MYR', TH: 'THB', ID: 'IDR', AU: 'AUD' }[market ?? ''] ?? 'SGD';
}
