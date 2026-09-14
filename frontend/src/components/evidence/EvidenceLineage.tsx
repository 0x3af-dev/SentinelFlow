import type { EvidenceEdgeDto, EvidenceNodeDto } from '@/api/types'
import { formatTimestamp, formatValue } from '@/lib/format'
import { layoutLayers } from '@/lib/lineage'
import { Panel } from '@/components/ui/Panel'
import { EmptyState } from '@/components/ui/EmptyState'

interface EvidenceLineageProps {
  nodes: EvidenceNodeDto[]
  edges: EvidenceEdgeDto[]
  className?: string
}

function NodeCard({ node, accent }: { node: EvidenceNodeDto; accent: boolean }) {
  const entries = Object.entries(node.value ?? {}).slice(0, 4)
  return (
    <div
      className={`min-w-44 max-w-64 border px-3 py-2 ${
        accent
          ? 'border-sky-500/50 bg-sky-500/5'
          : 'border-ink-700 bg-ink-850'
      }`}
    >
      <div className="flex items-center justify-between gap-3">
        <span className={`technical text-[12px] ${accent ? 'text-sky-300' : 'text-ink-100'}`}>{node.nodeType}</span>
        {accent && <span className="text-[9px] font-mono uppercase text-sky-500">→ decision</span>}
      </div>
      <p className="mt-0.5 mb-0 font-mono text-[10px] leading-4 text-ink-500">
        {node.entityType}/{node.entityId}
      </p>
      {entries.length > 0 && (
        <dl className="mt-1 space-y-0.5 border-t border-ink-800 pt-1">
          {entries.map(([key, value]) => (
            <div key={key} className="flex justify-between gap-2 text-[10px]">
              <dt className="m-0 font-mono text-ink-500">{key}</dt>
              <dd className="m-0 technical truncate text-ink-300" title={String(value)}>
                {formatValue(value)}
              </dd>
            </div>
          ))}
        </dl>
      )}
      <p className="mt-1 mb-0 font-mono text-[9px] text-ink-500">{formatTimestamp(node.observedAt)}</p>
    </div>
  )
}

export function EvidenceLineage({ nodes, edges, className = '' }: EvidenceLineageProps) {
  const { levels } = layoutLayers(nodes, edges)

  return (
    <Panel title="Evidence lineage" testId="evidence-lineage" className={className}>
      {nodes.length === 0 ? (
        <EmptyState message="No evidence nodes were found for this transaction." hint="Nothing was recorded for this decision lineage." />
      ) : (
        <ol className="m-0 space-y-3" aria-label="Evidence lineage stages">
          {levels.map((level, levelIndex) => (
            <li key={levelIndex} className="flex items-start gap-3">
              <span
                aria-hidden="true"
                className="mt-2 flex h-full w-px shrink-0 bg-ink-700"
                style={{ minHeight: '0.75rem' }}
              />
              <div className="flex flex-1 flex-wrap gap-2">
                {level.map((node) => (
                  <NodeCard key={node.id} node={node} accent={node.nodeType === 'DECISION'} />
                ))}
              </div>
            </li>
          ))}
        </ol>
      )}

      {edges.length > 0 && (
        <div className="mt-4 border-t border-ink-800 pt-3">
          <h3 className="section-label mb-2">Relationships ({edges.length})</h3>
          <ul className="m-0 grid grid-cols-1 gap-x-6 gap-y-1 sm:grid-cols-2">
            {edges.map((edge) => {
              const source = nodes.find((n) => n.id === edge.sourceNodeId)
              const target = nodes.find((n) => n.id === edge.targetNodeId)
              return (
                <li key={edge.id} className="flex items-center gap-2 font-mono text-[11px] text-ink-400">
                  <span className="technical text-ink-200">{source?.nodeType ?? '?'}</span>
                  <span className="text-ink-600">—{edge.relationshipType}▶</span>
                  <span className="technical text-ink-200">{target?.nodeType ?? '?'}</span>
                </li>
              )
            })}
          </ul>
        </div>
      )}
    </Panel>
  )
}