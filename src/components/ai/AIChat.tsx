import { useEffect, useRef } from 'react';
import { AIMessage } from './AIMessage';
import { AIInput } from './AIInput';
import { AILoading } from './AILoading';
import type { AIMessageType } from '../../mocks/aiMessages';

export interface AIChatProps {
  messages: AIMessageType[];
  isLoading: boolean;
  onSend: (message: string) => void;
}

export function AIChat({ messages, isLoading, onSend }: AIChatProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isLoading]);

  return (
    <div className="flex h-[calc(100vh-220px)] flex-col rounded-lg border border-border bg-background">
      {/* Liste des messages */}
      <div className="flex-1 space-y-4 overflow-y-auto p-4 md:p-6">
        {messages.map((message) => (
          <AIMessage key={message.id} message={message} />
        ))}
        {isLoading && <AILoading />}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div className="border-t border-border p-3 md:p-4">
        <AIInput onSend={onSend} disabled={isLoading} />
        <p className="mt-2 text-center text-xs text-muted-foreground">
          L'IA peut faire des erreurs. Vérifiez les informations importantes.
        </p>
      </div>
    </div>
  );
}