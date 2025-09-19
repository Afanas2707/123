package org.nobilis.nobichat.repository;

import org.nobilis.nobichat.model.SupplierFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SupplierFileRepository extends JpaRepository<SupplierFile, UUID> {
}
