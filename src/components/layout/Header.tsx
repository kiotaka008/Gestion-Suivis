import { Menu, Search, Bell, LogOut, User as UserIcon, Settings as SettingsIcon } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ThemeToggle } from '../ui/ThemeToggle';
import { Avatar } from '../ui/Avatar';
import { Dropdown } from '../ui/Dropdown';
import { ROUTES } from '../../constants/routes';
import { useAuth } from '../../hooks/useAuth';
import { notificationService } from '../../services/notificationService';

interface HeaderProps {
  onMenuClick: () => void;
}

export function Header({ onMenuClick }: HeaderProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout } = useAuth();

  // Nombre de notifications non lues, rafraîchi à chaque changement de page.
  const [unreadCount, setUnreadCount] = useState(0);
  useEffect(() => {
    let annule = false;
    notificationService
      .unreadCount()
      .then((n) => {
        if (!annule) setUnreadCount(n);
      })
      .catch(() => {
        if (!annule) setUnreadCount(0);
      });
    return () => {
      annule = true;
    };
  }, [location.pathname]);

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-4 border-b border-border bg-surface/95 px-4 backdrop-blur lg:px-6">
      <button
        onClick={onMenuClick}
        className="rounded-md p-2 text-muted-foreground hover:bg-muted lg:hidden"
        aria-label="Ouvrir le menu"
      >
        <Menu className="h-5 w-5" />
      </button>

      <div className="relative hidden max-w-md flex-1 md:block">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <input
          type="search"
          placeholder="Rechercher..."
          className="h-9 w-full rounded-md border border-border bg-background pl-10 pr-4 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary"
          aria-label="Rechercher"
        />
      </div>

      <div className="ml-auto flex items-center gap-2">
        <ThemeToggle />

        <button
          onClick={() => navigate(ROUTES.NOTIFICATIONS)}
          className="relative rounded-md p-2 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          aria-label={`Notifications${unreadCount > 0 ? ` (${unreadCount} non lues)` : ''}`}
        >
          <Bell className="h-5 w-5" />
          {unreadCount > 0 && (
            <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-semibold text-white">
              {unreadCount > 9 ? '9+' : unreadCount}
            </span>
          )}
        </button>

        <Dropdown
          trigger={
            <button className="flex items-center gap-3 rounded-md p-1 hover:bg-muted">
              <Avatar name={user?.name ?? 'Utilisateur'} size="sm" />
            </button>
          }
          items={[
            {
              label: 'Mon profil',
              icon: <UserIcon className="h-4 w-4" />,
              onClick: () => navigate(ROUTES.SETTINGS),
            },
            {
              label: 'Paramètres',
              icon: <SettingsIcon className="h-4 w-4" />,
              onClick: () => navigate(ROUTES.SETTINGS),
            },
            {
              label: 'Se déconnecter',
              icon: <LogOut className="h-4 w-4" />,
              variant: 'danger',
              onClick: () => {
                // Efface le cookie de session côté backend, puis retour à la connexion.
                void logout().finally(() => navigate(ROUTES.LOGIN, { replace: true }));
              },
            },
          ]}
        />
      </div>
    </header>
  );
}