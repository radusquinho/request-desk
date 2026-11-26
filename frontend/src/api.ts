import { decide, overlaps, RULES } from './rules'
import type { AuditEvent, History, Me, NewRequest, RequestStatus, Webhook, WorkRequest } from './types'
import { OPEN_STATUSES } from './types'

export interface Api {
  readonly mode: 'live' | 'demo'
  me(): Promise<Me>
  submit(body: NewRequest): Promise<WorkRequest>
  mine(): Promise<WorkRequest[]>
  history(id: number): Promise<History>
  queue(): Promise<WorkRequest[]>
  review(id: number, approve: boolean, comment: string): Promise<WorkRequest>
  audit(): Promise<AuditEvent[]>
  /** Demo only: move the clock forward and run the SLA job. */
  advanceClock?(hours: number): Promise<number>
  /** Demo only: wipe local data. */
  reset?(): Promise<void>
}

export class ApiError extends Error {}

export const USERS: Record<string, string[]> = {
  ana: ['EMPLOYEE'],
  luis: ['EMPLOYEE'],
  marta: ['EMPLOYEE', 'MANAGER'],
  admin: ['ADMIN', 'EMPLOYEE', 'MANAGER'],
}

// ---------------------------------------------------------------- live backend

export class HttpApi implements Api {
  readonly mode = 'live' as const
  private readonly baseUrl: string
  private readonly auth: string

  constructor(baseUrl: string, user: string, password: string) {
    this.baseUrl = baseUrl.replace(/\/$/, '')
    this.auth = 'Basic ' + btoa(`${user}:${password}`)
  }

  private async call<T>(path: string, init?: RequestInit): Promise<T> {
    const res = await fetch(this.baseUrl + path, {
      ...init,
      headers: { Authorization: this.auth, 'Content-Type': 'application/json' },
    })
    if (!res.ok) {
      const body = await res.json().catch(() => ({}))
      throw new ApiError(body.error ?? `HTTP ${res.status}`)
    }
    return res.json() as Promise<T>
  }

  me() { return this.call<Me>('/api/me') }
  submit(body: NewRequest) { return this.call<WorkRequest>('/api/requests', { method: 'POST', body: JSON.stringify(body) }) }
  mine() { return this.call<WorkRequest[]>('/api/requests/mine') }
  history(id: number) { return this.call<History>(`/api/requests/${id}`) }
  queue() { return this.call<WorkRequest[]>('/api/review/queue') }
  review(id: number, approve: boolean, comment: string) {
    return this.call<WorkRequest>(`/api/review/${id}/${approve ? 'approve' : 'reject'}`, {
      method: 'POST',
      body: JSON.stringify({ comment }),
    })
  }
  audit() { return this.call<AuditEvent[]>('/api/audit') }
}

// ---------------------------------------------------------------- browser demo

interface DemoState {
  nextId: number
  clockOffsetMs: number
  requests: WorkRequest[]
  audit: AuditEvent[]
  webhooks: (Webhook & { requestId: number })[]
}

const KEY = 'request-desk-demo-v1'
const HOUR = 60 * 60 * 1000

function load(): DemoState {
  const raw = localStorage.getItem(KEY)
  if (raw) return JSON.parse(raw) as DemoState
  return { nextId: 1, clockOffsetMs: 0, requests: [], audit: [], webhooks: [] }
}

/**
 * Runs the same rules in the browser with localStorage, so the public demo needs no server.
 * Webhooks are recorded but never sent: there is nowhere to send them from a static page.
 */
export class DemoApi implements Api {
  readonly mode = 'demo' as const
  private readonly user: string

  constructor(user: string) {
    this.user = user
  }

  private get roles() { return USERS[this.user] ?? [] }
  private has(role: string) { return this.roles.includes(role) }

  private now(state: DemoState) { return new Date(Date.now() + state.clockOffsetMs) }

  private record(state: DemoState, req: WorkRequest, action: string, actor: string, detail: string, at: string) {
    const id = state.nextId++
    state.audit.push({ id, requestId: req.id, action, actor, detail, at })
    state.webhooks.push({ id, requestId: req.id, eventType: action, state: 'PENDING', attempts: 0, deliveredAt: null, lastError: null })
  }

  private apply(req: WorkRequest, status: RequestStatus, reason: string, at: Date, actor: string) {
    req.status = status
    req.reason = reason
    const open = OPEN_STATUSES.includes(status)
    req.reviewDueAt = open ? new Date(at.getTime() + RULES.reviewSlaHours * HOUR).toISOString() : null
    if (!open) {
      req.decidedAt = at.toISOString()
      req.decidedBy = actor
    }
  }

  private save(state: DemoState) { localStorage.setItem(KEY, JSON.stringify(state)) }

  async me(): Promise<Me> { return { username: this.user, roles: [...this.roles].sort() } }

  async submit(body: NewRequest): Promise<WorkRequest> {
    const state = load()
    if (!body.title.trim()) throw new ApiError('Invalid request: title must not be blank')
    if (body.type === 'VACATION') {
      if (!body.startDate || !body.endDate) throw new ApiError('A vacation needs a start and an end date')
      if (body.endDate < body.startDate) throw new ApiError('The end date is before the start date')
    } else if (!body.amount || body.amount <= 0) {
      throw new ApiError('An expense or purchase needs a positive amount')
    }
    const now = this.now(state)
    const req: WorkRequest = {
      id: state.nextId++, type: body.type, requester: this.user, title: body.title.trim(),
      amount: body.type === 'VACATION' ? null : body.amount ?? null,
      startDate: body.type === 'VACATION' ? body.startDate! : null,
      endDate: body.type === 'VACATION' ? body.endDate! : null,
      status: 'PENDING_REVIEW', reason: 'Submitted', createdAt: now.toISOString(),
      reviewDueAt: null, decidedAt: null, decidedBy: null,
    }
    state.requests.push(req)
    this.record(state, req, 'SUBMITTED', this.user, `Submitted: ${req.title}`, now.toISOString())
    const overlap = req.type === 'VACATION' && state.requests.some(o =>
      o.id !== req.id && o.type === 'VACATION' && (o.status === 'APPROVED' || o.status === 'AUTO_APPROVED') && overlaps(req, o))
    const d = decide(req, now.toISOString().slice(0, 10), overlap)
    this.apply(req, d.status, d.reason, now, 'rules')
    this.record(state, req, `RULE_${d.status}`, 'rules', d.reason, now.toISOString())
    this.save(state)
    return req
  }

  async mine() {
    return load().requests.filter(r => r.requester === this.user).sort((a, b) => b.id - a.id)
  }

  async history(id: number): Promise<History> {
    const state = load()
    const request = state.requests.find(r => r.id === id)
    if (!request) throw new ApiError(`Request ${id} not found`)
    if (request.requester !== this.user && !this.has('MANAGER')) throw new ApiError('You can only see your own requests')
    return {
      request,
      audit: state.audit.filter(a => a.requestId === id),
      webhooks: state.webhooks.filter(w => w.requestId === id),
    }
  }

  async queue() {
    if (!this.has('MANAGER')) throw new ApiError('Forbidden')
    const statuses: RequestStatus[] = this.has('ADMIN') ? OPEN_STATUSES : ['PENDING_REVIEW']
    return load().requests.filter(r => statuses.includes(r.status)).sort((a, b) => a.id - b.id)
  }

  async review(id: number, approve: boolean, comment: string) {
    if (!this.has('MANAGER')) throw new ApiError('Forbidden')
    const state = load()
    const req = state.requests.find(r => r.id === id)
    if (!req) throw new ApiError(`Request ${id} not found`)
    if (!OPEN_STATUSES.includes(req.status)) throw new ApiError(`Request ${id} is already ${req.status}`)
    if (req.status === 'ESCALATED' && !this.has('ADMIN')) throw new ApiError('Escalated requests can only be decided by an admin')
    if (req.requester === this.user) throw new ApiError('You cannot review your own request')
    const now = this.now(state)
    const reason = `${approve ? 'Approved' : 'Rejected'} by ${this.user}${comment.trim() ? `: ${comment.trim()}` : ''}`
    const status: RequestStatus = approve ? 'APPROVED' : 'REJECTED'
    this.apply(req, status, reason, now, this.user)
    this.record(state, req, status, this.user, reason, now.toISOString())
    this.save(state)
    return req
  }

  async audit() {
    if (!this.has('MANAGER')) throw new ApiError('Forbidden')
    return [...load().audit].sort((a, b) => b.id - a.id).slice(0, 100)
  }

  async advanceClock(hours: number) {
    const state = load()
    state.clockOffsetMs += hours * HOUR
    const now = this.now(state)
    let escalated = 0
    for (const req of state.requests) {
      if (req.status === 'PENDING_REVIEW' && req.reviewDueAt && Date.parse(req.reviewDueAt) < now.getTime()) {
        const reason = `No decision within ${RULES.reviewSlaHours} h: escalated to an admin`
        this.apply(req, 'ESCALATED', reason, now, 'rules')
        this.record(state, req, 'SLA_ESCALATED', 'rules', reason, now.toISOString())
        escalated++
      }
    }
    this.save(state)
    return escalated
  }

  async reset() { localStorage.removeItem(KEY) }
}

export function createApi(user: string): Api {
  const url = import.meta.env.VITE_API_URL as string | undefined
  return url ? new HttpApi(url, user, (import.meta.env.VITE_DEMO_PASSWORD as string | undefined) ?? 'demo') : new DemoApi(user)
}
