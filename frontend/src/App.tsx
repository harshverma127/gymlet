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

function LoadingScreen() {
  return (
    <div className="auth-screen">
      <div className="auth-card-wrap">
        <Logo />
        <div className="auth-loading">
          <Spinner label="Loading your gym…" />
        </div>
      </div>
    </div>
  );
}

function Shell({ onLogout }: { onLogout: () => void }) {
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
          <Routes>
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
    </div>
  );
}

export default function App() {
  const { status, user, logout } = useAuth();

  if (status === "loading") {
    return (
      <ToastProvider>
        <LoadingScreen />
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
