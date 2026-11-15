/**
 * Préférences d'affichage propres à ce navigateur (mode compact, animations).
 * Elles sont enregistrées localement : elles ne suivent pas l'utilisateur sur un autre appareil.
 */

export interface UiPreferences {
  compact: boolean;
  animations: boolean;
}

const CLE = 'gs-ui-preferences';

function animationsParDefaut(): boolean {
  // Respecte le réglage « réduire les animations » du système.
  return !(typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);
}

export function lirePreferences(): UiPreferences {
  const parDefaut: UiPreferences = { compact: false, animations: animationsParDefaut() };
  try {
    const brut = localStorage.getItem(CLE);
    if (!brut) return parDefaut;
    const lu = JSON.parse(brut) as Partial<UiPreferences>;
    return {
      compact: typeof lu.compact === 'boolean' ? lu.compact : parDefaut.compact,
      animations: typeof lu.animations === 'boolean' ? lu.animations : parDefaut.animations,
    };
  } catch {
    return parDefaut;
  }
}

/** Applique les préférences à la page (classes sur <html>, voir index.css). */
export function appliquerPreferences(prefs: UiPreferences = lirePreferences()): void {
  const racine = document.documentElement;
  racine.classList.toggle('compact', prefs.compact);
  racine.classList.toggle('reduce-motion', !prefs.animations);
}

export function enregistrerPreferences(prefs: UiPreferences): void {
  try {
    localStorage.setItem(CLE, JSON.stringify(prefs));
  } catch {
    /* Stockage indisponible (navigation privée) : la préférence reste valable pour cette page. */
  }
  appliquerPreferences(prefs);
}
