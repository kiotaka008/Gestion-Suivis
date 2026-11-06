import { Bar } from 'react-chartjs-2';
import { useMemo } from 'react';
import { useTheme } from '../../hooks/useTheme';
import { getChartColors } from '../../utils/chartSetup';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { reportsPriorityDistribution } from '../../mocks/reports';

export function PriorityReportBar() {
  const { theme } = useTheme();
  const isDark =
    theme === 'dark' ||
    (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);

  const colors = useMemo(() => getChartColors(isDark), [isDark]);

  const data = {
    labels: reportsPriorityDistribution.labels,
    datasets: [
      {
        label: 'Projets',
        data: reportsPriorityDistribution.data,
        backgroundColor: [colors.danger, colors.warning, colors.info, colors.primary],
        borderRadius: 6,
        barThickness: 40,
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
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
        <CardTitle>Répartition par priorité</CardTitle>
        <CardDescription>Ensemble des projets</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="h-80">
          <Bar data={data} options={options} />
        </div>
      </CardContent>
    </Card>
  );
}