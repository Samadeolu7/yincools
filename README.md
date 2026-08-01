# AC Tech Tracker

A mobile-friendly (PWA) job & money tracker for a car-AC repair business,
built in Java/Spring Boot. The user (Dad) only ever sees simple forms, while
the system underneath keeps a provably correct financial record forever —
including when he changes his mind about a number.

The full design rationale lives in [`docs/BUILD_PLAN.md`](docs/BUILD_PLAN.md)
(the original build plan). This README tracks what's actually been built,
and is updated as each phase lands.

---

## The Core Principle

> **Nobody ever edits money. They only ever add a new fact about it.**

All money lives in one append-only table, `LedgerEntry`. Nothing is ever
`UPDATE`d or `DELETE`d there — a "correction" is just another row. Every
number anyone sees (balance owed, weekly total, profit) is a **sum over that
table**, never a stored value someone has to keep in sync. See
`LedgerService.adjust()` for the one method that implements this.

> **Second rule: record what's actually known — never a guess dressed up as
> precision, and never structure Dad doesn't need.** A walk-in's car is a
> free-text note, not a forced `Vehicle` row nobody will ever search for
> again. A shared parts cost across a fleet's visit is logged against the
> customer, not split across cars with fake precision. See `docs/BUILD_PLAN.md`
> §7 for the full reasoning.

## Architecture

Three layers, strict one-way dependency (`web` → `domain` → `persistence`):

```
domain/          Plain Java. No web/HTTP concerns.
  model/           Customer, Vehicle, Job, LedgerEntry, EntryType
  LedgerService     the ONLY thing allowed to write a LedgerEntry
  JobService        Dad-facing operations (createJob, editJob, recordPayment, voidJob)
  VehicleService    findOrCreate, vehiclesFor (fleet detection), suggestionList
  InsightService    read-only queries (weeklySummary, debtorList)

persistence/      Spring Data repositories. Dumb. No business logic.
                  LedgerRepository extends the bare Repository marker
                  interface (not JpaRepository) -- it has no update/delete
                  method to call, by construction.

web/              Thymeleaf controllers + templates. Talk only to domain/ services.
```

**Vehicles, in three shapes, matching what's actually known:** a `Job` can
reference a persisted `Vehicle` (customer with 1+ cars — auto-selected if
just one, a tap-to-pick chip list if more), or a free-text `vehicleNote`
(walk-in with no customer — never becomes a `Vehicle` row), or neither. A
`PARTS_COST` can likewise be fully attributed (jobId+vehicleId+customerId
set), a customer-level *shared visit* cost (`LedgerService.recordSharedCost`
— jobId/vehicleId null, customerId set, logged once with no correction path
since there's nothing scoped to correct it against), or a shop-wide
`SHOP_EXPENSE` (all three null).

## Running locally

```
./gradlew bootRun
```

Opens on `http://localhost:8080`, redirects to `/login` (PIN defaults to
`1234` locally via `APP_PIN` — override it for anything beyond a laptop).
After login it lands on the New Job screen. Data is stored in a file-backed
H2 database at `./data/` (gitignored) — the H2 console is at `/h2-console`
if you need to poke at the raw tables (also behind login).

## Running tests

```
./gradlew test
```

`LedgerServiceTest` and `JobServiceTest` use `@DataJpaTest` against a real
(embedded) database — no mocking of the persistence layer, since the whole
point of the design is that correctness is a property of the actual sums,
not of code that could be mocked into passing.

---

## Deployment

Docker/CI-CD setup mirrors the pattern already used for Bank-Recon on the
same VPS (multi-stage Dockerfile, Docker Hub image, GitHub Actions build +
SSH deploy) with two differences that reflect Yincools actually needing to
be reached directly by a browser rather than only by another backend
service internally:

- **Traefik-routed, not internal-only.** Bank-Recon publishes no port and
  is reachable only from Django's containers on a backend-only network.
  Yincools needs Dad's phone to reach it, so `docker-compose.yml` instead
  joins the network Traefik itself routes from and carries the standard
  `traefik.http.routers.*` labels, with `TRAEFIK_DOMAIN` / `TRAEFIK_NETWORK`
  required (no default) in `.env` -- the compose file refuses to start
  without them rather than silently using a wrong guess.
- **Dedicated Postgres, not a shared instance.** A `postgres` container
  lives in this app's own compose stack (`yincools_internal` network, no
  published port, persistent named volume) rather than pointing at
  infrastructure shared with other apps -- keeps this app's data isolated
  and its compose stack self-contained.

**Files:**

| File | Purpose |
|---|---|
| `Dockerfile` | Multi-stage: `eclipse-temurin:21-jdk` builds the jar via the project's own Gradle wrapper, `eclipse-temurin:21-jre` runs it |
| `docker-compose.yml` | `yincools` service (Traefik-labeled) + dedicated `postgres` service |
| `.env.example` | Every variable the compose file needs, with comments -- copy to `.env` on the server (gitignored) |
| `.github/workflows/deploy.yml` | Build+test with Gradle, build/push image to `samadeolu7/yincools`, SCP compose file + SSH `docker compose up -d` on push to `master` |
| `application-prod.properties` | Activated via `SPRING_PROFILES_ACTIVE=prod`; Postgres datasource from env vars, H2 console disabled |

Reuses the same `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN` / `SSH_HOST` /
`SSH_USER` / `SSH_PRIVATE_KEY` GitHub secrets already configured for
Bank-Recon. Deploy path on the server: `/opt/java-app/Yincools`.

**Before the first real deploy**, fill in `.env` on the server:
`TRAEFIK_DOMAIN`, `TRAEFIK_NETWORK` (the network your Traefik container
actually routes from -- **not** Bank-Recon's internal-only
`phoenix_kti_backend`), `APP_PIN`, `APP_REMEMBER_ME_KEY` (`openssl rand
-hex 32`), and Postgres credentials. `application-prod.properties`
requires `APP_PIN`/`APP_REMEMBER_ME_KEY` with no fallback, so the app
fails to start rather than silently running with the insecure dev default.

**Verification status:** the H2 dev profile has been exercised live
end-to-end throughout this build (every phase above). The `prod` profile
and the container path have *not* been -- neither Docker nor a usable local
Postgres login were available in the environment this was built in. Before
trusting this in production, do a dry run: `docker compose up` locally (or
on the VPS) against a throwaway `.env` and confirm the app boots, connects
to Postgres, and passes its healthcheck.

---

## Build Phase Progress

- [x] **Phase 0 — Skeleton.** Spring Boot project, `domain`/`persistence`/`web`
  packages, H2 for local dev.
- [x] **Phase 1 — Ledger core.** `Vehicle` entity alongside `Customer`/`Job`/
  `LedgerEntry`. `LedgerService.record()` / `adjust()` / `netFor()` /
  `recordShopExpense()` / `recordSharedCost()`. Unit-tested with repeated
  corrections to the same job (net right after every one), plus the
  shared-cost case (a `PARTS_COST` with `customerId` set but `jobId`/
  `vehicleId` null, and proof it doesn't net across different customers).
- [x] **Phase 2 — Job entry + receipt.** New Job form (quick-pick amounts,
  recent customers, minimal required fields), vehicle picker (auto-select
  for a single-vehicle customer, tap-to-pick chips for a fleet, free text
  for walk-ins), vehicle autocomplete (`static/vehicle-seed.json` merged
  client-side with `/api/vehicles/suggestions`), a "shared across today's
  visit" checkbox on parts cost, `JobService.createJob`, receipt render,
  WhatsApp share link (`wa.me`).
- [x] **Phase 3 — Edit/correction flow.** Edit Job screen prefilled with
  current values (charge, parts cost, paid), saves via the same
  `adjust()` mechanism as everything else. "Last entry" card pinned to the
  top of the New Job screen with a one-tap edit link. Void action zeroes a
  job out without deleting its history. No confirmation dialogs.
- [x] **Phase 4 — Insights.** `InsightService.weeklySummary()` (charged,
  paid, parts cost, shop expenses, profit for the Mon–Sun week containing a
  given date) and `debtorList()` (jobs with a positive balance, highest
  first). This Week and Who Owes Me screens, linked from a small nav on the
  New Job screen.
- [x] **Phase 5 — Shop expenses screen.** `/expenses/new` records a
  `SHOP_EXPENSE` ledger entry (amount + optional note) via
  `LedgerService.recordShopExpense()`; feeds straight into the weekly
  profit calculation with no separate write path.
- [x] **Phase 6a — PIN login + long-lived cookie.** Spring Security, single
  in-memory user, PIN-only login page, remember-me cookie valid ~1 year so
  Dad logs in once on his phone and never sees the login screen again.
  `app.pin` / `app.remember-me-key` come from `APP_PIN` / `APP_REMEMBER_ME_KEY`
  env vars in real deployments (dev defaults are insecure placeholders).
- [ ] **Phase 6b — PWA + offline.** Manifest + icon for home-screen install,
  service worker + local queue for offline job capture.
- [ ] **Phase 7 — Later insights** (once real data exists). Regas-due list,
  monthly profit trend, busiest job type.

### What exists right now

| Route | Behavior |
|---|---|
| `GET /` | Redirects to `/jobs/new` |
| `GET /jobs/new` | New Job form; shows the "last entry" quick-fix card and recent customers |
| `POST /jobs` | Creates a job, redirects to its receipt |
| `GET /jobs/{id}/edit` | Edit form prefilled with current charge / parts cost / paid |
| `POST /jobs/{id}/edit` | Corrects charge, parts cost, and payment via `LedgerService.adjust()` |
| `POST /jobs/{id}/void` | Zeroes charge/paid/parts cost for the job; ledger history is untouched |
| `GET /jobs/{id}/receipt` | Renders the receipt text + WhatsApp share link |
| `GET /insights/week` | This week: job count, charged, paid, parts cost, shop expenses, profit |
| `GET /insights/debtors` | Who owes me: jobs with a positive balance, highest first, tap to edit |
| `GET /expenses/new` | Shop expense form |
| `POST /expenses` | Records a shop expense, redirects back with a saved banner |
| `GET /login` | PIN entry screen; everything else requires auth |
| `POST /login` | Verifies the PIN, sets a ~1 year remember-me cookie |
| `GET /api/vehicles?phone=` | JSON: vehicles for that phone's customer (empty if none) |
| `GET /api/vehicles/suggestions` | JSON: distinct vehicle descriptions typed before |

### Known simplifications (intentional, not gaps)

- Parts cost isn't cached on `Job` (only charge/paid/balance are, per the
  original schema) — it's read straight from the ledger via
  `JobService.partsCostFor()` on the rare screens that need it.
- WhatsApp phone numbers are normalized to `+234` (Nigeria) assuming a
  leading `0` — revisit if the business ever serves customers outside
  Nigeria.
- "Profit" in the weekly summary is billed revenue minus costs (charged −
  parts cost − shop expenses), not cash collected — it reflects work done
  this week regardless of whether the customer has paid yet.
- A shared visit cost (`recordSharedCost`) is a plain append, never a
  correction target: with `jobId` null, Spring Data renders that parameter
  as `IS NULL` with no `customerId` filter, so a jobId-keyed `adjust()` would
  net across every customer's shared costs at once. There's no described
  flow for editing one later, so each is recorded once, like a shop expense.
- Per-vehicle cost/profit isn't built yet (Phase 7) — when it is, it must be
  visibly flagged as approximate wherever a visit shared costs across
  multiple vehicles (§7 of the build plan).
- No offline support yet (Phase 6b).
- `/h2-console` requires login in dev; the `prod` profile disables it
  outright (`spring.h2.console.enabled=false`) since prod uses Postgres and
  a raw SQL browser has no business being reachable at all once real data
  exists.

---

## Deliberately Not Building Yet

Full double-entry accounting (named accounts, debits/credits), multi-user
logins, parts inventory, tax reports, charts. The schema is one column
(`accountId` on `LedgerEntry`) away from real double-entry if it's ever
needed.
