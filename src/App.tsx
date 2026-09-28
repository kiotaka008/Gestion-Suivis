import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ErrorBoundary } from './components/ui/ErrorBoundary';
import { AuthProvider } from './contexts/AuthContext';
import { DashboardLayout } from './layouts/DashboardLayout';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { DashboardPage } from './pages/DashboardPage';
import { ProjectsPage } from './pages/ProjectsPage';
import { Container } from './components/ui/Container';
import { PageHeader } from './components/ui/PageHeader';
import { ROUTES } from './constants/routes';

const Placeholder = ({ title }: { title: string }) => (
  <Container>
    <PageHeader title={title} description="Page en construction." />
  </Container>
);

function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route path={ROUTES.LOGIN} element={<LoginPage />} />
            <Route path={ROUTES.REGISTER} element={<RegisterPage />} />

            {/* Route temporairement sans ProtectedRoute pour tester */}
            <Route>
              <Route element={<DashboardLayout />}>
                <Route path={ROUTES.DASHBOARD} element={<DashboardPage />} />
                <Route path={ROUTES.PROJECTS} element={<ProjectsPage />} />
                <Route path={ROUTES.ACTIVITIES} element={<Placeholder title="Activités" />} />
                <Route path={ROUTES.USERS} element={<Placeholder title="Utilisateurs" />} />
                <Route path={ROUTES.REPORTS} element={<Placeholder title="Rapports" />} />
                <Route path={ROUTES.NOTIFICATIONS} element={<Placeholder title="Notifications" />} />
                <Route path={ROUTES.SETTINGS} element={<Placeholder title="Paramètres" />} />
                <Route path={ROUTES.AI_ASSISTANT} element={<Placeholder title="Assistant IA" />} />
              </Route>
            </Route>

            <Route path="*" element={<Navigate to={ROUTES.DASHBOARD} replace />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </ErrorBoundary>
  );
}

export default App;