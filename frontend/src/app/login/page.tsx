"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useId, useState } from "react";
import { LoginChromeFooter, LoginChromeHeader } from "@/components/shell/LoginChrome";
import { useShell } from "@/components/shell/ShellProviders";
import { fetchAuthSession, isLocalAuthMode, pathAfterLogin, storeAuthToken } from "@/lib/authToken";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

type LoginBody = {
  token?: string;
  message?: string;
  mustChangePassword?: boolean;
  mfaChallengeId?: string;
  maskedEmail?: string;
  maskedMobile?: string;
};

function PasswordVisibilityToggle({
  visible,
  onToggle,
  labelShow,
  labelHide,
}: Readonly<{
  visible: boolean;
  onToggle: () => void;
  labelShow: string;
  labelHide: string;
}>) {
  const label = visible ? labelHide : labelShow;
  return (
    <button
      type="button"
      className="login-password-toggle"
      onClick={onToggle}
      aria-label={label}
      aria-pressed={visible}
    >
      {visible ? (
        <svg viewBox="0 0 24 24" width={18} height={18} aria-hidden>
          <path
            d="M3 3l18 18M10.6 10.6a2 2 0 002.8 2.8M9.9 4.2A10 10 0 0112 5c5 0 9 4 9 7a10.8 10.8 0 01-2.1 3.1M6.1 6.1A10.8 10.8 0 003 12c0 3 4 7 9 7 1.1 0 2.1-.2 3-.5"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
          />
        </svg>
      ) : (
        <svg viewBox="0 0 24 24" width={18} height={18} aria-hidden>
          <path
            d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          />
          <circle cx="12" cy="12" r="3" fill="none" stroke="currentColor" strokeWidth="2" />
        </svg>
      )}
    </button>
  );
}

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const { t } = useShell();
  const errorId = useId();
  const returnTo = params.get("returnTo") ?? "/overview";
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [mfaChallengeId, setMfaChallengeId] = useState<string | null>(null);
  const [maskedEmail, setMaskedEmail] = useState<string | null>(null);
  const [maskedMobile, setMaskedMobile] = useState<string | null>(null);
  const [otpCode, setOtpCode] = useState("");

  if (!isLocalAuthMode()) {
    return (
      <div className="login-page">
        <LoginChromeHeader />
        <main className="login-page-body">
          <p className="login-mock-hint">{t("loginMockOnly")}</p>
        </main>
        <LoginChromeFooter />
      </div>
    );
  }

  async function onPasswordSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (busy) return;
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
        setError(body.message ?? "Invalid username or password");
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
        const session = await fetchAuthSession();
        router.replace(pathAfterLogin(returnTo, session?.admin === true));
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  }

  async function onOtpSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!mfaChallengeId || busy) return;
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
        const session = await fetchAuthSession();
        router.replace(pathAfterLogin(returnTo, session?.admin === true));
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  }

  async function onResend() {
    if (!mfaChallengeId || busy) return;
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
    <div className="login-page">
      <LoginChromeHeader />
      <main className="login-page-body">
        <section className="login-card" aria-labelledby="login-card-title">
          <h1 id="login-card-title" className="login-card-title">
            {mfaChallengeId ? t("loginMfaTitle") : t("loginWelcome")}
          </h1>
          {!mfaChallengeId ? (
            <>
              <p className="login-card-lead">{t("loginLead")}</p>
              <form onSubmit={onPasswordSubmit} className="login-form">
                <div className="field">
                  <label htmlFor="login-username">{t("loginUsername")}</label>
                  <input
                    id="login-username"
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    autoComplete="username"
                    autoCapitalize="none"
                    autoCorrect="off"
                    spellCheck={false}
                    required
                    disabled={busy}
                  />
                </div>
                <div className="field login-password-field">
                  <label htmlFor="login-password">{t("loginPassword")}</label>
                  <div className="login-password-input-wrap">
                    <input
                      id="login-password"
                      type={showPassword ? "text" : "password"}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      autoComplete="current-password"
                      required
                      disabled={busy}
                    />
                    <PasswordVisibilityToggle
                      visible={showPassword}
                      onToggle={() => setShowPassword((v) => !v)}
                      labelShow={t("loginShowPassword")}
                      labelHide={t("loginHidePassword")}
                    />
                  </div>
                </div>
                {error ? (
                  <p id={errorId} className="login-error" role="alert">
                    {error}
                  </p>
                ) : null}
                <button type="submit" className="btn login-submit" disabled={busy} aria-busy={busy}>
                  {busy ? t("loginSigningIn") : t("loginSignIn")}
                </button>
              </form>
            </>
          ) : (
            <form onSubmit={onOtpSubmit} className="login-form">
              <p className="login-card-lead">
                {t("loginMfaLead")}{" "}
                {[maskedMobile, maskedEmail].filter(Boolean).join(" and ") || "your verified contacts"}.
              </p>
              <div className="field">
                <label htmlFor="login-otp">{t("loginOtpLabel")}</label>
                <input
                  id="login-otp"
                  inputMode="numeric"
                  pattern="[0-9]*"
                  maxLength={6}
                  value={otpCode}
                  onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ""))}
                  autoComplete="one-time-code"
                  required
                  disabled={busy}
                />
              </div>
              {error ? (
                <p className="login-error" role="alert">
                  {error}
                </p>
              ) : null}
              <button type="submit" className="btn login-submit" disabled={busy} aria-busy={busy}>
                {busy ? t("loginVerifying") : t("loginVerify")}
              </button>
              <button type="button" className="btn secondary" disabled={busy} onClick={() => void onResend()}>
                {t("loginResend")}
              </button>
              <button
                type="button"
                className="btn secondary login-back-btn"
                disabled={busy}
                onClick={() => {
                  setMfaChallengeId(null);
                  setOtpCode("");
                  setError(null);
                }}
              >
                {t("loginBack")}
              </button>
            </form>
          )}
        </section>
      </main>
      <LoginChromeFooter />
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
