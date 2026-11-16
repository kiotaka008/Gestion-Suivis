import { useMemo, useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Plus, ClipboardList } from 'lucide-react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { ProgressBar } from '../components/ui/ProgressBar';
import { EmptyState } from '../components/ui/EmptyState';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { SkeletonTable } from '../components/ui/SkeletonTable';
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
import type { Activity, ActivityStatus, Priority, Project, User } from '../types/models';
import { Alert } from '../components/ui/Alert';
import { activityService } from '../services/activityService';
import { projectService } from '../services/projectService';
import { ApiError } from '../services/ApiError';
import { userService } from '../services/userService';
import { usePermissions } from '../hooks/usePermissions';

function getStatusVariant(status: ActivityStatus) {
  switch (status) {
    case 'done': return 'success' as const;
    case 'in_progress': return 'info' as const;
    case 'blocked': return 'danger' as const;
    default: return 'muted' as const;
  }
}

function getStatusLabel(status: ActivityStatus) {
  switch (status) {
    case 'done': return 'Terminé';
    case 'in_progress': return 'En cours';
    case 'blocked': return 'Bloqué';
    default: return 'À faire';
  }
}

function getPriorityLabel(priority: Priority) {
  switch (priority) {
    case 'urgent': return 'Urgent';
    case 'high': return 'Haute';
    case 'medium': return 'Moyenne';
    default: return 'Basse';
  }
}

export function ActivitiesPage() {
  const navigate = useNavigate();
  const permissions = usePermissions();
  const [searchParams] = useSearchParams();
  const [activities, setActivities] = useState<Activity[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // Recherche transmise par la barre de recherche de l'en-tête (?search=...) : appliquée à
  // l'ouverture de la page et à chaque nouvelle recherche, même si la page est déjà affichée.
  const paramRecherche = searchParams.get('search') ?? '';
  const [search, setSearch] = useState(paramRecherche);
  const [dernierParam, setDernierParam] = useState(paramRecherche);
  if (paramRecherche !== dernierParam) {
    setDernierParam(paramRecherche);
    setSearch(paramRecherche);
  }
  const [status, setStatus] = useState('all');
  const [priority, setPriority] = useState('all');
  const [projectId, setProjectId] = useState('all');
  const [sort, setSort] = useState('recent');
  const [view, setView] = useState<ViewMode>('table');

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<Activity | null>(null);
  const [users, setUsers] = useState<User[]>([]);
  const [deleteTarget, setDeleteTarget] = useState<Activity | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [projects, setProjects] = useState<Project[]>([]);
  const [error, setError] = useState<string | null>(null);

  // Chargement des activités, des projets visibles (filtres, création, noms de projet) et,
  // pour les administrateurs et responsables, des utilisateurs (choix du responsable).
  const { canListUsers } = permissions;
  useEffect(() => {
    let annule = false;
    Promise.all([
      activityService.listAll(),
      projectService.listAll(),
      canListUsers ? userService.listAll() : Promise.resolve<User[]>([]),
    ])
      .then(([listeActivites, listeProjets, listeUtilisateurs]) => {
        if (annule) return;
        setActivities(listeActivites);
        setProjects(listeProjets);
        setUsers(listeUtilisateurs);
      })
      .catch((err) => {
        if (!annule) setError(err instanceof ApiError ? err.message : 'Chargement des activités impossible.');
      })
      .finally(() => {
        if (!annule) setIsLoading(false);
      });
    return () => {
      annule = true;
    };
  }, [canListUsers]);

  const proprietaireDuProjet = (id: string) => projects.find((p) => p.id === id)?.ownerId;
  // Projet terminé ou archivé : ses activités sont en lecture seule (le backend refuse avec 409).
  const estClos = (p: Pick<Project, 'status'> | undefined) => p?.status === 'completed' || p?.status === 'archived';
  const activiteModifiable = (a: Activity) => !estClos(projects.find((p) => p.id === a.projectId));
  // Projets sur lesquels l'utilisateur peut ajouter des activités.
  const projetsGeres = projects.filter((p) => permissions.canManageProject(p));
  const peutCreer = projetsGeres.some((p) => !estClos(p));

  const filtered = useMemo(() => {
    let result = [...activities];
    if (search.trim()) {
      const q = search.toLowerCase();
      result = result.filter(
        (a) => a.title.toLowerCase().includes(q) || a.description?.toLowerCase().includes(q)
      );
    }
    if (status !== 'all') result = result.filter((a) => a.status === status);
    if (priority !== 'all') result = result.filter((a) => a.priority === priority);
    if (projectId !== 'all') result = result.filter((a) => a.projectId === projectId);

    switch (sort) {
      case 'oldest':
        result.sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
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
        result.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
    }
    return result;
  }, [activities, search, status, priority, projectId, sort]);

  const findProjectName = (id: string) =>
    activities.find((a) => a.projectId === id)?.projectName ?? projects.find((p) => p.id === id)?.name ?? 'Projet inconnu';

  // Les erreurs remontent jusqu'au formulaire, qui affiche le message du backend.
  const handleCreate = async (data: Partial<Activity>) => {
    const creee = await activityService.create(data);
    setActivities((prev) => [creee, ...prev]);
  };

  const handleUpdate = async (data: Partial<Activity>) => {
    if (!editTarget) return;
    const modifiee = await activityService.update(editTarget.id, data);
    setActivities((prev) => prev.map((a) => (a.id === modifiee.id ? modifiee : a)));
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    try {
      await activityService.remove(deleteTarget.id);
      setActivities((prev) => prev.filter((a) => a.id !== deleteTarget.id));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Suppression impossible.');
    } finally {
      setIsDeleting(false);
      setDeleteTarget(null);
    }
  };

  return (
    <Container className="py-6">
      <PageHeader
        title="Activités"
        description="Toutes les activités de vos projets."
        actions={
          peutCreer && (
            <Button leftIcon={<Plus className="h-4 w-4" />} onClick={() => setIsCreateOpen(true)}>
              Nouvelle activité
            </Button>
          )
        }
      />

      {error && (
        <Alert variant="danger" className="mt-6">
          {error}
        </Alert>
      )}

      <div className="mt-6">
        <ActivitiesFilters
          search={search} onSearchChange={setSearch}
          status={status} onStatusChange={setStatus}
          priority={priority} onPriorityChange={setPriority}
          projectId={projectId} onProjectChange={setProjectId}
          sort={sort} onSortChange={setSort}
          view={view} onViewChange={setView}
          projects={projects}
        />
      </div>

      <div className="mt-6">
        {isLoading ? (
          <SkeletonTable rows={5} cols={6} />
        ) : filtered.length === 0 ? (
          <EmptyState
            icon={<ClipboardList className="h-6 w-6" />}
            title="Aucune activité trouvée"
            description={peutCreer ? 'Ajustez vos filtres ou créez une nouvelle activité.' : 'Ajustez vos filtres.'}
            action={peutCreer && <Button leftIcon={<Plus className="h-4 w-4" />} onClick={() => setIsCreateOpen(true)}>Créer une activité</Button>}
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
                  <TableRow
                    key={activity.id}
                    className="cursor-pointer"
                    onClick={() => navigate(`/activities/${activity.id}`)}
                  >
                    <TableCell><p className="font-medium text-foreground">{activity.title}</p></TableCell>
                    <TableCell className="text-sm text-muted-foreground">{findProjectName(activity.projectId)}</TableCell>
                    <TableCell className="w-48">
                      <div className="flex items-center gap-2">
                        <ProgressBar value={activity.progress} variant={activity.progress === 100 ? 'success' : activity.progress < 40 ? 'danger' : 'default'} />
                        <span className="w-10 shrink-0 text-xs text-muted-foreground">{activity.progress}%</span>
                      </div>
                    </TableCell>
                    <TableCell><Badge variant={getStatusVariant(activity.status)}>{getStatusLabel(activity.status)}</Badge></TableCell>
                    <TableCell><Badge variant="muted">{getPriorityLabel(activity.priority)}</Badge></TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {activity.dueDate ? new Date(activity.dueDate).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }) : '—'}
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
                onView={(a) => navigate(`/activities/${a.id}`)}
                onEdit={
                  activiteModifiable(activity) &&
                  permissions.canTrackActivity(activity, proprietaireDuProjet(activity.projectId))
                    ? (a) => setEditTarget(a)
                    : undefined
                }
                onDelete={
                  activiteModifiable(activity) &&
                  permissions.canManageActivity(proprietaireDuProjet(activity.projectId))
                    ? (a) => setDeleteTarget(a)
                    : undefined
                }
              />
            ))}
          </div>
        )}
      </div>

      <CreateActivityModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onCreate={handleCreate}
        projects={projetsGeres}
        assignees={users}
      />
      {editTarget && (
        <CreateActivityModal
          key={editTarget.id}
          isOpen
          activity={editTarget}
          trackOnly={!permissions.canManageActivity(proprietaireDuProjet(editTarget.projectId))}
          onClose={() => setEditTarget(null)}
          onCreate={handleUpdate}
          projects={projects}
          assignees={users}
        />
      )}
      <ConfirmDialog isOpen={!!deleteTarget} onClose={() => setDeleteTarget(null)} onConfirm={handleDeleteConfirm} title="Supprimer l'activité ?" description={`L'activité "${deleteTarget?.title}" sera définitivement supprimée.`} confirmLabel="Supprimer" isLoading={isDeleting} />
    </Container>
  );
}