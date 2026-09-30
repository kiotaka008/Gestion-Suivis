export interface AIMessageType {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

export const initialAIMessages: AIMessageType[] = [
  {
    id: 'm-001',
    role: 'assistant',
    content:
      "Bonjour ! Je suis votre assistant IA. Je peux vous aider à analyser vos projets, identifier des risques, générer des rapports ou répondre à vos questions. Que puis-je faire pour vous ?",
    createdAt: new Date(Date.now() - 60000).toISOString(),
  },
];

export const aiSuggestedQuestions = [
  'Quels projets sont en retard ?',
  "Analyse les risques du projet Migration cloud",
  'Résume les activités de cette semaine',
  'Génère un rapport de productivité',
  'Quels projets terminés ce mois-ci ?',
];

export function getMockAIResponse(question: string): string {
  const q = question.toLowerCase();

  if (q.includes('retard')) {
    return "Deux projets sont actuellement en retard :\n\n• **Refonte UX/UI** — 5 jours de retard, progression 30%\n• **Application mobile** — 2 jours de retard, progression 40%\n\nJe vous recommande de prioriser Refonte UX/UI, dont l'échéance est la plus critique.";
  }

  if (q.includes('risque')) {
    return "**Analyse des risques**\n\n1. **Risque élevé** — Le projet Refonte UX/UI est en attente depuis 3 semaines.\n2. **Risque moyen** — Trois activités sont bloquées sur Migration cloud.\n3. **Risque faible** — L'équipe est stable, aucun turnover détecté.\n\nSouhaitez-vous un plan de mitigation détaillé ?";
  }

  if (q.includes('résume') || q.includes('semaine')) {
    return "**Résumé de la semaine**\n\n• 8 activités terminées (+12% vs semaine précédente)\n• 14 activités en cours\n• 2 activités bloquées\n• Taux de productivité : 89%\n\nLes équipes sont globalement sur la bonne voie.";
  }

  if (q.includes('rapport')) {
    return "**Rapport de productivité — 30 derniers jours**\n\n• Taux de réussite : 75%\n• Temps moyen par activité : 12 jours\n• Projets livrés : 5\n• Productivité : 89%\n\nSouhaitez-vous exporter ce rapport en PDF ?";
  }

  if (q.includes('terminé') || q.includes('terminés')) {
    return "**Projets terminés ce mois-ci**\n\n• Projet IA — terminé le 28 août 2025\n\n5 projets sont en cours et 2 sont en retard. Je peux détailler chacun si vous le souhaitez.";
  }

  return "Je comprends votre question. Voici ce que je peux vous dire :\n\n• Vous avez actuellement 15 projets actifs\n• 8 projets sont en cours, 5 sont terminés\n• 2 projets sont en retard\n\nVoulez-vous que je détaille un aspect en particulier ?";
}