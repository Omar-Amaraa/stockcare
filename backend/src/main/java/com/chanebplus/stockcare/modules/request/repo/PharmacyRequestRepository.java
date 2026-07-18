package com.chanebplus.stockcare.modules.request.repo;

import com.chanebplus.stockcare.modules.request.domain.PharmacyRequest;
import com.chanebplus.stockcare.modules.request.domain.RequestStatus;
import com.chanebplus.stockcare.modules.request.domain.Urgency;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PharmacyRequestRepository extends JpaRepository<PharmacyRequest, UUID> {

    @EntityGraph(attributePaths = {"items", "items.medication", "pharmacy"})
    Optional<PharmacyRequest> findWithItemsById(UUID id);

    Page<PharmacyRequest> findByPharmacyId(UUID pharmacyId, Pageable pageable);

    @Query("""
            select r from PharmacyRequest r
            where r.depot.id = :depotId
              and (:status is null or r.status = :status)
              and (:urgency is null or r.urgency = :urgency)
              and (:pharmacyId is null or r.pharmacy.id = :pharmacyId)
            """)
    Page<PharmacyRequest> findForDepot(@Param("depotId") UUID depotId,
                                       @Param("status") RequestStatus status,
                                       @Param("urgency") Urgency urgency,
                                       @Param("pharmacyId") UUID pharmacyId,
                                       Pageable pageable);

    long countByDepotIdAndStatus(UUID depotId, RequestStatus status);

    @Query("""
            select case when count(r) > 0 then true else false end
            from PharmacyRequest r join r.items it
            where r.pharmacy.id = :pharmacyId and it.medication.id = :medicationId
              and r.status in :openStatuses
            """)
    boolean existsOpenForMedication(@Param("pharmacyId") UUID pharmacyId,
                                    @Param("medicationId") UUID medicationId,
                                    @Param("openStatuses") java.util.Collection<RequestStatus> openStatuses);
}
