import { MoreVertical, Calendar, Users, Edit, Trash2, Eye } from 'lucide-react';
import type { Project, ProjectStatus, Priority } from '../../types/models';
import { Card, CardContent, CardHeader } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { Avatar } from '../ui/Avatar';
import { ProgressBar } from '../ui/ProgressBar';
import { Dropdown } from '../ui/Dropdown';

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

function formatDate(date?: string) {
  if (!date) return '—';
  return new Date(date).toLocaleDateString('fr-FR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

export interface ProjectCardProps {
  project: Project;
  onEdit?: (project: Project) => void;
  onDelete?: (project: Project) => void;
  onView?: (project: Project) => void;
}

export function ProjectCard({ project, onEdit, onDelete, onView }: ProjectCardProps) {
  return (
    <Card className="group transition-shadow hover:shadow-md">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0 flex-1">
            <h3 className="truncate text-base font-semibold text-foreground">
              {project.name}
            </h3>
            {project.description && (
              <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">
                {project.description}
              </p>
            )}
          </div>

          <Dropdown
            trigger={
              <button
                className="rounded-md p-1 text-muted-foreground hover:bg-muted"
                aria-label="Actions du projet"
              >
                <MoreVertical className="h-4 w-4" />
              </button>
            }
            items={[
              {
                label: 'Voir',
                icon: <Eye className="h-4 w-4" />,
                onClick: () => onView?.(project),
              },
              {
                label: 'Modifier',
                icon: <Edit className="h-4 w-4" />,
                onClick: () => onEdit?.(project),
              },
              {
                label: 'Supprimer',
                icon: <Trash2 className="h-4 w-4" />,
                variant: 'danger',
                onClick: () => onDelete?.(project),
              },
            ]}
          />
        </div>
      </CardHeader>

      <CardContent className="space-y-4 pt-0">
        {/* Badges */}
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant={getStatusVariant(project.status)}>
            {getStatusLabel(project.status)}
          </Badge>
          <Badge variant="muted">Priorité : {getPriorityLabel(project.priority)}</Badge>
        </div>

        {/* Progression */}
        <div>
          <div className="mb-2 flex items-center justify-between text-xs">
            <span className="text-muted-foreground">Progression</span>
            <span className="font-medium text-foreground">{project.progress}%</span>
          </div>
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
        </div>

        {/* Footer : échéance + membres */}
        <div className="flex items-center justify-between border-t border-border pt-3">
          <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <Calendar className="h-3.5 w-3.5" />
            <span>{formatDate(project.dueDate)}</span>
          </div>

          <div className="flex items-center gap-2">
            <Users className="h-3.5 w-3.5 text-muted-foreground" />
            <div className="flex -space-x-2">
              {project.memberIds.slice(0, 3).map((memberId) => {
                const name =
                  memberId === project.owner?.id
                    ? project.owner.name
                    : `Membre ${memberId.slice(-1)}`;
                return <Avatar key={memberId} name={name} size="sm" className="ring-2 ring-surface" />;
              })}
              {project.memberIds.length > 3 && (
                <div className="flex h-8 w-8 items-center justify-center rounded-full bg-muted text-xs font-medium text-muted-foreground ring-2 ring-surface">
                  +{project.memberIds.length - 3}
                </div>
              )}
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}