package com.radusquinho.requestdesk.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.radusquinho.requestdesk.domain.RequestStatus;
import com.radusquinho.requestdesk.domain.RequestType;
import com.radusquinho.requestdesk.domain.WorkRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuleEngineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    private final RuleEngine engine = new RuleEngine(new RuleProperties(
            new BigDecimal("100"), new BigDecimal("1000"), 2, 15, new BigDecimal("5000"), Duration.ofHours(48)));

    @ParameterizedTest(name = "expense {0} EUR -> {1}")
    @CsvSource({
            "40, AUTO_APPROVED",
            "100, AUTO_APPROVED",
            "100.01, PENDING_REVIEW",
            "1000, PENDING_REVIEW",
            "1000.01, ESCALATED"
    })
    void expenseThresholds(String amount, RequestStatus expected) {
        Decision decision = engine.decide(money(RequestType.EXPENSE, amount), TODAY, false);
        assertThat(decision.status()).isEqualTo(expected);
        assertThat(decision.reason()).isNotBlank();
    }

    @ParameterizedTest(name = "purchase {0} EUR -> {1}")
    @CsvSource({
            "20, PENDING_REVIEW",
            "5000, PENDING_REVIEW",
            "5000.01, AUTO_REJECTED"
    })
    void purchasesAlwaysNeedAPersonUnlessTooBig(String amount, RequestStatus expected) {
        assertThat(engine.decide(money(RequestType.PURCHASE, amount), TODAY, false).status()).isEqualTo(expected);
    }

    @Test
    void shortVacationWithEnoughNoticeIsAutoApproved() {
        WorkRequest twoDays = vacation(TODAY.plusDays(20), TODAY.plusDays(21));
        assertThat(engine.decide(twoDays, TODAY, false).status()).isEqualTo(RequestStatus.AUTO_APPROVED);
    }

    @Test
    void shortNoticeNeedsAManager() {
        WorkRequest soon = vacation(TODAY.plusDays(3), TODAY.plusDays(3));
        assertThat(engine.decide(soon, TODAY, false).status()).isEqualTo(RequestStatus.PENDING_REVIEW);
    }

    @Test
    void longVacationNeedsAManager() {
        WorkRequest week = vacation(TODAY.plusDays(30), TODAY.plusDays(36));
        assertThat(engine.decide(week, TODAY, false).status()).isEqualTo(RequestStatus.PENDING_REVIEW);
    }

    @Test
    void overlapWithApprovedVacationGoesToReviewEvenIfShort() {
        WorkRequest twoDays = vacation(TODAY.plusDays(20), TODAY.plusDays(21));
        Decision decision = engine.decide(twoDays, TODAY, true);
        assertThat(decision.status()).isEqualTo(RequestStatus.PENDING_REVIEW);
        assertThat(decision.reason()).contains("Overlaps");
    }

    @Test
    void vacationInThePastIsRejected() {
        WorkRequest past = vacation(TODAY.minusDays(1), TODAY.plusDays(1));
        assertThat(engine.decide(past, TODAY, false).status()).isEqualTo(RequestStatus.AUTO_REJECTED);
    }

    private static WorkRequest money(RequestType type, String amount) {
        return new WorkRequest(type, "ana", "test", new BigDecimal(amount), null, null, Instant.EPOCH);
    }

    private static WorkRequest vacation(LocalDate start, LocalDate end) {
        return new WorkRequest(RequestType.VACATION, "ana", "test", null, start, end, Instant.EPOCH);
    }
}
