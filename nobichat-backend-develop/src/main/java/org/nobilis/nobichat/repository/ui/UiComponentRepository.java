package org.nobilis.nobichat.repository.ui;

import org.nobilis.nobichat.model.ui.UiComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UiComponentRepository extends JpaRepository<UiComponent, UUID> {
}
