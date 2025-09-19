package org.nobilis.nobichat.specification;

import org.nobilis.nobichat.model.Organization;
import org.nobilis.nobichat.model.Supplier;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public class SupplierSpecifications {

    public static Specification<Supplier> byOrganization(Organization organization) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("organization"), organization);
    }

    public static Specification<Supplier> bySearchText(String searchText) {
        if (!StringUtils.hasText(searchText)) {
            return null;
        }
        String pattern = "%" + searchText.toLowerCase() + "%";

        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern);
    }
}