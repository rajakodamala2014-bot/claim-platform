package com.claims.platform.claims.platform.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Lease-based lock in platform.job_lock so a scheduled job runs on one instance at a time. A lease expires on
 * its own if the holder dies, so a crashed instance never blocks a job forever.
 */
@Component
public class JobLock {
    private static final Logger log = LoggerFactory.getLogger(JobLock.class);
    private final JdbcClient jdbc;
    private final String instance = ManagementFactory.getRuntimeMXBean().getName();

    public JobLock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Runs {@code job} if this instance gets the lease for {@code name}; otherwise skips. */
    public <T> void runExclusively(String name, Duration lease, Supplier<T> job) {
        if (!tryAcquire(name, lease)) {
            log.debug("Job {} is running elsewhere; skipped", name);
            return;
        }
        try {
            T result = job.get();
            log.info("Job {} finished: {}", name, result);
        } catch (RuntimeException ex) {
            log.error("Job {} failed", name, ex);
        } finally {
            release(name);
        }
    }

    boolean tryAcquire(String name, Duration lease) {
        return jdbc.sql("""
                        INSERT INTO platform.job_lock (name, locked_until, locked_by)
                        VALUES (:name, now() + make_interval(secs => :seconds), :by)
                        ON CONFLICT (name) DO UPDATE SET locked_until = EXCLUDED.locked_until,
                            locked_by = EXCLUDED.locked_by, updated_at = now()
                        WHERE platform.job_lock.locked_until < now()
                        """)
                .param("name", name).param("seconds", (double) lease.toSeconds()).param("by", instance)
                .update() == 1;
    }

    void release(String name) {
        jdbc.sql("UPDATE platform.job_lock SET locked_until = now(), updated_at = now() WHERE name = :name AND locked_by = :by")
                .param("name", name).param("by", instance).update();
    }
}
