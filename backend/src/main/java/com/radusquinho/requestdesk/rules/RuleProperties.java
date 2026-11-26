package com.radusquinho.requestdesk.rules;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Business thresholds, read from application.yml (desk.rules.*) so they change without code. */
@ConfigurationProperties(prefix = "desk.rules")
public record RuleProperties(
        BigDecimal expenseAutoApproveMax,
        BigDecimal expenseEscalateAbove,
        int vacationAutoApproveMaxDays,
        int vacationMinNoticeDays,
        BigDecimal purchaseRejectAbove,
        Duration reviewSla) {
}
