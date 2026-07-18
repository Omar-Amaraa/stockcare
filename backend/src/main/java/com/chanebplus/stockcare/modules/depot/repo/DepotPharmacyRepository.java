package com.chanebplus.stockcare.modules.depot.repo;

import com.chanebplus.stockcare.modules.depot.domain.DepotPharmacy;
import com.chanebplus.stockcare.modules.pharmacy.domain.Pharmacy;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepotPharmacyRepository extends JpaRepository<DepotPharmacy, UUID> {

    boolean existsByDepotIdAndPharmacyId(UUID depotId, UUID pharmacyId);

    List<DepotPharmacy> findByDepotId(UUID depotId);

    java.util.Optional<DepotPharmacy> findFirstByPharmacyId(UUID pharmacyId);

    @Query("select dp.pharmacy from DepotPharmacy dp where dp.depot.id = :depotId order by dp.pharmacy.name")
    List<Pharmacy> findPharmaciesByDepotId(@Param("depotId") UUID depotId);

    @Query("select count(dp) from DepotPharmacy dp where dp.depot.id = :depotId")
    long countPharmaciesByDepotId(@Param("depotId") UUID depotId);
}
