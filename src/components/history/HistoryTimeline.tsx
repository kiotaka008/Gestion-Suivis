import { useCallback, useEffect, useState } from 'react';
import { History, RefreshCw } from 'lucide-react';
import { Button } from '../ui/Button';
import { Spinner } from '../ui/Spinner';
import { Alert } from '../ui/Alert';
import { EmptyState } from '../ui/EmptyState';
import { ApiError } from '../../services/ApiError';
import type { HistoryEvent } from '../../types/models';
import type { PaginatedResponse } from '../../types/api';

const PAR_PAGE = 20;

export interface HistoryTimelineProps {
  /** Charge une page de l'historique (1 = la plus récente). */
  load: (page: number, perPage: number) => Promise<PaginatedResponse<HistoryEvent>>;
  /** Texte de l'état vide. */
  emptyText?: string;
}

function formatDateHeure(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/**
 * Historique des actions (journal d'audit du backend), du plus récent au plus ancien.
 * Le backend applique les droits de consultation et l'isolation par organisation.
 */
export function HistoryTimeline({ load, emptyText = 'Aucune action enregistrée pour le moment.' }: HistoryTimelineProps) {
  const [events, setEvents] = useState<HistoryEvent[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const charger = useCallback(
    async (numero: number) => {
      setIsLoading(true);
      setError(null);
      try {
        const reponse = await load(numero, PAR_PAGE);
        setEvents((prev) => (numero === 1 ? reponse.data : [...prev, ...reponse.data]));
        setPage(numero);
        setTotalPages(reponse.meta.totalPages);
      } catch (err) {
        setError(err instanceof ApiError ? err.message : "Chargement de l'historique impossible.");
      } finally {
        setIsLoading(false);
      }
    },
    [load]
  );

  // Première page au montage (et si la source change).
  useEffect(() => {
    let annule = false;
    load(1, PAR_PAGE)
      .then((reponse) => {
        if (annule) return;
        setEvents(reponse.data);
        setPage(1);
        setTotalPages(reponse.meta.totalPages);
      })
      .catch((err) => {
        if (!annule) setError(err instanceof ApiError ? err.message : "Chargement de l'historique impossible.");
      })
      .finally(() => {
        if (!annule) setIsLoading(false);
      });
    return () => {
      annule = true;
    };
  }, [load]);

  if (error && events.length === 0) {
    return (
      <Alert variant="danger">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <span>{error}</span>
          <Button size="sm" variant="outline" leftIcon={<RefreshCw className="h-4 w-4" />} onClick={() => charger(1)}>
            Réessayer
          </Button>
        </div>
      </Alert>
    );
  }

  if (isLoading && events.length === 0) {
    return (
      <div className="flex justify-center py-8" aria-label="Chargement de l'historique">
        <Spinner />
      </div>
    );
  }

  if (events.length === 0) {
    return <EmptyState icon={<History className="h-6 w-6" />} title="Historique vide" description={emptyText} />;
  }

  return (
    <div>
      <ol className="relative space-y-4 border-l border-border pl-6" aria-label="Historique des actions">
        {events.map((event) => (
          <li key={event.id} className="relative">
            <span
              className="absolute -left-[1.85rem] top-1.5 h-3 w-3 rounded-full border-2 border-surface bg-primary"
              aria-hidden="true"
            />
            <p className="text-sm font-medium text-foreground">{event.description}</p>
            <p className="mt-0.5 text-xs text-muted-foreground">
              {event.author ? event.author.name : 'Action automatique'} · {formatDateHeure(event.createdAt)}
            </p>
          </li>
        ))}
      </ol>

      {error && (
        <Alert variant="danger" className="mt-4">
          {error}
        </Alert>
      )}

      {page < totalPages && (
        <div className="mt-4 flex justify-center">
          <Button variant="outline" size="sm" isLoading={isLoading} onClick={() => charger(page + 1)}>
            Afficher plus
          </Button>
        </div>
      )}
    </div>
  );
}
