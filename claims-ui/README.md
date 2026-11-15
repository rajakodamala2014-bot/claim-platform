# claims-ui — claimant portal

React 18 + Vite + TypeScript single-page app for customers. It uses only the claimant surface
(`/claimant/v1/**`) through the gateway and holds no business rules: what a claimant may see or do
(`statusLabel`, `nextStep`, `canWithdraw`, `canAppeal`, `actionNeeded`) comes from the API.

| Screen | Route | APIs |
|---|---|---|
| Sign in (dev personas) | any, when signed out | `POST /dev/token` (claims-service `dev` profile) |
| Your claims | `/claims` | C4 (cursor pagination) |
| Report an incident | `/claims/new` | C1 policies, C2 upload + signed PUT, C3 submit (`Idempotency-Key` per draft) |
| Claim detail | `/claims/:id` | C5, C6 timeline, C7/C8 information requests, C9 withdraw, C10 appeal |
| Messages | `/notifications` | N1 inbox, N2 mark read (unread count polled every 15 s) |
| Settings | `/settings` | N3/N4 notification preferences |

## Run

```sh
docker compose up -d --build     # builds the claims-ui service too; open http://localhost:8000 (gateway)
docker compose up -d --build claims-ui   # rebuild just the UI after a change
# or, with the stack running, hot reload on http://localhost:5173 (proxies /claimant and /dev/token to :8000)
cd claims-ui && npm install && npm run dev
npm test                         # vitest unit tests
npm run build                    # type-check + production build into dist/
```

Sign in as `priya` (SG; motor + property policies), or `wong`, `aisyah`, `somchai`, `budi`, `olivia`.
Policy `SG-MTR-POL-1500` exercises the Policy-system timeout (claim saved with cover pending).

## Notes

- Same origin everywhere (gateway, or Vite's proxy), so no CORS. Pre-signed upload URLs are absolute
  (`claims.documents.public-base-url`); `api.sameOriginUrl` keeps only path + query.
- The token sits in `sessionStorage` and is decoded only for display; services validate it. A 401 signs out.
- Client-side checks mirror the domain (upload types/size from `core.domain.Document`, required motor/property
  incident fields) only to fail fast; field errors from problem+json `errors` are shown as returned.
- Not part of the Maven reactor; `./scripts/ci.sh` does not build it.
