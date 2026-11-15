package com.claims.platform.notification.infrastructure;

import com.claims.platform.notification.application.NotificationPorts.NotificationLog;
import com.claims.platform.notification.domain.Audience;
import com.claims.platform.notification.domain.Channel;
import com.claims.platform.notification.domain.Notification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcNotificationLog implements NotificationLog {
    private final JdbcClient jdbc;

    public JdbcNotificationLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Notification n) {
        jdbc.sql("""
                        INSERT INTO notification.notification (id, recipient_id, audience, claim_id, claim_number, event_id,
                            event_type, channel, address, language, subject, body, status, attempts, next_attempt_at, created_at)
                        VALUES (:id, :recipient, :audience, :claim, :number, :event, :type, :channel, :address, :language,
                            :subject, :body, :status, :attempts, :next, :created)
                        ON CONFLICT (event_id, recipient_id, channel) DO NOTHING
                        """)
                .param("id", n.id()).param("recipient", n.recipientId()).param("audience", n.audience().name())
                .param("claim", n.claimId()).param("number", n.claimNumber()).param("event", n.eventId())
                .param("type", n.eventType()).param("channel", n.channel().name()).param("address", n.address())
                .param("language", n.language()).param("subject", n.subject()).param("body", n.body())
                .param("status", n.status().name()).param("attempts", n.attempts()).param("next", ts(n.nextAttemptAt()))
                .param("created", ts(n.createdAt()))
                .update();
    }

    @Override
    public void update(Notification n) {
        jdbc.sql("""
                        UPDATE notification.notification SET status = :status, attempts = :attempts, next_attempt_at = :next,
                            last_error = :error, provider = :provider, provider_message_id = :messageId, sent_at = :sent,
                            read_at = :read, updated_at = now()
                        WHERE id = :id
                        """)
                .param("id", n.id()).param("status", n.status().name()).param("attempts", n.attempts())
                .param("next", ts(n.nextAttemptAt())).param("error", n.lastError()).param("provider", n.provider())
                .param("messageId", n.providerMessageId()).param("sent", ts(n.sentAt())).param("read", ts(n.readAt()))
                .update();
    }

    @Override
    public List<Notification> due(Instant now, int limit) {
        return jdbc.sql("""
                        SELECT * FROM notification.notification WHERE status = 'PENDING' AND next_attempt_at <= :now
                        ORDER BY next_attempt_at, created_at LIMIT :limit FOR UPDATE SKIP LOCKED
                        """)
                .param("now", ts(now)).param("limit", limit).query((rs, n) -> map(rs)).list();
    }

    @Override
    public List<Notification> inbox(String recipientId, boolean unreadOnly, int limit) {
        return jdbc.sql("SELECT * FROM notification.notification WHERE recipient_id = :me AND channel = 'IN_APP' "
                        + "AND status <> 'PENDING'" + (unreadOnly ? " AND read_at IS NULL" : "")
                        + " ORDER BY created_at DESC, id LIMIT :limit")
                .param("me", recipientId).param("limit", limit).query((rs, n) -> map(rs)).list();
    }

    @Override
    public boolean markRead(UUID id, String recipientId, Instant at) {
        return jdbc.sql("""
                        UPDATE notification.notification SET read_at = COALESCE(read_at, :at), updated_at = now()
                        WHERE id = :id AND recipient_id = :me AND channel = 'IN_APP'
                        """)
                .param("id", id).param("me", recipientId).param("at", ts(at)).update() == 1;
    }

    @Override
    public List<Notification> forClaim(UUID claimId) {
        return jdbc.sql("SELECT * FROM notification.notification WHERE claim_id = :claim ORDER BY created_at, recipient_id, channel")
                .param("claim", claimId).query((rs, n) -> map(rs)).list();
    }

    @Override
    public Optional<Notification> byProviderMessage(String provider, String providerMessageId) {
        return jdbc.sql("SELECT * FROM notification.notification WHERE provider = :provider AND provider_message_id = :id")
                .param("provider", provider).param("id", providerMessageId).query((rs, n) -> map(rs)).optional();
    }

    private static OffsetDateTime ts(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Notification map(ResultSet rs) throws SQLException {
        return new Notification(rs.getObject("id", UUID.class), rs.getString("recipient_id"),
                Audience.valueOf(rs.getString("audience")), rs.getObject("claim_id", UUID.class), rs.getString("claim_number"),
                rs.getObject("event_id", UUID.class), rs.getString("event_type"), Channel.valueOf(rs.getString("channel")),
                rs.getString("address"), rs.getString("language"), rs.getString("subject"), rs.getString("body"),
                Notification.Status.valueOf(rs.getString("status")), rs.getInt("attempts"), instant(rs, "next_attempt_at"),
                rs.getString("last_error"), rs.getString("provider"), rs.getString("provider_message_id"),
                instant(rs, "sent_at"), instant(rs, "read_at"), instant(rs, "created_at"));
    }
}
