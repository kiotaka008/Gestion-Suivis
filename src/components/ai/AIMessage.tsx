import { Bot, User } from 'lucide-react';
import { cn } from '../../utils/cn';
import type { AIMessageType } from '../../mocks/aiMessages';

function formatContent(content: string) {
  const lines = content.split('\n');
  return lines.map((line, i) => {
    // Titre gras **texte**
    const parts = line.split(/(\*\*[^*]+\*\*)/g);
    const rendered = parts.map((part, j) => {
      if (part.startsWith('**') && part.endsWith('**')) {
        return (
          <strong key={j} className="font-semibold">
            {part.slice(2, -2)}
          </strong>
        );
      }
      return <span key={j}>{part}</span>;
    });

    return (
      <p key={i} className={cn('text-sm leading-relaxed', i > 0 && 'mt-1.5')}>
        {rendered}
      </p>
    );
  });
}

export interface AIMessageProps {
  message: AIMessageType;
}

export function AIMessage({ message }: AIMessageProps) {
  const isUser = message.role === 'user';

  return (
    <div className={cn('flex items-start gap-3', isUser && 'flex-row-reverse')}>
      <div
        className={cn(
          'flex h-8 w-8 shrink-0 items-center justify-center rounded-lg',
          isUser
            ? 'bg-primary text-white'
            : 'bg-primary/10 text-primary'
        )}
      >
        {isUser ? <User className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
      </div>

      <div
        className={cn(
          'max-w-[80%] rounded-lg border px-4 py-3',
          isUser
            ? 'border-primary bg-primary text-white'
            : 'border-border bg-surface text-foreground'
        )}
      >
        {formatContent(message.content)}
      </div>
    </div>
  );
}