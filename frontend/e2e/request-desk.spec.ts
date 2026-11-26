import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

const isoInDays = (days: number) => {
  const d = new Date()
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

async function submitExpense(page: Page, title: string, amount: string) {
  await page.getByTestId('type').selectOption('EXPENSE')
  await page.getByTestId('title').fill(title)
  await page.getByTestId('amount').fill(amount)
  await page.getByTestId('submit').click()
}

test.beforeEach(async ({ page }) => {
  await page.goto('/')
  await page.evaluate(() => localStorage.clear())
  await page.reload()
  await expect(page.getByTestId('demo-banner')).toBeVisible()
})

test('a small expense is approved by the rules and audited', async ({ page }) => {
  await submitExpense(page, 'Taxi to client', '40')

  await expect(page.getByTestId('notice')).toContainText('Auto-approved')
  await expect(page.getByTestId('timeline')).toContainText('SUBMITTED')
  await expect(page.getByTestId('timeline')).toContainText('RULE_AUTO_APPROVED')
})

test('a manager approves a pending expense from the queue', async ({ page }) => {
  await submitExpense(page, 'Conference ticket', '450')
  await expect(page.getByTestId('reason')).toHaveText('Expense of 450 EUR needs a manager')

  await page.getByTestId('user').selectOption('marta')
  await page.getByTestId('tab-review').click()
  await page.getByTestId('approve-1').click()

  await expect(page.getByTestId('notice')).toHaveText('#1 approved')
  await expect(page.getByTestId('tab-review')).toHaveText('Review queue (0)')
})

test('nobody can approve their own request', async ({ page }) => {
  await page.getByTestId('user').selectOption('marta')
  await submitExpense(page, 'Monitor', '300')

  await page.getByTestId('tab-review').click()
  await page.getByTestId('approve-1').click()

  await expect(page.getByTestId('notice')).toHaveText('You cannot review your own request')
})

test('an overdue review is escalated and only an admin can decide it', async ({ page }) => {
  await submitExpense(page, 'Laptop repair', '450')
  await page.getByTestId('advance').click()
  await expect(page.getByTestId('notice')).toContainText('escalated 1 request')

  await page.getByTestId('user').selectOption('marta')
  await expect(page.getByTestId('tab-review')).toHaveText('Review queue (0)')

  await page.getByTestId('user').selectOption('admin')
  await expect(page.getByTestId('tab-review')).toHaveText('Review queue (1)')
  await page.getByTestId('tab-review').click()
  await page.getByTestId('approve-1').click()

  await page.getByTestId('tab-audit').click()
  await expect(page.getByTestId('audit-table')).toContainText('SLA_ESCALATED')
  await expect(page.getByTestId('audit-table')).toContainText('Approved by admin')
})

test('a short vacation with enough notice is approved, an overlapping one is not', async ({ page }) => {
  await page.getByTestId('user').selectOption('luis')
  await page.getByTestId('type').selectOption('VACATION')
  await page.getByTestId('title').fill('Long weekend')
  await page.getByTestId('start').fill(isoInDays(30))
  await page.getByTestId('end').fill(isoInDays(31))
  await page.getByTestId('submit').click()
  await expect(page.getByTestId('notice')).toContainText('Auto-approved')

  await page.getByTestId('user').selectOption('ana')
  await page.getByTestId('tab-new').click()
  await page.getByTestId('type').selectOption('VACATION')
  await page.getByTestId('title').fill('Same days')
  await page.getByTestId('start').fill(isoInDays(31))
  await page.getByTestId('end').fill(isoInDays(32))
  await page.getByTestId('submit').click()
  await expect(page.getByTestId('reason')).toContainText('Overlaps another approved vacation')
})
