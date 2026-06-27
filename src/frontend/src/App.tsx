import { BrowserRouter, Routes, Route, Navigate, useNavigate, useLocation, Outlet, Link } from 'react-router-dom';
import { useAuth } from '@/lib/auth-context';
import { cn } from '@/lib/cn';
import { LoginPage } from '@/pages/LoginPage';
import { SourcesPage } from '@/pages/SourcesPage';
import { IngestionPage } from '@/pages/IngestionPage';
import { QualityDashboardPage } from '@/pages/QualityDashboardPage';
import { ConflictsPage } from '@/pages/ConflictsPage';
import { NotFound } from '@/pages/NotFound';

function ProtectedRoute() {
  const { isAuthenticated, loading } = useAuth();

  if (loading) {
    return null;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}

function AppLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const navItems = [
    { path: '/sources', label: 'Sources' },
    { path: '/ingestion', label: 'Ingestion' },
    { path: '/quality', label: 'Quality' },
    { path: '/conflicts', label: 'Conflicts' },
  ];

  return (
    <div className="min-h-screen flex flex-col bg-surface-base">
      <header 
        className="sticky top-0 z-10 flex items-center justify-between px-6"
        style={{ 
          height: '48px', 
          backgroundColor: 'var(--surface-base)', 
          borderBottom: '1px solid var(--border-default)' 
        }}
      >
        <nav className="flex h-full space-x-6">
          {navItems.map((item) => {
            const isActive = location.pathname.startsWith(item.path);
            return (
              <Link
                key={item.path}
                to={item.path}
                className={cn(
                  "flex items-center h-full text-body font-medium transition-colors",
                  isActive ? "text-[var(--accent-primary)]" : "text-[var(--text-secondary)]"
                )}
                style={{
                  borderBottom: isActive ? '2px solid var(--accent-primary)' : '2px solid transparent',
                  marginBottom: '-1px' // To overlap the header's bottom border
                }}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
        <div className="flex items-center space-x-4">
          <span className="text-body text-text-secondary">
            {user?.username}
          </span>
          <button 
            onClick={handleLogout}
            className="text-body text-text-secondary hover:text-text-primary transition-colors"
          >
            Logout
          </button>
        </div>
      </header>
      <main className="flex-1 pt-12 px-8 py-6">
        <Outlet />
      </main>
    </div>
  );
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/sources" element={<SourcesPage />} />
            <Route path="/ingestion" element={<IngestionPage />} />
            <Route path="/quality" element={<QualityDashboardPage />} />
            <Route path="/conflicts" element={<ConflictsPage />} />
          </Route>
        </Route>
        <Route path="/" element={<Navigate to="/sources" replace />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
