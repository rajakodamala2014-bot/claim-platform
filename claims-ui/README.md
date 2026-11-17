# claims-ui — customer and staff portal

React 18 + Vite + TypeScript single-page app. One app, two portals: after sign-in the role on the token picks
the customer screens (`CUSTOMER`) or the claim manager screens (`CLAIMS_ADJUSTER`). It calls only the gateway, and
holds no business rules: what a user may see or do (`statusLabel`, `nextStep`, `canWithdraw`, `canAppeal`,
`actionNeeded`, the staff workspace's allowed actions) comes from the API.

## Customer screens (`/claimant/v1/**`)

| Screen | Route | APIs |
|---|---|---|
| Sign in | any, when signed out | `POST /auth/login` (claims-service, `platform.app_user`) |
| Your claims | `/claims` | C4 (cursor pagination) |
| Report an incident | `/claims/new` | C1 policies, C2 upload + signed PUT, C3 submit (`Idempotency-Key` per draft) |
| Claim detail | `/claims/:id` | C5, C6 timeline, C7/C8 information requests, C9 withdraw, C10 appeal |
| Messages | `/notifications` | N1 inbox, N2 mark read (unread count polled every 15 s) |
| Settings | `/settings` | N3/N4 notification preferences |

## Claim manager screens (`/staff/v1/**`)

| Screen | Route | APIs |
|---|---|---|
| Work queue | `/work` | I1 work queue (views, cursor pagination), I2 search |
| Claim workspace | `/work/:id` | S-endpoints on `/staff/v1/claims/{id}`: pick up, start assessment, information requests, propose decision (full or partial cover, or rejection), settlement and retry; `If-Match` on every change. Names come from `/staff/v1/people` |
| Approvals | `/approvals` | `/staff/v1/approvals`, then approve or return a decision proposed by someone else |
| Insights | `/insights` | `/staff/v1/insights/{workload,performance,sla,exposure}` |

## Run

```sh
docker compose -f deployment/docker-compose.yml up -d --build     # builds the claims-ui service too; open http://localhost:8000 (gateway)
docker compose -f deployment/docker-compose.yml up -d --build claims-ui   # rebuild just the UI after a change
# or, with the stack running, hot reload on http://localhost:5173 (proxies /claimant, /staff, /admin and /auth to :8000)
cd claims-ui && npm install && npm run dev
npm test                         # vitest unit tests
npm run build                    # type-check + production build into dist/
```

Password for every seeded account: `ChangeMe123!`. Customers: `priya` (SG motor + property policies), `tan`, `wong`,
`aisyah`. Claim managers: `arun`, `kumar`, `faiz`, `meera`, `ho`, `lee`; a decision needs two of them in the same market
(for example `arun` proposes, `meera` approves). Policy `SG-MTR-POL-1500` exercises the Policy-system timeout (claim
saved with cover pending).

## Notes

- Same origin everywhere (gateway, or Vite's proxy), so no CORS. Pre-signed upload URLs are absolute
  (`claims.documents.public-base-url`); `api.sameOriginUrl` keeps only path + query.
- The token sits in `sessionStorage` and is decoded only for display and to pick the portal; services validate it. A 401 signs out.
- Client-side checks mirror the domain (upload types/size from `core.entity.Document`, required motor/property
  incident fields) only to fail fast; field errors from problem+json `errors` are shown as returned.
- Not part of the Maven reactor; `./scripts/ci.sh` does not build it.
