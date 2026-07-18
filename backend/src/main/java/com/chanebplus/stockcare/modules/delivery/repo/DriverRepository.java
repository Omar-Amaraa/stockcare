package com.chanebplus.stockcare.modules.delivery.repo;

import com.chanebplus.stockcare.modules.delivery.domain.Driver;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<Driver, UUID> {
    List<Driver> findByDepotIdAndActiveTrue(UUID depotId);
    List<Driver> findByDepotId(UUID depotId);
}
