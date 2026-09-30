import { useState } from 'react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { SettingsNav, type SettingsSection } from '../components/settings/SettingsNav';
import { ProfileSection } from '../components/settings/ProfileSection';
import { NotificationsSection } from '../components/settings/NotificationsSection';
import { SecuritySection } from '../components/settings/SecuritySection';
import { PreferencesSection } from '../components/settings/PreferencesSection';
import { IntegrationsSection } from '../components/settings/IntegrationsSection';

export function SettingsPage() {
  const [active, setActive] = useState<SettingsSection>('profile');

  return (
    <Container className="py-6">
      <PageHeader
        title="Paramètres"
        description="Gérez votre compte et vos préférences."
      />

      <div className="mt-6 grid gap-6 lg:grid-cols-[220px_1fr]">
        <aside>
          <SettingsNav active={active} onChange={setActive} />
        </aside>

        <div className="min-w-0">
          {active === 'profile' && <ProfileSection />}
          {active === 'notifications' && <NotificationsSection />}
          {active === 'security' && <SecuritySection />}
          {active === 'preferences' && <PreferencesSection />}
          {active === 'integrations' && <IntegrationsSection />}
        </div>
      </div>
    </Container>
  );
}