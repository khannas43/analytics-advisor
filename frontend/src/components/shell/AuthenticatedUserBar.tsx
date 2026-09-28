"use client";

import { useAuthSession } from "@/components/shell/AuthSessionProvider";
import { sessionRoleLabel } from "@/lib/sessionRoleLabel";

function UserAvatarIcon() {
  return (
    <svg viewBox="0 0 24 24" width={20} height={20} aria-hidden className="app-user-bar-icon">
      <circle cx="12" cy="8" r="4" fill="none" stroke="currentColor" strokeWidth="2" />
      <path
        d="M4 20c0-4 3.6-7 8-7s8 3 8 7"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
      />
    </svg>
  );
}

function LogoutIcon() {
  return (
    <svg viewBox="0 0 24 24" width={20} height={20} aria-hidden className="app-user-bar-icon">
      <path
        d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4M16 17l5-5-5-5M21 12H9"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function AuthenticatedUserBar() {
  const { session, loading, logout } = useAuthSession();

  if (loading && !session) {
    return (
      <div className="app-user-bar" aria-busy="true">
        <span className="app-user-bar-skeleton" />
      </div>
    );
  }

  if (!session) {
    return null;
  }

  const role = sessionRoleLabel(session);

  return (
    <div className="app-user-bar">
      <div className="app-user-bar-identity" title={`${session.username} · ${role}`}>
        <UserAvatarIcon />
        <span className="app-user-bar-text">
          <span className="app-user-bar-username">{session.username}</span>
          {role ? <span className="app-user-bar-role">{role}</span> : null}
        </span>
      </div>
      <button
        type="button"
        className="app-user-bar-logout"
        onClick={() => void logout()}
        aria-label="Log out"
        title="Log out"
      >
        <LogoutIcon />
        <span className="app-user-bar-logout-label">Log out</span>
      </button>
    </div>
  );
}
