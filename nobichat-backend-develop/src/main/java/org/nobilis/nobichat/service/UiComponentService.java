package org.nobilis.nobichat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.model.ui.UiComponent;
import org.nobilis.nobichat.repository.ui.UiComponentRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UiComponentService {

    private final UiComponentRepository uiComponentRepository;
    private final ObjectMapper objectMapper;

    public Optional<UiComponent> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return uiComponentRepository.findById(id);
    }

    public Optional<JsonNode> getConfig(UUID componentId) {
        return findById(componentId)
                .map(UiComponent::getConfig);
    }

    public <T> Optional<T> readConfig(UUID componentId, Class<T> type) {
        return getConfig(componentId)
                .map(node -> objectMapper.convertValue(node, type));
    }
}
