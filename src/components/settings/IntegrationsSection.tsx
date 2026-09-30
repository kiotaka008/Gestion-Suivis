import {
  Calendar,
  MessageSquare,
  Code2,
  Hash,
  LayoutGrid,
  FileText,
} from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';

interface Integration {
  id: string;
  name: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  color: string;
  connected: boolean;
}

const INTEGRATIONS: Integration[] = [
  {
    id: 'google-calendar',
    name: 'Google Calendar',
    description: 'Synchronisez vos échéances avec votre agenda.',
    icon: Calendar,
    color: 'text-blue-500',
    connected: false,
  },
  {
    id: 'slack',
    name: 'Slack',
    description: 'Recevez vos notifications dans Slack.',
    icon: Hash,
    color: 'text-purple-500',
    connected: true,
  },
  {
    id: 'github',
    name: 'GitHub',
    description: 'Liez vos commits et pull requests aux projets.',
    icon: Code2,
    color: 'text-foreground',
    connected: false,
  },
  {
    id: 'trello',
    name: 'Trello',
    description: 'Importez vos tableaux Trello.',
    icon: LayoutGrid,
    color: 'text-blue-400',
    connected: false,
  },
  {
    id: 'notion',
    name: 'Notion',
    description: 'Synchronisez votre documentation.',
    icon: FileText,
    color: 'text-foreground',
    connected: false,
  },
  {
    id: 'discord',
    name: 'Discord',
    description: 'Recevez vos notifications sur Discord.',
    icon: MessageSquare,
    color: 'text-indigo-500',
    connected: false,
  },
];

export function IntegrationsSection() {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Intégrations</h2>
        <p className="text-sm text-muted-foreground">
          Connectez vos outils préférés à ProjectManager.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Services disponibles</CardTitle>
          <CardDescription>
            Activez les intégrations que vous souhaitez utiliser.
          </CardDescription>
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

              <div className="flex shrink-0 items-center gap-2">
                {integration.connected && <Badge variant="success">Connecté</Badge>}
                <Button variant={integration.connected ? 'outline' : 'primary'} size="sm">
                  {integration.connected ? 'Déconnecter' : 'Connecter'}
                </Button>
              </div>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}