// Browser port of backend/src/main/java/.../rules/RuleEngine.java, used only by the demo mode.
// Same thresholds as application.yml. If you change one, change the other.
import type { RequestStatus, WorkRequest } from './types'

export const RULES = {
  expenseAutoApproveMax: 100,
  expenseEscalateAbove: 1000,
  vacationAutoApproveMaxDays: 2,
  vacationMinNoticeDays: 15,
  purchaseRejectAbove: 5000,
  reviewSlaHours: 48,
}

export interface Decision {
  status: RequestStatus
  reason: string
}

const DAY = 24 * 60 * 60 * 1000
const eur = (n: number) => `${n} EUR`

export function daysBetween(fromIso: string, toIso: string): number {
  return Math.round((Date.parse(toIso) - Date.parse(fromIso)) / DAY)
}

export function overlaps(a: WorkRequest, b: WorkRequest): boolean {
  if (!a.startDate || !a.endDate || !b.startDate || !b.endDate) return false
  return a.startDate <= b.endDate && a.endDate >= b.startDate
}

export function decide(req: WorkRequest, todayIso: string, overlapsApprovedVacation: boolean): Decision {
  const r = RULES
  if (req.type === 'EXPENSE') {
    const amount = req.amount ?? 0
    if (amount <= r.expenseAutoApproveMax)
      return { status: 'AUTO_APPROVED', reason: `Expense of ${eur(amount)} is within the ${eur(r.expenseAutoApproveMax)} auto-approval limit` }
    if (amount > r.expenseEscalateAbove)
      return { status: 'ESCALATED', reason: `Expense of ${eur(amount)} is above ${eur(r.expenseEscalateAbove)}: needs an admin` }
    return { status: 'PENDING_REVIEW', reason: `Expense of ${eur(amount)} needs a manager` }
  }
  if (req.type === 'PURCHASE') {
    const amount = req.amount ?? 0
    if (amount > r.purchaseRejectAbove)
      return { status: 'AUTO_REJECTED', reason: `Purchases above ${eur(r.purchaseRejectAbove)} go through procurement, not this desk` }
    return { status: 'PENDING_REVIEW', reason: 'Every purchase needs a manager' }
  }
  const start = req.startDate!
  if (start < todayIso) return { status: 'AUTO_REJECTED', reason: 'Vacation cannot start in the past' }
  if (overlapsApprovedVacation)
    return { status: 'PENDING_REVIEW', reason: 'Overlaps another approved vacation: a manager checks team coverage' }
  const days = daysBetween(start, req.endDate!) + 1
  const notice = daysBetween(todayIso, start)
  if (days <= r.vacationAutoApproveMaxDays && notice >= r.vacationMinNoticeDays)
    return { status: 'AUTO_APPROVED', reason: `${days} day(s) with ${notice} days' notice: within the auto-approval policy` }
  return { status: 'PENDING_REVIEW', reason: `${days} day(s) with ${notice} days' notice: needs a manager` }
}
