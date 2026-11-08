import { useState } from 'react';
import { aiService } from '../services/aiService';
import { ApiError } from '../services/ApiError';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { AIChat } from '../components/ai/AIChat';
import { AIInsightsPanel } from '../components/ai/AIInsightsPanel';
import type { AIMessageType } from '../mocks/aiMessages';

const initialMessages: AIMessageType[] = [
  {
    id: 'm-001',
    role: 'assistant',
    content:
      "Bonjour ! Je suis votre assistant IA. Je peux vous aider à analyser vos projets, identifier des risques, générer des rapports ou répondre à vos questions. Que puis-je faire pour vous ?",
    createdAt: new Date(Date.now() - 60000).toISOString(),
  },
];

export function AIAssistantPage() {
  const [messages, setMessages] = useState<AIMessageType[]>(initialMessages);
  const [isLoading, setIsLoading] = useState(false);
  // Identifiant de la conversation côté backend : l'IA garde ainsi le fil de l'échange.
  const [conversationId, setConversationId] = useState<string | null>(null);

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

    try {
      // Appel au backend Spring Boot (qui interroge le service IA)
      const response = await aiService.chat(content, conversationId);
      setConversationId(response.conversationId);

      const aiMessage: AIMessageType = {
        id: response.message.id,
        role: 'assistant',
        content: response.message.content,
        createdAt: response.message.createdAt ?? new Date().toISOString(),
      };

      setMessages((prev) => [...prev, aiMessage]);
    } catch (error) {
      const errorMessage: AIMessageType = {
        id: `m-${Date.now()}-e`,
        role: 'assistant',
        content:
          error instanceof ApiError
            ? `Désolé, l'assistant n'a pas pu répondre : ${error.message}`
            : "Désolé, une erreur est survenue lors de l'appel à l'IA. Veuillez réessayer.",
        createdAt: new Date().toISOString(),
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
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