package com.radusquinho.requestdesk.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookSignatureTest {

    @Test
    void matchesTheSignatureAPythonReceiverComputes() {
        // Reference value from Python: hmac.new(b"test-secret", b'{"event":"X"}', hashlib.sha256).hexdigest()
        assertThat(WebhookSignature.sign("{\"event\":\"X\"}", "test-secret"))
                .isEqualTo("sha256=0cada349ba798ae756c634949e0bc75c7709ecf558fa82647e23183c8d22d410");
    }
}
