import { forwardRef, type InputHTMLAttributes } from 'react';
import { Check } from 'lucide-react';
import { cn } from '../../utils/cn';

export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label?: string;
}

export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(
  ({ className, label, id, ...props }, ref) => {
    const checkboxId = id ?? props.name;

    return (
      <label
        htmlFor={checkboxId}
        className={cn(
          'inline-flex cursor-pointer items-center gap-2 text-sm text-foreground',
          props.disabled && 'cursor-not-allowed opacity-50',
          className
        )}
      >
        <span className="relative inline-flex h-4 w-4 shrink-0">
          <input
            ref={ref}
            id={checkboxId}
            type="checkbox"
            className="peer h-4 w-4 cursor-pointer appearance-none rounded border border-border bg-surface transition-colors checked:border-primary checked:bg-primary focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-background disabled:cursor-not-allowed"
            {...props}
          />
          <Check className="pointer-events-none absolute left-0 top-0 h-4 w-4 scale-0 text-white transition-transform peer-checked:scale-100" />
        </span>
        {label && <span>{label}</span>}
      </label>
    );
  }
);

Checkbox.displayName = 'Checkbox';