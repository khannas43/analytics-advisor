# Brief AA-10 — MFA by one-time password (everything but the SMS gateway)

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 7.1a.1–7.1a.6, 7.1a.8
**Follows:** AA-09 (`96c62a2`)

## Scope

MFA is **required, per-user, admin-controlled**, delivered as an OTP over **SMS
and email both** — one code, two delivery paths, one validity window.

**In scope:** contact verification, code generation and storage, the login
verification step, recovery, rate limiting, the `OtpSender` seam, a log-only
sender and an SMTP sender.

**Out of scope:** the **SMS gateway sender** (7.1a.7) — it needs a contract and
credentials that do not exist yet. Build the seam so it drops in; do not stub
something that pretends to send.

AA-06 already ships `mfa_required`, `email`, `mobile`, `email_verified` and
`mobile_verified` on `app_user`, and AA-07 already refuses to set
`mfa_required` on a user missing a contact. Build on those.

## Part 0 — The trap, stated first

**A user who has passed the password check but not the OTP must hold nothing
that works.**

The standard way to get this wrong is to issue the session token after the
password, then "remember" that MFA is outstanding. Anything that treats that
token as authenticated is a complete MFA bypass, and it will not show up in a
happy-path test because the happy path always completes the second step.

So: password success with `mfa_required` issues a **challenge**, not a session.
The challenge must be a distinct type that `SessionBearerAuthenticationFilter`
does not accept, usable only at the verify endpoint. It must not be accepted by
`/api/auth/change-password` either — the forced-password-change path is
authenticated and must sit *after* the second factor, not beside it.

Write the test that proves a challenge is rejected by an ordinary endpoint
before writing the flow.

## Part 1 — Code generation and storage

```
user_otp_challenge   id, user_id, code_hash, purpose, created_at, expires_at,
                     consumed_at, attempt_count, delivery_status
```

- **Hash the code** (BCrypt or SHA-256 with the user id as salt — it is short
  lived and high entropy is not available, so the point is only that a database
  reader cannot replay it). **Never store or log the digits.**
- Six digits, cryptographically random (`SecureRandom`, not `Math.random`).
- **Expires in 5 minutes, single use.** Mark `consumed_at` on success and reject
  a reuse.
- `purpose` distinguishes **login** from **contact verification** — a code
  issued to prove an email address must not log anybody in.
- One active challenge per user per purpose; issuing a new one supersedes the
  old.

## Part 2 — One code, both channels

Generate once, store once, deliver twice.

- Send the **same** code to the registered mobile and email in one operation,
  under one expiry.
- **Never two codes.** Two codes with two windows is a race, and whichever
  arrives second invalidates the one the user is typing — which reads to them as
  the system rejecting a correct code.
- **Delivery failure must be visible.** If **every** channel fails, the login
  attempt fails with a message saying the code could not be sent. It must never
  sit waiting for a code nobody sent. If one channel succeeds, proceed and record
  which.

## Part 3 — `OtpSender` seam

An interface with three implementations, selected by configuration:

| Implementation | When |
|---|---|
| **Log-only** | Default locally. Writes the code to the application log so development works with no gateway at all. |
| **SMTP** | Real email. Configuration comes from the admin page's gateway section, persisted through `ConnectionOverrideStore` under its own prefix, exactly as connection settings already are. |
| **SMS** | **Not in this brief.** Leave the interface and a clearly unimplemented binding that fails loudly if selected. |

The log-only sender is what keeps a laptop build independent of an SMS contract,
the same reason `AuthMode=mock` exists. **It must be impossible to select by
accident in a real deployment** — refuse to start if the log-only sender is
configured while `auth-mode=local` and the app is not on the `local` profile,
or equivalent. A deployment quietly writing OTPs to a log file is worse than one
that will not start.

**Secrets:** the SMTP password is a secret. It must not appear in the admin
config export — AA-04 established that for the DB2 and Presto passwords, and
the same masking applies.

## Part 4 — Contact verification (7.1a.2)

There is no enrollment for a delivered OTP — no shared secret to exchange. What
must be established is that both addresses reach the person.

- An endpoint to send a verification code to the user's own email, and one for
  mobile; verifying sets `email_verified` / `mobile_verified`.
- **Changing an email or mobile clears its verified flag**, always. An unverified
  contact on an MFA-required account must block login with a message telling the
  user to contact an admin — not silently skip the factor.
- AA-07's rule 6 already refuses to set `mfa_required` without verified
  contacts; confirm it still holds once the flags can actually change.

## Part 5 — Login flow

1. Username and password → invalid credentials behave exactly as now
   (indistinguishable, dummy hash on the unknown-user path — do not regress
   this).
2. If `mfa_required` is false → issue the session token as today. **Byte-identical
   behaviour for non-MFA users.**
3. If true → generate, deliver, return a **challenge** plus which channels were
   used (masked: `•••••1234`, `j•••@x.gov`). Never echo the full contact.
4. `POST /api/auth/verify-otp` with the challenge and the code → session token.
5. Wrong code increments `attempt_count`; after a configurable number the
   challenge is dead and the user must start again. Lock the account per the
   existing lockout policy rather than inventing a second one.
6. **Resend** with a cooldown. Non-delivery is common enough that "send another"
   must be an ordinary action, not a dead end — but it must not be a free
   oracle either.

## Part 6 — Recovery (7.1a.4)

A changed number or a dead mailbox locks the officer out, and only an admin can
move them.

- An admin corrects the contact and re-verifies. This is the entire recovery
  path.
- **Audit every such change.** Editing where a second factor is delivered is
  precisely the move an attacker would make, and §7.3's settled event set
  already covers admin actions that change who can see what — this is one.
- Respect AA-07's rules: an admin may only do this for a user inside their
  subtree, and never for a SuperAdmin.

## Part 7 — Tests

- **Part 0's trap:** a challenge rejected by an ordinary endpoint, by
  `change-password`, and by anything using the session filter.
- One code, not two: a single stored challenge with both deliveries attempted.
- All channels fail → login fails with a message, no hanging state.
- Expiry, single use, reuse rejected, wrong purpose rejected.
- Attempt limit, and resend cooldown.
- Changing an email clears `email_verified`.
- `mfa_required = false` → login path byte-identical to today.
- The code never appears in a response body or in any log except the log-only
  sender.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17**. Report the count — **430**.
- `npm run build` / `npm run test` clean.
- Live against PostgreSQL in `local` mode with the log-only sender: enable MFA
  for a user with verified contacts, log in, confirm the challenge alone reaches
  nothing, read the code from the log, verify, and reach a scoped endpoint. Then
  confirm a second use of the same code is refused.
- Report anything you could not test without a real gateway.

## Do not

- Do not issue a session token before the second factor.
- Do not generate two codes.
- Do not store or log the digits outside the log-only sender.
- Do not implement an SMS sender against a guessed API.
