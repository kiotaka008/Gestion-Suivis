import { type ReactNode } from 'react';
import { AlertTriangle, Check, Info, XCircle } from 'lucide-react';
import { cn } from '../../utils/cn';

export interface AlertProps {
  variant?: 'info' | 'success' | 'warning' | 'danger';
  title?: string;
  children: ReactNode;
  className?: string;
}

export function Alert({ variant = 'info', title, children, className }: AlertProps) {
  const variants = {
    info: { container: 'border-info/30 bg-info/10 text-info', icon: <Info className="h-5 w-5" /> },
    success: { container: 'border-success/30 bg-success/10 text-success', icon: <Check className="h-5 w-5" /> },
    warning: { container: 'border-warning/30 bg-warning/10 text-warning', icon: <AlertTriangle className="h-5 w-5" /> },
    danger: { container: 'border-danger/30 bg-danger/10 text-danger', icon: <XCircle className="h-5 w-5" /> },
  };

  const v = variants[variant];

  return (
    <div
      role="alert"
      className={cn('flex gap-3 rounded-md border p-4', v.container, className)}
    >
      <div className="shrink-0">{v.icon}</div>
      <div className="flex-1">
        {title && <p className="mb-1 text-sm font-semibold">{title}</p>}
        <div className="text-sm">{children}</div>
      </div>
    </div>
  );
}