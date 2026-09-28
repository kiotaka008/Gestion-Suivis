import { Calendar, Flag } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { Badge } from '../ui/Badge';
import type { Priority } from '../../types/models';
import { mockProjects } from '../../mocks/projects';

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

function formatDate(date?: string) {
  if (!date) return '—';
  const d = new Date(date);
  return d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' });
}

export function UpcomingDeadlines() {
  const upcoming = [...mockProjects]
    .filter((p) => p.dueDate && p.status !== 'completed' && p.status !== 'archived')
    .sort((a, b) => new Date(a.dueDate!).getTime() - new Date(b.dueDate!).getTime())
    .slice(0, 4);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Échéances à venir</CardTitle>
        <CardDescription>Prochaines dates limites</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {upcoming.map((project) => (
          <div
            key={project.id}
            className="flex items-center justify-between gap-3 rounded-lg border border-border p-3 transition-colors hover:bg-muted/40"
          >
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-foreground">
                {project.name}
              </p>
              <div className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
                <Calendar className="h-3 w-3" />
                <span>{formatDate(project.dueDate)}</span>
              </div>
            </div>
            <Badge variant={getPriorityVariant(project.priority)}>
              <Flag className="mr-1 h-3 w-3" />
              {getPriorityLabel(project.priority)}
            </Badge>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}