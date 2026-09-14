export function KeyValue({
  label,
  value,
  mono = true,
  labelWidth,
}: {
  label: string
  value: React.ReactNode
  mono?: boolean
  labelWidth?: string
}) {
  return (
    <div className="flex items-baseline justify-between gap-3 py-0.5">
      <dt
        className={`shrink-0 text-xs text-ink-400 ${labelWidth ?? ''}`}
        style={labelWidth ? undefined : { minWidth: '9rem' }}
      >
        {label}
      </dt>
      <dd className={`m-0 text-right text-[13px] text-ink-100 ${mono ? 'technical' : ''}`}>{value}</dd>
    </div>
  )
}

export function DefinitionList({ children }: { children: React.ReactNode }) {
  return <dl className="m-0 space-y-0.5">{children}</dl>
}