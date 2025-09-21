package org.nobilis.nobichat.specification;

import jakarta.persistence.criteria.Expression;
import org.nobilis.nobichat.constants.ChatMode;
import org.nobilis.nobichat.model.Template;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class TemplateSpecifications {

    public static Specification<Template> hasId(UUID id) {
        return (root, query, criteriaBuilder) ->
                id == null ? criteriaBuilder.conjunction() : criteriaBuilder.equal(root.get("id"), id);
    }

    public static Specification<Template> hasMode(ChatMode mode) {
        return (root, query, criteriaBuilder) ->
                mode == null ? criteriaBuilder.conjunction() : criteriaBuilder.equal(root.get("mode"), mode);
    }

    public static Specification<Template> hasViewId(String viewId) {
        return (root, query, criteriaBuilder) -> {
            if (viewId == null || viewId.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            Expression<String> viewIdPath = criteriaBuilder.function(
                    "jsonb_extract_path_text",
                    String.class,
                    root.get("schema"),
                    criteriaBuilder.literal("view"),
                    criteriaBuilder.literal("id")
            );
            return criteriaBuilder.equal(viewIdPath, viewId);
        };
    }

    public static Specification<Template> hasViewVersion(String viewVersion) {
        return (root, query, criteriaBuilder) -> {
            if (viewVersion == null || viewVersion.isBlank()) {
                return criteriaBuilder.conjunction();
            }

            Expression<String> viewVersionPath = criteriaBuilder.function(
                    "jsonb_extract_path_text",
                    String.class,
                    root.get("schema"),
                    criteriaBuilder.literal("view"),
                    criteriaBuilder.literal("version")
            );

            return criteriaBuilder.equal(viewVersionPath, viewVersion);
        };
    }
}