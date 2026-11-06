import { type ReactNode } from 'react';
import { cn } from '../../utils/cn';

export interface ReportsKPICardProps {
  label: string;
  value: string | number;
  icon: ReactNode;
  variant?: 'default' | 'success' | 'warning' | 'danger';
  trend?: {
    value: number;
    isPositive: boolean;
  };
}

export function ReportsKPICard({
  label,
  value,
  icon,
  variant = 'default',
  trend,
}: ReportsKPICardProps) {
  const variants = {
    default: 'bg-primary/10 text-primary',
    success: 'bg-success/10 text-success',
    warning: 'bg-warning/10 text-warning',
    danger: 'bg-danger/10 text-danger',
  };

  return (
    <div className="rounded-xl border border-border bg-surface p-6 shadow-sm">
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <p className="text-sm font-medium text-muted-foreground">{label}</p>
          <p className="mt-2 text-3xl font-bold text-foreground">{value}</p>
          {trend && (
            <p
              className={cn(
                'mt-2 inline-flex items-center gap-1 text-xs font-medium',
                trend.isPositive ? 'text-success' : 'text-danger'
              )}
            >
              <span>{trend.isPositive ? '+' : '-'}</span>
              <span>{Math.abs(trend.value)}%</span>
              <span className="text-muted-foreground">vs période précédente</span>
            </p>
          )}
        </div>
        <div
          className={cn(
            'flex h-12 w-12 shrink-0 items-center justify-center rounded-lg',
            variants[variant]
          )}
        >
          {icon}
        </div>
      </div>
    </div>
  );
}