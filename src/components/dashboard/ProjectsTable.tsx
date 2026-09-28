import { Avatar } from '../ui/Avatar';
import { Badge } from '../ui/Badge';
import { ProgressBar } from '../ui/ProgressBar';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../ui/Table';
import type { Project, ProjectStatus } from '../../types/models';
import { mockProjects } from '../../mocks/projects';

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

export function ProjectsTable() {
  const projects = mockProjects.slice(0, 5) as Project[];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Projets en cours</CardTitle>
        <CardDescription>Suivi rapide des projets actifs</CardDescription>
      </CardHeader>
      <CardContent className="p-0">
        <div className="overflow-x-auto">
          <Table>
            <TableHead>
              <TableRow>
                <TableHeader>Projet</TableHeader>
                <TableHeader>Responsable</TableHeader>
                <TableHeader>Progression</TableHeader>
                <TableHeader>Statut</TableHeader>
                <TableHeader>Échéance</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {projects.map((project) => (
                <TableRow key={project.id}>
                  <TableCell className="font-medium">{project.name}</TableCell>
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
      </CardContent>
    </Card>
  );
}