import { useEffect, useRef, useState, type ReactNode } from 'react';
import { cn } from '../../utils/cn';

export interface DropdownItem {
  label: string;
  icon?: ReactNode;
  onClick?: () => void;
  variant?: 'default' | 'danger';
}

export interface DropdownProps {
  trigger: ReactNode;
  items: DropdownItem[];
  align?: 'left' | 'right';
}

export function Dropdown({ trigger, items, align = 'right' }: DropdownProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;

    // Fermer au clic extérieur
    const onClickOutside = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    
    // Fermer avec Échap et naviguer avec les flèches
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        return;
      }

      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        const items = menuRef.current?.querySelectorAll('button');
        if (!items) return;
        
        const currentIndex = Array.from(items).indexOf(document.activeElement as HTMLButtonElement);
        let nextIndex;

        if (e.key === 'ArrowDown') {
          nextIndex = currentIndex < items.length - 1 ? currentIndex + 1 : 0;
        } else {
          nextIndex = currentIndex > 0 ? currentIndex - 1 : items.length - 1;
        }
        
        (items[nextIndex] as HTMLElement).focus();
      }
    };

    document.addEventListener('mousedown', onClickOutside);
    document.addEventListener('keydown', onKeyDown);

    // Mettre le focus sur le premier élément
    setTimeout(() => {
      const firstButton = menuRef.current?.querySelector('button');
      firstButton?.focus();
    }, 50);

    return () => {
      document.removeEventListener('mousedown', onClickOutside);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  return (
    <div ref={ref} className="relative inline-block">
      <div onClick={() => setOpen((o) => !o)} aria-haspopup="true" aria-expanded={open}>
        {trigger}
      </div>

      {open && (
        <div
          ref={menuRef}
          role="menu"
          className={cn(
            'absolute z-40 mt-1 min-w-48 rounded-md border border-border bg-surface p-1 shadow-lg',
            align === 'right' ? 'right-0' : 'left-0'
          )}
        >
          {items.map((item, i) => (
            <button
              key={i}
              role="menuitem"
              onClick={() => {
                item.onClick?.();
                setOpen(false);
              }}
              className={cn(
                'flex w-full items-center gap-2 rounded-sm px-3 py-2 text-left text-sm transition-colors focus:outline-none focus:bg-muted',
                item.variant === 'danger' ? 'text-danger hover:bg-danger/10' : 'text-foreground hover:bg-muted'
              )}
            >
              {item.icon}
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}