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

## Architecture

Three layers, strict one-way dependency (`web` → `domain` → `persistence`):

```
domain/          Plain Java. No web/HTTP concerns.
  model/           Customer, Job, LedgerEntry, EntryType
  LedgerService     the ONLY thing allowed to write a LedgerEntry
  JobService        Dad-facing operations (createJob, editJob, recordPayment, voidJob)
  InsightService    read-only queries (weeklySummary, debtorList)

persistence/      Spring Data repositories. Dumb. No business logic.
                  LedgerRepository extends the bare Repository marker
                  interface (not JpaRepository) -- it has no update/delete
                  method to call, by construction.

web/              Thymeleaf controllers + templates. Talk only to domain/ services.
```

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

## Build Phase Progress

- [x] **Phase 0 — Skeleton.** Spring Boot project, `domain`/`persistence`/`web`
  packages, H2 for local dev.
- [x] **Phase 1 — Ledger core.** `LedgerService.record()` / `adjust()` /
  `netFor()` / `recordShopExpense()`. Unit-tested with repeated corrections
  to the same job, asserting the net is right after every single one.
- [x] **Phase 2 — Job entry + receipt.** New Job form (quick-pick amounts,
  recent customers, minimal required fields), `JobService.createJob`,
  receipt render, WhatsApp share link (`wa.me`).
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
- No offline support yet (Phase 6b).
- `/h2-console` requires login like everything else (rather than being
  publicly reachable) -- fine for now, but plan to disable it entirely via
  a prod profile before real deployment.

---

## Deliberately Not Building Yet

Full double-entry accounting (named accounts, debits/credits), multi-user
logins, parts inventory, tax reports, charts. The schema is one column
(`accountId` on `LedgerEntry`) away from real double-entry if it's ever
needed.
