import { Search, SlidersHorizontal, LayoutGrid, List } from 'lucide-react';
import { Select } from '../ui/Select';
import { cn } from '../../utils/cn';

export type ViewMode = 'table' | 'cards';

export interface ProjectsFiltersProps {
  search: string;
  onSearchChange: (value: string) => void;
  status: string;
  onStatusChange: (value: string) => void;
  priority: string;
  onPriorityChange: (value: string) => void;
  sort: string;
  onSortChange: (value: string) => void;
  view: ViewMode;
  onViewChange: (view: ViewMode) => void;
}

export function ProjectsFilters({
  search,
  onSearchChange,
  status,
  onStatusChange,
  priority,
  onPriorityChange,
  sort,
  onSortChange,
  view,
  onViewChange,
}: ProjectsFiltersProps) {
  return (
    <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
      {/* Recherche */}
      <div className="relative w-full lg:w-72">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        <input
          type="search"
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder="Rechercher un projet..."
          className="h-10 w-full rounded-md border border-border bg-surface pl-10 pr-3 text-sm text-foreground placeholder:text-muted-foreground focus:border-transparent focus:outline-none focus:ring-2 focus:ring-primary"
        />
      </div>

      {/* Filtres + tri alignés à droite */}
      <div className="flex flex-wrap items-center gap-2 lg:ml-auto">
        <div className="hidden items-center gap-1.5 text-xs text-muted-foreground md:flex">
          <SlidersHorizontal className="h-4 w-4" />
          <span>Filtres</span>
        </div>

        <Select
          value={status}
          onChange={(e) => onStatusChange(e.target.value)}
          options={[
            { value: 'all', label: 'Tous les statuts' },
            { value: 'active', label: 'En cours' },
            { value: 'completed', label: 'Terminés' },
            { value: 'on_hold', label: 'En attente' },
            { value: 'archived', label: 'Archivés' },
          ]}
          wrapperClassName="w-auto"
          className="w-auto min-w-40"
        />

        <Select
          value={priority}
          onChange={(e) => onPriorityChange(e.target.value)}
          options={[
            { value: 'all', label: 'Toutes priorités' },
            { value: 'urgent', label: 'Urgent' },
            { value: 'high', label: 'Haute' },
            { value: 'medium', label: 'Moyenne' },
            { value: 'low', label: 'Basse' },
          ]}
          wrapperClassName="w-auto"
          className="w-auto min-w-40"
        />

        <Select
          value={sort}
          onChange={(e) => onSortChange(e.target.value)}
          options={[
            { value: 'recent', label: 'Plus récents' },
            { value: 'oldest', label: 'Plus anciens' },
            { value: 'name', label: 'Nom (A-Z)' },
            { value: 'progress', label: 'Progression' },
            { value: 'dueDate', label: 'Échéance' },
          ]}
          wrapperClassName="w-auto"
          className="w-auto min-w-40"
        />

        {/* Bascule Table / Cards */}
        <div className="flex h-10 items-center rounded-md border border-border bg-surface p-0.5">
          <button
            onClick={() => onViewChange('table')}
            aria-label="Vue tableau"
            className={cn(
              'flex h-9 w-9 items-center justify-center rounded transition-colors',
              view === 'table'
                ? 'bg-primary text-white'
                : 'text-muted-foreground hover:bg-muted'
            )}
          >
            <List className="h-4 w-4" />
          </button>
          <button
            onClick={() => onViewChange('cards')}
            aria-label="Vue cartes"
            className={cn(
              'flex h-9 w-9 items-center justify-center rounded transition-colors',
              view === 'cards'
                ? 'bg-primary text-white'
                : 'text-muted-foreground hover:bg-muted'
            )}
          >
            <LayoutGrid className="h-4 w-4" />
          </button>
        </div>
      </div>
    </div>
  );
}