"use client";

import { useEffect, useState } from "react";
import { useAuthSession } from "@/components/shell/AuthSessionProvider";
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

const ROLE_OPTIONS = ["SUPER_ADMIN", "ADMIN", "OFFICER", "AUDIT_READER"] as const;

export type AdminIdentityView = "users" | "hierarchy";

function errorText(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

export function AdminIdentityPanel({ view }: Readonly<{ view: AdminIdentityView }>) {
  if (view === "hierarchy") {
    return <ScopeHierarchySection />;
  }
  return <UsersRolesSection />;
}

function UsersRolesSection() {
  const { session } = useAuthSession();
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [grantable, setGrantable] = useState<NodeView[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [loadedKey, setLoadedKey] = useState<number | null>(null);
  const loading = loadedKey !== refreshKey;
  const [newUser, setNewUser] = useState({
    username: "",
    email: "",
    mobile: "",
    initialPassword: "",
    roles: ["OFFICER"] as string[],
    scopeNodeIds: [] as number[],
  });

  useEffect(() => {
    let cancelled = false;
    Promise.all([listUsers(), listGrantableNodes()])
      .then(([nextUsers, nextGrantable]) => {
        if (cancelled) return;
        setUsers(nextUsers);
        setGrantable(nextGrantable);
        setError(null);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(errorText(err));
      })
      .finally(() => {
        if (!cancelled) setLoadedKey(refreshKey);
      });
    return () => {
      cancelled = true;
    };
  }, [refreshKey]);

  const bumpRefresh = () => {
    setRefreshKey((n) => n + 1);
    return Promise.resolve();
  };

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
      setRefreshKey((n) => n + 1);
    } catch (err: unknown) {
      setError(errorText(err));
    }
  }

  return (
    <section className="srse-card" style={{ marginBottom: "1rem" }}>
      {error ? <p className="srse-error-text">{error}</p> : null}
      {message ? <p className="srse-muted">{message}</p> : null}
      {loading && users.length === 0 && !error ? <p className="srse-text-muted">Loading…</p> : null}
      <div className="admin-table-scroll">
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
                  <UserRowActions user={u} grantable={grantable} onDone={bumpRefresh} setError={setError} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <h2 className="srse-subheading" style={{ marginTop: "1rem" }}>
        Create user
      </h2>
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
          aria-label="Roles"
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
          aria-label="Scope assignments"
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
  );
}

function ScopeHierarchySection() {
  const { session } = useAuthSession();
  const [dimensions, setDimensions] = useState<DimensionView[]>([]);
  const [nodes, setNodes] = useState<NodeView[]>([]);
  const [selectedDimensionId, setSelectedDimensionId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [loadedKey, setLoadedKey] = useState<number | null>(null);
  const loading = loadedKey !== refreshKey;

  const [dimForm, setDimForm] = useState({ code: "", name: "", displayOrder: 1 });
  const [levelForm, setLevelForm] = useState({ dimensionId: 0, depth: 1, name: "" });
  const [nodeForm, setNodeForm] = useState({
    dimensionId: 0,
    levelId: 0,
    parentId: "" as string,
    code: "",
    name: "",
  });

  useEffect(() => {
    let cancelled = false;
    listDimensions()
      .then((nextDimensions) => {
        if (cancelled) return;
        setDimensions(nextDimensions);
        setSelectedDimensionId((current) => {
          if (current && nextDimensions.some((dimension) => dimension.id === current)) {
            return current;
          }
          return nextDimensions[0]?.id ?? null;
        });
        setError(null);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(errorText(err));
      })
      .finally(() => {
        if (!cancelled) setLoadedKey(refreshKey);
      });
    return () => {
      cancelled = true;
    };
  }, [refreshKey]);

  useEffect(() => {
    if (selectedDimensionId == null) return;
    let cancelled = false;
    Promise.all([listLevels(selectedDimensionId), listScopeNodes(selectedDimensionId)])
      .then(([nextLevels, nextNodes]) => {
        if (cancelled) return;
        setNodes(nextNodes);
        setLevelForm((form) => ({
          ...form,
          dimensionId: form.dimensionId || selectedDimensionId,
        }));
        setNodeForm((form) => ({
          ...form,
          dimensionId: selectedDimensionId,
          levelId: form.levelId || nextLevels[0]?.id || 0,
        }));
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(errorText(err));
      });
    return () => {
      cancelled = true;
    };
  }, [selectedDimensionId, refreshKey]);

  const bumpRefresh = () => {
    setRefreshKey((n) => n + 1);
    return Promise.resolve();
  };

  async function onCreateDimension() {
    if (!session?.superAdmin) {
      setError("SuperAdmin only");
      return;
    }
    await createDimension(dimForm);
    setMessage("Dimension created.");
    setRefreshKey((n) => n + 1);
  }

  async function onCreateLevel() {
    if (!session?.superAdmin) {
      setError("SuperAdmin only");
      return;
    }
    await createLevel(levelForm);
    setMessage("Level created.");
    setRefreshKey((n) => n + 1);
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
    setRefreshKey((n) => n + 1);
  }

  return (
    <section className="srse-card">
      {error ? <p className="srse-error-text">{error}</p> : null}
      {message ? <p className="srse-muted">{message}</p> : null}
      {loading && dimensions.length === 0 && !error ? <p className="srse-text-muted">Loading…</p> : null}
      {session?.superAdmin ? (
        <>
          <h2 className="srse-subheading">Dimension</h2>
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
          <h2 className="srse-subheading">Level</h2>
          <div className="srse-form-stack">
            <select
              className="srse-input"
              aria-label="Level dimension"
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
        <p className="srse-muted">
          Dimensions and levels are SuperAdmin-only. You can add nodes under scopes you hold.
        </p>
      )}
      <h2 className="srse-subheading">Nodes</h2>
      <ul>
        {nodes.map((n) => (
          <li key={n.id}>
            {n.path} {n.name}
            <button
              type="button"
              className="srse-btn srse-btn-ghost srse-btn-sm"
              onClick={() => deleteScopeNode(n.id).then(bumpRefresh).catch((e) => setError(String(e)))}
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
        aria-label={`Scopes for ${user.username}`}
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
