import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { createApi, USERS } from './api'
import type { AuditEvent, History, NewRequest, RequestType, WorkRequest } from './types'
import './App.css'

type Tab = 'new' | 'mine' | 'review' | 'audit'

const STATUS_LABEL: Record<string, string> = {
  AUTO_APPROVED: 'Auto-approved',
  AUTO_REJECTED: 'Auto-rejected',
  PENDING_REVIEW: 'Pending review',
  ESCALATED: 'Escalated',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
}

function StatusBadge({ status }: { status: string }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{STATUS_LABEL[status] ?? status}</span>
}

function describe(r: WorkRequest) {
  if (r.type === 'VACATION') return `${r.startDate} → ${r.endDate}`
  return `${r.amount} EUR`
}

const fmt = (iso: string) => new Date(iso).toLocaleString('en-GB', { dateStyle: 'short', timeStyle: 'short' })

export default function App() {
  const [user, setUser] = useState('ana')
  const api = useMemo(() => createApi(user), [user])
  const isManager = USERS[user].includes('MANAGER')
  const [tab, setTab] = useState<Tab>('new')
  const [mine, setMine] = useState<WorkRequest[]>([])
  const [queue, setQueue] = useState<WorkRequest[]>([])
  const [audit, setAudit] = useState<AuditEvent[]>([])
  const [detail, setDetail] = useState<History | null>(null)
  const [notice, setNotice] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null)

  const refresh = useCallback(async () => {
    try {
      setMine(await api.mine())
      setQueue(isManager ? await api.queue() : [])
      setAudit(isManager ? await api.audit() : [])
    } catch (e) {
      setNotice({ kind: 'error', text: (e as Error).message })
    }
  }, [api, isManager])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const switchUser = (name: string) => {
    setUser(name)
    setDetail(null)
    setNotice(null)
    if (!USERS[name].includes('MANAGER') && (tab === 'review' || tab === 'audit')) setTab('new')
  }

  const open = async (id: number) => {
    try {
      setDetail(await api.history(id))
    } catch (e) {
      setNotice({ kind: 'error', text: (e as Error).message })
    }
  }

  /** Runs an action, shows its message, and refreshes every list. */
  const act = async (fn: () => Promise<string>, keepDetail = true) => {
    try {
      const text = await fn()
      setNotice({ kind: 'ok', text })
      await refresh()
      if (keepDetail && detail) setDetail(await api.history(detail.request.id))
    } catch (e) {
      setNotice({ kind: 'error', text: (e as Error).message })
    }
  }

  const submit = (body: NewRequest) => act(async () => {
    const r = await api.submit(body)
    setTab('mine')
    setDetail(await api.history(r.id))
    return `Request #${r.id}: ${STATUS_LABEL[r.status]}. ${r.reason}`
  }, false)

  const review = (r: WorkRequest, approve: boolean) => act(async () => {
    await api.review(r.id, approve, '')
    return `#${r.id} ${approve ? 'approved' : 'rejected'}`
  })

  return (
    <div className="app">
      <header className="top">
        <div>
          <h1>Request Desk</h1>
          <p className="sub">Internal requests, decided by rules first and people second.</p>
        </div>
        <label className="who">
          Signed in as
          <select data-testid="user" value={user} onChange={e => switchUser(e.target.value)}>
            {Object.entries(USERS).map(([name, roles]) => (
              <option key={name} value={name}>
                {name} ({roles.filter(x => x !== 'EMPLOYEE').join(', ').toLowerCase() || 'employee'})
              </option>
            ))}
          </select>
        </label>
      </header>

      {api.mode === 'demo' && (
        <div className="demo" data-testid="demo-banner">
          <span>Browser demo: same rules as the Spring Boot API, data kept in this browser only.</span>
          <span className="demo-actions">
            <button data-testid="advance" onClick={() => act(async () => {
              const n = await api.advanceClock!(49)
              return `Clock moved 49 h. The SLA job escalated ${n} request(s).`
            })}>Skip 49 h (run SLA job)</button>
            <button className="ghost" onClick={() => act(async () => {
              await api.reset!()
              setDetail(null)
              return 'Demo data cleared'
            }, false)}>Reset</button>
          </span>
        </div>
      )}

      <nav className="tabs">
        <button data-testid="tab-new" className={tab === 'new' ? 'on' : ''} onClick={() => setTab('new')}>New request</button>
        <button data-testid="tab-mine" className={tab === 'mine' ? 'on' : ''} onClick={() => setTab('mine')}>My requests ({mine.length})</button>
        {isManager && <button data-testid="tab-review" className={tab === 'review' ? 'on' : ''} onClick={() => setTab('review')}>Review queue ({queue.length})</button>}
        {isManager && <button data-testid="tab-audit" className={tab === 'audit' ? 'on' : ''} onClick={() => setTab('audit')}>Audit log</button>}
      </nav>

      {notice && <p data-testid="notice" className={`notice ${notice.kind}`} role="status">{notice.text}</p>}

      <main className="layout">
        <section className="panel">
          {tab === 'new' && <NewRequestForm onSubmit={submit} />}
          {tab === 'mine' && <RequestTable rows={mine} onOpen={open} empty="You have no requests yet." />}
          {tab === 'review' && (
            <RequestTable rows={queue} onOpen={open} empty="Nothing waiting for you." showRequester
              actions={r => (
                <>
                  <button data-testid={`approve-${r.id}`} onClick={() => review(r, true)}>Approve</button>
                  <button data-testid={`reject-${r.id}`} className="ghost" onClick={() => review(r, false)}>Reject</button>
                </>
              )} />
          )}
          {tab === 'audit' && <AuditTable rows={audit} />}
        </section>

        <aside className="panel detail" data-testid="detail">
          {detail ? <Detail history={detail} /> : <p className="muted">Select a request to see its audit trail and webhook events.</p>}
        </aside>
      </main>

      <footer className="foot">
        Spring Boot 4 · Spring Security · PostgreSQL · React + TypeScript · Playwright ·{' '}
        <a href="https://github.com/radusquinho/request-desk">Source on GitHub</a>
      </footer>
    </div>
  )
}

function NewRequestForm({ onSubmit }: { onSubmit: (body: NewRequest) => void }) {
  const [type, setType] = useState<RequestType>('EXPENSE')
  const [title, setTitle] = useState('')
  const [amount, setAmount] = useState('')
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')

  const submit = (e: FormEvent) => {
    e.preventDefault()
    onSubmit(type === 'VACATION'
      ? { type, title, startDate: start, endDate: end }
      : { type, title, amount: Number(amount) })
  }

  return (
    <form className="form" onSubmit={submit} data-testid="new-form">
      <label>Type
        <select data-testid="type" value={type} onChange={e => setType(e.target.value as RequestType)}>
          <option value="EXPENSE">Expense</option>
          <option value="PURCHASE">Purchase</option>
          <option value="VACATION">Vacation</option>
        </select>
      </label>
      <label>Title
        <input data-testid="title" value={title} onChange={e => setTitle(e.target.value)}
          placeholder="e.g. Taxi to client meeting" required maxLength={200} />
      </label>
      {type === 'VACATION' ? (
        <div className="row">
          <label>From<input data-testid="start" type="date" value={start} onChange={e => setStart(e.target.value)} required /></label>
          <label>To<input data-testid="end" type="date" value={end} onChange={e => setEnd(e.target.value)} required /></label>
        </div>
      ) : (
        <label>Amount (EUR)
          <input data-testid="amount" type="number" min="0.01" step="0.01" value={amount}
            onChange={e => setAmount(e.target.value)} required />
        </label>
      )}
      <button data-testid="submit" type="submit">Submit</button>
      <p className="muted small">
        Rules: expenses up to 100 EUR are approved automatically, above 1,000 EUR go to an admin.
        Vacations of up to 2 days with 15 days' notice are approved automatically. Every purchase needs a manager.
        Pending reviews escalate after 48 h.
      </p>
    </form>
  )
}

function RequestTable({ rows, onOpen, empty, showRequester, actions }: {
  rows: WorkRequest[]
  onOpen: (id: number) => void
  empty: string
  showRequester?: boolean
  actions?: (r: WorkRequest) => ReactNode
}) {
  if (rows.length === 0) return <p className="muted">{empty}</p>
  return (
    <table className="table">
      <thead>
        <tr><th>#</th>{showRequester && <th>By</th>}<th>Type</th><th>Title</th><th>Detail</th><th>Status</th>{actions && <th />}</tr>
      </thead>
      <tbody>
        {rows.map(r => (
          <tr key={r.id} data-testid={`row-${r.id}`}>
            <td><button className="link" onClick={() => onOpen(r.id)}>#{r.id}</button></td>
            {showRequester && <td>{r.requester}</td>}
            <td>{r.type.toLowerCase()}</td>
            <td>{r.title}</td>
            <td>{describe(r)}</td>
            <td><StatusBadge status={r.status} /></td>
            {actions && <td className="actions">{actions(r)}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function AuditTable({ rows }: { rows: AuditEvent[] }) {
  if (rows.length === 0) return <p className="muted">No events yet.</p>
  return (
    <table className="table" data-testid="audit-table">
      <thead><tr><th>When</th><th>Request</th><th>Action</th><th>Actor</th><th>Detail</th></tr></thead>
      <tbody>
        {rows.map(e => (
          <tr key={e.id}><td>{fmt(e.at)}</td><td>#{e.requestId}</td><td><code>{e.action}</code></td><td>{e.actor}</td><td>{e.detail}</td></tr>
        ))}
      </tbody>
    </table>
  )
}

function Detail({ history }: { history: History }) {
  const r = history.request
  return (
    <div>
      <h2>#{r.id} {r.title}</h2>
      <p><StatusBadge status={r.status} /> <span data-testid="reason">{r.reason}</span></p>
      <p className="muted small">
        {r.type.toLowerCase()} · {describe(r)} · by {r.requester}
        {r.reviewDueAt && <> · review due {fmt(r.reviewDueAt)}</>}
      </p>
      <h3>Audit trail</h3>
      <ol className="timeline" data-testid="timeline">
        {history.audit.map(a => (
          <li key={a.id}><code>{a.action}</code> <span className="muted">{a.actor} · {fmt(a.at)}</span><br />{a.detail}</li>
        ))}
      </ol>
      <h3>Webhook outbox</h3>
      <ul className="hooks">
        {history.webhooks.map(w => (
          <li key={w.id}><code>{w.eventType}</code> <span className={`hook hook-${w.state.toLowerCase()}`}>{w.state}</span> · attempts {w.attempts}</li>
        ))}
      </ul>
    </div>
  )
}
