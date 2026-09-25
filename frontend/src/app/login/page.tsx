"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";
import { isLocalAuthMode, storeAuthToken } from "@/lib/authToken";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

type LoginBody = {
  token?: string;
  message?: string;
  mustChangePassword?: boolean;
  mfaChallengeId?: string;
  maskedEmail?: string;
  maskedMobile?: string;
};

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const returnTo = params.get("returnTo") ?? "/analysis";
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [mfaChallengeId, setMfaChallengeId] = useState<string | null>(null);
  const [maskedEmail, setMaskedEmail] = useState<string | null>(null);
  const [maskedMobile, setMaskedMobile] = useState<string | null>(null);
  const [otpCode, setOtpCode] = useState("");

  if (!isLocalAuthMode()) {
    return (
      <main className="srse-page">
        <p>Login is only used when NEXT_PUBLIC_AUTH_MODE=local. Mock mode uses automatic mock-login.</p>
      </main>
    );
  }

  async function onPasswordSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE}/api/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      const body = (await res.json()) as LoginBody;
      if (!res.ok) {
        setError(body.message ?? "Login failed");
        return;
      }
      if (body.mfaChallengeId) {
        setMfaChallengeId(body.mfaChallengeId);
        setMaskedEmail(body.maskedEmail ?? null);
        setMaskedMobile(body.maskedMobile ?? null);
        setOtpCode("");
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

  async function onOtpSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!mfaChallengeId) return;
    setBusy(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE}/api/auth/verify-otp`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ challengeId: mfaChallengeId, code: otpCode }),
      });
      const body = (await res.json()) as LoginBody;
      if (!res.ok || !body.token) {
        setError(body.message ?? "Verification failed");
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

  async function onResend() {
    if (!mfaChallengeId) return;
    setBusy(true);
    setError(null);
    try {
      const res = await fetch(`${API_BASE}/api/auth/resend-otp`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ challengeId: mfaChallengeId }),
      });
      const body = (await res.json()) as LoginBody;
      if (!res.ok) {
        setError(body.message ?? "Could not resend code");
        return;
      }
      if (body.mfaChallengeId) {
        setMfaChallengeId(body.mfaChallengeId);
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
        <h1 className="srse-card-title">{mfaChallengeId ? "Verification code" : "Sign in"}</h1>
        {!mfaChallengeId ? (
          <form onSubmit={onPasswordSubmit} className="srse-form-stack">
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
        ) : (
          <form onSubmit={onOtpSubmit} className="srse-form-stack">
            <p className="srse-muted-text">
              Enter the code sent to{" "}
              {[maskedMobile, maskedEmail].filter(Boolean).join(" and ") || "your verified contacts"}.
            </p>
            <label>
              Code
              <input
                className="srse-input"
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={6}
                value={otpCode}
                onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ""))}
                autoComplete="one-time-code"
                required
              />
            </label>
            {error ? <p className="srse-error-text">{error}</p> : null}
            <button type="submit" className="srse-btn srse-btn-primary" disabled={busy}>
              {busy ? "Verifying…" : "Verify"}
            </button>
            <button type="button" className="srse-btn" disabled={busy} onClick={() => void onResend()}>
              Send another code
            </button>
            <button
              type="button"
              className="srse-btn srse-btn-link"
              onClick={() => {
                setMfaChallengeId(null);
                setOtpCode("");
                setError(null);
              }}
            >
              Back to sign in
            </button>
          </form>
        )}
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
