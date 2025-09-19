package org.nobilis.nobichat.repository;

import org.nobilis.nobichat.model.SupplierContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SupplierContactRepository extends JpaRepository<SupplierContact, UUID> {
}
