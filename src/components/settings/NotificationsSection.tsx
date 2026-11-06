import { useState } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { Switch } from '../ui/Switch';

interface Pref {
  id: string;
  label: string;
  description: string;
  value: boolean;
}

const INITIAL: Pref[] = [
  {
    id: 'email',
    label: 'Notifications par email',
    description: 'Recevoir un résumé quotidien des activités.',
    value: true,
  },
  {
    id: 'push',
    label: 'Notifications push',
    description: 'Notifications en temps réel dans le navigateur.',
    value: true,
  },
  {
    id: 'project',
    label: 'Alertes de projet',
    description: 'Être notifié lors de changements importants.',
    value: true,
  },
  {
    id: 'deadline',
    label: 'Rappels d\'échéance',
    description: 'Rappels 3 jours avant une échéance.',
    value: true,
  },
  {
    id: 'comment',
    label: 'Commentaires',
    description: 'Notification lorsqu\'un membre commente.',
    value: false,
  },
  {
    id: 'ai',
    label: 'Rapports IA',
    description: 'Résumé hebdomadaire généré par l\'IA.',
    value: false,
  },
];

export function NotificationsSection() {
  const [prefs, setPrefs] = useState<Pref[]>(INITIAL);

  const toggle = (id: string) => {
    setPrefs((prev) =>
      prev.map((p) => (p.id === id ? { ...p, value: !p.value } : p))
    );
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Notifications</h2>
        <p className="text-sm text-muted-foreground">
          Choisissez comment et quand vous souhaitez être notifié.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Préférences de notification</CardTitle>
          <CardDescription>
            Activez ou désactivez les notifications selon vos préférences.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {prefs.map((pref) => (
            <div
              key={pref.id}
              className="flex items-start justify-between gap-4 rounded-lg border border-border p-4"
            >
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-foreground">{pref.label}</p>
                <p className="mt-0.5 text-xs text-muted-foreground">{pref.description}</p>
              </div>
              <Switch
                checked={pref.value}
                onChange={() => toggle(pref.id)}
              />
            </div>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}