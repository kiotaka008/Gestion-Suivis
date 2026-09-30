import { useMemo, useState } from 'react';
import { Plus, ClipboardList } from 'lucide-react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { ProgressBar } from '../components/ui/ProgressBar';
import { EmptyState } from '../components/ui/EmptyState';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../components/ui/Table';
import { ActivitiesFilters, type ViewMode } from '../components/activities/ActivitiesFilters';
import { ActivityCard } from '../components/activities/ActivityCard';
import { CreateActivityModal } from '../components/activities/CreateActivityModal';
import type { Activity, ActivityStatus, Priority } from '../types/models';
import { mockActivities } from '../mocks/activities';
import { mockProjects } from '../mocks/projects';

function getStatusVariant(status: ActivityStatus) {
  switch (status) {
    case 'done':
      return 'success' as const;
    case 'in_progress':
      return 'info' as const;
    case 'blocked':
      return 'danger' as const;
    default:
      return 'muted' as const;
  }
}

function getStatusLabel(status: ActivityStatus) {
  switch (status) {
    case 'done':
      return 'Terminé';
    case 'in_progress':
      return 'En cours';
    case 'blocked':
      return 'Bloqué';
    default:
      return 'À faire';
  }
}

function getPriorityLabel(priority: Priority) {
  switch (priority) {
    case 'urgent':
      return 'Urgent';
    case 'high':
      return 'Haute';
    case 'medium':
      return 'Moyenne';
    default:
      return 'Basse';
  }
}

export function ActivitiesPage() {
  const [activities, setActivities] = useState<Activity[]>(mockActivities);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('all');
  const [priority, setPriority] = useState('all');
  const [projectId, setProjectId] = useState('all');
  const [sort, setSort] = useState('recent');
  const [view, setView] = useState<ViewMode>('table');

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Activity | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const filtered = useMemo(() => {
    let result = [...activities];

    if (search.trim()) {
      const q = search.toLowerCase();
      result = result.filter(
        (a) =>
          a.title.toLowerCase().includes(q) ||
          a.description?.toLowerCase().includes(q)
      );
    }

    if (status !== 'all') {
      result = result.filter((a) => a.status === status);
    }
    if (priority !== 'all') {
      result = result.filter((a) => a.priority === priority);
    }
    if (projectId !== 'all') {
      result = result.filter((a) => a.projectId === projectId);
    }

    switch (sort) {
      case 'oldest':
        result.sort(
          (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
        );
        break;
      case 'title':
        result.sort((a, b) => a.title.localeCompare(b.title));
        break;
      case 'progress':
        result.sort((a, b) => b.progress - a.progress);
        break;
      case 'dueDate':
        result.sort((a, b) => {
          if (!a.dueDate) return 1;
          if (!b.dueDate) return -1;
          return new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime();
        });
        break;
      default:
        result.sort(
          (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
        );
    }

    return result;
  }, [activities, search, status, priority, projectId, sort]);

  const findProjectName = (id: string) =>
    mockProjects.find((p) => p.id === id)?.name ?? 'Projet inconnu';

  const handleCreate = (data: Partial<Activity>) => {
    const newActivity: Activity = {
      id: `a-${Date.now()}`,
      title: data.title ?? 'Sans titre',
      description: data.description,
      projectId: data.projectId ?? mockProjects[0].id,
      status: data.status ?? 'todo',
      priority: data.priority ?? 'medium',
      progress: 0,
      dueDate: data.dueDate,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    setActivities((prev) => [newActivity, ...prev]);
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    await new Promise((r) => setTimeout(r, 400));
    setActivities((prev) => prev.filter((a) => a.id !== deleteTarget.id));
    setIsDeleting(false);
    setDeleteTarget(null);
  };

  return (
    <Container className="py-6">
      <PageHeader
        title="Activités"
        description="Toutes les activités de vos projets."
        actions={
          <Button
            leftIcon={<Plus className="h-4 w-4" />}
            onClick={() => setIsCreateOpen(true)}
          >
            Nouvelle activité
          </Button>
        }
      />

      <div className="mt-6">
        <ActivitiesFilters
          search={search}
          onSearchChange={setSearch}
          status={status}
          onStatusChange={setStatus}
          priority={priority}
          onPriorityChange={setPriority}
          projectId={projectId}
          onProjectChange={setProjectId}
          sort={sort}
          onSortChange={setSort}
          view={view}
          onViewChange={setView}
          projects={mockProjects}
        />
      </div>

      <div className="mt-6">
        {filtered.length === 0 ? (
          <EmptyState
            icon={<ClipboardList className="h-6 w-6" />}
            title="Aucune activité trouvée"
            description="Ajustez vos filtres ou créez une nouvelle activité."
            action={
              <Button
                leftIcon={<Plus className="h-4 w-4" />}
                onClick={() => setIsCreateOpen(true)}
              >
                Créer une activité
              </Button>
            }
          />
        ) : view === 'table' ? (
          <div className="overflow-x-auto rounded-lg border border-border bg-surface">
            <Table>
              <TableHead>
                <TableRow>
                  <TableHeader>Activité</TableHeader>
                  <TableHeader>Projet</TableHeader>
                  <TableHeader>Progression</TableHeader>
                  <TableHeader>Statut</TableHeader>
                  <TableHeader>Priorité</TableHeader>
                  <TableHeader>Échéance</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {filtered.map((activity) => (
                  <TableRow key={activity.id}>
                    <TableCell>
                      <p className="font-medium text-foreground">{activity.title}</p>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {findProjectName(activity.projectId)}
                    </TableCell>
                    <TableCell className="w-48">
                      <div className="flex items-center gap-2">
                        <ProgressBar
                          value={activity.progress}
                          variant={
                            activity.progress === 100
                              ? 'success'
                              : activity.progress < 40
                              ? 'danger'
                              : 'default'
                          }
                        />
                        <span className="w-10 shrink-0 text-xs text-muted-foreground">
                          {activity.progress}%
                        </span>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant={getStatusVariant(activity.status)}>
                        {getStatusLabel(activity.status)}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant="muted">{getPriorityLabel(activity.priority)}</Badge>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {activity.dueDate
                        ? new Date(activity.dueDate).toLocaleDateString('fr-FR', {
                            day: '2-digit',
                            month: 'short',
                            year: 'numeric',
                          })
                        : '—'}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {filtered.map((activity) => (
              <ActivityCard
                key={activity.id}
                activity={activity}
                projectName={findProjectName(activity.projectId)}
                onDelete={(a) => setDeleteTarget(a)}
              />
            ))}
          </div>
        )}
      </div>

      <CreateActivityModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onCreate={handleCreate}
        projects={mockProjects}
      />

      <ConfirmDialog
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDeleteConfirm}
        title="Supprimer l'activité ?"
        description={`L'activité "${deleteTarget?.title}" sera définitivement supprimée.`}
        confirmLabel="Supprimer"
        isLoading={isDeleting}
      />
    </Container>
  );
}