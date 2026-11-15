import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ClipboardList, FolderKanban, Search } from 'lucide-react';
import { projectService } from '../../services/projectService';
import { activityService } from '../../services/activityService';
import { ROUTES } from '../../constants/routes';
import { Spinner } from '../ui/Spinner';
import type { Activity, Project } from '../../types/models';

const LONGUEUR_MIN = 2;
const NB_RESULTATS = 5;
const DELAI_MS = 300;

interface Resultats {
  projects: Project[];
  activities: Activity[];
  erreur?: boolean;
}

interface Lien {
  id: string;
  label: string;
  detail?: string;
  to: string;
  type: 'projet' | 'activite';
}

/**
 * Recherche globale de l'en-tête : projets et activités visibles par l'utilisateur,
 * via le paramètre `search` des listes du backend (mêmes droits que les pages).
 */
export function GlobalSearch() {
  const navigate = useNavigate();
  const conteneur = useRef<HTMLDivElement>(null);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Resultats | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isOpen, setIsOpen] = useState(false);

  // Recherche différée pendant la saisie.
  useEffect(() => {
    const q = query.trim();
    if (q.length < LONGUEUR_MIN) return;
    let annule = false;
    const minuterie = setTimeout(() => {
      setIsLoading(true);
      const params = { search: q, perPage: NB_RESULTATS, page: 1 };
      Promise.all([projectService.list(params), activityService.list(params)])
        .then(([projets, activites]) => {
          if (!annule) setResults({ projects: projets.data, activities: activites.data });
        })
        .catch(() => {
          if (!annule) setResults({ projects: [], activities: [], erreur: true });
        })
        .finally(() => {
          if (!annule) setIsLoading(false);
        });
    }, DELAI_MS);
    return () => {
      annule = true;
      clearTimeout(minuterie);
    };
  }, [query]);

  // Fermeture au clic en dehors.
  useEffect(() => {
    if (!isOpen) return;
    const auClic = (e: MouseEvent) => {
      if (conteneur.current && !conteneur.current.contains(e.target as Node)) setIsOpen(false);
    };
    document.addEventListener('mousedown', auClic);
    return () => document.removeEventListener('mousedown', auClic);
  }, [isOpen]);

  const liens: Lien[] = results
    ? [
        ...results.projects.map((p) => ({
          id: p.id,
          label: p.name,
          detail: p.description,
          to: `${ROUTES.PROJECTS}/${p.id}`,
          type: 'projet' as const,
        })),
        ...results.activities.map((a) => ({
          id: a.id,
          label: a.title,
          detail: a.projectName,
          to: `${ROUTES.ACTIVITIES}/${a.id}`,
          type: 'activite' as const,
        })),
      ]
    : [];

  const ouvrir = (to: string) => {
    setIsOpen(false);
    setQuery('');
    setResults(null);
    navigate(to);
  };

  const auClavier = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      setIsOpen(false);
    } else if (e.key === 'Enter' && liens.length > 0) {
      e.preventDefault();
      ouvrir(liens[0].to);
    }
  };

  const q = query.trim();
  const afficher = isOpen && q.length >= LONGUEUR_MIN;

  return (
    <div ref={conteneur} className="relative hidden max-w-md flex-1 md:block">
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
      <input
        type="search"
        placeholder="Rechercher un projet ou une activité..."
        className="h-9 w-full rounded-md border border-border bg-background pl-10 pr-4 text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary"
        aria-label="Rechercher un projet ou une activité"
        aria-expanded={afficher}
        aria-controls="resultats-recherche"
        value={query}
        onChange={(e) => {
          setQuery(e.target.value);
          setIsOpen(true);
          if (e.target.value.trim().length < LONGUEUR_MIN) setResults(null);
        }}
        onFocus={() => setIsOpen(true)}
        onKeyDown={auClavier}
      />

      {afficher && (
        <div
          id="resultats-recherche"
          role="listbox"
          className="absolute left-0 right-0 z-40 mt-1 max-h-96 overflow-y-auto rounded-md border border-border bg-surface p-1 shadow-lg"
        >
          {isLoading && !results ? (
            <div className="flex items-center gap-2 px-3 py-2 text-sm text-muted-foreground">
              <Spinner size="sm" /> Recherche…
            </div>
          ) : results?.erreur ? (
            <p className="px-3 py-2 text-sm text-danger">Recherche impossible pour le moment.</p>
          ) : liens.length === 0 ? (
            <p className="px-3 py-2 text-sm text-muted-foreground">Aucun résultat pour « {q} ».</p>
          ) : (
            <>
              {liens.map((lien) => (
                <button
                  key={`${lien.type}-${lien.id}`}
                  type="button"
                  role="option"
                  aria-selected={false}
                  onClick={() => ouvrir(lien.to)}
                  className="flex w-full items-start gap-3 rounded-sm px-3 py-2 text-left text-sm hover:bg-muted focus:bg-muted focus:outline-none"
                >
                  {lien.type === 'projet' ? (
                    <FolderKanban className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                  ) : (
                    <ClipboardList className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
                  )}
                  <span className="min-w-0">
                    <span className="block truncate font-medium text-foreground">{lien.label}</span>
                    <span className="block truncate text-xs text-muted-foreground">
                      {lien.type === 'projet' ? 'Projet' : 'Activité'}
                      {lien.detail ? ` · ${lien.detail}` : ''}
                    </span>
                  </span>
                </button>
              ))}
              <div className="mt-1 flex gap-1 border-t border-border pt-1">
                <button
                  type="button"
                  onClick={() => ouvrir(`${ROUTES.PROJECTS}?search=${encodeURIComponent(q)}`)}
                  className="flex-1 rounded-sm px-3 py-2 text-left text-xs text-primary hover:bg-muted"
                >
                  Tous les projets
                </button>
                <button
                  type="button"
                  onClick={() => ouvrir(`${ROUTES.ACTIVITIES}?search=${encodeURIComponent(q)}`)}
                  className="flex-1 rounded-sm px-3 py-2 text-left text-xs text-primary hover:bg-muted"
                >
                  Toutes les activités
                </button>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  );
}
