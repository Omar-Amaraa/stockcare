package com.chanebplus.stockcare.modules.delivery.repo;

import com.chanebplus.stockcare.modules.delivery.domain.Vehicle;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    List<Vehicle> findByDepotIdAndActiveTrue(UUID depotId);
    List<Vehicle> findByDepotId(UUID depotId);
}
