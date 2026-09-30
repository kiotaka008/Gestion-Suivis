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
import type { ProjectStatus } from '../../types/models';
import { reportsProjectsSummary } from '../../mocks/reports';

function getStatusVariant(status: string) {
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

function getStatusLabel(status: string) {
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

export function ProjectsSummaryTable() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Synthèse par projet</CardTitle>
        <CardDescription>Progression détaillée de chaque projet</CardDescription>
      </CardHeader>
      <CardContent className="p-0">
        <div className="overflow-x-auto">
          <Table>
            <TableHead>
              <TableRow>
                <TableHeader>Projet</TableHeader>
                <TableHeader>Progression</TableHeader>
                <TableHeader>Statut</TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {reportsProjectsSummary.map((project) => (
                <TableRow key={project.id}>
                  <TableCell className="font-medium">{project.name}</TableCell>
                  <TableCell className="w-64">
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
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </CardContent>
    </Card>
  );
}