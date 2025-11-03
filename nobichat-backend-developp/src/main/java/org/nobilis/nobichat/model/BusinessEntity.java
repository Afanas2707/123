package org.nobilis.nobichat.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
public abstract class BusinessEntity {

    public final static String DEFAULT_USER = "system";

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_date")
    private Instant creationDate;

    @Column(name = "last_updated_by")
    private String lastUpdatedBy;

    @Column(name = "last_updated_date")
    private Instant lastUpdateDate;

    @PrePersist
    public void prePersist() {
        this.creationDate = Instant.now();
        this.lastUpdateDate = this.creationDate;

        if (StringUtils.isEmpty(this.createdBy)) {
            this.createdBy = findCurrentUser();
        }
        this.lastUpdatedBy = this.createdBy;
    }

    @PreUpdate
    public void preUpdate() {
        this.lastUpdateDate = Instant.now();
        this.lastUpdatedBy = findCurrentUser();
    }

    private String findCurrentUser() {
        String user = null;
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            UserDetails userDetails = null;
            if (SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof UserDetails) {
                userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            }
            user = userDetails != null ? userDetails.getUsername() : null;
        }
        return user == null ? DEFAULT_USER : user;
    }
}
