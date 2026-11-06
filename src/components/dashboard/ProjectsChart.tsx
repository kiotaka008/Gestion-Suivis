import { Line } from 'react-chartjs-2';
import { useMemo } from 'react';
import { useTheme } from '../../hooks/useTheme';
import { getChartColors } from '../../utils/chartSetup';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { monthlyEvolution } from '../../mocks/dashboardStats';

export function ProjectsChart() {
  const { theme } = useTheme();
  const isDark =
    theme === 'dark' ||
    (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);

  const colors = useMemo(() => getChartColors(isDark), [isDark]);

  const data = {
    labels: monthlyEvolution.labels,
    datasets: [
      {
        label: 'Total',
        data: monthlyEvolution.datasets[0].data,
        borderColor: colors.primary,
        backgroundColor: `${colors.primary}20`,
        tension: 0.4,
        fill: true,
        pointRadius: 4,
        pointHoverRadius: 6,
        pointBackgroundColor: colors.primary,
        pointBorderColor: '#fff',
        pointBorderWidth: 2,
      },
      {
        label: 'En cours',
        data: monthlyEvolution.datasets[1].data,
        borderColor: colors.success,
        backgroundColor: 'transparent',
        tension: 0.4,
        pointRadius: 4,
        pointHoverRadius: 6,
        pointBackgroundColor: colors.success,
        pointBorderColor: '#fff',
        pointBorderWidth: 2,
      },
      {
        label: 'Terminés',
        data: monthlyEvolution.datasets[2].data,
        borderColor: colors.warning,
        backgroundColor: 'transparent',
        tension: 0.4,
        pointRadius: 4,
        pointHoverRadius: 6,
        pointBackgroundColor: colors.warning,
        pointBorderColor: '#fff',
        pointBorderWidth: 2,
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'bottom' as const,
        labels: {
          color: colors.text,
          usePointStyle: true,
          pointStyle: 'circle' as const,
          padding: 20,
          font: { size: 12 },
        },
      },
      tooltip: {
        backgroundColor: isDark ? '#1e293b' : '#ffffff',
        titleColor: colors.textStrong,
        bodyColor: colors.text,
        borderColor: colors.grid,
        borderWidth: 1,
        padding: 12,
        cornerRadius: 8,
        usePointStyle: true,
      },
    },
    scales: {
      x: {
        grid: { display: false },
        ticks: { color: colors.text, font: { size: 12 } },
      },
      y: {
        beginAtZero: true,
        grid: { color: colors.grid },
        ticks: { color: colors.text, font: { size: 12 } },
      },
    },
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Évolution des projets</CardTitle>
        <CardDescription>7 derniers mois</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="h-72">
          <Line data={data} options={options} />
        </div>
      </CardContent>
    </Card>
  );
}