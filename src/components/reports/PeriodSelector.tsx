import { cn } from '../../utils/cn';

export type ReportPeriod = '7d' | '30d' | '3m' | '6m' | '12m';

const PERIODS: { value: ReportPeriod; label: string }[] = [
  { value: '7d', label: '7 jours' },
  { value: '30d', label: '30 jours' },
  { value: '3m', label: '3 mois' },
  { value: '6m', label: '6 mois' },
  { value: '12m', label: '12 mois' },
];

export interface PeriodSelectorProps {
  value: ReportPeriod;
  onChange: (value: ReportPeriod) => void;
}

export function PeriodSelector({ value, onChange }: PeriodSelectorProps) {
  return (
    <div className="inline-flex items-center rounded-md border border-border bg-surface p-0.5">
      {PERIODS.map((p) => (
        <button
          key={p.value}
          onClick={() => onChange(p.value)}
          className={cn(
            'rounded px-3 py-1.5 text-xs font-medium transition-colors',
            value === p.value
              ? 'bg-primary text-white'
              : 'text-muted-foreground hover:bg-muted hover:text-foreground'
          )}
        >
          {p.label}
        </button>
      ))}
    </div>
  );
}