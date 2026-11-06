import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Button } from './Button';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = { hasError: false };

  public static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  public componentDidCatch(error: Error, info: ErrorInfo) {
    if (import.meta.env.DEV) {
      console.error('ErrorBoundary:', error, info);
    }
  }

  public render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen flex-col items-center justify-center bg-background p-6 text-center">
          <AlertTriangle className="mb-4 h-12 w-12 text-danger" />
          <h1 className="text-xl font-semibold text-foreground">Une erreur est survenue</h1>
          <p className="mt-2 max-w-md text-sm text-muted-foreground">
            L'application a rencontré un problème inattendu.
          </p>
          <Button className="mt-6" onClick={() => window.location.reload()}>
            Recharger la page
          </Button>
        </div>
      );
    }
    return this.props.children;
  }
}