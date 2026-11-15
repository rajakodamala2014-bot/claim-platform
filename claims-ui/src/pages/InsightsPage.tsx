import { staffApi } from '../api';
import { Card, ErrorBox, Loading, useLoad } from '../components/ui';
import { formatDateTime, formatMoney } from '../format';

export function InsightsPage() {
  const workload = useLoad(() => staffApi.workload(), []);
  const sla = useLoad(() => staffApi.sla(), []);
  const exposure = useLoad(() => staffApi.exposure(), []);
  const performance = useLoad(() => staffApi.performance(), []);
  const error = workload.error ?? sla.error ?? exposure.error ?? performance.error;
  const loading = workload.loading || sla.loading || exposure.loading || performance.loading;
  return (
    <>
      <h1>Team insights</h1>
      <p className="muted">Open work, deadlines, money still reserved, and closed-claim results. A few seconds behind the claim.</p>
      <ErrorBox error={error} />
      {loading && <Loading />}
      <Card title="Workload">
        <ul className="claim-list">
          {(workload.data?.items ?? []).map((row) => (
            <li key={row.handlerId} className="claim-row">
              <span>{row.handlerId}</span>
              <span className="muted small">{row.openClaims} open · oldest {formatDateTime(row.oldestSubmittedAt)}</span>
            </li>
          ))}
        </ul>
      </Card>
      <Card title="SLA watch">
        <ul className="claim-list">
          {(sla.data?.items ?? []).map((row) => (
            <li key={row.claimId} className="claim-row">
              <span className="claim-number">{row.claimNumber}</span>
              <span className="chip">{row.slaState}</span>
            </li>
          ))}
        </ul>
      </Card>
      <Card title="Exposure">
        <ul className="claim-list">
          {(exposure.data?.items ?? []).map((row) => (
            <li key={`${row.market}-${row.lineOfBusiness}-${row.totalReserve.currency}`} className="claim-row">
              <span>{row.market} {row.lineOfBusiness}</span>
              <span>{row.outstandingClaims} claims · {formatMoney(row.totalReserve)}</span>
            </li>
          ))}
        </ul>
      </Card>
      <Card title="Performance, last 30 days">
        <ul className="claim-list">
          {(performance.data?.items ?? []).map((row) => (
            <li key={row.handlerId} className="claim-row">
              <span>{row.handlerId}</span>
              <span className="muted small">{row.closedClaims} closed · {row.averageCycleHours} h · SLA met {row.slaMetPercent ?? '—'}</span>
            </li>
          ))}
        </ul>
      </Card>
    </>
  );
}
