import { forwardRef, type InputHTMLAttributes } from 'react';
import { cn } from '../../utils/cn';

export interface SwitchProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'size'> {
  label?: string;
}

export const Switch = forwardRef<HTMLInputElement, SwitchProps>(
  ({ className, label, id, ...props }, ref) => {
    const switchId = id ?? props.name;

    return (
      <label
        htmlFor={switchId}
        className={cn(
          'inline-flex cursor-pointer items-center gap-3 text-sm text-foreground',
          props.disabled && 'cursor-not-allowed opacity-50',
          className
        )}
      >
        <span className="relative inline-flex h-6 w-11 shrink-0 items-center">
          <input
            ref={ref}
            id={switchId}
            type="checkbox"
            role="switch"
            className="peer h-6 w-11 cursor-pointer appearance-none rounded-full bg-muted transition-colors checked:bg-primary focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-background disabled:cursor-not-allowed"
            {...props}
          />
          <span className="pointer-events-none absolute left-0.5 top-0.5 h-5 w-5 rounded-full bg-white shadow-sm transition-transform peer-checked:translate-x-5" />
        </span>
        {label && <span>{label}</span>}
      </label>
    );
  }
);

Switch.displayName = 'Switch';