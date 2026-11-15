import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowLeft,
  Calendar,
  CheckCircle2,
  Edit,
  History,
  Flag,
  FolderKanban,
  TrendingUp,
  Trash2,
  User as UserIcon,
} from 'lucide-react';
import { Container } from '../components/ui/Container';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Avatar } from '../components/ui/Avatar';
import { ProgressBar } from '../components/ui/ProgressBar';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/Card';
import { EmptyState } from '../components/ui/EmptyState';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { Alert } from '../components/ui/Alert';
import { Spinner } from '../components/ui/Spinner';
import { CreateActivityModal } from '../components/activities/CreateActivityModal';
import { activityService } from '../services/activityService';
import { projectService } from '../services/projectService';
import { userService } from '../services/userService';
import { ApiError } from '../services/ApiError';
import { usePermissions } from '../hooks/usePermissions';
import { HistoryTimeline } from '../components/history/HistoryTimeline';
import { ROUTES } from '../constants/routes';
import type { Activity, ActivityStatus, Priority, Project, User } from '../types/models';

const STATUTS: Record<ActivityStatus, { label: string; variant: 'success' | 'info' | 'danger' | 'muted' }> = {
  done: { label: 'Terminée', variant: 'success' },
  in_progress: { label: 'En cours', variant: 'info' },
  blocked: { label: 'Bloquée', variant: 'danger' },
  todo: { label: 'À faire', variant: 'muted' },
};

const PRIORITES: Record<Priority, { label: string; variant: 'danger' | 'warning' | 'info' | 'muted' }> = {
  urgent: { label: 'Urgent', variant: 'danger' },
  high: { label: 'Haute', variant: 'warning' },
  medium: { label: 'Moyenne', variant: 'info' },
  low: { label: 'Basse', variant: 'muted' },
};

function formatDate(date?: string) {
  if (!date) return '—';
  return new Date(date).toLocaleDateString('fr-FR', { day: '2-digit', month: 'long', year: 'numeric' });
}

function Champ({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <p className="text-xs font-medium uppercase text-muted-foreground">{label}</p>
      <div className="mt-1 text-sm text-foreground">{children}</div>
    </div>
  );
}

export function ActivityDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const permissions = usePermissions();

  const [activity, setActivity] = useState<Activity | null>(null);
  const [project, setProject] = useState<Project | null>(null);
  const [users, setUsers] = useState<User[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  // Activité, puis son projet (droits de gestion) ; 404/403 : « introuvable ».
  useEffect(() => {
    if (!id) return;
    let annule = false;
    activityService
      .getById(id)
      .then(async (a) => {
        if (annule) return;
        setActivity(a);
        try {
          const p = await projectService.getById(a.projectId);
          if (!annule) setProject(p);
        } catch {
          /* Projet non visible : l'activité reste affichée, sans actions de gestion. */
        }
      })
      .catch((err) => {
        if (annule) return;
        setActivity(null);
        if (err instanceof ApiError && err.status !== 404) setError(err.message);
      })
      .finally(() => {
        if (!annule) setIsLoading(false);
      });
    return () => {
      annule = true;
    };
  }, [id]);

  // Historique de l'activité (source stable pour HistoryTimeline).
  const chargerHistorique = useCallback(
    (page: number, perPage: number) => activityService.history(id ?? '', page, perPage),
    [id]
  );

  // Responsables possibles (ADMIN et MANAGER seulement).
  const { canListUsers } = permissions;
  useEffect(() => {
    if (!canListUsers) return;
    let annule = false;
    userService
      .listAll()
      .then((liste) => {
        if (!annule) setUsers(liste);
      })
      .catch(() => {
        /* Sans la liste, le choix du responsable est masqué. */
      });
    return () => {
      annule = true;
    };
  }, [canListUsers]);

  if (isLoading) {
    return (
      <Container className="flex justify-center py-16">
        <Spinner size="lg" />
      </Container>
    );
  }

  if (!activity) {
    return (
      <Container className="py-6">
        <EmptyState
          icon={<AlertTriangle className="h-6 w-6" />}
          title="Activité introuvable"
          description={error ?? "L'activité demandée n'existe pas, a été supprimée ou ne vous est pas accessible."}
          action={
            <Link to={ROUTES.ACTIVITIES}>
              <Button variant="outline" leftIcon={<ArrowLeft className="h-4 w-4" />}>
                Retour aux activités
              </Button>
            </Link>
          }
        />
      </Container>
    );
  }

  const peutGerer = permissions.canManageActivity(project?.ownerId);
  const peutSuivre = permissions.canTrackActivity(activity, project?.ownerId);
  const statut = STATUTS[activity.status];
  const priorite = PRIORITES[activity.priority];

  // Les erreurs remontent jusqu'au formulaire, qui affiche le message du backend.
  const handleUpdate = async (data: Partial<Activity>) => {
    setActivity(await activityService.update(activity.id, data));
  };

  const handleDelete = async () => {
    setIsDeleting(true);
    try {
      await activityService.remove(activity.id);
      setDeleteOpen(false);
      navigate(ROUTES.ACTIVITIES);
    } catch (err) {
      setDeleteOpen(false);
      setError(err instanceof ApiError ? err.message : 'Suppression impossible.');
    } finally {
      setIsDeleting(false);
    }
  };

  return (
    <Container className="py-6">
      <Link
        to={ROUTES.ACTIVITIES}
        className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        Retour aux activités
      </Link>

      {error && (
        <Alert variant="danger" className="mb-4">
          {error}
        </Alert>
      )}

      <div className="flex flex-col gap-4 border-b border-border pb-6 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold text-foreground">{activity.title}</h1>
            <Badge variant={statut.variant}>{statut.label}</Badge>
            <Badge variant={priorite.variant}>
              <Flag className="mr-1 h-3 w-3" />
              {priorite.label}
            </Badge>
            {activity.isOverdue && <Badge variant="danger">En retard</Badge>}
          </div>
          <Link
            to={`${ROUTES.PROJECTS}/${activity.projectId}`}
            className="mt-2 inline-flex items-center gap-1.5 text-sm text-primary hover:underline"
          >
            <FolderKanban className="h-4 w-4" />
            {activity.projectName ?? project?.name ?? 'Voir le projet'}
          </Link>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {peutSuivre && (
            <Button variant="outline" leftIcon={<Edit className="h-4 w-4" />} onClick={() => setIsEditOpen(true)}>
              {peutGerer ? 'Modifier' : "Mettre à jour l'avancement"}
            </Button>
          )}
          {peutGerer && (
            <Button variant="danger" leftIcon={<Trash2 className="h-4 w-4" />} onClick={() => setDeleteOpen(true)}>
              Supprimer
            </Button>
          )}
        </div>
      </div>

      <div className="mt-6 grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Détails</CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            <Champ label="Description">
              {activity.description ? (
                <p className="whitespace-pre-line">{activity.description}</p>
              ) : (
                <p className="text-muted-foreground">Aucune description.</p>
              )}
            </Champ>

            <Champ label="Progression">
              <div className="flex items-center gap-3">
                <ProgressBar
                  value={activity.progress}
                  variant={activity.progress === 100 ? 'success' : activity.progress < 40 ? 'danger' : 'default'}
                  className="flex-1"
                />
                <span className="font-semibold">{activity.progress}%</span>
              </div>
            </Champ>

            <div className="grid gap-4 sm:grid-cols-2">
              <Champ label="Date de début">{formatDate(activity.startDate)}</Champ>
              <Champ label="Échéance">
                <span className={activity.isOverdue ? 'font-semibold text-danger' : undefined}>
                  {formatDate(activity.dueDate)}
                </span>
              </Champ>
              <Champ label="Créée le">{formatDate(activity.createdAt)}</Champ>
              <Champ label="Mise à jour">{formatDate(activity.updatedAt)}</Champ>
              {activity.completedAt && <Champ label="Terminée le">{formatDate(activity.completedAt)}</Champ>}
            </div>
          </CardContent>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <UserIcon className="h-5 w-5" />
                Responsable
              </CardTitle>
            </CardHeader>
            <CardContent>
              {activity.assignee ? (
                <div className="flex items-center gap-3">
                  <Avatar name={activity.assignee.name} src={activity.assignee.avatarUrl} />
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-foreground">{activity.assignee.name}</p>
                    <p className="truncate text-xs text-muted-foreground">{activity.assignee.email}</p>
                  </div>
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">Aucun responsable affecté.</p>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <TrendingUp className="h-5 w-5" />
                Suivi
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div className="flex items-center gap-2">
                <Calendar className="h-4 w-4 text-muted-foreground" />
                <span>
                  {activity.dueDate ? `Échéance le ${formatDate(activity.dueDate)}` : "Pas d'échéance"}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <CheckCircle2 className="h-4 w-4 text-muted-foreground" />
                <span>{statut.label}</span>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      <Card className="mt-6">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <History className="h-5 w-5" />
            Historique
          </CardTitle>
        </CardHeader>
        <CardContent>
          {/* Clé : recharge l'historique après une modification de l'activité. */}
          <HistoryTimeline key={activity.updatedAt} load={chargerHistorique} />
        </CardContent>
      </Card>

      {isEditOpen && (
        <CreateActivityModal
          key={activity.id}
          isOpen
          activity={activity}
          trackOnly={!peutGerer}
          projects={project ? [project] : []}
          assignees={users}
          onClose={() => setIsEditOpen(false)}
          onCreate={handleUpdate}
        />
      )}

      <ConfirmDialog
        isOpen={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={handleDelete}
        title="Supprimer l'activité ?"
        description={`L'activité "${activity.title}" sera définitivement supprimée.`}
        confirmLabel="Supprimer"
        isLoading={isDeleting}
      />
    </Container>
  );
}
