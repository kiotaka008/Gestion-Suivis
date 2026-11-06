export const monthlyEvolution = {
  labels: ['Mar', 'Avr', 'Mai', 'Juin', 'Juil', 'Août', 'Sept'],
  datasets: [
    { label: 'Total', data: [8, 10, 12, 14, 16, 20, 24] },
    { label: 'En cours', data: [5, 6, 7, 8, 9, 11, 13] },
    { label: 'Terminés', data: [2, 3, 4, 5, 6, 8, 10] },
  ],
};

export const statusDistribution = {
  labels: ['En cours', 'Terminés', 'En attente', 'En retard'],
  data: [8, 5, 2, 1],
};

export const tasksPerWeek = {
  labels: ['Sem 36', 'Sem 37', 'Sem 38', 'Sem 39'],
  datasets: [
    { label: 'Terminées', data: [12, 15, 18, 22] },
    { label: 'En cours', data: [8, 10, 9, 14] },
    { label: 'En retard', data: [2, 1, 3, 2] },
  ],
};