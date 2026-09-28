import { Bar } from 'react-chartjs-2';
import { useMemo } from 'react';
import { useTheme } from '../../hooks/useTheme';
import { getChartColors } from '../../utils/chartSetup';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { tasksPerWeek } from '../../mocks/dashboardStats';

export function TasksBarChart() {
  const { theme } = useTheme();
  const isDark =
    theme === 'dark' ||
    (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);

  const colors = useMemo(() => getChartColors(isDark), [isDark]);

  const data = {
    labels: tasksPerWeek.labels,
    datasets: [
      {
        label: 'Terminées',
        data: tasksPerWeek.datasets[0].data,
        backgroundColor: colors.success,
        borderRadius: 6,
      },
      {
        label: 'En cours',
        data: tasksPerWeek.datasets[1].data,
        backgroundColor: colors.info,
        borderRadius: 6,
      },
      {
        label: 'En retard',
        data: tasksPerWeek.datasets[2].data,
        backgroundColor: colors.danger,
        borderRadius: 6,
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
        <CardTitle>Activités par semaine</CardTitle>
        <CardDescription>4 dernières semaines</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="h-72">
          <Bar data={data} options={options} />
        </div>
      </CardContent>
    </Card>
  );
}