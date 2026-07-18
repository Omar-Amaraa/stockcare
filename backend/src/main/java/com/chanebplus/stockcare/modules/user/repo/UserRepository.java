package com.chanebplus.stockcare.modules.user.repo;

import com.chanebplus.stockcare.modules.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface UserRepository extends JpaRepository<User, UUID> {

    @EntityGraph(attributePaths = {"pharmacy", "depot"})
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    java.util.List<User> findByPharmacyId(UUID pharmacyId);

    java.util.List<User> findByDepotId(UUID depotId);
}
