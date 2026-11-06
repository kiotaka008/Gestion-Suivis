import { useState, useEffect } from 'react';
import { TrendingUp, AlertTriangle, Clock, Zap } from 'lucide-react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { PeriodSelector, type ReportPeriod } from '../components/reports/PeriodSelector';
import { ReportsKPICard } from '../components/reports/ReportsKPICard';
import { MonthlyEvolutionChart } from '../components/reports/MonthlyEvolutionChart';
import { StatusReportDoughnut } from '../components/reports/StatusReportDoughnut';
import { PriorityReportBar } from '../components/reports/PriorityReportBar';
import { ProjectsSummaryTable } from '../components/reports/ProjectsSummaryTable';
import { Skeleton } from '../components/ui/Skeleton';
import { SkeletonTable } from '../components/ui/SkeletonTable';

export function ReportsPage() {
  const [period, setPeriod] = useState<ReportPeriod>('6m');
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const timer = setTimeout(() => setIsLoading(false), 800);
    return () => clearTimeout(timer);
  }, []);

  return (
    <Container className="py-6">
      <PageHeader
        title="Rapports"
        description="Analyses et statistiques de vos projets."
        actions={<PeriodSelector value={period} onChange={setPeriod} />}
      />

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
      ) : (
        <>
          <div className="mt-6 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <ReportsKPICard label="Taux de réussite" value="75%" icon={<TrendingUp className="h-6 w-6" />} variant="success" trend={{ value: 8, isPositive: true }} />
            <ReportsKPICard label="Projets en retard" value={2} icon={<AlertTriangle className="h-6 w-6" />} variant="danger" trend={{ value: 3, isPositive: false }} />
            <ReportsKPICard label="Temps moyen" value="12 jours" icon={<Clock className="h-6 w-6" />} variant="default" trend={{ value: 5, isPositive: true }} />
            <ReportsKPICard label="Productivité" value="89%" icon={<Zap className="h-6 w-6" />} variant="success" trend={{ value: 12, isPositive: true }} />
          </div>
          <div className="mt-6"><MonthlyEvolutionChart /></div>
          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <StatusReportDoughnut />
            <PriorityReportBar />
          </div>
          <div className="mt-6"><ProjectsSummaryTable /></div>
        </>
      )}
    </Container>
  );
}