package org.nobilis.nobichat.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.Template;
import org.nobilis.nobichat.repository.ChatMessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FieldPermissionService {

    private final ChatMessageRepository chatMessageRepository;

    /**
     * Извлекает из UI-схемы сессии все разрешенные поля и действия.
     *
     * @param sessionId ID сессии чата.
     * @return Множество строк с именами полей (e.g., "name", "contacts.email") и действий (e.g., "action:delete").
     */
    @Transactional(readOnly = true)
    public Set<String> getPermittedFieldsAndActions(UUID sessionId) {
        JsonNode uiSchema = getUISchemaForSession(sessionId);
        Set<String> permissions = new HashSet<>();
        extractPermissionsRecursively(uiSchema, permissions);
        log.debug("Для сессии {} разрешены следующие права: {}", sessionId, permissions);
        return permissions;
    }

    private JsonNode getUISchemaForSession(UUID sessionId) {
        Template template = null;
        if (template == null || template.getSchema() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "В последнем сообщении сессии отсутствует UI-схема.");
        }
        return template.getSchema();
    }

    /**
     * Рекурсивно обходит uiSchema и извлекает все значения из ключа "field",
     * а также специальные маркеры действий из узлов "action".
     */
    private void extractPermissionsRecursively(JsonNode node, Set<String> permissions) {
        if (node.isObject()) {
            if (node.has("field") && node.get("field").isTextual()) {
                permissions.add(node.get("field").asText());
            }

            if (node.has("action")) {
                JsonNode actionNode = node.get("action");
                String actionType = actionNode.path("type").asText("").toLowerCase();
                String method = actionNode.path("method").asText("").toLowerCase();

                if ("api".equals(actionType)) {
                    if ("delete".equals(method)) {
                        permissions.add("action:delete");
                    }
                    if ("post".equals(method)) {
                        permissions.add("action:create");
                    }
                    if ("patch".equals(method) || "put".equals(method)) {
                        permissions.add("action:update");
                    }
                }
            }

            node.fields().forEachRemaining(entry -> extractPermissionsRecursively(entry.getValue(), permissions));

        } else if (node.isArray()) {
            node.forEach(element -> extractPermissionsRecursively(element, permissions));
        }
    }
}