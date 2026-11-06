import { useState, useEffect } from 'react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { AIChat } from '../components/ai/AIChat';
import { AIInsightsPanel } from '../components/ai/AIInsightsPanel';
import { Spinner } from '../components/ui/Spinner';
import {
  initialAIMessages,
  getMockAIResponse,
  type AIMessageType,
} from '../mocks/aiMessages';

export function AIAssistantPage() {
  const [messages, setMessages] = useState<AIMessageType[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isAiTyping, setIsAiTyping] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      setMessages(initialAIMessages);
      setIsLoading(false);
    }, 600);
    return () => clearTimeout(timer);
  }, []);

  const handleSend = async (content: string) => {
    const userMessage: AIMessageType = {
      id: `m-${Date.now()}-u`,
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setIsAiTyping(true);

    await new Promise((r) => setTimeout(r, 1200));

    const aiMessage: AIMessageType = {
      id: `m-${Date.now()}-a`,
      role: 'assistant',
      content: getMockAIResponse(content),
      createdAt: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, aiMessage]);
    setIsAiTyping(false);
  };

  return (
    <Container className="py-6">
      <PageHeader
        title="Assistant IA"
        description="Posez vos questions et obtenez des analyses de vos projets."
      />

      {isLoading ? (
        <div className="mt-6 flex h-[calc(100vh-220px)] items-center justify-center rounded-lg border border-border bg-background">
          <div className="flex flex-col items-center gap-4">
            <Spinner size="lg" />
            <p className="text-sm text-muted-foreground">Initialisation de l'assistant...</p>
          </div>
        </div>
      ) : (
        <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_320px]">
          <AIChat messages={messages} isLoading={isAiTyping} onSend={handleSend} />
          <AIInsightsPanel onSelectQuestion={handleSend} />
        </div>
      )}
    </Container>
  );
}