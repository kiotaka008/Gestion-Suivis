import { useState } from 'react';
import axios from 'axios';
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
      // Appel au backend Spring Boot
      const response = await axios.post('/api/ai/chat', {
        content,
      });

      // Récupérer la réponse de l'IA
      const aiMessage: AIMessageType = {
        id: `m-${Date.now()}-a`,
        role: 'assistant',
        content: response.data.message.content,
        createdAt: new Date().toISOString(),
      };

      setMessages((prev) => [...prev, aiMessage]);
    } catch (error) {
      console.error('Erreur IA:', error);
      const errorMessage: AIMessageType = {
        id: `m-${Date.now()}-e`,
        role: 'assistant',
        content: "Désolé, une erreur est survenue lors de l'appel à l'IA. Veuillez réessayer.",
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