import { useEffect, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import { cn } from '../../utils/cn';

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
  size?: 'sm' | 'md' | 'lg';
}

export function Modal({ isOpen, onClose, title, description, children, footer, size = 'md' }: ModalProps) {
  const modalRef = useRef<HTMLDivElement>(null);

  // Dernière version de onClose. Les formulaires passent une fonction recréée à chaque rendu :
  // si l'effet ci-dessous en dépendait, il se relancerait à chaque frappe au clavier et
  // replacerait le focus au début de la fenêtre après chaque caractère saisi.
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!isOpen) return;
    const modal = modalRef.current;
    const SELECTEUR = 'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';

    // 1. Fermer avec Échap
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCloseRef.current();
    };
    document.addEventListener('keydown', onKey);
    document.body.style.overflow = 'hidden';

    // 2. Focus initial, une seule fois à l'ouverture : premier champ de saisie s'il y en a un,
    //    sinon premier élément focusable (bouton de fermeture).
    const minuterie = setTimeout(() => {
      const champ = modal?.querySelector<HTMLElement>('input:not([disabled]), select:not([disabled]), textarea:not([disabled])');
      (champ ?? modal?.querySelector<HTMLElement>(SELECTEUR))?.focus();
    }, 50);

    // 3. Piège à focus : Tab et Maj+Tab restent dans la fenêtre (éléments recalculés à chaque fois,
    //    le contenu pouvant changer pendant que la fenêtre est ouverte).
    const handleTab = (e: KeyboardEvent) => {
      if (e.key !== 'Tab' || !modal) return;
      const elements = Array.from(modal.querySelectorAll<HTMLElement>(SELECTEUR));
      if (elements.length === 0) return;
      const premier = elements[0];
      const dernier = elements[elements.length - 1];
      if (e.shiftKey && document.activeElement === premier) {
        e.preventDefault();
        dernier.focus();
      } else if (!e.shiftKey && document.activeElement === dernier) {
        e.preventDefault();
        premier.focus();
      }
    };
    modal?.addEventListener('keydown', handleTab);

    return () => {
      clearTimeout(minuterie);
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = '';
      modal?.removeEventListener('keydown', handleTab);
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const sizes = { sm: 'max-w-sm', md: 'max-w-md', lg: 'max-w-2xl' };

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby={title ? 'modal-title' : undefined}>
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} aria-hidden="true" />
      <div ref={modalRef} className={cn('relative w-full rounded-lg border border-border bg-surface shadow-lg', sizes[size])}>
        {(title || description) && (
          <div className="flex items-start justify-between gap-4 border-b border-border p-6">
            <div>
              {title && <h2 id="modal-title" className="text-lg font-semibold text-foreground">{title}</h2>}
              {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
            </div>
            <button onClick={onClose} aria-label="Fermer" className="rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground focus:outline-none focus:ring-2 focus:ring-primary">
              <X className="h-5 w-5" />
            </button>
          </div>
        )}
        <div className="p-6">{children}</div>
        {footer && <div className="flex justify-end gap-2 border-t border-border p-6">{footer}</div>}
      </div>
    </div>,
    document.body
  );
}