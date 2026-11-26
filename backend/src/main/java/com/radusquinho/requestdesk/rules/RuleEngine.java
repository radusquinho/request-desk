package com.radusquinho.requestdesk.rules;

import com.radusquinho.requestdesk.domain.RequestStatus;
import com.radusquinho.requestdesk.domain.WorkRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * Pure decision logic: no database, no clock, no Spring calls. Everything it needs is
 * passed in, which keeps it easy to unit-test and to port to the browser demo.
 */
@Component
public class RuleEngine {

    private final RuleProperties rules;

    public RuleEngine(RuleProperties rules) {
        this.rules = rules;
    }

    public Decision decide(WorkRequest request, LocalDate today, boolean overlapsApprovedVacation) {
        return switch (request.getType()) {
            case EXPENSE -> decideExpense(request.getAmount());
            case PURCHASE -> decidePurchase(request.getAmount());
            case VACATION -> decideVacation(request, today, overlapsApprovedVacation);
        };
    }

    private Decision decideExpense(BigDecimal amount) {
        if (amount.compareTo(rules.expenseAutoApproveMax()) <= 0) {
            return new Decision(RequestStatus.AUTO_APPROVED,
                    "Expense of " + eur(amount) + " is within the " + eur(rules.expenseAutoApproveMax()) + " auto-approval limit");
        }
        if (amount.compareTo(rules.expenseEscalateAbove()) > 0) {
            return new Decision(RequestStatus.ESCALATED,
                    "Expense of " + eur(amount) + " is above " + eur(rules.expenseEscalateAbove()) + ": needs an admin");
        }
        return new Decision(RequestStatus.PENDING_REVIEW, "Expense of " + eur(amount) + " needs a manager");
    }

    private Decision decidePurchase(BigDecimal amount) {
        if (amount.compareTo(rules.purchaseRejectAbove()) > 0) {
            return new Decision(RequestStatus.AUTO_REJECTED,
                    "Purchases above " + eur(rules.purchaseRejectAbove()) + " go through procurement, not this desk");
        }
        return new Decision(RequestStatus.PENDING_REVIEW, "Every purchase needs a manager");
    }

    private Decision decideVacation(WorkRequest request, LocalDate today, boolean overlapsApprovedVacation) {
        if (request.getStartDate().isBefore(today)) {
            return new Decision(RequestStatus.AUTO_REJECTED, "Vacation cannot start in the past");
        }
        if (overlapsApprovedVacation) {
            return new Decision(RequestStatus.PENDING_REVIEW,
                    "Overlaps another approved vacation: a manager checks team coverage");
        }
        long days = request.days();
        long notice = ChronoUnit.DAYS.between(today, request.getStartDate());
        if (days <= rules.vacationAutoApproveMaxDays() && notice >= rules.vacationMinNoticeDays()) {
            return new Decision(RequestStatus.AUTO_APPROVED,
                    days + " day(s) with " + notice + " days' notice: within the auto-approval policy");
        }
        return new Decision(RequestStatus.PENDING_REVIEW,
                days + " day(s) with " + notice + " days' notice: needs a manager");
    }

    private static String eur(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString() + " EUR";
    }
}
