export type ArtifactKind = 'actual' | 'simulation' | 'counterfactual' | 'historical' | 'replay'

const kindClass: Record<ArtifactKind, string> = {
  actual: 'actual-stamp',
  simulation: 'simulation-stamp',
  counterfactual: 'counterfactual-stamp',
  historical: 'historical-stamp',
  replay: 'historical-stamp',
}

const kindLabel: Record<ArtifactKind, string> = {
  actual: 'Actual',
  simulation: 'Simulated',
  counterfactual: 'Counterfactual',
  historical: 'Historical reconstruction',
  replay: 'Historical reconstruction',
}

/**
 * The visual language that keeps ACTUAL vs SIMULATED vs COUNTERFACTUAL vs
 * HISTORICAL unmistakable (§44). Display-only; never derives data.
 */
export function Stamp({ kind, label }: { kind: ArtifactKind; label?: string }) {
  return <span className={`stamp ${kindClass[kind]}`}>{label ?? kindLabel[kind]}</span>
}

export function StampRow({ stamps }: { stamps: ArtifactKind[] }) {
  return (
    <span className="flex items-center gap-2">
      {stamps.map((s) => (
        <Stamp key={s} kind={s} />
      ))}
    </span>
  )
}