import { useState } from 'react';
import { TrendingUp, AlertTriangle, Clock, Zap } from 'lucide-react';
import { Container } from '../components/ui/Container';
import { PageHeader } from '../components/ui/PageHeader';
import { PeriodSelector, type ReportPeriod } from '../components/reports/PeriodSelector';
import { ReportsKPICard } from '../components/reports/ReportsKPICard';
import { MonthlyEvolutionChart } from '../components/reports/MonthlyEvolutionChart';
import { StatusReportDoughnut } from '../components/reports/StatusReportDoughnut';
import { PriorityReportBar } from '../components/reports/PriorityReportBar';
import { ProjectsSummaryTable } from '../components/reports/ProjectsSummaryTable';

export function ReportsPage() {
  const [period, setPeriod] = useState<ReportPeriod>('6m');

  return (
    <Container className="py-6">
      <PageHeader
        title="Rapports"
        description="Analyses et statistiques de vos projets."
        actions={<PeriodSelector value={period} onChange={setPeriod} />}
      />

      {/* KPIs */}
      <div className="mt-6 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
        <ReportsKPICard
          label="Taux de réussite"
          value="75%"
          icon={<TrendingUp className="h-6 w-6" />}
          variant="success"
          trend={{ value: 8, isPositive: true }}
        />
        <ReportsKPICard
          label="Projets en retard"
          value={2}
          icon={<AlertTriangle className="h-6 w-6" />}
          variant="danger"
          trend={{ value: 3, isPositive: false }}
        />
        <ReportsKPICard
          label="Temps moyen"
          value="12 jours"
          icon={<Clock className="h-6 w-6" />}
          variant="default"
          trend={{ value: 5, isPositive: true }}
        />
        <ReportsKPICard
          label="Productivité"
          value="89%"
          icon={<Zap className="h-6 w-6" />}
          variant="success"
          trend={{ value: 12, isPositive: true }}
        />
      </div>

      {/* Graphique Line */}
      <div className="mt-6">
        <MonthlyEvolutionChart />
      </div>

      {/* Doughnut + Bar */}
      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        <StatusReportDoughnut />
        <PriorityReportBar />
      </div>

      {/* Tableau de synthèse */}
      <div className="mt-6">
        <ProjectsSummaryTable />
      </div>
    </Container>
  );
}