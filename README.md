# Yincools

A mobile-friendly (PWA) job & money tracker for Yincools, a car-AC repair
business, built in Java/Spring Boot. The user (Dad) only ever sees simple
forms, while the system underneath keeps a provably correct financial record
forever — including when he changes his mind about a number.

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
  model/           Customer, Vehicle, Job, Quote, LedgerEntry, EntryType
  LedgerService     the ONLY thing allowed to write a LedgerEntry
  JobService        Dad-facing operations (createJob, editJob, recordPayment, voidJob)
  VehicleService    findOrCreate, vehiclesFor (fleet detection), suggestionList
  CustomerService   resolveOrCreate -- shared by JobService and QuoteService
  QuoteService      createQuote, convertToJob -- never touches LedgerEntry
  InsightService    read-only queries (weeklySummary, debtorList)

persistence/      Spring Data repositories. Dumb. No business logic.
                  LedgerRepository extends the bare Repository marker
                  interface (not JpaRepository) -- it has no update/delete
                  method to call, by construction.

web/              Thymeleaf controllers + templates. Talk only to domain/ services.
```

## Visual Identity

Design tokens (`static/css/tokens.css`) are pulled directly from the real
Yincools mark, not a palette invented from scratch:

| Token | Hex | Role |
|---|---|---|
| `--color-primary` | `#DD2B1C` | brand red — buttons, active states, the debtor list's "needs attention" |
| `--color-ink` | `#151616` | near-black — body text, borders |
| `--color-surface` | `#FFFFFF` | background |

Red is used as an accent only, never a surface — a large red background
reads as a warning banner, and the mark itself only ever uses red as an
accent against black and white. The home-screen icon (`icon-192.png` /
`icon-512.png`) is generated from the actual logo file
(`static/square_yincools_logo.png`), not a placeholder.

The letterhead (`fragments/letterhead.html`) is two fragments — `header`
(logo, two-tone wordmark with the "AUTO NIG." banner, tagline) and `footer`
(head office address, WhatsApp/tel/email, bankers) — matching the real
physical shop letterhead line for line, not an invented layout. Both the
receipt and the quote preview use them. Every detail (suffix, tagline,
address, contact numbers, bankers) is config (`app.business.*` /
`APP_BUSINESS_*` env vars in `application.properties`), defaulting to the
real published business details since there's one shop and none of it is
secret.

**Two-layer CSS, on purpose.** `tokens.css` holds only values (colors,
spacing, radius); `static/css/components.css` builds the actual reusable
UI out of them — `.btn`/`.btn-primary`/`.btn-outline`, form inputs (plain
element selectors, not a class every template has to remember — every
text/tel/password input and select looks the same, there's no case yet
where one shouldn't), chips, cards, quick-picks, the bottom nav. Every
screen links both files instead of carrying its own copy of the same
rules. This is the concrete answer to "keep this future-proof without
sacrificing ease of use now": a new screen gets consistent styling for
free by linking two stylesheets, and a system-wide visual change (which
already happened once, going from a placeholder green accent to the real
brand red) is a one-file edit instead of a hunt through every template.

**One consistent bottom nav, everywhere it matters.** New Quote, New Job,
This Week, Who Owes Me, and Shop Expense all share the same persistent
bottom nav (`fragments/bottom-nav.html`) — same position, same color,
same active-tab highlight — so Dad never has to relearn where things are
depending on which screen he's on. Quote leads the nav and is the actual
landing page (`/`, login, and the PWA `start_url` all land on
`/quotes/new`) because a quote is what happens *before* a job exists --
it's the harder sell to get right, and converting an accepted quote into
a job is already a one-tap "Convert to Job" away. Receipt and Edit Job
stay nav-free: they're reached mid-task, right after New Job/New Quote,
not a place he navigates from. Adding a new section later (customer list,
settings, ...) is one line in that fragment, not a per-screen redesign.

**Vehicles, in three shapes, matching what's actually known:** a `Job` can
reference a persisted `Vehicle` (customer with 1+ cars — auto-selected if
just one, a tap-to-pick chip list if more), or a free-text `vehicleNote`
(walk-in with no customer — never becomes a `Vehicle` row), or neither. A
`PARTS_COST` can likewise be fully attributed (jobId+vehicleId+customerId
set), a customer-level *shared visit* cost (`LedgerService.recordSharedCost`
— jobId/vehicleId null, customerId set, logged once with no correction path
since there's nothing scoped to correct it against), or a shop-wide
`SHOP_EXPENSE` (all three null).

**Quotes are Dad's pitch, not his bookkeeping.** A `Quote` lives entirely
outside `LedgerEntry` -- `QuoteService` never calls `LedgerService` at all,
only `JobService.createJobFromResolvedIdentity()` on conversion, and only
then does the ledger find out a job exists. A quote is itemized (`QuoteItem`
rows: part name + amount, "table format" per Dad's request) rather than a
single total -- the total is never stored, it's always the sum of a quote's
items (`QuoteService.totalFor()`), so there's nothing to keep in sync as
rows are added. Converting charges the job the quote's total but starts it
at **zero paid** -- a quote is an estimate shown before the customer decides
what they'll actually pay, so assuming "paid in full" would be presenting a
guess as a fact. Dad fills in what was actually paid from the normal Edit
Job screen, same `adjust()` mechanism as any other correction. Conversion is
idempotent -- converting an already-converted quote just returns the
existing job. A quote is never deleted or marked "rejected"; it either has a
`convertedToJobId` or it doesn't.

**Parts have a real, growing "database" now, same pattern as vehicles.**
Every part name ever typed on a quote (`QuoteItem.partName`) is a future
suggestion -- `/api/parts/suggestions` returns the distinct list, merged
client-side with the static seed (`parts-seed.json`), same merge pattern as
`/api/vehicles/suggestions`. This single source powers both the itemized
quote table's autocomplete and New Job's parts chips, so a part learned in
one place shows up in the other.

## Running locally

```
./gradlew bootRun
```

Opens on `http://localhost:8080`, redirects to `/login` (PIN defaults to
`1234` locally via `APP_PIN` — override it for anything beyond a laptop).
After login it lands on the New Quote screen -- a quote is what happens
before a job exists, so it's the front door. Data is stored in a file-backed
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
- [x] **Phase 3 — Quotes + parts chips.** `Quote` entity/service, entirely
  outside the ledger. New Quote screen reuses the Phase 2 vehicle picker
  as-is. Quotes are itemized (`QuoteItem` part-name/amount rows, total
  computed by summing them) rather than a single amount -- an editable
  table, not a rigid form. No "add row" button: starts with two empty
  rows, and filling in the last one grows a fresh empty row after it
  (`quote-items.js`), so the table just keeps up with however many parts
  Dad types without an extra tap; empty rows are always excluded from
  what's submitted. Part names are a real, growing suggestion list
  (`/api/parts/suggestions`, merged client-side with the static
  `parts-seed.json` seed, same pattern as vehicles), shared by both the
  quote table and New Job's parts chips. The quote preview deliberately
  separates the *app screen* from the *document*: the page around it is
  plain app chrome (title, buttons), and the actual quote renders as its
  own document (`.quote-doc` in `components.css`) built like a real
  quotation, not a card floating on a dashboard -- no shadow, no rounded
  box, no centering, since invoices are grids and alignment, not Material
  widgets. The page around it is visibly a different color
  (`--color-surface-muted`) from the document itself (plain white with a
  hairline border, no shadow) -- the contrast is what makes `.quote-doc`'s
  fixed width read as a sheet of paper sitting on a desk, rather than
  just "the layout is narrow on mobile" (the alternative, actually
  fixing the page to A4's pixel proportions, was deliberately rejected --
  item count varies, and a fixed aspect ratio would either strand a
  two-item quote in mostly dead space or need real pagination logic for a
  long one; letting height grow naturally while only the width reads as
  "page-like" gets the same perception cheaply). Logo and business
  identity anchor the top-left; QUOTATION and an "ESTIMATE" badge sit
  top-right, the way real invoices put metadata in the corner; a single
  ruled line closes the header -- deliberately the only rule on the page,
  since five ruled sections read as a spreadsheet and one clean rule
  reads as a document; everything below it is separated by whitespace
  alone. A "Bill To" / "Quote Details" pair sits side by side underneath (customer/vehicle/phone on
  the left, a generated quote number `QT-000034` / date / a computed
  7-day "Valid Until" / service on the right -- validity is a display
  convention, not a stored field, since nothing about expiring quotes is
  actually enforced anywhere). The Description/Qty/Amount table (qty is
  always 1 -- there's no quantity concept in the data, purely
  presentational) is deliberately the dominant element on the page, with
  real row height and a couple of faint blank ruled rows trailing the
  real ones, like a preprinted invoice pad rather than a table that just
  stops. Totals live below the table, not inside it -- Subtotal and a
  literal "Discount ₦0.00" (there's no discount concept in the ledger,
  but showing zero is an accurate statement, not fabricated data) stay
  quiet, and TOTAL is the one thing sized and colored to compete with the
  table for attention. The table itself is zebra-striped with a thin
  vertical rule around the Qty column, the same scannability convention
  every real invoicing tool (QuickBooks, Zoho, Xero) uses -- a plain-
  ruled table with every row the same weight is harder to track across
  than one where alternate rows are quietly shaded. A "Thank you for your
  business!" line and a blank two-up signature row (Prepared By on the
  business's side, Customer Acceptance blank for an in-person sign-off if
  the quote gets printed) get real space above them instead of sitting
  cramped under the total, and the footer is two clean columns (address/
  contact | bank details) instead of one dense paragraph. One-tap "Share Quote" renders that document to a PNG
  (`html2canvas`, vendored in `static/vendor/` -- no CDN dependency) and
  hands it straight to the OS's native share sheet (`navigator.share`),
  so whatever lands in WhatsApp or email looks like the real letterhead
  instead of a plain-text summary, and Dad never has to screenshot it
  himself. This also sidesteps the old limit where a WhatsApp button only
  existed if a customer phone had been entered -- the native share sheet
  lets him pick the contact himself either way. It's progressive
  enhancement over the original text-only `wa.me`/`mailto:` links
  (`quote-share.js`): those are always rendered first and stay as the
  fallback on any browser that can't share files (most desktops), and are
  only hidden once the richer path is confirmed to work. The header/
  footer (logo, wordmark, tagline, address/contact/bankers) are the same
  bytes on every single quote -- only the Bill To/table/totals block in
  the middle changes -- so `quote-share.js` rasterizes them once and
  caches the result in `localStorage`, keyed off a hash of their markup
  *and* the CSS files' actual text, not just the markup alone -- a pure
  styling change (a color, a spacing value, a `white-space: nowrap` fix)
  leaves the HTML byte-identical, so hashing markup alone would silently
  keep serving a stale pre-change render forever on any phone that had
  already generated one share. Caught and fixed this exact gap after a
  CSS-only fix ("AUTO NIG." wrapping mid-phrase) shipped without the old
  hash noticing; verified the fix with two headless Chrome runs sharing
  one profile (so `localStorage` persisted between them) -- editing
  `components.css` on disk between runs correctly busted the cache (a
  full 3-call re-render instead of reusing the stale chrome), and a third
  run with no further change correctly hit the cache again (1 call).
  Every share after the first only asks `html2canvas` to redo the small
  card and composites it onto the cached chrome, instead of re-decoding
  the logo image and re-laying-out the footer text on every tap.
  "Convert to Job" lands on the normal Edit Job screen, charged the
  quote's total but starting at zero paid -- a quote is an estimate, not
  an assumption that it was paid in full. A quote is editable at any
  time, including after it's already become a job (`QuoteService.editQuote`,
  reusing the same itemized-table form) -- Dad often only learns the real
  part price after the work is under way, and there was never a real
  reason to freeze the quote once converted. Editing the items only ever
  changes the quote's own record; editing them on an already-converted
  quote also pushes the new total onto that job's `CHARGE` ledger entry
  (`JobService.updateCharge`), so the two can't silently disagree about
  the price -- parts cost and paid stay untouched, since the quote never
  described those. The quote preview links straight to the resulting job
  once converted instead of a dead end.
- [x] **Phase 4 — Edit/correction flow.** Edit Job screen prefilled with
  current values (charge, parts cost, paid), saves via the same
  `adjust()` mechanism as everything else. "Last entry" card pinned to the
  top of the New Job screen with a one-tap edit link. Void action zeroes a
  job out without deleting its history. No confirmation dialogs.
- [x] **Phase 5 — Insights.** `InsightService.weeklySummary()` (charged,
  paid, parts cost, shop expenses, profit for the Mon–Sun week containing a
  given date) and `debtorList()` (jobs with a positive balance, highest
  first). This Week and Who Owes Me screens, linked from the bottom nav.
- [x] **Phase 6 — Shop expenses screen.** `/expenses/new` records a
  `SHOP_EXPENSE` ledger entry (amount + optional note) via
  `LedgerService.recordShopExpense()`; feeds straight into the weekly
  profit calculation with no separate write path.
- [x] **Phase 7a — PIN login + long-lived cookie.** Spring Security, PIN-only
  login page, remember-me cookie valid ~1 year so Dad logs in once on his
  phone and never sees the login screen again. `app.pin` / `app.remember-me-key`
  come from `APP_PIN` / `APP_REMEMBER_ME_KEY` env vars in real deployments
  (dev defaults are insecure placeholders). Two accounts share this exact
  same login screen -- "shop" (Dad, day to day) and "owner" (extra
  oversight access, `APP_OWNER_PIN`) -- and the form never asks which one
  you are; `PinAuthenticationProvider` tries the submitted PIN against
  both known accounts and authenticates as whichever one matches, rather
  than looking a single account up by a submitted username the way
  Spring's default `DaoAuthenticationProvider` would. A plain
  `InMemoryUserDetailsManager`-backed `UserDetailsService` still exists
  alongside it, since remember-me needs one to reload a user by username
  on later requests. `/suppliers/**` requires the owner's `ROLE_OWNER` --
  Dad's account gets a plain 403 there, and it's deliberately not linked
  from anywhere in his nav.
- [x] **Phase 7b — PWA + offline.** `manifest.webmanifest` + icons generated
  from the real Yincools mark (installable, `start_url=/quotes/new`, named
  "Yincools" — not a generic default PWA name/icon). `sw.js` precaches New
  Job's and New Quote's static assets and keeps a network-first/cache-
  fallback copy of both page shells. Both forms' submits are intercepted
  client-side (`offline-queue.js` for jobs, `offline-quote-queue.js` for
  quotes): each tries its CSRF-exempt JSON endpoint (`/api/jobs`,
  `/api/quotes`) with a timeout, and on failure queues the record in
  `localStorage`, shows a client-rendered plain-text summary (with a
  working WhatsApp link, and for quotes an email link too) immediately,
  and retries on the next page load or `online` event. A client-generated
  `clientId` UUID makes retries safe -- `JobService.createJobIdempotent()`
  / `QuoteService.createQuoteIdempotent()` return the existing record
  instead of writing a duplicate if the same `clientId` comes in twice
  (the real risk: a lost *response*, not a lost request). Both API
  endpoints are deliberately CSRF-exempt (see below) but still require
  the same login as everything else. The letterhead-styled, image-
  shareable quote preview is deliberately **not** what's shown offline --
  it needs the real server-rendered page, so the offline path trades that
  down to a plain-text summary rather than trying to replicate it
  client-side; the full experience is one tap away once the quote syncs.
  Aggregate screens (This Week, Who Owes Me) were deliberately **not**
  made to work offline -- they're reads over server data, and failing
  honestly with no signal is fine; only capturing the job/quote was worth the
  complexity.
- [x] **Credit supplier tagging + reconciliation report.** Not part of the
  original phase plan -- added because Dad has one regular supplier who
  extends credit and keeps his own tally, and there was no way to check
  what Dad agreed to pay against what the supplier later claims. A
  `LedgerEntry.partsSupplier` field (nullable, only ever set for
  PARTS_COST entries actually from that supplier) tags a job's parts cost
  when Dad checks a "Bought on credit from X" box on New Job or Edit Job
  -- shown only when `app.credit-supplier.name` is actually configured,
  since there's no supplier structure worth having until there's a real
  supplier to track. No separate "agreed price" field exists because the
  parts cost amount Dad already enters *is* that price. Tagging with no
  amount change (confirming a supplier after the fact, without correcting
  the cost) still writes a ledger row -- normally a zero-delta adjustment
  is a no-op, but a supplier tag is itself a fact worth a row even when
  the number doesn't move. `/suppliers/credit` (owner login only) lists
  everything tagged within a date range with a running total, for
  checking against the supplier's own invoice by hand -- deliberately not
  an auto-diff against a second, supplier-stated number; that's a bigger
  feature for if this turns out not to be enough.
- [ ] **Phase 8 — Later insights** (once real data exists). Regas-due list
  (per-vehicle, unlocked by Phase 1's `Vehicle` entity), monthly profit
  trend, busiest job type.

### What exists right now

| Route | Behavior |
|---|---|
| `GET /` | Redirects to `/quotes/new` |
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
| `GET /quotes/new` | New Quote form; shows open (unconverted) quotes to resume |
| `POST /quotes` | Creates a quote (no `LedgerEntry` written), redirects to its preview |
| `GET /quotes/{id}` | Professional quotation-document preview with a one-tap image share (native share sheet) plus WhatsApp/email text fallbacks; Convert to Job button if still open |
| `POST /quotes/{id}/convert` | Creates the job (charged the quote total, zero paid) and redirects to its Edit screen; idempotent |
| `GET /quotes/{id}/edit` | Edit Quote form, prefilled with current customer/vehicle/work type/items |
| `POST /quotes/{id}/edit` | Replaces the quote's items; if already converted, also pushes the new total onto the job's charge |
| `POST /api/jobs` | JSON, CSRF-exempt: idempotent-by-`clientId` job creation for the offline queue |
| `POST /api/quotes` | JSON, CSRF-exempt: idempotent-by-`clientId` quote creation for the offline queue |
| `GET /api/parts/suggestions` | JSON: distinct part names ever used on a quote |
| `GET /suppliers/credit` | Owner login only (`ROLE_OWNER`, 403 for "shop"): credit-supplier parts cost within a date range, with a running total |
| `GET /manifest.webmanifest`, `/sw.js` | PWA manifest and service worker |

### Known simplifications (intentional, not gaps)

- The quote document (`.quote-doc`) is one specific template, not a
  reusable `DocumentLayout` abstraction shared by future invoices/job
  sheets/receipts, and there's no pagination story for a long item table.
  Both were floated during the quote's design and deliberately deferred:
  there's exactly one document type in the app right now, so extracting a
  shared shape would be guessing at what's actually common between
  documents that don't exist yet. Worth revisiting the day a second
  document type is actually being built -- that's when the real shared
  shape becomes visible instead of assumed.
- `sw.js`'s `CACHE_NAME` is no longer a hand-maintained version string --
  it's a `${buildVersion}` placeholder, expanded by `build.gradle`'s
  `processResources` to `System.currentTimeMillis()` at build time, so
  every build gets a fresh cache name with no manual step to remember.
  This replaced a manually-bumped version number after it silently bit
  the project *twice*: once during the letterhead redesign, and again
  across four straight commits of the quote's visual redesign (paper-on-
  desk framing, invoice conventions, etc.) that all shipped correctly to
  the server but never reached an already-installed phone, because
  nobody remembered to bump the string. Two rounds of "I'll remember
  next time" was the signal that this needed to stop depending on memory
  at all -- see the comment in `sw.js` and `build.gradle`.
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
- Per-vehicle cost/profit isn't built yet (Phase 8) — when it is, it must be
  visibly flagged as approximate wherever a visit shared costs across
  multiple vehicles (§7 of the build plan).
- Converting a quote carries over the vehicle, work type, and computed
  total only — the itemized part/amount breakdown isn't copied to the job
  as line items, since `Job`'s `PARTS_COST` is still a single amount (per
  §3 of the build plan). The quote's total becomes the job's `CHARGE`; Dad
  can still log his own parts cost separately on the job if he wants to
  track it, unrelated to the customer-facing quote breakdown.
- `QuoteItem` rows are the source of the parts suggestion list
  (`/api/parts/suggestions`) — there's no separate "Part" catalog entity,
  since every quote line already records a clean part name.
- `/h2-console` requires login in dev; the `prod` profile disables it
  outright (`spring.h2.console.enabled=false`) since prod uses Postgres and
  a raw SQL browser has no business being reachable at all once real data
  exists.
- `/api/jobs` is exempt from CSRF, unlike every other POST endpoint. This is
  safe because it only accepts `application/json`, which browsers can't
  send cross-origin as a "simple request" -- it triggers a CORS preflight,
  and no CORS policy is configured to allow one, so the browser blocks it.
  It still requires the same session/remember-me login as everything else.
- The service worker only precaches New Job's static assets and the *last
  successfully-loaded* copy of `/jobs/new` itself -- it never precaches the
  page at install time, since the server-rendered HTML (CSRF token, recent
  customers, last entry) would go stale sitting in a cache. The offline
  submission path doesn't depend on that token anyway (see the CSRF point
  above), so a stale cached copy of the page still submits correctly.
- Manifest/icon links and the offline scripts are only on New Job and the
  login page -- the landing/entry screens, so those are the ones that
  matter for "add to home screen" and first impression. Every screen links
  `tokens.css` + `components.css` for consistent colors and controls, but
  only those two carry the manifest/offline scripts.
- `SecurityConfig` permits static assets (`/css/**`, `/images/**`,
  `/manifest.webmanifest`, icons, the JS/JSON files) without login --
  they're not per-user data, and the login page itself (rendered before
  authentication) needs the logo and tokens to look right.

---

## Deliberately Not Building Yet

Full double-entry accounting (named accounts, debits/credits), multi-user
logins, parts inventory, tax reports, charts. The schema is one column
(`accountId` on `LedgerEntry`) away from real double-entry if it's ever
needed.
