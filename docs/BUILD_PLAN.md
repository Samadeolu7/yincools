# AC Tech Tracker — Build Plan

A mobile-friendly (PWA) job & money tracker for a car-AC repair business, built in
Java/Spring Boot. Designed so the *user* (Dad) only ever sees simple forms, while
the *system* underneath keeps a provably correct financial record forever —
including when he changes his mind about a number.

---

## 1. The Core Principle

> **Nobody ever edits money. They only ever add a new fact about it.**

Dad will fat-finger amounts, forget a payment, or realize a job was billed wrong.
That's normal and must never be a blocker. The system's job is to absorb every
correction silently and stay mathematically correct without him — or you —
having to think about it.

The way we achieve this: **all money lives in one append-only table
(`LedgerEntry`)**. Nothing is ever `UPDATE`d or `DELETE`d there. A "correction" is
just another row. Every number Dad sees (balance owed, weekly total, profit) is
a **sum over that table**, never a stored value someone has to keep in sync.

This one rule is what makes the system:
- **Self-correcting** — editing a job can never desync two different numbers,
  because there's only one source they're both computed from.
- **Auditable** — you can always answer "why does this say ₦12,000?" by listing
  the rows that sum to it.
- **Expandable for free** — a new insight (monthly profit, busiest job type,
  regas-due list) is a new *read-only query* against the same table. The write
  path never changes, so old features can't break when you add new ones.

---

## 2. Architecture

Three layers, strict one-way dependency (`web` → `domain` → `persistence`):

```
domain/          Plain Java. No web/HTTP concerns.
  model/           Customer, Job, LedgerEntry, EntryType
  LedgerService     the ONLY thing allowed to write a LedgerEntry
  JobService        Dad-facing operations (createJob, editJob, addPayment...)
  InsightService    read-only queries (weekly summary, debtors, later: regas due)

persistence/      Spring Data repositories. Dumb. No business logic.

web/              Thymeleaf controllers + templates. Talk only to domain/ services.
```

**Rule that matters most:** `LedgerRepository` exposes `save()` for inserts only.
No `update`, no `delete` methods are ever written for it. If a bug ever tries to
mutate a ledger row, it should fail to compile, not fail silently at 2am.

Why this layering, concretely: if you later build a native Android app, or
someone else wants an API, `domain/` doesn't change at all — you write a new
`web/`-equivalent adapter on top of the same services.

---

## 3. Data Model

```java
enum EntryType { CHARGE, PAYMENT, PARTS_COST, SHOP_EXPENSE }

@Entity
class LedgerEntry {
    @Id @GeneratedValue Long id;

    @Enumerated(EnumType.STRING)
    EntryType type;

    BigDecimal signedAmount;   // usually positive; corrections may be negative
    Long jobId;                // null for SHOP_EXPENSE
    Long customerId;           // null for SHOP_EXPENSE
    LocalDate date;
    String note;               // optional, human-readable, e.g. "corrected amount"
    boolean isCorrection;      // true if this row adjusts an earlier fact
    Instant createdAt;         // immutable — never touched after insert
}

@Entity
class Customer {
    @Id @GeneratedValue Long id;
    String name;
    String phone;
}

@Entity
class Job {
    // This is a READ CACHE, not a source of truth. It exists purely so
    // screens load fast without summing the ledger every time. It is only
    // ever written by LedgerService, and can be fully rebuilt from
    // LedgerEntry at any time (that rebuild IS the balance-check job — see §8).
    @Id @GeneratedValue Long id;
    Long customerId;
    String vehicleDescription;   // "Camry 2010"
    String workType;             // REGAS, COMPRESSOR, CONDENSER, FAN, DIAGNOSIS, OTHER
    LocalDate date;

    BigDecimal cachedCharge;
    BigDecimal cachedPaid;
    BigDecimal cachedBalance;    // cachedCharge - cachedPaid
}
```

`ShopExpense` doesn't need its own table — it's just a `LedgerEntry` of type
`SHOP_EXPENSE` with `jobId = null`.

---

## 4. The Correction Mechanism — the part you asked about

This is the piece that makes the whole thing work. One method, used for every
possible edit:

```java
// domain/LedgerService.java
public void adjust(EntryType type, Long jobId, Long customerId,
                    BigDecimal newTotal, String note) {

    BigDecimal currentNet = ledgerRepo.sumSignedAmount(jobId, type); // 0 if none yet
    BigDecimal delta = newTotal.subtract(currentNet);

    if (delta.compareTo(BigDecimal.ZERO) == 0) return; // nothing changed, no-op

    LedgerEntry entry = new LedgerEntry();
    entry.setType(type);
    entry.setSignedAmount(delta);
    entry.setJobId(jobId);
    entry.setCustomerId(customerId);
    entry.setDate(LocalDate.now());
    entry.setNote(note);
    entry.setIsCorrection(currentNet.compareTo(BigDecimal.ZERO) != 0);

    ledgerRepo.save(entry); // INSERT only — nothing else touches this table
    jobCacheService.refresh(jobId);
}
```

**Walkthrough.** Dad creates a job and charges ₦15,000:

| # | type   | signedAmount | note              |
|---|--------|--------------|-------------------|
| 1 | CHARGE | +15,000      | initial entry     |

Net charge for the job = 15,000. Two weeks later he realizes it should've been
₦12,000 and edits the job. `adjust(CHARGE, job, customer, 12000, ...)` runs:
`currentNet` = 15,000, `delta` = 12,000 − 15,000 = **−3,000**:

| # | type   | signedAmount | note              |
|---|--------|--------------|-------------------|
| 1 | CHARGE | +15,000      | initial entry     |
| 2 | CHARGE | −3,000       | corrected entry   |

Net charge = 12,000. Correct, automatically, with zero special-case code. He
can do this ten more times and it stays correct — every insight query is
still just `SUM(signedAmount)`, so nothing anywhere needs to know a
correction ever happened.

**From Dad's side**, none of this is visible. He opens "Edit job," changes the
amount field, taps Save. There's no concept of "reversal" or "ledger" in the
UI at all — that vocabulary stays entirely inside `domain/`. `JobService`
translates his simple edit into the right `adjust()` calls:

```java
// domain/JobService.java
public void editJob(Long jobId, BigDecimal newCharge, BigDecimal newPartsCost) {
    Job job = jobRepo.findById(jobId);
    ledgerService.adjust(CHARGE, jobId, job.getCustomerId(), newCharge, null);
    ledgerService.adjust(PARTS_COST, jobId, job.getCustomerId(), newPartsCost, null);
}

public void recordPayment(Long jobId, BigDecimal amountPaidTotal) {
    Job job = jobRepo.findById(jobId);
    ledgerService.adjust(PAYMENT, jobId, job.getCustomerId(), amountPaidTotal, null);
}

public void voidJob(Long jobId) {
    // "delete" = zero everything out, ledger keeps the full history
    Job job = jobRepo.findById(jobId);
    ledgerService.adjust(CHARGE, jobId, job.getCustomerId(), BigDecimal.ZERO, "voided");
    ledgerService.adjust(PAYMENT, jobId, job.getCustomerId(), BigDecimal.ZERO, "voided");
    ledgerService.adjust(PARTS_COST, jobId, job.getCustomerId(), BigDecimal.ZERO, "voided");
}
```

Note `recordPayment` takes the **new total paid**, not "add this payment" —
so if he mis-typed a payment amount, editing it works exactly the same way as
editing a charge. One mental model for every kind of correction.

You (not Dad) get a hidden `/admin/job/{id}/history` screen later that just
lists the raw `LedgerEntry` rows for a job — useful for debugging, never
shown in his normal flow.

---

## 5. Services Summary

| Service | Responsibility |
|---|---|
| `LedgerService` | Only thing that writes `LedgerEntry`. `record()` for new facts, `adjust()` for corrections, `netFor()` for sums. |
| `JobService` | Dad-facing verbs: createJob, editJob, recordPayment, voidJob. Translates into LedgerService calls + refreshes the Job cache. |
| `InsightService` | Read-only. weeklySummary(), debtorList(). Later: regasDue(), monthlyProfit() — new methods, never touches existing ones. |

---

## 6. Screens → Services

| Screen | Calls |
|---|---|
| New job | `JobService.createJob(...)` → `LedgerService.record(CHARGE...)`, `record(PARTS_COST...)`, `record(PAYMENT...)` |
| Edit job | `JobService.editJob(...)` / `recordPayment(...)` |
| Receipt / WhatsApp share | reads `Job` cache, formats text, `wa.me/<phone>?text=` link |
| This week | `InsightService.weeklySummary()` |
| Who owes me | `InsightService.debtorList()` |
| Shop spending | `LedgerService.record(SHOP_EXPENSE...)` |

---

## 7. Making It Easy Enough To Actually Use

The ledger guarantees correctness; it doesn't guarantee Dad opens the app.
Every extra tap between him finishing a job and the record existing is a
reason to skip logging it — so this section is arguably as important as the
ledger itself, because it's what determines whether there's any data to be
correct *about*.

- **Kill typing wherever possible.** Numeric keypad only on amount fields
  (`inputmode="numeric"`). Quick-pick buttons for common amounts (₦5,000 /
  ₦10,000 / ₦15,000 / ₦20,000) that fill the field in one tap, still editable.
  A "recent customers" list above search, so repeat customers are a tap, not
  a re-typed phone number. Prefill vehicle from the customer's last job.
  Notes field stays plain text so the phone's built-in mic/voice-to-text
  works with zero build effort.
- **Only require what's essential.** Work type, amount charged, amount paid —
  that's it. Customer name, phone, vehicle are optional. A half-filled record
  that exists beats a complete one he skipped because he was busy. Nudge him
  toward filling in the phone number later, once he trusts the app enough to
  want the receipt-sharing feature it unlocks.
- **Land on New Job, not a dashboard.** He does this 15–20x/day; the insight
  screens he checks occasionally. Zero navigation to the thing he does
  constantly. If the PWA install supports it on his phone, a home-screen
  shortcut jumps straight past any menu.
- **Catch mistakes right after they happen.** A "Last entry" card pinned to
  the top of the New Job screen right after save, one tap to fix it. Most
  mistakes get caught seconds later, while he still remembers the right
  number — same `adjust()` mechanism from §4, just surfaced at the moment
  it's useful, instead of making him hunt through a job list.
- **Design for the physical environment.** Large buttons, high contrast —
  this gets checked in bright workshop sunlight, not a quiet office.
- **Let the receipt be the hook, not a reminder nag.** The receipt already
  makes him look more professional than typing it wouldn't — that's what
  pulls him back to the app, not notifications. If you want a nudge at all,
  one soft end-of-day "log today's jobs?" ping is enough. No streaks, no guilt.

---

## 8. Offline-First Design

Workshop connectivity here is spotty, not just occasionally down, so **losing
a job because there was no signal is not acceptable.** Confirmed device:
**Samsung A17 (Android)** — Chrome and Samsung Internet both have solid,
mature service worker support, so there's no iOS-Safari-style storage
eviction risk to design around.

**Be selective about what has to work offline.** Don't try to replicate
`LedgerService`'s math in JavaScript — that would create a second source of
truth and break the whole premise of §1. Only the thing that would otherwise
be *lost* needs to survive zero signal: **capturing the job.** Aggregate
screens ("This week," "Who owes me") are reads over server data — offline,
they just show "last updated 2 hours ago" rather than pretending to be live.
That's an honest failure mode, not a broken one.

**Mechanism:**

1. A **service worker** caches the New Job page's HTML/CSS/JS on first load,
   so it opens instantly with zero network from then on.
2. On submit, JS tries `fetch()` POST to a small JSON endpoint. If that fails
   or times out, the job is written to a **local queue** instead
   (`localStorage` is plenty — a handful of pending jobs at most).
3. Dad sees "Saved" either way. A small badge — "2 unsynced" — sits quietly
   near the top, informational, not a warning.
4. When the app reopens with a connection, or a `navigator.onLine` event
   fires, it walks the queue and POSTs each pending job.

**The one real trap: duplicates.** If a POST actually succeeded server-side
but the phone lost signal before the response arrived, a naive retry creates
the job twice. Fixed by generating a UUID client-side *before* the first
attempt, and having the server treat that ID as the request's identity:

```java
// web/JobApiController.java
@PostMapping("/api/jobs")
public ResponseEntity<?> createJob(@RequestBody JobRequest req) {
    if (jobRepo.existsByClientId(req.getClientId())) {
        return ResponseEntity.ok(jobRepo.findByClientId(req.getClientId())); // already processed
    }
    Job job = jobService.createJob(req); // stores req.getClientId() on the Job row
    return ResponseEntity.ok(job);
}
```

That one check makes retries completely safe — sync as many times as needed,
worst case is a wasted network call, never a duplicate job.

**Receipts still work offline** — generate the receipt text in JS from the
form data at the moment of save, not after a server round-trip, so he can
share it on WhatsApp immediately. WhatsApp queues the send itself if he's
got no signal at that moment.

**Deliberately skipped:** the Background Sync API. It's the "textbook"
approach, but support is inconsistent enough across devices that a simple
"retry on open + retry on reconnect" covers the real case — a workshop with
intermittent signal — just as well, with far less complexity.

---

## 9. Build Phases

**Phase 0 — Skeleton.** Spring Boot project, entities, repositories, H2 for
local dev / Postgres on your VPS for real use.

**Phase 1 — Ledger core (build this first, alone, and test it hard).**
`LedgerService.record()`, `adjust()`, `netFor()`. Write unit tests that create
a job, correct it three times, and assert the net is always right. This phase
has no UI at all — get the math bulletproof before anyone touches a screen.

**Phase 2 — Job entry + receipt.** New job form with the friction-reducing
UX from §7 (quick-pick amounts, recent customers, minimal required fields),
`JobService.createJob`, receipt render, WhatsApp share link.

**Phase 3 — Edit/correction flow.** Edit job screen, pre-filled with current
cache values, plus the "Last entry" quick-fix card. Save always succeeds, no
confirmation dialogs, no validation friction beyond "is this a number."

**Phase 4 — Insights.** This week screen, Who owes me screen.

**Phase 5 — Shop expenses screen.**

**Phase 6 — PWA + offline.** Manifest + icon for home-screen install, PIN
login with long-lived cookie, the full offline mechanism from §8 (service
worker, local queue, client-generated UUIDs, offline-generated receipts).

**Phase 7 — Later, once real data exists (~10 months in).** Regas-due list,
monthly profit trend, busiest job type — all new `InsightService` methods,
zero changes to Phases 0–6.

---

## 10. The Safety Net

A scheduled job (nightly, or on-demand from an admin button) that verifies
the offline sync and correction paths from §§7–8 never let the cache drift:

1. Recomputes every `Job.cachedBalance` from scratch by summing `LedgerEntry`.
2. Compares it to the stored cache value.
3. Logs (doesn't silently fix) any mismatch.

Because only `LedgerService` is ever allowed to write, and it always refreshes
the cache after writing, this should **never** find a mismatch. If it ever
does, that's a real bug to look at immediately — not something a customer
complaint surfaces three months later.

---

## 11. Stack Recap

- **Spring Boot + Thymeleaf**, installed as a PWA (home-screen icon, opens
  fullscreen) — no native Android learning curve, and you can ship updates
  instantly instead of re-sending an APK.
- **PostgreSQL** on your existing VPS, behind the Traefik setup you already run.
- **PIN + long-lived cookie** auth — no email/password for Dad to forget.
- **WhatsApp share via `wa.me` links** — zero API integration needed.
- **Target device: Samsung A17 (Android)** — Chrome/Samsung Internet, solid
  service worker support, no special-casing needed for the offline design in §8.

---

## 12. Deliberately Not Building Yet

Full double-entry accounting (named accounts, debits/credits), multi-user
logins, parts inventory, tax reports, charts. The schema above is one column
(`accountId` on `LedgerEntry`) away from real double-entry if you ever need
it — but building that now solves a problem Dad doesn't have.
