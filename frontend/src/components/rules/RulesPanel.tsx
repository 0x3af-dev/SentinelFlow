import type { DisagreementInfo, RuleInfo } from '@/api/types'
import { formatValue } from '@/lib/format'
import { Panel } from '@/components/ui/Panel'
import { Badge } from '@/components/ui/Badge'
import { EmptyState } from '@/components/ui/EmptyState'

interface RulesPanelProps {
  rules: RuleInfo[]
  disagreement: DisagreementInfo
}

const ruleTone: Record<string, string> = {
  HIGH: 'bg-red-500/10 border-red-500/40 text-red-400',
  MEDIUM: 'bg-amber-500/10 border-amber-500/40 text-amber-300',
  LOW: 'bg-sky-500/10 border-sky-500/40 text-sky-300',
}

const FALLBACK_RULE_TONE = 'bg-amber-500/10 border-amber-500/40 text-amber-300'

/** Level 3: which deterministic rules fired, plus the descriptive model/rule disagreement. */
export function RulesPanel({ rules, disagreement }: RulesPanelProps) {
  return (
    <Panel
      title="Triggered rules"
      right={
        <span className="technical text-[11px] text-ink-400">
          {rules.length} rule{rules.length === 1 ? '' : 's'}
        </span>
      }
    >
      {rules.length === 0 ? (
        <EmptyState
          message="No rules were triggered for this decision."
          hint="The policy decision did not depend on a triggered rule."
        />
      ) : (
        <ul className="divide-y divide-ink-800">
          {rules.map((rule) => {
            const observed = Object.entries(rule.observedValues ?? {})
            return (
              <li key={`${rule.ruleId}:${rule.ruleVersion}`} className="py-2.5">
                <div className="flex items-center gap-2">
                  <span className="technical text-[13px] text-ink-100">{rule.ruleId}</span>
                  <span className="technical text-[11px] text-ink-500">v{rule.ruleVersion}</span>
                  {rule.severity && (
                    <Badge
                      tone={ruleTone[rule.severity.toUpperCase()] ?? FALLBACK_RULE_TONE}
                      className="!px-1.5 !py-0 text-[10px]"
                    >
                      {rule.severity}
                    </Badge>
                  )}
                </div>
                <p className="mt-0.5 mb-0 text-xs text-ink-300">{rule.description}</p>
                {observed.length > 0 && (
                  <dl className="mt-1 ml-2 space-y-0.5 border-l border-ink-800 pl-3">
                    {observed.map(([key, value]) => (
                      <div key={key} className="flex gap-2 text-xs">
                        <dt className="m-0 shrink-0 font-mono text-[11px] text-ink-500">{key}</dt>
                        <dd className="m-0 technical text-ink-300">{formatValue(value)}</dd>
                      </div>
                    ))}
                  </dl>
                )}
              </li>
            )
          })}
        </ul>
      )}

      <div className="mt-3 flex items-start gap-3 border-t border-ink-800 pt-3">
        <div className="shrink-0 font-mono text-[11px] uppercase tracking-wider text-ink-300">
          Model <span className="text-ink-500">/</span> Rule
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone="bg-ink-800 border-ink-600 text-ink-200">ML {disagreement.modelLevel}</Badge>
          <Badge tone="bg-ink-800 border-ink-600 text-ink-200">Rules {disagreement.ruleLevel}</Badge>
          <Badge tone="bg-ink-800 border-ink-600 text-sky-300">{disagreement.category}</Badge>
        </div>
      </div>
      <p className="mt-2 mb-0 text-xs text-ink-400">
        {disagreement.summary}. Diagnostic only — the production decision was determined by the persisted policy
        evaluation; disagreement does not change it.
      </p>
    </Panel>
  )
}