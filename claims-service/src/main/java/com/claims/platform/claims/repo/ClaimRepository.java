package com.claims.platform.claims.repo;

import com.claims.platform.claims.domain.entity.Claim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.id = :id")
    Optional<Claim> lockById(@Param("id") UUID id);

    @Query("""
            select c from Claim c
            where c.marketCode = :market
              and (:status is null or c.status = :status)
              and (:lob is null or c.lineOfBusiness = :lob)
            """)
    Page<Claim> queue(@Param("market") String market, @Param("status") String status,
                      @Param("lob") String lob, Pageable pageable);

    @Query("""
            select c from Claim c
            where (:claimantId is null or c.claimantId = :claimantId)
              and (:assigneeId is null or c.assignedTo = :assigneeId)
              and (:status is null or c.status = :status)
              and (:openOnly = false or c.status <> 'CLOSED')
            """)
    Page<Claim> search(@Param("claimantId") String claimantId, @Param("assigneeId") String assigneeId,
                       @Param("status") String status, @Param("openOnly") boolean openOnly, Pageable pageable);
}
