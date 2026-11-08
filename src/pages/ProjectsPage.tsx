import { useMemo, useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Plus, FolderKanban } from 'lucide-react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { Avatar } from '../components/ui/Avatar';
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
import { ProjectsFilters, type ViewMode } from '../components/projects/ProjectsFilters';
import { ProjectCard } from '../components/projects/ProjectCard';
import { CreateProjectModal } from '../components/projects/CreateProjectModal';
import { SkeletonTable } from '../components/ui/SkeletonTable';
import type { Project, ProjectStatus, Priority } from '../types/models';
import { Alert } from '../components/ui/Alert';
import { projectService } from '../services/projectService';
import { ApiError } from '../services/ApiError';

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

export function ProjectsPage() {
  const navigate = useNavigate();
  const [projects, setProjects] = useState<Project[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('all');
  const [priority, setPriority] = useState('all');
  const [sort, setSort] = useState('recent');
  const [view, setView] = useState<ViewMode>('table');

  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Project | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Chargement des projets visibles par l'utilisateur connecté.
  useEffect(() => {
    let annule = false;
    projectService
      .listAll()
      .then((liste) => {
        if (!annule) setProjects(liste);
      })
      .catch((err) => {
        if (!annule) setError(err instanceof ApiError ? err.message : 'Chargement des projets impossible.');
      })
      .finally(() => {
        if (!annule) setIsLoading(false);
      });
    return () => {
      annule = true;
    };
  }, []);

  const filtered = useMemo(() => {
    let result = [...projects];

    if (search.trim()) {
      const q = search.toLowerCase();
      result = result.filter(
        (p) =>
          p.name.toLowerCase().includes(q) ||
          p.description?.toLowerCase().includes(q)
      );
    }

    if (status !== 'all') {
      result = result.filter((p) => p.status === status);
    }

    if (priority !== 'all') {
      result = result.filter((p) => p.priority === priority);
    }

    switch (sort) {
      case 'oldest':
        result.sort(
          (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
        );
        break;
      case 'name':
        result.sort((a, b) => a.name.localeCompare(b.name));
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
  }, [projects, search, status, priority, sort]);

  // Les erreurs remontent jusqu'au formulaire, qui affiche le message du backend.
  const handleCreate = async (data: Partial<Project>) => {
    const cree = await projectService.create(data);
    setProjects((prev) => [cree, ...prev]);
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setIsDeleting(true);
    try {
      await projectService.remove(deleteTarget.id);
      setProjects((prev) => prev.filter((p) => p.id !== deleteTarget.id));
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
        title="Projets"
        description="Gérez tous vos projets en un seul endroit."
        actions={
          <Button
            leftIcon={<Plus className="h-4 w-4" />}
            onClick={() => setIsCreateOpen(true)}
          >
            Nouveau projet
          </Button>
        }
      />

      {error && (
        <Alert variant="danger" className="mt-6">
          {error}
        </Alert>
      )}

      <div className="mt-6">
        <ProjectsFilters
          search={search}
          onSearchChange={setSearch}
          status={status}
          onStatusChange={setStatus}
          priority={priority}
          onPriorityChange={setPriority}
          sort={sort}
          onSortChange={setSort}
          view={view}
          onViewChange={setView}
        />
      </div>

      <div className="mt-6">
        {/* ÉTAT DE CHARGEMENT (SKELETON) */}
        {isLoading ? (
          <SkeletonTable rows={6} cols={6} />
        ) : filtered.length === 0 ? (
          /* ÉTAT VIDE */
          <EmptyState
            icon={<FolderKanban className="h-6 w-6" />}
            title="Aucun projet trouvé"
            description="Ajustez vos filtres ou créez un nouveau projet."
            action={
              <Button
                leftIcon={<Plus className="h-4 w-4" />}
                onClick={() => setIsCreateOpen(true)}
              >
                Créer un projet
              </Button>
            }
          />
        ) : view === 'table' ? (
          /* VUE TABLEAU */
          <div className="overflow-x-auto rounded-lg border border-border bg-surface">
            <Table>
              <TableHead>
                <TableRow>
                  <TableHeader>Projet</TableHeader>
                  <TableHeader>Responsable</TableHeader>
                  <TableHeader>Progression</TableHeader>
                  <TableHeader>Statut</TableHeader>
                  <TableHeader>Priorité</TableHeader>
                  <TableHeader>Échéance</TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {filtered.map((project) => (
                  <TableRow
                    key={project.id}
                    className="cursor-pointer"
                    onClick={() => navigate(`/projects/${project.id}`)}
                  >
                    <TableCell>
                      <div className="min-w-0">
                        <p className="font-medium text-foreground">{project.name}</p>
                        {project.description && (
                          <p className="truncate text-xs text-muted-foreground">
                            {project.description}
                          </p>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      {project.owner && (
                        <div className="flex items-center gap-2">
                          <Avatar name={project.owner.name} size="sm" />
                          <span className="text-sm">{project.owner.name}</span>
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="w-48">
                      <div className="flex items-center gap-2">
                        <ProgressBar
                          value={project.progress}
                          variant={
                            project.progress === 100
                              ? 'success'
                              : project.progress < 40
                              ? 'danger'
                              : 'default'
                          }
                        />
                        <span className="w-10 shrink-0 text-xs text-muted-foreground">
                          {project.progress}%
                        </span>
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge variant={getStatusVariant(project.status)}>
                        {getStatusLabel(project.status)}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant="muted">{getPriorityLabel(project.priority)}</Badge>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {project.dueDate
                        ? new Date(project.dueDate).toLocaleDateString('fr-FR', {
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
          /* VUE CARTES */
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {filtered.map((project) => (
              <ProjectCard
                key={project.id}
                project={project}
                onView={(p) => navigate(`/projects/${p.id}`)}
                onDelete={(p) => setDeleteTarget(p)}
              />
            ))}
          </div>
        )}
      </div>

      <CreateProjectModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onCreate={handleCreate}
      />

      <ConfirmDialog
        isOpen={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDeleteConfirm}
        title="Supprimer le projet ?"
        description={`Le projet "${deleteTarget?.name}" sera définitivement supprimé.`}
        confirmLabel="Supprimer"
        isLoading={isDeleting}
      />
    </Container>
  );
}