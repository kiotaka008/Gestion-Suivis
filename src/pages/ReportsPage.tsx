import { useState, useEffect } from 'react';
import { TrendingUp, AlertTriangle, Clock, Zap } from 'lucide-react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { Alert } from '../components/ui/Alert';
import { PeriodSelector, type ReportPeriod } from '../components/reports/PeriodSelector';
import { ReportsKPICard } from '../components/reports/ReportsKPICard';
import { MonthlyEvolutionChart } from '../components/reports/MonthlyEvolutionChart';
import { StatusReportDoughnut } from '../components/reports/StatusReportDoughnut';
import { PriorityReportBar } from '../components/reports/PriorityReportBar';
import { ProjectsSummaryTable } from '../components/reports/ProjectsSummaryTable';
import { Skeleton } from '../components/ui/Skeleton';
import { SkeletonTable } from '../components/ui/SkeletonTable';
import { reportService } from '../services/reportService';
import { ApiError } from '../services/ApiError';
import type { Report } from '../types/models';

function pourcentage(valeur: number) {
  return `${Number.isInteger(valeur) ? valeur : valeur.toFixed(1)}%`;
}

export function ReportsPage() {
  const [period, setPeriod] = useState<ReportPeriod>('6m');
  const [report, setReport] = useState<Report | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Bilan recalculé par le backend à chaque changement de période.
  useEffect(() => {
    let annule = false;
    reportService
      .overview(period)
      .then((bilan) => {
        if (!annule) setReport(bilan);
      })
      .catch((err) => {
        if (!annule) setError(err instanceof ApiError ? err.message : 'Chargement du rapport impossible.');
      })
      .finally(() => {
        if (!annule) setIsLoading(false);
      });
    return () => {
      annule = true;
    };
  }, [period]);

  const changerPeriode = (nouvelle: ReportPeriod) => {
    if (nouvelle === period) return;
    setIsLoading(true);
    setError(null);
    setPeriod(nouvelle);
  };

  return (
    <Container className="py-6">
      <PageHeader
        title="Rapports"
        description="Analyses et statistiques de vos projets."
        actions={<PeriodSelector value={period} onChange={changerPeriode} />}
      />

      {error && (
        <Alert variant="danger" className="mt-6">
          {error}
        </Alert>
      )}

      {isLoading ? (
        <div className="mt-6 space-y-6">
          {/* Skeleton KPIs */}
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="rounded-xl border border-border bg-surface p-6">
                <Skeleton className="h-4 w-24 mb-2" />
                <Skeleton className="h-8 w-16" />
              </div>
            ))}
          </div>
          {/* Skeleton Graphiques */}
          <Skeleton className="h-80 w-full rounded-xl" />
          <div className="grid gap-6 lg:grid-cols-2">
            <Skeleton className="h-80 w-full rounded-xl" />
            <Skeleton className="h-80 w-full rounded-xl" />
          </div>
          {/* Skeleton Tableau */}
          <SkeletonTable rows={4} cols={3} />
        </div>
      ) : report ? (
        <>
          <div className="mt-6 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <ReportsKPICard label="Taux de réalisation" value={pourcentage(report.completionRate)} icon={<TrendingUp className="h-6 w-6" />} variant="success" />
            <ReportsKPICard label="Projets en retard" value={report.overdueProjects} icon={<AlertTriangle className="h-6 w-6" />} variant="danger" />
            <ReportsKPICard
              label="Durée moyenne"
              value={report.averageCompletionDays == null ? '—' : `${Math.round(report.averageCompletionDays)} jours`}
              icon={<Clock className="h-6 w-6" />}
              variant="default"
            />
            <ReportsKPICard label="Activités en retard" value={pourcentage(report.overdueRate)} icon={<Zap className="h-6 w-6" />} variant={report.overdueRate > 0 ? 'danger' : 'success'} />
          </div>
          <div className="mt-6"><MonthlyEvolutionChart data={report.monthlyEvolution} /></div>
          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <StatusReportDoughnut data={report.statusDistribution} />
            <PriorityReportBar data={report.priorityDistribution} />
          </div>
          <div className="mt-6"><ProjectsSummaryTable projects={report.projectsSummary} /></div>
        </>
      ) : null}
    </Container>
  );
}
