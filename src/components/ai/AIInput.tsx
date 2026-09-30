import { useState, type KeyboardEvent } from 'react';
import { Send } from 'lucide-react';
import { cn } from '../../utils/cn';

export interface AIInputProps {
  onSend: (message: string) => void;
  disabled?: boolean;
}

export function AIInput({ onSend, disabled }: AIInputProps) {
  const [value, setValue] = useState('');

  const handleSend = () => {
    const trimmed = value.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed);
    setValue('');
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="rounded-lg border border-border bg-surface p-2 focus-within:ring-2 focus-within:ring-primary">
      <div className="flex items-end gap-2">
        <textarea
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Posez une question à l'IA..."
          rows={1}
          disabled={disabled}
          className={cn(
            'max-h-40 min-h-[40px] flex-1 resize-none bg-transparent px-2 py-2 text-sm text-foreground placeholder:text-muted-foreground',
            'focus:outline-none disabled:opacity-50'
          )}
        />
        <button
          onClick={handleSend}
          disabled={disabled || !value.trim()}
          className={cn(
            'flex h-9 w-9 shrink-0 items-center justify-center rounded-lg transition-colors',
            'bg-primary text-white hover:bg-primary-hover',
            'disabled:cursor-not-allowed disabled:opacity-50'
          )}
          aria-label="Envoyer"
        >
          <Send className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}