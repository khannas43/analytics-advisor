"use client";

import { useCallback, useEffect, useState } from "react";
import {
  createDimension,
  createLevel,
  createScopeNode,
  deleteScopeNode,
  listDimensions,
  listGrantableNodes,
  listLevels,
  listScopeNodes,
  type DimensionView,
  type LevelView,
  type NodeView,
} from "@/lib/scopeAdminApi";
import {
  createUser,
  deactivateUser,
  listUsers,
  reactivateUser,
  replaceUserScopes,
  resetUserPassword,
  type UserSummary,
} from "@/lib/userAdminApi";
import { fetchAuthSession, type AuthSession } from "@/lib/authToken";

const ROLE_OPTIONS = ["SUPER_ADMIN", "ADMIN", "OFFICER", "AUDIT_READER"] as const;

export function AdminIdentityPanel() {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [tab, setTab] = useState<"users" | "hierarchy">("users");
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [dimensions, setDimensions] = useState<DimensionView[]>([]);
  const [levels, setLevels] = useState<LevelView[]>([]);
  const [nodes, setNodes] = useState<NodeView[]>([]);
  const [grantable, setGrantable] = useState<NodeView[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [newUser, setNewUser] = useState({
    username: "",
    email: "",
    mobile: "",
    initialPassword: "",
    roles: ["OFFICER"] as string[],
    scopeNodeIds: [] as number[],
  });

  const [dimForm, setDimForm] = useState({ code: "", name: "", displayOrder: 1 });
  const [levelForm, setLevelForm] = useState({ dimensionId: 0, depth: 1, name: "" });
  const [nodeForm, setNodeForm] = useState({
    dimensionId: 0,
    levelId: 0,
    parentId: "" as string,
    code: "",
    name: "",
  });

  const reload = useCallback(async () => {
    setError(null);
    const s = await fetchAuthSession();
    setSession(s);
    const [u, d, g] = await Promise.all([listUsers(), listDimensions(), listGrantableNodes()]);
    setUsers(u);
    setDimensions(d);
    setGrantable(g);
    const dimId = nodeForm.dimensionId || d[0]?.id;
    if (dimId) {
      const [lv, nd] = await Promise.all([listLevels(dimId), listScopeNodes(dimId)]);
      setLevels(lv);
      setNodes(nd);
      setLevelForm((f) => ({ ...f, dimensionId: dimId }));
      setNodeForm((f) => ({ ...f, dimensionId: dimId, levelId: lv[0]?.id ?? 0 }));
    }
  }, [nodeForm.dimensionId]);

  useEffect(() => {
    reload().catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)));
  }, [reload]);

  async function onCreateUser() {
    setMessage(null);
    try {
      await createUser({
        username: newUser.username,
        email: newUser.email || undefined,
        mobile: newUser.mobile || undefined,
        initialPassword: newUser.initialPassword,
        roles: newUser.roles,
        scopeNodeIds: newUser.scopeNodeIds,
      });
      setMessage("User created.");
      await reload();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  async function onCreateDimension() {
    if (!session?.superAdmin) {
      setError("SuperAdmin only");
      return;
    }
    await createDimension(dimForm);
    setMessage("Dimension created.");
    await reload();
  }

  async function onCreateLevel() {
    if (!session?.superAdmin) {
      setError("SuperAdmin only");
      return;
    }
    await createLevel(levelForm);
    setMessage("Level created.");
    await reload();
  }

  async function onCreateNode() {
    await createScopeNode({
      dimensionId: nodeForm.dimensionId,
      levelId: nodeForm.levelId,
      parentId: nodeForm.parentId ? Number(nodeForm.parentId) : null,
      code: nodeForm.code,
      name: nodeForm.name,
    });
    setMessage("Node created.");
    await reload();
  }

  return (
    <div style={{ marginBottom: "1.5rem" }}>
      <div className="srse-nav" style={{ marginBottom: "1rem" }}>
        <button
          type="button"
          className={tab === "users" ? "srse-nav-link active" : "srse-nav-link"}
          onClick={() => setTab("users")}
        >
          Users &amp; scopes
        </button>
        <button
          type="button"
          className={tab === "hierarchy" ? "srse-nav-link active" : "srse-nav-link"}
          onClick={() => setTab("hierarchy")}
        >
          Scope hierarchy
        </button>
      </div>
      {error ? <p className="srse-error-text">{error}</p> : null}
      {message ? <p className="srse-muted">{message}</p> : null}

      {tab === "users" ? (
        <section className="srse-card" style={{ marginBottom: "1rem" }}>
          <h2 className="srse-card-title">Users</h2>
          <table className="srse-table">
            <thead>
              <tr>
                <th>Username</th>
                <th>Roles</th>
                <th>Scopes</th>
                <th>Active</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id}>
                  <td>{u.username}</td>
                  <td>{u.roles.join(", ") || "—"}</td>
                  <td>{u.scopeAssignments.map((s) => s.path).join(" ") || "—"}</td>
                  <td>{u.active ? "yes" : "no"}</td>
                  <td>
                    <UserRowActions user={u} grantable={grantable} onDone={reload} setError={setError} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <h3 style={{ marginTop: "1rem" }}>Create user</h3>
          <div className="srse-form-stack">
            <input
              className="srse-input"
              placeholder="Username"
              value={newUser.username}
              onChange={(e) => setNewUser({ ...newUser, username: e.target.value })}
            />
            <input
              className="srse-input"
              placeholder="Initial password"
              type="password"
              value={newUser.initialPassword}
              onChange={(e) => setNewUser({ ...newUser, initialPassword: e.target.value })}
            />
            <select
              multiple
              className="srse-input"
              value={newUser.roles}
              onChange={(e) =>
                setNewUser({
                  ...newUser,
                  roles: Array.from(e.target.selectedOptions).map((o) => o.value),
                })
              }
            >
              {ROLE_OPTIONS.filter((r) => r !== "SUPER_ADMIN" || session?.superAdmin).map((r) => (
                <option key={r} value={r}>
                  {r}
                </option>
              ))}
            </select>
            <select
              multiple
              className="srse-input"
              value={newUser.scopeNodeIds.map(String)}
              onChange={(e) =>
                setNewUser({
                  ...newUser,
                  scopeNodeIds: Array.from(e.target.selectedOptions).map((o) => Number(o.value)),
                })
              }
            >
              {grantable.map((n) => (
                <option key={n.id} value={n.id}>
                  {n.path} ({n.name})
                </option>
              ))}
            </select>
            <button type="button" className="srse-btn srse-btn-primary" onClick={onCreateUser}>
              Create user
            </button>
          </div>
        </section>
      ) : (
        <section className="srse-card">
          <h2 className="srse-card-title">Scope hierarchy</h2>
          {session?.superAdmin ? (
            <>
              <h3>Dimension</h3>
              <div className="srse-form-stack">
                <input
                  className="srse-input"
                  placeholder="Code"
                  value={dimForm.code}
                  onChange={(e) => setDimForm({ ...dimForm, code: e.target.value })}
                />
                <input
                  className="srse-input"
                  placeholder="Name"
                  value={dimForm.name}
                  onChange={(e) => setDimForm({ ...dimForm, name: e.target.value })}
                />
                <button type="button" className="srse-btn srse-btn-sm" onClick={onCreateDimension}>
                  Add dimension
                </button>
              </div>
              <h3>Level</h3>
              <div className="srse-form-stack">
                <select
                  className="srse-input"
                  value={levelForm.dimensionId}
                  onChange={(e) => setLevelForm({ ...levelForm, dimensionId: Number(e.target.value) })}
                >
                  {dimensions.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.code}
                    </option>
                  ))}
                </select>
                <input
                  className="srse-input"
                  type="number"
                  placeholder="Depth"
                  value={levelForm.depth}
                  onChange={(e) => setLevelForm({ ...levelForm, depth: Number(e.target.value) })}
                />
                <input
                  className="srse-input"
                  placeholder="Level name"
                  value={levelForm.name}
                  onChange={(e) => setLevelForm({ ...levelForm, name: e.target.value })}
                />
                <button type="button" className="srse-btn srse-btn-sm" onClick={onCreateLevel}>
                  Add level
                </button>
              </div>
            </>
          ) : (
            <p className="srse-muted">Dimensions and levels are SuperAdmin-only. You can add nodes under scopes you hold.</p>
          )}
          <h3>Nodes</h3>
          <ul>
            {nodes.map((n) => (
              <li key={n.id}>
                {n.path} {n.name}
                <button
                  type="button"
                  className="srse-btn srse-btn-ghost srse-btn-sm"
                  onClick={() => deleteScopeNode(n.id).then(reload).catch((e) => setError(String(e)))}
                >
                  Delete
                </button>
              </li>
            ))}
          </ul>
          <div className="srse-form-stack">
            <input
              className="srse-input"
              placeholder="Parent id (empty for root — SuperAdmin)"
              value={nodeForm.parentId}
              onChange={(e) => setNodeForm({ ...nodeForm, parentId: e.target.value })}
            />
            <input
              className="srse-input"
              placeholder="Code"
              value={nodeForm.code}
              onChange={(e) => setNodeForm({ ...nodeForm, code: e.target.value })}
            />
            <input
              className="srse-input"
              placeholder="Name"
              value={nodeForm.name}
              onChange={(e) => setNodeForm({ ...nodeForm, name: e.target.value })}
            />
            <button type="button" className="srse-btn srse-btn-primary" onClick={onCreateNode}>
              Add node
            </button>
          </div>
        </section>
      )}
    </div>
  );
}

function UserRowActions({
  user,
  grantable,
  onDone,
  setError,
}: Readonly<{
  user: UserSummary;
  grantable: NodeView[];
  onDone: () => Promise<void>;
  setError: (m: string) => void;
}>) {
  const [scopeIds, setScopeIds] = useState(user.scopeAssignments.map((s) => s.scopeNodeId));

  return (
    <span style={{ display: "flex", gap: "0.25rem", flexWrap: "wrap" }}>
      <select
        multiple
        className="srse-input srse-btn-sm"
        value={scopeIds.map(String)}
        onChange={(e) => setScopeIds(Array.from(e.target.selectedOptions).map((o) => Number(o.value)))}
      >
        {grantable.map((n) => (
          <option key={n.id} value={n.id}>
            {n.path}
          </option>
        ))}
      </select>
      <button
        type="button"
        className="srse-btn srse-btn-sm"
        onClick={() =>
          replaceUserScopes(user.id, scopeIds)
            .then(onDone)
            .catch((e) => setError(e instanceof Error ? e.message : String(e)))
        }
      >
        Save scopes
      </button>
      {user.active ? (
        <button
          type="button"
          className="srse-btn srse-btn-ghost srse-btn-sm"
          onClick={() => deactivateUser(user.id).then(onDone)}
        >
          Deactivate
        </button>
      ) : (
        <button type="button" className="srse-btn srse-btn-sm" onClick={() => reactivateUser(user.id).then(onDone)}>
          Reactivate
        </button>
      )}
      <button
        type="button"
        className="srse-btn srse-btn-ghost srse-btn-sm"
        onClick={() => {
          const pw = window.prompt("New password");
          if (pw) resetUserPassword(user.id, pw).then(onDone);
        }}
      >
        Reset password
      </button>
    </span>
  );
}
