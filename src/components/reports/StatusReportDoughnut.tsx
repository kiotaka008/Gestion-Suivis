import { Doughnut } from 'react-chartjs-2';
import { useMemo } from 'react';
import { useTheme } from '../../hooks/useTheme';
import { getChartColors } from '../../utils/chartSetup';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { reportsStatusDistribution } from '../../mocks/reports';

export function StatusReportDoughnut() {
  const { theme } = useTheme();
  const isDark =
    theme === 'dark' ||
    (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches);

  const colors = useMemo(() => getChartColors(isDark), [isDark]);
  const total = reportsStatusDistribution.data.reduce((a, b) => a + b, 0);

  const data = {
    labels: reportsStatusDistribution.labels,
    datasets: [
      {
        data: reportsStatusDistribution.data,
        backgroundColor: [colors.info, colors.success, colors.warning, colors.danger],
        borderColor: isDark ? '#1e293b' : '#ffffff',
        borderWidth: 3,
        hoverOffset: 8,
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '70%',
    plugins: {
      legend: {
        position: 'bottom' as const,
        labels: {
          color: colors.text,
          usePointStyle: true,
          pointStyle: 'circle' as const,
          padding: 16,
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
        callbacks: {
          label: (ctx: { label: string; parsed: number }) =>
            ` ${ctx.label} : ${ctx.parsed} (${Math.round((ctx.parsed / total) * 100)}%)`,
        },
      },
    },
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Répartition par statut</CardTitle>
        <CardDescription>Ensemble des projets</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="relative h-80">
          <Doughnut data={data} options={options} />
          <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
            <span className="text-3xl font-bold text-foreground">{total}</span>
            <span className="text-xs text-muted-foreground">projets</span>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}