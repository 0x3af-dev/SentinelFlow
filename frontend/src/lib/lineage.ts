import type { EvidenceEdgeDto, EvidenceNodeDto } from '@/api/types'

export interface LayeredGraph {
  levels: EvidenceNodeDto[][]
}

/**
 * Deterministic, layered layout of a persisted evidence subgraph (display
 * only). Level = longest path from any root; no recursive traversal, no graph
 * library, bounded render. Backend computes nothing new for this view.
 */
export function layoutLayers(nodes: EvidenceNodeDto[], edges: EvidenceEdgeDto[]): LayeredGraph {
  const index = new Map(nodes.map((n) => [n.id, n]))
  const incoming = new Map<string, number>(nodes.map((n) => [n.id, 0]))
  const outgoing = new Map<string, string[]>(nodes.map((n) => [n.id, []]))

  for (const edge of edges) {
    if (!index.has(edge.sourceNodeId) || !index.has(edge.targetNodeId)) continue
    incoming.set(edge.targetNodeId, (incoming.get(edge.targetNodeId) ?? 0) + 1)
    outgoing.get(edge.sourceNodeId)?.push(edge.targetNodeId)
  }

  const order = new Map(nodes.map((n, i) => [n.id, i]))

  const levelOf = new Map<string, number>()
  const queue = nodes.filter((n) => (incoming.get(n.id) ?? 0) === 0)
  const topo: EvidenceNodeDto[] = []
  while (queue.length > 0) {
    queue.sort((a, b) => (order.get(a.id) ?? 0) - (order.get(b.id) ?? 0))
    const node = queue.shift() as EvidenceNodeDto
    topo.push(node)
    for (const targetId of outgoing.get(node.id) ?? []) {
      const next = (incoming.get(targetId) ?? 0) - 1
      incoming.set(targetId, next)
      if (next === 0) {
        const target = index.get(targetId)
        if (target) queue.push(target)
      }
    }
  }

  for (const node of nodes) {
    levelOf.set(node.id, 0)
  }
  for (const node of topo) {
    for (const targetId of outgoing.get(node.id) ?? []) {
      levelOf.set(targetId, Math.max(levelOf.get(targetId) ?? 0, (levelOf.get(node.id) ?? 0) + 1))
    }
  }

  const maxLevel = nodes.reduce((max, n) => Math.max(max, levelOf.get(n.id) ?? 0), 0)
  const levels: EvidenceNodeDto[][] = Array.from({ length: maxLevel + 1 }, () => [])
  for (const node of topo) levels[levelOf.get(node.id) ?? 0]!.push(node)

  return { levels }
}