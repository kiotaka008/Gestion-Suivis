import { CheckCircle2, Clock, AlertCircle, Circle } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { Badge } from '../ui/Badge';
import type { Activity, ActivityStatus } from '../../types/models';
import { mockActivities } from '../../mocks/activities';
import { mockProjects } from '../../mocks/projects';

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

export function RecentActivities() {
  const activities = mockActivities.slice(0, 5);

  const findProjectName = (projectId: string) =>
    mockProjects.find((p) => p.id === projectId)?.name ?? 'Projet inconnu';

  return (
    <Card>
      <CardHeader>
        <CardTitle>Activités récentes</CardTitle>
        <CardDescription>Dernières mises à jour</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {activities.map((activity: Activity) => (
          <div
            key={activity.id}
            className="flex items-start gap-3 rounded-lg border border-border p-3 transition-colors hover:bg-muted/40"
          >
            <div className="mt-0.5 shrink-0">{getStatusIcon(activity.status)}</div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-foreground">
                {activity.title}
              </p>
              <p className="truncate text-xs text-muted-foreground">
                {findProjectName(activity.projectId)}
              </p>
            </div>
            <Badge variant={getStatusVariant(activity.status)}>
              {getStatusLabel(activity.status)}
            </Badge>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}