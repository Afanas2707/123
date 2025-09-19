package org.nobilis.nobichat.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.nobilis.nobichat.constants.EntityType;
import org.nobilis.nobichat.dto.supplier.SupplierListDto;
import org.nobilis.nobichat.model.Supplier;
import org.nobilis.nobichat.model.SupplierContact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface SupplierMapper {

    @Mapping(target = "primaryContactName", source = "contacts", qualifiedByName = "toPrimaryContactName")
    @Mapping(target = "logoUrl", source = "supplier.id", qualifiedByName = "getSupplierLogoUrl")
    @Mapping(target = "contactAvatarUrl", source = "supplier.contacts", qualifiedByName = "getPrimaryContactAvatarUrl")
    SupplierListDto toSupplierListDto(Supplier supplier);

    @Named("toPrimaryContactName")
    default String toPrimaryContactName(List<SupplierContact> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return null;
        }
        return findPrimaryContact(contacts)
                .map(SupplierContact::getFullName)
                .orElse(null);
    }

    @Named("getSupplierLogoUrl")
    default String getSupplierLogoUrl(UUID supplierId) {
        if (supplierId == null) {
            return null;
        }
        return String.format("/images/%s/%s", EntityType.supplier, supplierId);
    }

    @Named("getPrimaryContactAvatarUrl")
    default String getPrimaryContactAvatarUrl(List<SupplierContact> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return null;
        }
        return findPrimaryContact(contacts)
                .map(SupplierContact::getId)
                .map(contactId ->
                        String.format("/images/%s/%s", EntityType.supplierContact, contactId))
                .orElse(null);
    }

    private Optional<SupplierContact> findPrimaryContact(List<SupplierContact> contacts) {
        return contacts.stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsPrimary()))
                .findFirst();
    }
}