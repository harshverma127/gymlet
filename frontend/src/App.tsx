import { useState } from "react";
import { NavLink, Route, Routes } from "react-router-dom";
import { ToastProvider } from "./components/Toast";
import { ProfileProvider, useAuth } from "./state";
import {
  ChartIcon,
  CalendarIcon,
  DumbbellIcon,
  UserIcon,
  FlagIcon,
  LogoutIcon,
} from "./components/Icons";
import { Spinner } from "./components/ui";
import {
  ConnectionOverlay,
  UnreachableCard,
  WakingCard,
  useConnection,
} from "./components/Connection";
import { AuthScreen } from "./pages/Auth";
import { TodayPage } from "./pages/Today";
import { HistoryPage } from "./pages/History";
import { HistoryDetailPage } from "./pages/HistoryDetail";
import { ProgressPage } from "./pages/Progress";
import { ProfilePage } from "./pages/Profile";

const NAV = [
  { to: "/", label: "Today", icon: DumbbellIcon, end: true },
  { to: "/history", label: "History", icon: FlagIcon },
  { to: "/progress", label: "Progress", icon: ChartIcon },
  { to: "/profile", label: "Profile", icon: UserIcon },
];

function Logo() {
  return (
    <div className="logo">
      <span className="logo-icon">
        <DumbbellIcon size={18} />
      </span>
      <span className="logo-text">GYMLET</span>
    </div>
  );
}

/**
 * Shown while the app is deciding who you are. If the backend is asleep it
 * shows the cute waking card and retries automatically — it never jumps to
 * the login screen just because the first request couldn't get through.
 */
function StartupScreen({ onRetry }: { onRetry: () => void }) {
  const connection = useConnection();
  return (
    <div className="auth-screen">
      <div className="auth-card-wrap">
        <Logo />
        {connection === "waking" ? (
          <WakingCard />
        ) : connection === "unreachable" ? (
          <UnreachableCard onRetry={onRetry} />
        ) : (
          <div className="auth-loading">
            <Spinner label="Loading your gym…" />
          </div>
        )}
      </div>
    </div>
  );
}

function Shell({ onLogout }: { onLogout: () => void }) {
  // Bumped by the fallback "Retry now" button so the current page refetches
  // (without a browser reload) and kicks off a fresh wake attempt.
  const [pageKey, setPageKey] = useState(0);

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="sidebar-inner">
          <Logo />
          <nav className="side-nav">
            {NAV.map(({ to, label, icon: Icon, end }) => (
              <NavLink key={to} to={to} end={end} className="side-link">
                <Icon size={18} />
                <span>{label}</span>
              </NavLink>
            ))}
          </nav>
          <button type="button" className="side-logout" onClick={() => void onLogout()}>
            <LogoutIcon size={16} />
            <span>Log out</span>
          </button>
        </div>
        <div className="sidebar-foot">
          <CalendarIcon size={15} />
          <span>My cute gym companion</span>
        </div>
      </aside>

      <div className="main-col">
        <header className="topbar">
          <Logo />
          <button
            type="button"
            className="icon-btn topbar-logout"
            onClick={() => void onLogout()}
            aria-label="Log out"
            title="Log out"
          >
            <LogoutIcon size={17} />
          </button>
        </header>
        <main className="content">
          <Routes key={pageKey}>
            <Route path="/" element={<TodayPage />} />
            <Route path="/history" element={<HistoryPage />} />
            <Route path="/history/:id" element={<HistoryDetailPage />} />
            <Route path="/progress" element={<ProgressPage />} />
            <Route path="/profile" element={<ProfilePage />} />
          </Routes>
        </main>
        <nav className="bottom-nav" aria-label="Primary">
          {NAV.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className="bottom-link">
              <Icon size={21} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
      </div>

      <ConnectionOverlay onRetry={() => setPageKey((k) => k + 1)} />
    </div>
  );
}

export default function App() {
  const { status, user, logout, retryBootstrap } = useAuth();

  if (status === "loading") {
    return (
      <ToastProvider>
        <StartupScreen onRetry={retryBootstrap} />
      </ToastProvider>
    );
  }

  if (status === "out") {
    return (
      <ToastProvider>
        <AuthScreen />
      </ToastProvider>
    );
  }

  // Keyed by username so the profile + data reload cleanly when the user switches.
  return (
    <ProfileProvider key={user!.username}>
      <ToastProvider>
        <Shell onLogout={() => void logout()} />
      </ToastProvider>
    </ProfileProvider>
  );
}
