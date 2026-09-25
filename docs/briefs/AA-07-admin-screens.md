# Brief AA-07 — Admin screens for users, roles and scopes

**Repo:** `analytics-advisor` · **Covers:** `PRODUCT_PLAN.md` 7.1.5, and settles 7.1.6
**Follows:** AA-06 (`f1d9470`)

## Why

AA-06 built the model and local authentication, but left no way to use it. There
is no admin API at all — only `/api/auth/login` and `/api/auth/change-password`
— so the bootstrap SuperAdmin is the *only* account that can ever exist, there
is no way to create a scope node, and in `local` mode the browser has no login
screen. This brief closes all three.

It is large. If it needs splitting, split at the Part 3 / Part 4 line: backend
first, frontend second. Do not split inside Part 3 — the authorization rules
only make sense together.

## Part 0 — Settle 7.1.6: admins are scoped

**Decision: yes, an Admin is scoped; a SuperAdmin is not.** This is the security
core of the brief and everything else depends on it.

The reasoning is a privilege-escalation one. If an Admin can assign any scope
node, then a Jaipur district admin can grant themselves — or anyone — the
geography root, and the entire scoping model becomes decorative. Scoping that
any admin can opt out of protects nobody.

So:

- **`SUPER_ADMIN`** — unscoped. Sees and manages every user, every node, every
  dimension. Created only by bootstrap or by another SuperAdmin.
- **`ADMIN`** — scoped by their own assignments. May manage users *within their
  own subtree* and grant *only scopes they themselves hold*.
- **`OFFICER`** — no admin rights at all.

## Part 1 — Hierarchy admin API

Nothing can create a `scope_dimension`, `scope_level` or `scope_node` today, so
there is nothing to assign. Add CRUD under `/api/admin/scope/**`
(**`SRSE_ADMIN`** authority, already enforced by the `/api/admin/**` matcher).

- Dimensions and levels: **SuperAdmin only.** Adding a dimension changes what
  every user can reach — recall that an absent assignment means *no* access, so
  a new dimension locks everyone out of it until granted. That is deliberate
  (PRODUCT_PLAN §7.1 rule 3) and it is not a district admin's decision.
- Nodes: an Admin may create nodes **under a node they hold**. A SuperAdmin
  anywhere.
- **`path` is derived, never supplied by the caller.** Compute it from the
  parent's path plus the node code, and **always store it with a trailing
  separator** — AA-06's evaluator normalises both sides, but storing it
  consistently is what keeps the data sane. Reject a code containing `/`.
- **Deleting a node** that has descendants or assignments must be **refused with
  a message naming what blocks it**, not cascaded. Silently removing assignments
  changes who can see what, invisibly. Offer deactivation if a node needs
  retiring; do not add a delete that works around this.
- Renaming a node is fine. **Re-parenting is not in this brief** — it rewrites
  every descendant path and every assignment underneath, and deserves its own
  thinking.

## Part 2 — User admin API

Under `/api/admin/users/**`. `UserProvisioningService` already exists; give it a
controller and fill the gaps.

| Endpoint | Notes |
|---|---|
| `GET /api/admin/users` | Filtered by the caller's scope — see Part 3 |
| `POST /api/admin/users` | Username, email, mobile, roles, initial password |
| `PUT /api/admin/users/{id}` | Email, mobile, roles |
| `POST /api/admin/users/{id}/deactivate` and `/reactivate` | **No DELETE, ever** (7.1c) |
| `POST /api/admin/users/{id}/reset-password` | Admin-set; sets `must_change_password` |
| `PUT /api/admin/users/{id}/scopes` | Replace the user's assignment set |

- An initial or reset password always lands with `must_change_password = true`.
- **Never return `password_hash`** in any response. AA-06 kept it out of every
  DTO; keep it that way.
- Creating a user with **no scope assignments is allowed** — they simply see no
  data, which is deny-by-default working correctly. Do not invent a default
  assignment to make the screen feel complete.
- `mfa_required` is editable here, but see the guard in Part 3.

## Part 3 — Authorization rules (the part that matters)

These are not UI conveniences. Enforce every one **server-side**, and test each.
A UI that hides a button is not a control.

1. **An admin may never grant a scope they do not themselves hold.** Every node
   in a submitted assignment set must be within the caller's own subtree.
   SuperAdmin is exempt.
2. **An admin may only see and manage users inside their subtree.** A user
   qualifies if *every* assignment they hold is within the caller's subtree — not
   "any", or a Jaipur admin could edit a user who also holds Alwar and thereby
   reach outside their scope.
3. **Only a SuperAdmin may grant or revoke `SUPER_ADMIN`.**
4. **Nobody may edit their own roles or scopes**, SuperAdmin included. Changing
   your own privileges is the escalation path that defeats the other three, and
   it costs nothing to forbid. A second SuperAdmin exists for genuine changes.
5. **Nobody may deactivate themselves**, and the **last active SuperAdmin cannot
   be deactivated or demoted** — that bricks the installation with no way back,
   exactly like the bootstrap MFA trap.
6. **An admin may not set `mfa_required` on a user lacking a verified mobile or
   email** (7.1a). The UI must refuse rather than create an account that cannot
   log in.
7. A user with **no roles** is valid and can log in, seeing nothing.

Rule 2 is the one most likely to be got subtly wrong, and it fails silently in
the permissive direction. Test it with a user holding assignments in two
dimensions and two subtrees.

## Part 4 — Login and forced password change (frontend)

In `local` mode the browser currently cannot authenticate at all —
`authToken.ts` calls `/api/auth/mock-login`, which only exists in mock mode.

- A **login page** posting to `/api/auth/login`, storing the token the way
  `authToken.ts` already stores the mock one. Keep the mock path working when
  `NEXT_PUBLIC_AUTH_MODE=mock`; do not replace it.
- A **forced password-change screen**. AA-06 returns `mustChangePassword` on the
  login response and blocks every protected endpoint with 403 until it is done,
  so without this screen the bootstrap SuperAdmin cannot use the product at all.
  I verified that 403 behaviour against a running backend — it is real, not
  theoretical.
- On 401, send the user back to login rather than showing a raw error.

## Part 5 — Admin screens

- **Users**: list, create, edit, deactivate/reactivate, reset password, assign
  roles, assign scopes. The scope picker walks the dimension → level → node tree
  and must show **only nodes the caller may grant** (Part 3 rule 1).
- **Hierarchy**: manage dimensions, levels and nodes, within the same limits.
- `/admin456` currently has **no route guard** and is unlinked. Now that real
  roles exist, give it one and add proper navigation. Being unlinked was never a
  control; the server-side checks were.

Follow the existing admin page's conventions rather than inventing a new style.

## Part 6 — Tests

- Every Part 3 rule, server-side, with the request bypassing the UI.
- Node deletion refused when descendants or assignments exist.
- Path derivation: child of `/G/JAIPUR/` gets `/G/JAIPUR/SANGANER/`, with the
  trailing separator.
- Last-SuperAdmin protection.
- Extend `OperationalStoreLiquibaseIT` if any changeset is added.
- Frontend: keep `npm run test` green.

## Acceptance

- `mvn -f backend/pom.xml test` green on **JDK 17** (no root pom). Report the
  count — it was **375**.
- `npm run build` and `npm run test` clean.
- Against a running backend on PostgreSQL in `local` mode: log in as the
  bootstrap SuperAdmin, be forced to change the password, create a dimension, a
  level and two nodes, create a scoped Admin, and confirm **that Admin cannot
  grant a node outside their own subtree** — by calling the API directly, not
  just by the button being hidden.
- Report which of Part 3's seven rules have tests.

## Do not

- Do not add a user DELETE endpoint.
- Do not implement the scope predicate on queries — still 7.2.
- Do not touch `analysis/`, `compiler/` or `lakehouse/`.
