export const reportsMonthlyEvolution = {
  labels: ['Avr', 'Mai', 'Juin', 'Juil', 'Août', 'Sept'],
  datasets: [
    { label: 'Créés', data: [4, 6, 5, 8, 7, 9] },
    { label: 'Terminés', data: [2, 4, 3, 5, 6, 7] },
  ],
};

export const reportsStatusDistribution = {
  labels: ['En cours', 'Terminés', 'En attente', 'En retard'],
  data: [8, 5, 2, 1],
};

export const reportsPriorityDistribution = {
  labels: ['Urgent', 'Haute', 'Moyenne', 'Basse'],
  data: [2, 5, 6, 3],
};

export const reportsProjectsSummary = [
  { id: 'p-001', name: 'Refonte du site web', progress: 75, status: 'active' },
  { id: 'p-002', name: 'Application mobile', progress: 40, status: 'active' },
  { id: 'p-003', name: 'Projet IA', progress: 100, status: 'completed' },
  { id: 'p-004', name: 'Refonte UX/UI', progress: 30, status: 'on_hold' },
  { id: 'p-005', name: 'Migration cloud', progress: 60, status: 'active' },
  { id: 'p-006', name: 'API Backend v2', progress: 50, status: 'active' },
];