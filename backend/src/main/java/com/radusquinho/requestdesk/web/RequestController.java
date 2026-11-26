package com.radusquinho.requestdesk.web;

import com.radusquinho.requestdesk.domain.WorkRequest;
import com.radusquinho.requestdesk.service.ForbiddenActionException;
import com.radusquinho.requestdesk.service.RequestService;
import com.radusquinho.requestdesk.web.Dtos.AuditView;
import com.radusquinho.requestdesk.web.Dtos.HistoryView;
import com.radusquinho.requestdesk.web.Dtos.MeView;
import com.radusquinho.requestdesk.web.Dtos.NewRequest;
import com.radusquinho.requestdesk.web.Dtos.RequestView;
import com.radusquinho.requestdesk.web.Dtos.ReviewCommand;
import com.radusquinho.requestdesk.web.Dtos.WebhookView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RequestController {

    private final RequestService service;

    public RequestController(RequestService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public MeView me(Authentication auth) {
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", ""))
                .sorted()
                .toList();
        return new MeView(auth.getName(), roles);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestView submit(@Valid @RequestBody NewRequest body, Authentication auth) {
        WorkRequest created = service.submit(body.type(), auth.getName(), body.title(), body.amount(),
                body.startDate(), body.endDate());
        return RequestView.of(created);
    }

    @GetMapping("/requests/mine")
    public List<RequestView> mine(Authentication auth) {
        return service.mine(auth.getName()).stream().map(RequestView::of).toList();
    }

    @GetMapping("/requests/{id}")
    public HistoryView history(@PathVariable long id, Authentication auth) {
        WorkRequest request = service.find(id);
        if (!request.getRequester().equals(auth.getName()) && !has(auth, "ROLE_MANAGER")) {
            throw new ForbiddenActionException("You can only see your own requests");
        }
        return new HistoryView(RequestView.of(request),
                service.history(id).stream().map(AuditView::of).toList(),
                service.webhooks(id).stream().map(WebhookView::of).toList());
    }

    @GetMapping("/review/queue")
    public List<RequestView> queue(Authentication auth) {
        return service.queue(has(auth, "ROLE_ADMIN")).stream().map(RequestView::of).toList();
    }

    @PostMapping("/review/{id}/approve")
    public RequestView approve(@PathVariable long id, @Valid @RequestBody(required = false) ReviewCommand body,
                               Authentication auth) {
        return RequestView.of(service.review(id, true, auth.getName(), has(auth, "ROLE_ADMIN"), comment(body)));
    }

    @PostMapping("/review/{id}/reject")
    public RequestView reject(@PathVariable long id, @Valid @RequestBody(required = false) ReviewCommand body,
                              Authentication auth) {
        return RequestView.of(service.review(id, false, auth.getName(), has(auth, "ROLE_ADMIN"), comment(body)));
    }

    @GetMapping("/audit")
    public List<AuditView> audit() {
        return service.recentAudit().stream().map(AuditView::of).toList();
    }

    private static String comment(ReviewCommand body) {
        return body == null ? null : body.comment();
    }

    private static boolean has(Authentication auth, String authority) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
    }
}
