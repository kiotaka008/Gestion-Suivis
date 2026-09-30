import { MoreVertical, Calendar, Edit, Trash2, Eye, Clock, CheckCircle2, AlertCircle, Circle } from 'lucide-react';
import type { Activity, ActivityStatus, Priority } from '../../types/models';
import { Card, CardContent, CardHeader } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { ProgressBar } from '../ui/ProgressBar';
import { Dropdown } from '../ui/Dropdown';

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

function getStatusIcon(status: ActivityStatus) {
  switch (status) {
    case 'done':
      return <CheckCircle2 className="h-4 w-4 text-success" />;
    case 'in_progress':
      return <Clock className="h-4 w-4 text-info" />;
    case 'blocked':
      return <AlertCircle className="h-4 w-4 text-danger" />;
    default:
      return <Circle className="h-4 w-4 text-muted-foreground" />;
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

export interface ActivityCardProps {
  activity: Activity;
  projectName: string;
  onEdit?: (activity: Activity) => void;
  onDelete?: (activity: Activity) => void;
  onView?: (activity: Activity) => void;
}

export function ActivityCard({ activity, projectName, onEdit, onDelete, onView }: ActivityCardProps) {
  return (
    <Card className="group transition-shadow hover:shadow-md">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-3">
          <div className="flex min-w-0 flex-1 items-start gap-3">
            <div className="mt-0.5 shrink-0">{getStatusIcon(activity.status)}</div>
            <div className="min-w-0 flex-1">
              <h3 className="truncate text-base font-semibold text-foreground">
                {activity.title}
              </h3>
              <p className="mt-0.5 truncate text-xs text-muted-foreground">{projectName}</p>
            </div>
          </div>

          <Dropdown
            trigger={
              <button
                className="rounded-md p-1 text-muted-foreground hover:bg-muted"
                aria-label="Actions de l'activité"
              >
                <MoreVertical className="h-4 w-4" />
              </button>
            }
            items={[
              {
                label: 'Voir',
                icon: <Eye className="h-4 w-4" />,
                onClick: () => onView?.(activity),
              },
              {
                label: 'Modifier',
                icon: <Edit className="h-4 w-4" />,
                onClick: () => onEdit?.(activity),
              },
              {
                label: 'Supprimer',
                icon: <Trash2 className="h-4 w-4" />,
                variant: 'danger',
                onClick: () => onDelete?.(activity),
              },
            ]}
          />
        </div>
      </CardHeader>

      <CardContent className="space-y-4 pt-0">
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant={getStatusVariant(activity.status)}>
            {getStatusLabel(activity.status)}
          </Badge>
          <Badge variant={getPriorityVariant(activity.priority)}>
            {getPriorityLabel(activity.priority)}
          </Badge>
        </div>

        <div>
          <div className="mb-2 flex items-center justify-between text-xs">
            <span className="text-muted-foreground">Progression</span>
            <span className="font-medium text-foreground">{activity.progress}%</span>
          </div>
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
        </div>

        <div className="flex items-center gap-1.5 border-t border-border pt-3 text-xs text-muted-foreground">
          <Calendar className="h-3.5 w-3.5" />
          <span>Échéance : {formatDate(activity.dueDate)}</span>
        </div>
      </CardContent>
    </Card>
  );
}