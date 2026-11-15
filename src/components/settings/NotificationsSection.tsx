import { Bell, CalendarClock, CheckCircle2, FolderKanban, UserCheck } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { Alert } from '../ui/Alert';
import { Badge } from '../ui/Badge';

/** Notifications réellement créées par le backend (NotificationService, EcheancesService). */
const NOTIFICATIONS_ENVOYEES = [
  {
    id: 'projet',
    icon: FolderKanban,
    label: 'Nouveau projet ou changement de chef de projet',
    description: 'Le chef de projet est prévenu quand un projet lui est confié.',
  },
  {
    id: 'affectation',
    icon: UserCheck,
    label: "Affectation d'une activité",
    description: "Le responsable est prévenu quand une activité lui est affectée.",
  },
  {
    id: 'terminee',
    icon: CheckCircle2,
    label: 'Activité terminée',
    description: "Le chef de projet est prévenu quand une activité de son projet est terminée.",
  },
  {
    id: 'echeance',
    icon: CalendarClock,
    label: "Rappels d'échéance",
    description:
      "Chaque matin, le responsable d'une activité est prévenu si son échéance est dépassée ou arrive dans les 2 jours.",
  },
];

export function NotificationsSection() {
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Notifications</h2>
        <p className="text-sm text-muted-foreground">
          Notifications envoyées par l'application dans votre centre de notifications.
        </p>
      </div>

      <Alert variant="info">
        Le choix des notifications à recevoir n'est pas encore disponible : il nécessite d'enregistrer vos
        préférences sur le serveur. En attendant, toutes les notifications ci-dessous sont actives, et
        aucune notification n'est envoyée par email.
      </Alert>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Bell className="h-5 w-5" />
            Notifications envoyées
          </CardTitle>
          <CardDescription>Elles apparaissent dans la page Notifications et sur la cloche de l'en-tête.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-3">
          {NOTIFICATIONS_ENVOYEES.map((n) => (
            <div key={n.id} className="flex items-start justify-between gap-4 rounded-lg border border-border p-4">
              <div className="flex min-w-0 items-start gap-3">
                <n.icon className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" />
                <div className="min-w-0">
                  <p className="text-sm font-medium text-foreground">{n.label}</p>
                  <p className="mt-0.5 text-xs text-muted-foreground">{n.description}</p>
                </div>
              </div>
              <Badge variant="success">Active</Badge>
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}
