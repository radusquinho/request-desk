package com.radusquinho.requestdesk.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByRequestIdOrderByAtAscIdAsc(Long requestId);

    List<AuditEvent> findTop100ByOrderByAtDescIdDesc();
}
