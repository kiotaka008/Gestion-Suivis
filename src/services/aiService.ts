import api from './api';

export interface AIChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string | null;
}

export interface AIChatResponse {
  conversationId: string;
  message: AIChatMessage;
}

export const aiService = {
  /** Envoie une question ; sans `conversationId`, le backend crée une nouvelle conversation. */
  async chat(content: string, conversationId?: string | null): Promise<AIChatResponse> {
    const { data } = await api.post<AIChatResponse>('/ai/chat', {
      content,
      conversationId: conversationId ?? undefined,
    });
    return data;
  },
};
