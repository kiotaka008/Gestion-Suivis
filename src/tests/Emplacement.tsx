import { useLocation } from 'react-router-dom';

/** Affiche l'adresse courante (vérifier une navigation dans les tests). */
export function Emplacement() {
  const location = useLocation();
  return <div data-testid="emplacement">{location.pathname + location.search}</div>;
}
