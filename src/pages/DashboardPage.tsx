import { useState, useEffect } from 'react';
import { FolderKanban, Clock, CheckCircle2, AlertTriangle } from 'lucide-react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { Alert } from '../components/ui/Alert';
import { StatCard } from '../components/dashboard/StatCard';
import { ProjectsChart } from '../components/dashboard/ProjectsChart';
import { StatusDoughnut } from '../components/dashboard/StatusDoughnut';
import { TasksBarChart } from '../components/dashboard/TasksBarChart';
import { RecentActivities } from '../components/dashboard/RecentActivities';
import { UpcomingDeadlines } from '../components/dashboard/UpcomingDeadlines';
import { ProjectsTable } from '../components/dashboard/ProjectsTable';
import { DashboardSkeleton } from '../components/dashboard/DashboardSkeleton';
import { useAuth } from '../hooks/useAuth';
import { reportService } from '../services/reportService';
import { projectService } from '../services/projectService';
import { ApiError } from '../services/ApiError';
import type { DashboardStats, Project } from '../types/models';

export function DashboardPage() {
  const { user } = useAuth();
  const firstName = user?.name?.split(' ')[0] ?? 'vous';

  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [recentProjects, setRecentProjects] = useState<Project[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Statistiques calculées par le backend sur le périmètre de l'utilisateur connecté.
  useEffect(() => {
    let annule = false;
    Promise.all([
      reportService.dashboard(),
      projectService.list({ page: 1, perPage: 5, sortBy: 'updatedAt', sortOrder: 'desc' }),
    ])
      .then(([statistiques, projets]) => {
        if (annule) return;
        setStats(statistiques);
        setRecentProjects(projets.data);
      })
      .catch((err) => {
        if (!annule) setError(err instanceof ApiError ? err.message : 'Chargement du tableau de bord impossible.');
      })
      .finally(() => {
        if (!annule) setIsLoading(false);
      });
    return () => {
      annule = true;
    };
  }, []);

  return (
    <Container className="py-6">
      <PageHeader
        title={`Bonjour, ${firstName}`}
        description="Voici un aperçu de l'avancement de vos projets."
      />

      {error && (
        <Alert variant="danger" className="mt-6">
          {error}
        </Alert>
      )}

      {isLoading ? (
        <div className="mt-6">
          <DashboardSkeleton />
        </div>
      ) : stats ? (
        <>
          {/* Cartes de statistiques */}
          <div className="mt-6 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <StatCard
              label="Total projets"
              value={stats.totalProjects}
              icon={<FolderKanban className="h-6 w-6" />}
            />
            <StatCard
              label="Projets en cours"
              value={stats.activeProjects}
              icon={<Clock className="h-6 w-6" />}
              variant="success"
            />
            <StatCard
              label="Projets terminés"
              value={stats.completedProjects}
              icon={<CheckCircle2 className="h-6 w-6" />}
              variant="success"
            />
            <StatCard
              label="Projets en retard"
              value={stats.overdueProjects}
              icon={<AlertTriangle className="h-6 w-6" />}
              variant="danger"
            />
          </div>

          {/* Graphiques : Line + Doughnut */}
          <div className="mt-6 grid gap-6 lg:grid-cols-3">
            <div className="lg:col-span-2">
              <ProjectsChart monthlyEvolution={stats.monthlyEvolution} />
            </div>
            <StatusDoughnut statusDistribution={stats.statusDistribution} />
          </div>

          {/* Graphique Bar */}
          <div className="mt-6">
            <TasksBarChart tasksPerWeek={stats.tasksPerWeek} />
          </div>

          {/* Listes : Activités + Échéances */}
          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <RecentActivities activities={stats.recentActivities} />
            <UpcomingDeadlines projects={stats.upcomingDeadlines} />
          </div>

          {/* Tableau des projets récemment modifiés */}
          <div className="mt-6">
            <ProjectsTable projects={recentProjects} />
          </div>
        </>
      ) : null}
    </Container>
  );
}
