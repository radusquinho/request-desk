export type RequestType = 'VACATION' | 'EXPENSE' | 'PURCHASE'

export type RequestStatus =
  | 'AUTO_APPROVED'
  | 'AUTO_REJECTED'
  | 'PENDING_REVIEW'
  | 'ESCALATED'
  | 'APPROVED'
  | 'REJECTED'

export interface WorkRequest {
  id: number
  type: RequestType
  requester: string
  title: string
  amount: number | null
  startDate: string | null
  endDate: string | null
  status: RequestStatus
  reason: string
  createdAt: string
  reviewDueAt: string | null
  decidedAt: string | null
  decidedBy: string | null
}

export interface AuditEvent {
  id: number
  requestId: number
  action: string
  actor: string
  detail: string
  at: string
}

export interface Webhook {
  id: number
  eventType: string
  state: 'PENDING' | 'DELIVERED' | 'FAILED'
  attempts: number
  deliveredAt: string | null
  lastError: string | null
}

export interface History {
  request: WorkRequest
  audit: AuditEvent[]
  webhooks: Webhook[]
}

export interface Me {
  username: string
  roles: string[]
}

export interface NewRequest {
  type: RequestType
  title: string
  amount?: number
  startDate?: string
  endDate?: string
}

export const OPEN_STATUSES: RequestStatus[] = ['PENDING_REVIEW', 'ESCALATED']
