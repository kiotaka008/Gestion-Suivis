import { useState } from 'react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { AIChat } from '../components/ai/AIChat';
import { AIInsightsPanel } from '../components/ai/AIInsightsPanel';
import {
  initialAIMessages,
  getMockAIResponse,
  type AIMessageType,
} from '../mocks/aiMessages';

export function AIAssistantPage() {
  const [messages, setMessages] = useState<AIMessageType[]>(initialAIMessages);
  const [isLoading, setIsLoading] = useState(false);

  const handleSend = async (content: string) => {
    // Message utilisateur
    const userMessage: AIMessageType = {
      id: `m-${Date.now()}-u`,
      role: 'user',
      content,
      createdAt: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setIsLoading(true);

    // Simulation du délai de réponse
    await new Promise((r) => setTimeout(r, 1200));

    const aiMessage: AIMessageType = {
      id: `m-${Date.now()}-a`,
      role: 'assistant',
      content: getMockAIResponse(content),
      createdAt: new Date().toISOString(),
    };

    setMessages((prev) => [...prev, aiMessage]);
    setIsLoading(false);
  };

  return (
    <Container className="py-6">
      <PageHeader
        title="Assistant IA"
        description="Posez vos questions et obtenez des analyses de vos projets."
      />

      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_320px]">
        <AIChat messages={messages} isLoading={isLoading} onSend={handleSend} />
        <AIInsightsPanel onSelectQuestion={handleSend} />
      </div>
    </Container>
  );
}