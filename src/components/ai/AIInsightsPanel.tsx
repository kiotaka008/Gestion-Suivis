import { TrendingUp, AlertTriangle, FileText, Sparkles } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../ui/Card';
import { aiSuggestedQuestions } from '../../mocks/aiMessages';

export interface AIInsightsPanelProps {
  onSelectQuestion: (question: string) => void;
}

const QUICK_ACTIONS = [
  {
    id: 'analyze',
    label: 'Analyser les risques',
    icon: AlertTriangle,
    color: 'text-warning',
    bg: 'bg-warning/10',
    question: 'Analyse les risques du projet Migration cloud',
  },
  {
    id: 'summary',
    label: 'Résumer la semaine',
    icon: TrendingUp,
    color: 'text-info',
    bg: 'bg-info/10',
    question: 'Résume les activités de cette semaine',
  },
  {
    id: 'report',
    label: 'Générer un rapport',
    icon: FileText,
    color: 'text-success',
    bg: 'bg-success/10',
    question: 'Génère un rapport de productivité',
  },
];

export function AIInsightsPanel({ onSelectQuestion }: AIInsightsPanelProps) {
  return (
    <div className="space-y-4">
      {/* Actions rapides */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Sparkles className="h-4 w-4 text-primary" />
            Actions rapides
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          {QUICK_ACTIONS.map((action) => (
            <button
              key={action.id}
              onClick={() => onSelectQuestion(action.question)}
              className="flex w-full items-center gap-3 rounded-lg border border-border p-3 text-left transition-colors hover:bg-muted"
            >
              <div className={`flex h-9 w-9 items-center justify-center rounded-lg ${action.bg} ${action.color}`}>
                <action.icon className="h-4 w-4" />
              </div>
              <span className="text-sm font-medium text-foreground">{action.label}</span>
            </button>
          ))}
        </CardContent>
      </Card>

      {/* Suggestions */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Suggestions</CardTitle>
          <CardDescription>Questions fréquentes</CardDescription>
        </CardHeader>
        <CardContent className="space-y-2">
          {aiSuggestedQuestions.map((question, i) => (
            <button
              key={i}
              onClick={() => onSelectQuestion(question)}
              className="w-full rounded-md border border-border px-3 py-2 text-left text-xs text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
            >
              {question}
            </button>
          ))}
        </CardContent>
      </Card>
    </div>
  );
}