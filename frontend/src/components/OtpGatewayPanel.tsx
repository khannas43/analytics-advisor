"use client";

import { useCallback, useEffect, useState } from "react";
import {
  fetchSmsGateway,
  fetchSmtpGateway,
  saveSmsGateway,
  saveSmtpGateway,
  testSmsGateway,
  testSmtpGateway,
  type SmsGatewayView,
  type SmtpGatewayView,
} from "@/lib/gatewayApi";

export function OtpGatewayPanel() {
  const [smtp, setSmtp] = useState<SmtpGatewayView | null>(null);
  const [sms, setSms] = useState<SmsGatewayView | null>(null);
  const [smtpHost, setSmtpHost] = useState("");
  const [smtpPort, setSmtpPort] = useState("587");
  const [smtpUser, setSmtpUser] = useState("");
  const [smtpPassword, setSmtpPassword] = useState("");
  const [smtpFrom, setSmtpFrom] = useState("");
  const [smtpTls, setSmtpTls] = useState(true);
  const [smsEndpoint, setSmsEndpoint] = useState("");
  const [smsUser, setSmsUser] = useState("");
  const [smsPassword, setSmsPassword] = useState("");
  const [smsSenderId, setSmsSenderId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState<"smtp" | "sms" | null>(null);
  const [testing, setTesting] = useState<"smtp" | "sms" | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      const [smtpView, smsView] = await Promise.all([fetchSmtpGateway(), fetchSmsGateway()]);
      setSmtp(smtpView);
      setSms(smsView);
      setSmtpHost(smtpView.host ?? "");
      setSmtpPort(String(smtpView.port || 587));
      setSmtpUser(smtpView.username ?? "");
      setSmtpFrom(smtpView.fromAddress ?? "");
      setSmtpTls(smtpView.tls);
      setSmsEndpoint(smsView.endpoint ?? "");
      setSmsUser(smsView.username ?? "");
      setSmsSenderId(smsView.senderId ?? "");
      setSmtpPassword("");
      setSmsPassword("");
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }, []);

  useEffect(() => {
    load().catch(() => {});
  }, [load]);

  async function onSaveSmtp() {
    setSaving("smtp");
    setError(null);
    setMessage(null);
    try {
      const updated = await saveSmtpGateway({
        host: smtpHost.trim(),
        port: Number(smtpPort) || 587,
        username: smtpUser.trim(),
        password: smtpPassword,
        fromAddress: smtpFrom.trim(),
        tls: smtpTls,
      });
      setSmtp(updated);
      setSmtpPassword("");
      setMessage("SMTP gateway saved to the connection override file (survives container restart).");
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(null);
    }
  }

  async function onTestSmtp() {
    setTesting("smtp");
    setError(null);
    setMessage(null);
    try {
      const result = await testSmtpGateway();
      setMessage(result.success ? `Test email sent: ${result.message}` : `SMTP test failed: ${result.message}`);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setTesting(null);
    }
  }

  async function onSaveSms() {
    setSaving("sms");
    setError(null);
    setMessage(null);
    try {
      const updated = await saveSmsGateway({
        endpoint: smsEndpoint.trim(),
        username: smsUser.trim(),
        password: smsPassword,
        senderId: smsSenderId.trim(),
      });
      setSms(updated);
      setSmsPassword("");
      setMessage("SMS gateway fields saved (sender implementation still pending).");
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(null);
    }
  }

  async function onTestSms() {
    setTesting("sms");
    setError(null);
    setMessage(null);
    try {
      const result = await testSmsGateway();
      setMessage(result.message);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setTesting(null);
    }
  }

  return (
    <section className="srse-card" style={{ marginBottom: "1.25rem" }}>
      <h2 className="srse-section-title">OTP gateways (SuperAdmin)</h2>
      <p className="srse-text-muted" style={{ marginBottom: "1rem", maxWidth: "52rem" }}>
        Email and SMS settings for one-time passwords. Stored in the same{" "}
        <code>connection-overrides.properties</code> volume as JDBC credentials — not in the database
        or AA-04 config export. Passwords are write-only here.
      </p>
      {error && <p className="srse-text-danger">{error}</p>}
      {message && <p className="srse-text-success">{message}</p>}
      {!smtp && !error && <p className="srse-text-muted">Loading…</p>}

      {smtp && (
        <div style={{ marginBottom: "1.5rem" }}>
          <h3 className="srse-card-title">Email (SMTP)</h3>
          <div style={{ display: "grid", gap: "0.5rem", maxWidth: "28rem" }}>
            <input className="srse-input" placeholder="Host" value={smtpHost} onChange={(e) => setSmtpHost(e.target.value)} />
            <input className="srse-input" placeholder="Port" value={smtpPort} onChange={(e) => setSmtpPort(e.target.value)} />
            <input className="srse-input" placeholder="Username" value={smtpUser} onChange={(e) => setSmtpUser(e.target.value)} />
            <input
              type="password"
              className="srse-input"
              placeholder={smtp.passwordConfigured ? "Password (leave blank to keep)" : "Password"}
              value={smtpPassword}
              onChange={(e) => setSmtpPassword(e.target.value)}
            />
            <input className="srse-input" placeholder="From address" value={smtpFrom} onChange={(e) => setSmtpFrom(e.target.value)} />
            <label style={{ display: "flex", alignItems: "center", gap: "0.5rem", fontSize: "0.9rem" }}>
              <input type="checkbox" checked={smtpTls} onChange={(e) => setSmtpTls(e.target.checked)} />
              Use STARTTLS
            </label>
            <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
              <button type="button" className="srse-btn srse-btn-primary" disabled={saving === "smtp"} onClick={onSaveSmtp}>
                {saving === "smtp" ? "Saving…" : "Save SMTP"}
              </button>
              <button type="button" className="srse-btn srse-btn-ghost" disabled={testing === "smtp"} onClick={onTestSmtp}>
                {testing === "smtp" ? "Sending…" : "Send test to my verified email"}
              </button>
            </div>
          </div>
        </div>
      )}

      {sms && (
        <div>
          <h3 className="srse-card-title">SMS</h3>
          <p className="srse-text-muted" style={{ fontSize: "0.85rem", marginBottom: "0.5rem" }}>
            Fields are persisted for when the provider contract lands; delivery is not wired yet.
          </p>
          <div style={{ display: "grid", gap: "0.5rem", maxWidth: "28rem" }}>
            <input className="srse-input" placeholder="Endpoint URL" value={smsEndpoint} onChange={(e) => setSmsEndpoint(e.target.value)} />
            <input className="srse-input" placeholder="Username / API key id" value={smsUser} onChange={(e) => setSmsUser(e.target.value)} />
            <input
              type="password"
              className="srse-input"
              placeholder={sms.passwordConfigured ? "Secret (leave blank to keep)" : "Secret"}
              value={smsPassword}
              onChange={(e) => setSmsPassword(e.target.value)}
            />
            <input className="srse-input" placeholder="Sender ID" value={smsSenderId} onChange={(e) => setSmsSenderId(e.target.value)} />
            <div style={{ display: "flex", gap: "0.5rem", flexWrap: "wrap" }}>
              <button type="button" className="srse-btn srse-btn-primary" disabled={saving === "sms"} onClick={onSaveSms}>
                {saving === "sms" ? "Saving…" : "Save SMS"}
              </button>
              <button type="button" className="srse-btn srse-btn-ghost" disabled={testing === "sms"} onClick={onTestSms}>
                {testing === "sms" ? "Testing…" : "Test SMS gateway"}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
