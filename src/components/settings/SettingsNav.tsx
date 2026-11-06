import { User, Bell, Shield, Sliders, Plug } from 'lucide-react';
import { cn } from '../../utils/cn';

export type SettingsSection = 'profile' | 'notifications' | 'security' | 'preferences' | 'integrations';

const SECTIONS: { id: SettingsSection; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
  { id: 'profile', label: 'Profil', icon: User },
  { id: 'notifications', label: 'Notifications', icon: Bell },
  { id: 'security', label: 'Sécurité', icon: Shield },
  { id: 'preferences', label: 'Préférences', icon: Sliders },
  { id: 'integrations', label: 'Intégrations', icon: Plug },
];

export interface SettingsNavProps {
  active: SettingsSection;
  onChange: (section: SettingsSection) => void;
}

export function SettingsNav({ active, onChange }: SettingsNavProps) {
  return (
    <nav
      className="flex gap-1 overflow-x-auto lg:flex-col lg:overflow-visible"
      aria-label="Sections des paramètres"
    >
      {SECTIONS.map((section) => (
        <button
          key={section.id}
          onClick={() => onChange(section.id)}
          className={cn(
            'flex shrink-0 items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
            'focus:outline-none focus-visible:ring-2 focus-visible:ring-primary',
            active === section.id
              ? 'bg-primary/10 text-primary'
              : 'text-muted-foreground hover:bg-muted hover:text-foreground'
          )}
        >
          <section.icon className="h-4 w-4" />
          <span>{section.label}</span>
        </button>
      ))}
    </nav>
  );
}