import { useMemo, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import {
  ArrowLeft,
  Edit,
  Trash2,
  Calendar,
  Flag,
  Users,
  FileText,
  History,
  ClipboardList,
  LayoutDashboard,
  AlertTriangle,
} from 'lucide-react';
import { Container } from '../components/ui/Container';
import { Button } from '../components/ui/Button';
import { Badge } from '../components/ui/Badge';
import { Avatar } from '../components/ui/Avatar';
import { ProgressBar } from '../components/ui/ProgressBar';
import { Tabs, type Tab } from '../components/ui/Tabs';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/Card';
import { EmptyState } from '../components/ui/EmptyState';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import type { Activity, ActivityStatus, Priority, ProjectStatus, Role } from '../types/models';
import { mockProjects } from '../mocks/projects';
import { mockActivities } from '../mocks/activities';
import { ROUTES } from '../constants/routes';

function getStatusVariant(status: ProjectStatus) {
  switch (status) {
    case 'completed':
      return 'success' as const;
    case 'active':
      return 'info' as const;
    case 'on_hold':
      return 'warning' as const;
    default:
      return 'muted' as const;
  }
}

function getStatusLabel(status: ProjectStatus) {
  switch (status) {
    case 'completed':
      return 'Terminé';
    case 'active':
      return 'En cours';
    case 'on_hold':
      return 'En attente';
    default:
      return 'Archivé';
  }
}

function getPriorityVariant(priority: Priority) {
  switch (priority) {
    case 'urgent':
      return 'danger' as const;
    case 'high':
      return 'warning' as const;
    case 'medium':
      return 'info' as const;
    default:
      return 'muted' as const;
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

function getActivityStatusVariant(status: ActivityStatus) {
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

function getActivityStatusLabel(status: ActivityStatus) {
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

function getRoleLabel(role: Role) {
  switch (role) {
    case 'admin':
      return 'Administrateur';
    case 'manager':
      return 'Responsable';
    default:
      return 'Membre';
  }
}

function formatDate(date?: string) {
  if (!date) return '—';
  return new Date(date).toLocaleDateString('fr-FR', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  });
}

const TABS: Tab[] = [
  { id: 'overview', label: "Vue d'ensemble" },
  { id: 'activities', label: 'Activités' },
  { id: 'members', label: 'Membres' },
  { id: 'files', label: 'Fichiers' },
  { id: 'history', label: 'Historique' },
];

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('overview');
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);

  const project = useMemo(() => mockProjects.find((p) => p.id === id), [id]);

  const activities = useMemo<Activity[]>(
    () => mockActivities.filter((a) => a.projectId === id),
    [id]
  );

  const handleDelete = async () => {
    setIsDeleting(true);
    await new Promise((r) => setTimeout(r, 500));
    setIsDeleting(false);
    setDeleteOpen(false);
    navigate(ROUTES.PROJECTS);
  };

  if (!project) {
    return (
      <Container className="py-6">
        <EmptyState
          icon={<AlertTriangle className="h-6 w-6" />}
          title="Projet introuvable"
          description="Le projet demandé n'existe pas ou a été supprimé."
          action={
            <Link to={ROUTES.PROJECTS}>
              <Button variant="outline" leftIcon={<ArrowLeft className="h-4 w-4" />}>
                Retour aux projets
              </Button>
            </Link>
          }
        />
      </Container>
    );
  }

  return (
    <Container className="py-6">
      <Link
        to={ROUTES.PROJECTS}
        className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" />
        Retour aux projets
      </Link>

      <div className="flex flex-col gap-4 border-b border-border pb-6 lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold text-foreground">{project.name}</h1>
            <Badge variant={getStatusVariant(project.status)}>
              {getStatusLabel(project.status)}
            </Badge>
            <Badge variant={getPriorityVariant(project.priority)}>
              <Flag className="mr-1 h-3 w-3" />
              {getPriorityLabel(project.priority)}
            </Badge>
          </div>
          {project.description && (
            <p className="mt-2 text-sm text-muted-foreground">{project.description}</p>
          )}
        </div>

        <div className="flex items-center gap-2">
          <Button variant="outline" leftIcon={<Edit className="h-4 w-4" />}>
            Modifier
          </Button>
          <Button
            variant="danger"
            leftIcon={<Trash2 className="h-4 w-4" />}
            onClick={() => setDeleteOpen(true)}
          >
            Supprimer
          </Button>
        </div>
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="pb-3">
            <CardDescription>Progression globale</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-3">
              <ProgressBar
                value={project.progress}
                variant={
                  project.progress === 100
                    ? 'success'
                    : project.progress < 40
                    ? 'danger'
                    : 'default'
                }
                className="flex-1"
              />
              <span className="text-lg font-bold text-foreground">{project.progress}%</span>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-3">
            <CardDescription>Échéance</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2 text-foreground">
              <Calendar className="h-5 w-5 text-muted-foreground" />
              <span className="font-semibold">{formatDate(project.dueDate)}</span>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-3">
            <CardDescription>Membres</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-2">
              <Users className="h-5 w-5 text-muted-foreground" />
              <span className="font-semibold text-foreground">
                {project.memberIds.length} membre{project.memberIds.length > 1 ? 's' : ''}
              </span>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="mt-6">
        <Tabs tabs={TABS} active={activeTab} onChange={setActiveTab} />
      </div>

      <div className="mt-6">
        {activeTab === 'overview' && (
          <div className="grid gap-6 lg:grid-cols-3">
            <div className="lg:col-span-2">
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <LayoutDashboard className="h-5 w-5" />
                    Informations générales
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div>
                    <p className="text-xs font-medium uppercase text-muted-foreground">Description</p>
                    <p className="mt-1 text-sm text-foreground">
                      {project.description ?? 'Aucune description.'}
                    </p>
                  </div>
                  <div className="grid gap-4 md:grid-cols-2">
                    <div>
                      <p className="text-xs font-medium uppercase text-muted-foreground">Date de début</p>
                      <p className="mt-1 text-sm text-foreground">{formatDate(project.startDate)}</p>
                    </div>
                    <div>
                      <p className="text-xs font-medium uppercase text-muted-foreground">Échéance</p>
                      <p className="mt-1 text-sm text-foreground">{formatDate(project.dueDate)}</p>
                    </div>
                    <div>
                      <p className="text-xs font-medium uppercase text-muted-foreground">Créé le</p>
                      <p className="mt-1 text-sm text-foreground">{formatDate(project.createdAt)}</p>
                    </div>
                    <div>
                      <p className="text-xs font-medium uppercase text-muted-foreground">Mis à jour</p>
                      <p className="mt-1 text-sm text-foreground">{formatDate(project.updatedAt)}</p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>

            <div>
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2">
                    <Users className="h-5 w-5" />
                    Équipe
                  </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                  {project.owner && (
                    <div>
                      <p className="text-xs font-medium uppercase text-muted-foreground">Responsable</p>
                      <div className="mt-2 flex items-center gap-2">
                        <Avatar name={project.owner.name} size="sm" />
                        <div>
                          <p className="text-sm font-medium text-foreground">{project.owner.name}</p>
                          <p className="text-xs text-muted-foreground">
                            {getRoleLabel(project.owner.role)}
                          </p>
                        </div>
                      </div>
                    </div>
                  )}
                  <div>
                    <p className="text-xs font-medium uppercase text-muted-foreground">
                      Membres ({project.memberIds.length})
                    </p>
                    <div className="mt-2 space-y-2">
                      {project.memberIds.map((memberId) => (
                        <div key={memberId} className="flex items-center gap-2">
                          <Avatar name={`Membre ${memberId.slice(-1)}`} size="sm" />
                          <span className="text-sm text-foreground">
                            Membre {memberId.slice(-1)}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>
        )}

        {activeTab === 'activities' && (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <ClipboardList className="h-5 w-5" />
                Activités ({activities.length})
              </CardTitle>
              <CardDescription>Toutes les activités liées à ce projet.</CardDescription>
            </CardHeader>
            <CardContent>
              {activities.length === 0 ? (
                <EmptyState
                  icon={<ClipboardList className="h-6 w-6" />}
                  title="Aucune activité"
                  description="Ce projet n'a pas encore d'activités."
                />
              ) : (
                <div className="space-y-2">
                  {activities.map((activity) => (
                    <div
                      key={activity.id}
                      className="flex items-center justify-between gap-3 rounded-lg border border-border p-3"
                    >
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-foreground">
                          {activity.title}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          Échéance : {formatDate(activity.dueDate)}
                        </p>
                      </div>
                      <Badge variant={getActivityStatusVariant(activity.status)}>
                        {getActivityStatusLabel(activity.status)}
                      </Badge>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        )}

        {activeTab === 'members' && (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Users className="h-5 w-5" />
                Membres du projet
              </CardTitle>
              <CardDescription>Personnes ayant accès à ce projet.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {project.owner && (
                <div className="flex items-center justify-between rounded-lg border border-border p-3">
                  <div className="flex items-center gap-3">
                    <Avatar name={project.owner.name} />
                    <div>
                      <p className="text-sm font-medium text-foreground">{project.owner.name}</p>
                      <p className="text-xs text-muted-foreground">{project.owner.email}</p>
                    </div>
                  </div>
                  <Badge variant="info">Responsable</Badge>
                </div>
              )}
              {project.memberIds
                .filter((mid) => mid !== project.ownerId)
                .map((memberId) => (
                  <div
                    key={memberId}
                    className="flex items-center justify-between rounded-lg border border-border p-3"
                  >
                    <div className="flex items-center gap-3">
                      <Avatar name={`Membre ${memberId.slice(-1)}`} />
                      <div>
                        <p className="text-sm font-medium text-foreground">
                          Membre {memberId.slice(-1)}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          membre{memberId.slice(-1)}@company.com
                        </p>
                      </div>
                    </div>
                    <Badge variant="muted">Membre</Badge>
                  </div>
                ))}
            </CardContent>
          </Card>
        )}

        {activeTab === 'files' && (
          <EmptyState
            icon={<FileText className="h-6 w-6" />}
            title="Aucun fichier"
            description="Le partage de fichiers sera bientôt disponible."
          />
        )}

        {activeTab === 'history' && (
          <EmptyState
            icon={<History className="h-6 w-6" />}
            title="Historique vide"
            description="L'historique des modifications s'affichera ici."
          />
        )}
      </div>

      <ConfirmDialog
        isOpen={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={handleDelete}
        title="Supprimer le projet ?"
        description={`Le projet "${project.name}" et toutes ses activités seront définitivement supprimés.`}
        confirmLabel="Supprimer"
        isLoading={isDeleting}
      />
    </Container>
  );
}