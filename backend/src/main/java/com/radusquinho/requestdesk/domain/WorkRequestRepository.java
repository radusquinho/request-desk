package com.radusquinho.requestdesk.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkRequestRepository extends JpaRepository<WorkRequest, Long> {

    List<WorkRequest> findByRequesterOrderByCreatedAtDesc(String requester);

    List<WorkRequest> findByStatusInOrderByCreatedAtAsc(Collection<RequestStatus> statuses);

    List<WorkRequest> findByStatusAndReviewDueAtBefore(RequestStatus status, Instant cutoff);

    List<WorkRequest> findByTypeAndStatusIn(RequestType type, Collection<RequestStatus> statuses);
}
