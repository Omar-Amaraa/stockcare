package com.chanebplus.stockcare.modules.delivery.repo;

import com.chanebplus.stockcare.modules.delivery.domain.Delivery;
import com.chanebplus.stockcare.modules.delivery.domain.DeliveryStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRepository extends JpaRepository<Delivery, UUID> {

    @EntityGraph(attributePaths = {"stops", "stops.pharmacy", "vehicle", "driver"})
    Optional<Delivery> findWithStopsById(UUID id);

    List<Delivery> findByDepotIdOrderByCreatedAtDesc(UUID depotId);

    /** Route proposals of a depot in a given state — e.g. PLANNED = awaiting the depot's decision. */
    List<Delivery> findByDepotIdAndStatus(UUID depotId, DeliveryStatus status);

    List<Delivery> findByStatusIn(List<DeliveryStatus> statuses);

    @Query("""
            select distinct d from Delivery d join d.stops s
            where s.pharmacy.id = :pharmacyId
            order by d.createdAt desc
            """)
    List<Delivery> findByPharmacyStop(@Param("pharmacyId") UUID pharmacyId);

    @Query("select case when count(d) > 0 then true else false end from Delivery d join d.stops s "
            + "where d.id = :deliveryId and s.pharmacy.id = :pharmacyId")
    boolean existsStopForPharmacy(@Param("deliveryId") UUID deliveryId, @Param("pharmacyId") UUID pharmacyId);

    /** Requests already committed to a live delivery at this depot — never re-plan them. */
    @Query("""
            select distinct i.requestId from Delivery d join d.items i
            where d.depot.id = :depotId
              and d.status <> com.chanebplus.stockcare.modules.delivery.domain.DeliveryStatus.CANCELLED
            """)
    List<UUID> findPlannedRequestIds(@Param("depotId") UUID depotId);

    /**
     * Requests carried by route proposals still awaiting the depot's decision (status PLANNED).
     * The real-time re-planning workflow folds these back into the next fleet-wide solve.
     */
    @Query("""
            select distinct i.requestId from Delivery d join d.items i
            where d.depot.id = :depotId
              and d.status = com.chanebplus.stockcare.modules.delivery.domain.DeliveryStatus.PLANNED
            """)
    List<UUID> findProposalRequestIds(@Param("depotId") UUID depotId);
}
