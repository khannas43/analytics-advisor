import {
  authorizedFetch as scopedAuthorizedFetch,
  type AuthScope,
} from "@/lib/authToken";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

async function authorizedFetch(
  input: string,
  init: RequestInit = {},
  scope: AuthScope = "officer",
): Promise<Response> {
  return scopedAuthorizedFetch(scope, input, init);
}

export type ConnectionPlaneInfo = {
  jdbcUrl: string;
  username: string;
  driverClassName: string;
  status: string;
};

export type ConnectionsInfo = {
  dataMode: string;
  environmentLabel: string;
  operational: ConnectionPlaneInfo;
  analytical: ConnectionPlaneInfo;
};

export type UpdateConnectionRequest = {
  jdbcUrl: string;
  username: string;
  password: string;
  driverClassName: string;
};

export type UpdateConnectionResponse = {
  plane: ConnectionPlaneInfo | null;
  restartRequired: boolean;
};

export async function getConnections(): Promise<ConnectionsInfo> {
  const res = await authorizedFetch(`${API_BASE}/api/admin/connections`, { credentials: "include" }, "admin");
  if (!res.ok) {
    throw new Error(`Admin service error ${res.status}: ${await res.text()}`);
  }
  return res.json();
}

async function updateConnection(
  plane: "analytical" | "operational",
  req: UpdateConnectionRequest,
): Promise<UpdateConnectionResponse> {
  const res = await authorizedFetch(`${API_BASE}/api/admin/connections/${plane}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(req),
  }, "admin");
  if (!res.ok) {
    throw new Error(`Connection update error ${res.status}: ${await res.text()}`);
  }
  return res.json();
}

export function updateAnalyticalConnection(req: UpdateConnectionRequest): Promise<UpdateConnectionResponse> {
  return updateConnection("analytical", req);
}

export function updateOperationalConnection(req: UpdateConnectionRequest): Promise<UpdateConnectionResponse> {
  return updateConnection("operational", req);
}

// ---------------------------------------------------------------------------
// Admin: lakehouse Catalog → Schema → Table → Column cascade
// ---------------------------------------------------------------------------

export type LakehouseColumnInfo = { name: string; dataType: string };

export type TableRegistration = {
  id: number;
  catalog: string;
  schema: string;
  table: string;
  /** SILVER / GOLD / null — a display tag, not a level of the hierarchy. */
  layer: string | null;
  qualifiedName: string;
};

async function adminGet<T>(path: string): Promise<T> {
  const res = await authorizedFetch(`${API_BASE}${path}`, { credentials: "include" }, "admin");
  if (!res.ok) {
    throw new Error(`Admin service error ${res.status}: ${await res.text()}`);
  }
  return res.json() as Promise<T>;
}

const enc = encodeURIComponent;

export function browseCatalogs(): Promise<string[]> {
  return adminGet<string[]>(`/api/admin/lakehouse/browse/catalogs`);
}

export function browseSchemas(catalog: string): Promise<string[]> {
  return adminGet<string[]>(`/api/admin/lakehouse/browse/catalogs/${enc(catalog)}/schemas`);
}

export function browseTables(catalog: string, schema: string): Promise<string[]> {
  return adminGet<string[]>(
    `/api/admin/lakehouse/browse/catalogs/${enc(catalog)}/schemas/${enc(schema)}/tables`,
  );
}

export function browseColumns(
  catalog: string,
  schema: string,
  table: string,
): Promise<LakehouseColumnInfo[]> {
  return adminGet<LakehouseColumnInfo[]>(
    `/api/admin/lakehouse/browse/catalogs/${enc(catalog)}/schemas/${enc(schema)}` +
      `/tables/${enc(table)}/columns`,
  );
}

export function listRegistrations(): Promise<TableRegistration[]> {
  return adminGet<TableRegistration[]>(`/api/admin/lakehouse/registrations`);
}

export function listLakehouseLayers(): Promise<string[]> {
  return adminGet<string[]>(`/api/admin/lakehouse/layers`);
}

export async function registerTable(req: {
  catalog: string;
  schema: string;
  table: string;
  layer: string;
}): Promise<TableRegistration> {
  const res = await authorizedFetch(`${API_BASE}/api/admin/lakehouse/registrations`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(req),
  }, "admin");
  if (!res.ok) {
    throw new Error(`Admin service error ${res.status}: ${await res.text()}`);
  }
  return res.json();
}

export async function updateTableRegistration(id: number, layer: string): Promise<TableRegistration> {
  const res = await authorizedFetch(`${API_BASE}/api/admin/lakehouse/registrations/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ layer }),
  }, "admin");
  if (!res.ok) {
    throw new Error(`Admin service error ${res.status}: ${await res.text()}`);
  }
  return res.json();
}

export async function unregisterTable(id: number): Promise<void> {
  const res = await authorizedFetch(`${API_BASE}/api/admin/lakehouse/registrations/${id}`, {
    method: "DELETE",
    credentials: "include",
  }, "admin");
  if (!res.ok) {
    throw new Error(`Admin service error ${res.status}: ${await res.text()}`);
  }
}
