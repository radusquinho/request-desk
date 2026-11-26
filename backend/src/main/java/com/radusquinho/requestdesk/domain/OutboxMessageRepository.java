package com.radusquinho.requestdesk.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findTop20ByStateAndNextAttemptAtLessThanEqualOrderByIdAsc(OutboxMessage.State state, Instant now);

    List<OutboxMessage> findByRequestIdOrderByIdAsc(Long requestId);
}
