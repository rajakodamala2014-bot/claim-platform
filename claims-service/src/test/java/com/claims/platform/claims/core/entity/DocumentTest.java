package com.claims.platform.claims.core.entity;

import com.claims.platform.claims.core.exception.DomainException;
import com.claims.platform.claims.core.exception.ErrorCode;
import com.claims.platform.claims.core.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTest {
    private static final Instant NOW = Instant.parse("2026-11-14T02:00:00Z");

    @Test
    void requestedUploadIsValidatedAndFileNameSanitised() {
        Document doc = Document.requested(UUID.randomUUID(), "cust-priya", "../../etc/passwd.pdf", "application/pdf", 10, NOW);

        assertThat(doc.fileName()).doesNotContain("/").isEqualTo(".._.._etc_passwd.pdf");
        assertThat(doc.status()).isEqualTo(Document.Status.PENDING_UPLOAD);
        assertThatThrownBy(() -> Document.requested(UUID.randomUUID(), "c", "a.exe", "application/x-msdownload", 10, NOW))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Document.requested(UUID.randomUUID(), "c", "a.jpg", "image/jpeg", Document.MAX_SIZE_BYTES + 1, NOW))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void completedOnlyOnceAndNeverLargerThanAnnounced() {
        Document doc = Document.requested(UUID.randomUUID(), "c", "a.jpg", "image/jpeg", 100, NOW);

        assertThatThrownBy(() -> doc.uploaded(101, NOW)).extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.UPLOAD_REJECTED);
        Document done = doc.uploaded(80, NOW);
        assertThat(done.sizeBytes()).isEqualTo(80);
        assertThatThrownBy(() -> done.uploaded(80, NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    void attachRequiresOwnerCompletionAndNoOtherClaim() {
        Document pending = Document.requested(UUID.randomUUID(), "c", "a.jpg", "image/jpeg", 10, NOW);
        Document done = pending.uploaded(10, NOW);
        ClaimId claim = ClaimId.newId();

        assertThatThrownBy(() -> pending.attachTo(claim, "c")).extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.UPLOAD_REJECTED);
        assertThatThrownBy(() -> done.attachTo(claim, "someone-else")).extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.UPLOAD_NOT_FOUND);
        Document attached = done.attachTo(claim, "c");
        assertThat(attached.attachTo(claim, "c")).isEqualTo(attached);
        assertThatThrownBy(() -> attached.attachTo(ClaimId.newId(), "c")).isInstanceOf(DomainException.class);
    }
}
