import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

export function SearchBar({ initialValue }: { initialValue?: string }) {
  const [query, setQuery] = useState(initialValue ?? '')
  const navigate = useNavigate()

  const submit = (event: React.FormEvent) => {
    event.preventDefault()
    const reference = query.trim()
    if (reference.length === 0) return
    navigate(`/transactions/${encodeURIComponent(reference)}`)
  }

  return (
    <form onSubmit={submit} role="search" className="flex items-stretch">
      <label className="sr-only" htmlFor="transaction-search">
        Search transaction reference
      </label>
      <input
        id="transaction-search"
        type="search"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search transaction reference… e.g. txn-demo-001"
        spellCheck={false}
        autoCapitalize="none"
        className="w-72 max-w-[50vw] border border-ink-700 bg-ink-900 px-3 py-1.5 font-mono text-[13px] text-ink-100 placeholder:text-ink-500 focus:border-sky-500"
      />
      <button
        type="submit"
        className="border border-l-0 border-ink-700 bg-ink-800 px-3 font-mono text-xs text-ink-200 hover:bg-ink-700"
      >
        Search
      </button>
    </form>
  )
}