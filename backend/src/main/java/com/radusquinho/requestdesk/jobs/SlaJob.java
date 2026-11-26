package com.radusquinho.requestdesk.jobs;

import com.radusquinho.requestdesk.service.RequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Escalates pending reviews that passed their SLA. */
@Component
public class SlaJob {

    private static final Logger log = LoggerFactory.getLogger(SlaJob.class);
    private final RequestService service;

    public SlaJob(RequestService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${desk.jobs.sla-check}")
    public void run() {
        int escalated = service.escalateOverdue();
        if (escalated > 0) {
            log.info("SLA job escalated {} request(s)", escalated);
        }
    }
}
