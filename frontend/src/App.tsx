import { NavLink, Route, Routes } from "react-router-dom";
import { ToastProvider } from "./components/Toast";
import { ProfileProvider } from "./state";
import { ChartIcon, CalendarIcon, DumbbellIcon, UserIcon, FlagIcon } from "./components/Icons";
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

export default function App() {
  return (
    <ProfileProvider>
      <ToastProvider>
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
            </div>
            <div className="sidebar-foot">
              <CalendarIcon size={15} />
              <span>My cute gym companion</span>
            </div>
          </aside>

          <div className="main-col">
            <header className="topbar">
              <Logo />
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
      </ToastProvider>
    </ProfileProvider>
  );
}
