# Brief AA-19 — Gateway configuration panels, and backup/restore

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 8.6, 8.7, 9.5
**Follows:** AA-18 (`8b81e40`)

Three small items that between them clear the remaining hand-edit steps from a
deployment. None of them touch the query engine.

## Part 1 — Email gateway panel (8.7)

**Most of this already exists.** AA-10 built `OtpSmtpSettings`, which reads
`srse.otp.smtp.*` from `ConnectionOverrideStore` — the same properties file on a
mounted volume that connection settings use, so an edit survives container
recreation rather than only a restart. What is missing is a way to write it
without editing config by hand.

- Admin page section: host, port, username, password, from-address, TLS on/off.
- Saved through `ConnectionOverrideStore` under the existing prefix. **Do not add
  a second store.**
- **A "send a test message" action** that sends to the logged-in admin's own
  verified email and reports the result. A gateway first discovered to be broken
  when an officer cannot log in was configured too late.
- **The password is a secret.** It must be write-only in the UI (never returned
  to the browser) and must not appear in the AA-04 config export. AA-04 already
  establishes that for the DB2 and Presto passwords; this is the same rule and
  the export currently carries no SMTP fields, so the job is to keep it that way
  as the panel is added.
- **SuperAdmin only.** Changing where one-time passwords are delivered is exactly
  the move an attacker would make, so it sits with the SuperAdmin and is
  **audited** as an admin action.

## Part 2 — SMS gateway panel (8.6)

The same panel for the SMS gateway: endpoint, credentials, sender ID, test
action, write-only secret, SuperAdmin only, audited.

**The sender itself stays unimplemented** (7.1a.7, waiting on a contract). Build
the form and persistence so that when credentials arrive the only remaining work
is the `OtpSender` implementation. The test action should report clearly that no
SMS sender is configured rather than appearing to succeed.

Do not guess at a provider's API shape. Store the fields a gateway generally
needs and leave the request format to the implementation.

## Part 3 — Backup and restore (9.5)

The operational database now holds seventeen tables, including users, scope
assignments, saved queries and the audit log. This item is mostly a **documented,
tested procedure** rather than a feature — but three consequences of *restoring*
need writing down, because none of them is obvious and all are security-relevant.

Produce `docs/BACKUP_AND_RESTORE.md` covering PostgreSQL (`pg_dump` /
`pg_restore`) and DB2, and **actually perform a restore into an empty database**
to prove the procedure, rather than documenting it from the manual.

Three things the document must say plainly:

1. **A restore rolls the audit log backwards.** The log is append-only by design
   (A9), and restoring an earlier snapshot silently discards every entry written
   since. That is a gap in a record whose whole value is completeness, so a
   restore is itself an event worth recording — by hand if necessary — and
   whoever restores must know what window they have lost.
2. **A restore resurrects revoked access.** A user deactivated after the backup
   comes back active. A scope grant revoked after the backup returns. A password
   changed after the backup reverts to the old one, which may be the one that
   prompted the change. After any restore, someone must re-check user status,
   role and scope assignments against what they should be.
3. **`DATABASECHANGELOG` travels with the data.** Restoring a backup taken under
   an older application version restores its migration state too, and the newer
   application will then either apply the missing changesets or fail validation.
   Say which combinations are safe: restore into the *same or older* application
   version, then upgrade forward.

Also note what is **not** in a database backup and must be captured separately:
the `ConnectionOverrideStore` properties file on its mounted volume, which after
Parts 1 and 2 holds the gateway credentials as well as the connection ones.

## Part 4 — Tests

- SMTP settings round-trip through `ConnectionOverrideStore` and are read back by
  `OtpSmtpSettings`.
- The password is never present in any response body, and never in the AA-04
  config export — assert on the serialised JSON, as AA-04 does.
- A non-SuperAdmin is refused on both panels.
- Saving either gateway writes an audit row.
- The SMS test action reports "not configured" rather than success.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17**, with the operational
  database up so `OperationalSchemaMatchesEntitiesTest` actually runs rather than
  skipping. Report the count — **511**.
- `npm run build` / `npm run test` clean.
- Live: configure SMTP through the panel, send a test message, then restart the
  backend and confirm the settings survived — that is the whole point of using
  the override store.
- Live: take a backup, restore it into an empty database, and start the
  application against the restored copy. Report what you had to do beyond the
  documented steps.

## Do not

- Do not add a second settings store.
- Do not return either gateway password to the browser.
- Do not let gateway credentials into the config export.
- Do not document a restore procedure you have not run.
