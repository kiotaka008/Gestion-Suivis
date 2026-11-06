import { useState, useEffect } from 'react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { SettingsNav, type SettingsSection } from '../components/settings/SettingsNav';
import { ProfileSection } from '../components/settings/ProfileSection';
import { NotificationsSection } from '../components/settings/NotificationsSection';
import { SecuritySection } from '../components/settings/SecuritySection';
import { PreferencesSection } from '../components/settings/PreferencesSection';
import { IntegrationsSection } from '../components/settings/IntegrationsSection';
import { Skeleton } from '../components/ui/Skeleton';

export function SettingsPage() {
  const [active, setActive] = useState<SettingsSection>('profile');
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => setIsLoading(false), 800);
    return () => clearTimeout(timer);
  }, []);

  return (
    <Container className="py-6">
      <PageHeader title="Paramètres" description="Gérez votre compte et vos préférences." />

      <div className="mt-6 grid gap-6 lg:grid-cols-[220px_1fr]">
        <aside>
          <SettingsNav active={active} onChange={setActive} />
        </aside>

        <div className="min-w-0">
          {isLoading ? (
            <div className="space-y-6">
              <div className="space-y-2">
                <Skeleton className="h-6 w-32" />
                <Skeleton className="h-4 w-64" />
              </div>
              <div className="rounded-xl border border-border bg-surface p-6 space-y-4">
                <Skeleton className="h-5 w-40" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-24 w-full" />
                <div className="flex justify-end">
                  <Skeleton className="h-10 w-32" />
                </div>
              </div>
            </div>
          ) : (
            <>
              {active === 'profile' && <ProfileSection />}
              {active === 'notifications' && <NotificationsSection />}
              {active === 'security' && <SecuritySection />}
              {active === 'preferences' && <PreferencesSection />}
              {active === 'integrations' && <IntegrationsSection />}
            </>
          )}
        </div>
      </div>
    </Container>
  );
}