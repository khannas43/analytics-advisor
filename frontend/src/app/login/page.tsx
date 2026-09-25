"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { isLocalAuthMode, storeAuthToken } from "@/lib/authToken";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const returnTo = params.get("returnTo") ?? "/analysis";
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (!isLocalAuthMode()) {
    return (
      <main className="srse-page">
        <p>Login is only used when NEXT_PUBLIC_AUTH_MODE=local. Mock mode uses automatic mock-login.</p>
      </main>
    );
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      const body = (await res.json()) as { token?: string; message?: string; mustChangePassword?: boolean };
      if (!res.ok) {
        setError(body.message ?? "Login failed");
        return;
      }
      if (!body.token) {
        setError("No token returned");
        return;
      }
      storeAuthToken(body.token);
      if (body.mustChangePassword) {
        router.replace("/change-password");
      } else {
        router.replace(returnTo);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="srse-page" style={{ maxWidth: "28rem", margin: "2rem auto" }}>
      <section className="srse-card">
        <h1 className="srse-card-title">Sign in</h1>
        <form onSubmit={onSubmit} className="srse-form-stack">
          <label>
            Username
            <input
              className="srse-input"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              required
            />
          </label>
          <label>
            Password
            <input
              className="srse-input"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </label>
          {error ? <p className="srse-error-text">{error}</p> : null}
          <button type="submit" className="srse-btn srse-btn-primary" disabled={busy}>
            {busy ? "Signing in…" : "Sign in"}
          </button>
        </form>
      </section>
    </main>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
