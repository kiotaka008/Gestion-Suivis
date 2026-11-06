import { useState } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { Select } from '../ui/Select';
import { Switch } from '../ui/Switch';
import { Sun, Moon, Monitor } from 'lucide-react';
import { useTheme, type Theme } from '../../hooks/useTheme';
import { cn } from '../../utils/cn';

const THEMES: { value: Theme; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
  { value: 'light', label: 'Clair', icon: Sun },
  { value: 'dark', label: 'Sombre', icon: Moon },
  { value: 'system', label: 'Système', icon: Monitor },
];

export function PreferencesSection() {
  const { theme, setTheme } = useTheme();
  const [language, setLanguage] = useState('fr');
  const [timezone, setTimezone] = useState('indian/antananarivo');
  const [compactMode, setCompactMode] = useState(false);
  const [animations, setAnimations] = useState(true);

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-lg font-semibold text-foreground">Préférences</h2>
        <p className="text-sm text-muted-foreground">
          Personnalisez votre expérience.
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
            Définissez votre langue et votre fuseau horaire.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <Select
            label="Langue"
            value={language}
            onChange={(e) => setLanguage(e.target.value)}
            options={[
              { value: 'fr', label: 'Français' },
              { value: 'en', label: 'English' },
              { value: 'mg', label: 'Malagasy' },
            ]}
          />
          <Select
            label="Fuseau horaire"
            value={timezone}
            onChange={(e) => setTimezone(e.target.value)}
            options={[
              { value: 'indian/antananarivo', label: '(GMT+3) Antananarivo' },
              { value: 'europe/paris', label: '(GMT+1) Paris' },
              { value: 'utc', label: '(GMT+0) UTC' },
            ]}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Interface</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-sm font-medium text-foreground">Mode compact</p>
              <p className="text-xs text-muted-foreground">
                Réduire les espacements pour afficher plus de contenu.
              </p>
            </div>
            <Switch checked={compactMode} onChange={() => setCompactMode(!compactMode)} />
          </div>
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="text-sm font-medium text-foreground">Animations</p>
              <p className="text-xs text-muted-foreground">
                Activer les transitions et effets visuels.
              </p>
            </div>
            <Switch checked={animations} onChange={() => setAnimations(!animations)} />
          </div>
        </CardContent>
      </Card>
    </div>
  );
}