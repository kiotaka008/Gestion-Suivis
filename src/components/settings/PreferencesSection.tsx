import { useState } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { Switch } from '../ui/Switch';
import { Sun, Moon, Monitor, Globe, Clock } from 'lucide-react';
import { useTheme, type Theme } from '../../hooks/useTheme';
import { cn } from '../../utils/cn';
import { enregistrerPreferences, lirePreferences, type UiPreferences } from '../../utils/uiPreferences';

const THEMES: { value: Theme; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
  { value: 'light', label: 'Clair', icon: Sun },
  { value: 'dark', label: 'Sombre', icon: Moon },
  { value: 'system', label: 'Système', icon: Monitor },
];

function fuseauDuNavigateur(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone;
  } catch {
    return 'inconnu';
  }
}

export function PreferencesSection() {
  const { theme, setTheme } = useTheme();
  const [prefs, setPrefs] = useState<UiPreferences>(() => lirePreferences());

  const changer = (modif: Partial<UiPreferences>) => {
    const suivantes = { ...prefs, ...modif };
    setPrefs(suivantes);
    enregistrerPreferences(suivantes);
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Préférences</h2>
        <p className="text-sm text-muted-foreground">
          Personnalisez l'affichage sur ce navigateur.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Apparence</CardTitle>
          <CardDescription>
            Choisissez le thème qui vous convient.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid gap-3 sm:grid-cols-3">
            {THEMES.map((t) => (
              <button
                key={t.value}
                onClick={() => setTheme(t.value)}
                aria-pressed={theme === t.value}
                className={cn(
                  'flex flex-col items-center gap-2 rounded-lg border-2 p-4 transition-colors',
                  theme === t.value
                    ? 'border-primary bg-primary/5'
                    : 'border-border hover:border-primary/40 hover:bg-muted'
                )}
              >
                <t.icon className={cn('h-5 w-5', theme === t.value ? 'text-primary' : 'text-muted-foreground')} />
                <span className={cn('text-sm font-medium', theme === t.value ? 'text-primary' : 'text-foreground')}>
                  {t.label}
                </span>
              </button>
            ))}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Langue et région</CardTitle>
          <CardDescription>
            Réglages appliqués automatiquement par l'application.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-start gap-3">
            <Globe className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" />
            <div>
              <p className="text-sm font-medium text-foreground">Langue : français</p>
              <p className="text-xs text-muted-foreground">L'application n'est disponible qu'en français pour le moment.</p>
            </div>
          </div>
          <div className="flex items-start gap-3">
            <Clock className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" />
            <div>
              <p className="text-sm font-medium text-foreground">Fuseau horaire : {fuseauDuNavigateur()}</p>
              <p className="text-xs text-muted-foreground">
                Les dates et heures sont affichées dans le fuseau horaire de votre navigateur.
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Interface</CardTitle>
          <CardDescription>Enregistré sur ce navigateur uniquement.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-sm font-medium text-foreground">Mode compact</p>
              <p className="text-xs text-muted-foreground">
                Réduire la taille du texte et les espacements pour afficher plus de contenu.
              </p>
            </div>
            <Switch
              aria-label="Mode compact"
              checked={prefs.compact}
              onChange={() => changer({ compact: !prefs.compact })}
            />
          </div>
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-sm font-medium text-foreground">Animations</p>
              <p className="text-xs text-muted-foreground">
                Activer les transitions et effets visuels.
              </p>
            </div>
            <Switch
              aria-label="Animations"
              checked={prefs.animations}
              onChange={() => changer({ animations: !prefs.animations })}
            />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
