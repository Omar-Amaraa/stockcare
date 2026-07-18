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
}
