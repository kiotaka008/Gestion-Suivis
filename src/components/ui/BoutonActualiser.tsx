import { useState } from 'react';
import { RefreshCw } from 'lucide-react';
import { Button } from './Button';

export interface BoutonActualiserProps {
  /**
   * Relit les données de la page. La page affiche elle-même les erreurs ; elle ne modifie
   * jamais une saisie en cours ni l'élément en cours de modification.
   */
  onActualiser: () => Promise<void>;
}

/**
 * AUD-002, F9 : actualisation manuelle des données d'une page (aucune relecture automatique).
 * Pendant la relecture, le bouton est désactivé : une seule relecture à la fois.
 */
export function BoutonActualiser({ onActualiser }: BoutonActualiserProps) {
  const [enCours, setEnCours] = useState(false);

  const actualiser = async () => {
    if (enCours) return;
    setEnCours(true);
    try {
      await onActualiser();
    } catch {
      /* Les pages affichent leurs propres messages d'erreur. */
    } finally {
      setEnCours(false);
    }
  };

  return (
    <Button variant="outline" leftIcon={<RefreshCw className="h-4 w-4" />} isLoading={enCours} onClick={actualiser}>
      Actualiser
    </Button>
  );
}
