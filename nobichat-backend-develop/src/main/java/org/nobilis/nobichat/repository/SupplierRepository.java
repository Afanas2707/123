package org.nobilis.nobichat.repository;

import org.nobilis.nobichat.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID>, JpaSpecificationExecutor<Supplier> {
    @Query("SELECT s FROM Supplier s LEFT JOIN FETCH s.contacts WHERE s.id = :id")
    Optional<Supplier> findByIdWithContacts(@Param("id") UUID id);

    @Query("SELECT s FROM Supplier s " +
            "LEFT JOIN FETCH s.organization " +
            "LEFT JOIN FETCH s.contacts " +
            "LEFT JOIN FETCH s.events e LEFT JOIN FETCH e.user " +
            "LEFT JOIN FETCH s.files f LEFT JOIN FETCH f.uploadedByUser " +
            "LEFT JOIN FETCH s.nomenclatures " +
            "LEFT JOIN FETCH s.prices " +
            "LEFT JOIN FETCH s.orders o LEFT JOIN FETCH o.user LEFT JOIN FETCH o.items " +
            "WHERE s.id = :id")
    Optional<Supplier> findByIdWithDetails(@Param("id") UUID id);
}
