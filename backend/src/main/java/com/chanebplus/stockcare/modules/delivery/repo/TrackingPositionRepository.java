package com.chanebplus.stockcare.modules.delivery.repo;

import com.chanebplus.stockcare.modules.delivery.domain.TrackingPosition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingPositionRepository extends JpaRepository<TrackingPosition, UUID> {
    List<TrackingPosition> findByDeliveryIdOrderByRecordedAtAsc(UUID deliveryId);
}
