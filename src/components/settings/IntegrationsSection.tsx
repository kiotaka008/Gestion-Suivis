import {
  Calendar,
  MessageSquare,
  Code2,
  Hash,
  LayoutGrid,
  FileText,
} from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { Alert } from '../ui/Alert';

interface Integration {
  id: string;
  name: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
}

/** Intégrations envisagées : aucune n'est développée (ni côté frontend, ni côté backend). */
const INTEGRATIONS: Integration[] = [
  {
    id: 'google-calendar',
    name: 'Google Calendar',
    description: 'Synchroniser les échéances avec un agenda.',
    icon: Calendar,
    color: 'text-blue-500',
  },
  {
    id: 'slack',
    name: 'Slack',
    description: 'Recevoir les notifications dans Slack.',
    icon: Hash,
    color: 'text-purple-500',
  },
  {
    id: 'github',
    name: 'GitHub',
    description: 'Lier des commits et pull requests aux projets.',
    icon: Code2,
    color: 'text-foreground',
  },
  {
    id: 'trello',
    name: 'Trello',
    description: 'Importer des tableaux Trello.',
    icon: LayoutGrid,
    color: 'text-blue-400',
  },
  {
    id: 'notion',
    name: 'Notion',
    description: 'Synchroniser la documentation.',
    icon: FileText,
    color: 'text-foreground',
  },
  {
    id: 'discord',
    name: 'Discord',
    description: 'Recevoir les notifications sur Discord.',
    icon: MessageSquare,
    color: 'text-indigo-500',
  },
];

export function IntegrationsSection() {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Intégrations</h2>
        <p className="text-sm text-muted-foreground">
          Connexion de l'application à des outils externes.
        </p>
      </div>

      <Alert variant="info">
        Aucune intégration externe n'est disponible pour le moment. Les services ci-dessous sont envisagés
        pour une version future.
      </Alert>

      <Card>
        <CardHeader>
          <CardTitle>Intégrations envisagées</CardTitle>
          <CardDescription>Elles ne peuvent pas encore être activées.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {INTEGRATIONS.map((integration) => (
            <div
              key={integration.id}
              className="flex items-center justify-between gap-4 rounded-lg border border-border p-4"
            >
              <div className="flex min-w-0 items-center gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-muted">
                  <integration.icon className={`h-5 w-5 ${integration.color}`} />
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-medium text-foreground">{integration.name}</p>
                  <p className="truncate text-xs text-muted-foreground">
                    {integration.description}
                  </p>
                </div>
              </div>

              <Badge variant="muted">Non disponible</Badge>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
