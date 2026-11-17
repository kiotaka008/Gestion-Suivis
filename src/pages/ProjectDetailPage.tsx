import { useCallback, useEffect, useMemo, useState } from 'react';
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
  Archive,
  ArchiveRestore,
  Plus,
  UserMinus,
  UserPlus,
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
import type { Activity, ActivityStatus, Priority, ProjectStatus, Role, User } from '../types/models';
import { Select } from '../components/ui/Select';
import { CreateProjectModal } from '../components/projects/CreateProjectModal';
import { CreateActivityModal } from '../components/activities/CreateActivityModal';
import { activityService } from '../services/activityService';
import { userService } from '../services/userService';
import { usePermissions } from '../hooks/usePermissions';
import { HistoryTimeline } from '../components/history/HistoryTimeline';
import { Alert } from '../components/ui/Alert';
import { BoutonActualiser } from '../components/ui/BoutonActualiser';
import { Spinner } from '../components/ui/Spinner';
import { projectService } from '../services/projectService';
import { ApiError } from '../services/ApiError';
import type { Project } from '../types/models';
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

  const [project, setProject] = useState<Project | null>(null);
  const [activities, setActivities] = useState<Activity[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  // AUD-002, F9 : incrémenté par « Actualiser » pour relire aussi l'historique.
  const [versionHistorique, setVersionHistorique] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const permissions = usePermissions();
  const [users, setUsers] = useState<User[]>([]);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isActivityOpen, setIsActivityOpen] = useState(false);
  const [nouveauMembre, setNouveauMembre] = useState('');
  const [enCours, setEnCours] = useState(false);

  // Utilisateurs de l'organisation (choix du chef de projet, des membres et des responsables),
  // visibles seulement par les administrateurs et responsables.
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
        /* Sans la liste, les choix de membres et de responsables sont simplement masqués. */
      });
    return () => {
      annule = true;
    };
  }, [canListUsers]);

  // Historique du projet et de ses activités (source stable pour HistoryTimeline).
  const chargerHistorique = useCallback(
    (page: number, perPage: number) => projectService.history(id ?? '', page, perPage),
    [id]
  );

  const peutGerer = permissions.canManageProject(project);
  const estClos = project?.status === 'completed' || project?.status === 'archived';
  const candidatsMembres = useMemo(
    () =>
      users.filter(
        (u) => u.isActive && u.id !== project?.ownerId && !(project?.memberIds ?? []).includes(u.id)
      ),
    [users, project]
  );

  // Les erreurs des formulaires remontent jusqu'à eux et affichent le message du backend.
  const handleUpdate = async (data: Partial<Project>) => {
    if (!project) return;
    setProject(await projectService.update(project.id, data));
  };

  // Conflit 409 : le projet est relu ; le formulaire garde la saisie jusqu'au clic « Recharger ».
  const rechargerProjet = async () => {
    if (!project) return;
    setProject(await projectService.getById(project.id));
  };

  // AUD-002, F9 : après un 404 ou un 409, le projet est relu une fois (un 404 peut aussi viser
  // un membre : seule une relecture en 404 signifie que le projet a disparu). Le message
  // d'erreur déjà affiché n'est jamais remplacé.
  const relireProjetApresEchec = async (err: unknown) => {
    if (!project) return;
    const statut = err instanceof ApiError ? err.status : undefined;
    if (statut !== 404 && statut !== 409) return;
    try {
      setProject(await projectService.getById(project.id));
    } catch (relecture) {
      if (relecture instanceof ApiError && relecture.status === 404) setProject(null);
    }
  };

  // AUD-002, F9 : actualisation manuelle du projet, de ses activités et de son historique ;
  // les formulaires ouverts gardent leur saisie (ils comparent à leur propre référence).
  const actualiser = async () => {
    if (!project) return;
    try {
      const [projet, liste] = await Promise.all([
        projectService.getById(project.id),
        projectService.activities(project.id),
      ]);
      setProject(projet);
      setActivities(liste);
      setVersionHistorique((v) => v + 1);
      setError(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Actualisation impossible.');
      if (err instanceof ApiError && err.status === 404) setProject(null);
    }
  };

  const executer = async (action: () => Promise<Project>, messageEchec: string) => {
    setError(null);
    setEnCours(true);
    try {
      setProject(await action());
      return true;
    } catch (err) {
      setError(err instanceof ApiError ? err.message : messageEchec);
      await relireProjetApresEchec(err);
      return false;
    } finally {
      setEnCours(false);
    }
  };

  const changerStatut = (statut: ProjectStatus) => {
    if (!project) return;
    void executer(() => projectService.update(project.id, { status: statut }), 'Modification impossible.');
  };

  const ajouterMembre = async () => {
    if (!project || !nouveauMembre) return;
    const ok = await executer(() => projectService.addMember(project.id, nouveauMembre), 'Ajout du membre impossible.');
    if (ok) setNouveauMembre('');
  };

  const retirerMembre = (userId: string) => {
    if (!project) return;
    void executer(() => projectService.removeMember(project.id, userId), 'Retrait du membre impossible.');
  };

  const handleCreateActivity = async (data: Partial<Activity>) => {
    if (!project) return;
    let creee: Activity;
    try {
      creee = await activityService.create({ ...data, projectId: project.id });
    } catch (err) {
      // AUD-002, F9 : projet relu (clôture, par exemple) ; le formulaire affiche le message et
      // garde la saisie.
      await relireProjetApresEchec(err);
      throw err;
    }
    setActivities((prev) => [creee, ...prev]);
    // La progression du projet est recalculée par le backend.
    setProject(await projectService.getById(project.id));
  };

  // Projet et activités visibles, chargés depuis le backend (404/403 → « introuvable »).
  useEffect(() => {
    if (!id) return;
    let annule = false;
    Promise.all([projectService.getById(id), projectService.activities(id)])
      .then(([projet, liste]) => {
        if (annule) return;
        setProject(projet);
        setActivities(liste);
      })
      .catch((err) => {
        if (annule) return;
        setProject(null);
        if (err instanceof ApiError && err.status !== 404) setError(err.message);
      })
      .finally(() => {
        if (!annule) setIsLoading(false);
      });
    return () => {
      annule = true;
    };
  }, [id]);

  const handleDelete = async () => {
    if (!project) return;
    setIsDeleting(true);
    try {
      await projectService.remove(project.id);
      setDeleteOpen(false);
      navigate(ROUTES.PROJECTS);
    } catch (err) {
      setDeleteOpen(false);
      setError(err instanceof ApiError ? err.message : 'Suppression impossible.');
      // 404 : le projet a disparu (« introuvable ») ; 409 : relu.
      if (err instanceof ApiError && err.status === 404) setProject(null);
      else await relireProjetApresEchec(err);
    } finally {
      setIsDeleting(false);
    }
  };

  if (isLoading) {
    return (
      <Container className="flex justify-center py-16">
        <Spinner size="lg" />
      </Container>
    );
  }

  if (!project) {
    return (
      <Container className="py-6">
        <EmptyState
          icon={<AlertTriangle className="h-6 w-6" />}
          title="Projet introuvable"
          description={error ?? "Le projet demandé n'existe pas ou a été supprimé."}
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

      {error && (
        <Alert variant="danger" className="mb-4">
          {error}
        </Alert>
      )}

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

        <div className="flex flex-wrap items-center gap-2">
          <BoutonActualiser onActualiser={actualiser} />
          {peutGerer && (
            <Button variant="outline" leftIcon={<Edit className="h-4 w-4" />} onClick={() => setIsEditOpen(true)}>
              Modifier
            </Button>
          )}
          {peutGerer &&
            (project.status === 'archived' ? (
              <Button
                variant="outline"
                leftIcon={<ArchiveRestore className="h-4 w-4" />}
                isLoading={enCours}
                onClick={() => changerStatut('active')}
              >
                Réactiver
              </Button>
            ) : (
              <Button
                variant="outline"
                leftIcon={<Archive className="h-4 w-4" />}
                isLoading={enCours}
                onClick={() => changerStatut('archived')}
              >
                Archiver
              </Button>
            ))}
          {permissions.canDeleteProject && (
            <Button
              variant="danger"
              leftIcon={<Trash2 className="h-4 w-4" />}
              onClick={() => setDeleteOpen(true)}
            >
              Supprimer
            </Button>
          )}
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
                      {(project.members ?? []).length === 0 && (
                        <p className="text-sm text-muted-foreground">Aucun membre.</p>
                      )}
                      {(project.members ?? []).map((membre) => (
                        <div key={membre.id} className="flex items-center gap-2">
                          <Avatar name={membre.name} src={membre.avatarUrl} size="sm" />
                          <span className="text-sm text-foreground">{membre.name}</span>
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
              {estClos && (
                <Alert variant="info" className="mt-2">
                  {project.status === 'archived' ? 'Projet archivé' : 'Projet terminé'} : les activités sont en lecture
                  seule.{peutGerer && ' Réactivez le projet pour les modifier.'}
                </Alert>
              )}
              {peutGerer && !estClos && (
                <div className="pt-2">
                  <Button size="sm" leftIcon={<Plus className="h-4 w-4" />} onClick={() => setIsActivityOpen(true)}>
                    Nouvelle activité
                  </Button>
                </div>
              )}
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
                    <Link
                      key={activity.id}
                      to={`/activities/${activity.id}`}
                      className="flex items-center justify-between gap-3 rounded-lg border border-border p-3 transition-colors hover:bg-muted"
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
                    </Link>
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
              {peutGerer && canListUsers && (
                <div className="flex flex-col gap-2 rounded-lg border border-dashed border-border p-3 sm:flex-row sm:items-end">
                  <Select
                    label="Ajouter un membre"
                    name="nouveauMembre"
                    wrapperClassName="flex-1"
                    value={nouveauMembre}
                    onChange={(e) => setNouveauMembre(e.target.value)}
                    disabled={candidatsMembres.length === 0}
                    options={[
                      {
                        value: '',
                        label: candidatsMembres.length === 0 ? 'Aucun utilisateur à ajouter' : 'Choisir un utilisateur…',
                      },
                      ...candidatsMembres.map((u) => ({ value: u.id, label: `${u.name} (${u.email})` })),
                    ]}
                  />
                  <Button
                    leftIcon={<UserPlus className="h-4 w-4" />}
                    onClick={ajouterMembre}
                    disabled={!nouveauMembre}
                    isLoading={enCours}
                  >
                    Ajouter
                  </Button>
                </div>
              )}
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
              {(project.members ?? [])
                .filter((membre) => membre.id !== project.ownerId)
                .map((membre) => (
                  <div
                    key={membre.id}
                    className="flex items-center justify-between rounded-lg border border-border p-3"
                  >
                    <div className="flex items-center gap-3">
                      <Avatar name={membre.name} src={membre.avatarUrl} />
                      <div>
                        <p className="text-sm font-medium text-foreground">{membre.name}</p>
                        <p className="text-xs text-muted-foreground">{membre.email}</p>
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <Badge variant="muted">Membre</Badge>
                      {peutGerer && (
                        <Button
                          size="sm"
                          variant="ghost"
                          leftIcon={<UserMinus className="h-4 w-4" />}
                          onClick={() => retirerMembre(membre.id)}
                          disabled={enCours}
                          aria-label={`Retirer ${membre.name} du projet`}
                        >
                          Retirer
                        </Button>
                      )}
                    </div>
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
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <History className="h-5 w-5" />
                Historique
              </CardTitle>
              <CardDescription>Actions sur le projet et ses activités, de la plus récente à la plus ancienne.</CardDescription>
            </CardHeader>
            <CardContent>
              <HistoryTimeline key={versionHistorique} load={chargerHistorique} />
            </CardContent>
          </Card>
        )}
      </div>

      {isEditOpen && (
        <CreateProjectModal
          key={project.id}
          isOpen
          project={project}
          // Seul un administrateur peut changer le chef de projet (règle du backend).
          owners={permissions.isAdmin ? users : undefined}
          onClose={() => setIsEditOpen(false)}
          onCreate={handleUpdate}
          onConflit={rechargerProjet}
        />
      )}

      {isActivityOpen && (
        <CreateActivityModal
          isOpen
          projects={[project]}
          defaultProjectId={project.id}
          assignees={users}
          onClose={() => setIsActivityOpen(false)}
          onCreate={handleCreateActivity}
        />
      )}

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