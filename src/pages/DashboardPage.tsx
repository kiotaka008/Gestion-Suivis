import { useState, useEffect } from 'react';
import { FolderKanban, Clock, CheckCircle2, AlertTriangle } from 'lucide-react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { StatCard } from '../components/dashboard/StatCard';
import { ProjectsChart } from '../components/dashboard/ProjectsChart';
import { StatusDoughnut } from '../components/dashboard/StatusDoughnut';
import { TasksBarChart } from '../components/dashboard/TasksBarChart';
import { RecentActivities } from '../components/dashboard/RecentActivities';
import { UpcomingDeadlines } from '../components/dashboard/UpcomingDeadlines';
import { ProjectsTable } from '../components/dashboard/ProjectsTable';
import { DashboardSkeleton } from '../components/dashboard/DashboardSkeleton';
import { useAuth } from '../hooks/useAuth';

export function DashboardPage() {
  const { user } = useAuth();
  const firstName = user?.name?.split(' ')[0] ?? 'vous';

  const [isLoading, setIsLoading] = useState(true);

  // Simulation du chargement initial (à remplacer par l'appel API)
  useEffect(() => {
    const timer = setTimeout(() => {
      setIsLoading(false);
    }, 1000); // 1 seconde de délai

    return () => clearTimeout(timer);
  }, []);

  return (
    <Container className="py-6">
      <PageHeader
        title={`Bonjour, ${firstName}`}
        description="Voici un aperçu de l'avancement de vos projets."
      />

      {isLoading ? (
        <div className="mt-6">
          <DashboardSkeleton />
        </div>
      ) : (
        <>
          {/* Cartes de statistiques */}
          <div className="mt-6 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <StatCard
              label="Total projets"
              value={15}
              icon={<FolderKanban className="h-6 w-6" />}
              trend={{ value: 12, isPositive: true }}
            />
            <StatCard
              label="Projets en cours"
              value={8}
              icon={<Clock className="h-6 w-6" />}
              variant="success"
              trend={{ value: 5, isPositive: true }}
            />
            <StatCard
              label="Projets terminés"
              value={5}
              icon={<CheckCircle2 className="h-6 w-6" />}
              variant="success"
            />
            <StatCard
              label="Projets en retard"
              value={2}
              icon={<AlertTriangle className="h-6 w-6" />}
              variant="danger"
              trend={{ value: 3, isPositive: false }}
            />
          </div>

          {/* Graphiques : Line + Doughnut */}
          <div className="mt-6 grid gap-6 lg:grid-cols-3">
            <div className="lg:col-span-2">
              <ProjectsChart />
            </div>
            <StatusDoughnut />
          </div>

          {/* Graphique Bar */}
          <div className="mt-6">
            <TasksBarChart />
          </div>

          {/* Listes : Activités + Échéances */}
          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <RecentActivities />
            <UpcomingDeadlines />
          </div>

          {/* Tableau des projets */}
          <div className="mt-6">
            <ProjectsTable />
          </div>
        </>
      )}
    </Container>
  );
}