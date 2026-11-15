import { describe, it, expect, beforeEach } from 'vitest';
import { appliquerPreferences, enregistrerPreferences, lirePreferences } from '../utils/uiPreferences';

describe('Préférences d’interface', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.className = '';
  });

  it('applique le mode compact et la désactivation des animations sur <html>', () => {
    appliquerPreferences({ compact: true, animations: false });
    expect(document.documentElement.classList.contains('compact')).toBe(true);
    expect(document.documentElement.classList.contains('reduce-motion')).toBe(true);

    appliquerPreferences({ compact: false, animations: true });
    expect(document.documentElement.classList.contains('compact')).toBe(false);
    expect(document.documentElement.classList.contains('reduce-motion')).toBe(false);
  });

  it('enregistre et relit les préférences', () => {
    enregistrerPreferences({ compact: true, animations: false });
    expect(lirePreferences()).toEqual({ compact: true, animations: false });
  });

  it('ignore une valeur enregistrée illisible', () => {
    localStorage.setItem('gs-ui-preferences', '{pas du json');
    expect(lirePreferences().compact).toBe(false);
  });
});
