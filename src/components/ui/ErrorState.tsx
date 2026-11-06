import { AlertTriangle, RotateCcw } from 'lucide-react';
import { Button } from './Button';

export interface ErrorStateProps {
  title?: string;
  description?: string;
  onRetry?: () => void;
}

export function ErrorState({
  title = 'Une erreur est survenue',
  description = 'Impossible de charger les données. Veuillez réessayer.',
  onRetry,
}: ErrorStateProps) {
  return (
    <div className="flex flex-col items-center justify-center rounded-lg border border-border bg-surface p-12 text-center">
      <div className="mb-4 rounded-full bg-danger/10 p-3 text-danger">
        <AlertTriangle className="h-6 w-6" />
      </div>
      <h3 className="text-base font-semibold text-foreground">{title}</h3>
      <p className="mt-1 max-w-sm text-sm text-muted-foreground">{description}</p>
      {onRetry && (
        <Button
          variant="outline"
          size="sm"
          className="mt-6"
          leftIcon={<RotateCcw className="h-4 w-4" />}
          onClick={onRetry}
        >
          Réessayer
        </Button>
      )}
    </div>
  );
}