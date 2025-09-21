package org.nobilis.nobichat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.llm.LLMResponseDto;
import org.nobilis.nobichat.dto.ontology.EntityMetaData;
import org.nobilis.nobichat.dto.ontology.OntologyDto;
import org.nobilis.nobichat.model.ChatMessage; // Для события LLMCallEvent
import org.nobilis.nobichat.events.event.LlmCallEvent; // Импорт события
import org.springframework.context.ApplicationEventPublisher; // Импорт ApplicationEventPublisher
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Сервис, отвечающий за формирование промптов для LLM
 * и базовую обработку (очистку) её ответов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmPromptService {

    private final ObjectMapper objectMapper;
    private final OntologyService ontologyService;
    private final LLMService llmService; // Добавляем LLMService для выполнения запросов
    private final ApplicationEventPublisher eventPublisher; // Добавляем для публикации событий
    private final UiComponentService uiComponentService;

    /**
     * Формирует промпт для LLM для извлечения намерения, сущности и Query-структуры.
     *
     * @param userQuery Запрос пользователя.
     * @return Сформированный промпт.
     */
    public String buildQueryExtractionPrompt(String userQuery) {
        List<OntologyDto.EntitySchema.FieldSchema> allFields = ontologyService.getAllQueryableFields();

        try {
            String promptTemplate = """
                    [ROLE]
                    Ты — продвинутый AI-ассистент для CRM-системы. Твоя задача — проанализировать запрос пользователя и преобразовать его в структурированный JSON-объект.

                    [CONTEXT]
                    1. Возможные типы намерений (INTENT_TYPE):
                       - `GENERATE_LIST_VIEW`: если пользователь хочет увидеть список сущностей или открыть форму для создания новой (без файла или URL).
                       - `CREATE_ENTITY_FROM_DOCUMENT`: если пользователь хочет создать сущность ИЗ ФАЙЛА или ДОКУМЕНТА, но НЕ указывает URL.
                       - `CREATE_ENTITY_FROM_URL`: если пользователь просит создать сущность ИЗ ССЫЛКИ или URL, но НЕ упоминает файл/документ.
                       - `CREATE_ENTITY_FROM_HYBRID_SOURCE`: если пользователь хочет создать сущность, используя И ФАЙЛ, И ССЫЛКУ одновременно.
                       - `GET_INN_REPORT`: если пользователь просит найти информацию, отчет, выписку или проверить компанию по ИНН. **Также используй этот интент для общих запросов типа "покажи детали", "подробнее", "дай сводку", когда пользователь уже находится на карточке какой-либо сущности.**
                       - `OPEN_NTH_ITEM_FROM_LIST`: Если пользователь просит открыть, показать или развернуть конкретный элемент по его номеру в текущем списке или для указанной сущности. (Например: 'Открой клиента 3', 'Покажи 5-й заказ', 'Разверни 1-й')
                       - `UNKNOWN`: если намерение неясно или не поддерживается.

                    2. Возможные сущности (ENTITY_NAME) и их синонимы:
                       %s

                    3. Для каждой сущности доступны следующие ПОЛЯ, их ТИПЫ и СИНОНИМЫ. Используй их для точного маппинга фильтров.
                       ```json
                       %s
                       ```

                    4. Доступные ОПЕРАТОРЫ для фильтрации: `contains`, `not_equals`, `greater_than`, `less_than`.
                    5. ЛОГИЧЕСКИЕ ОПЕРАТОРЫ для объединения условий: `AND`, `OR`.

                    [OUTPUT FORMAT]
                    Твой ответ — это ВСЕГДА валидный JSON-объект. Не добавляй никаких пояснений.
                    ```json
                    {
                      "intent": "INTENT_TYPE",
                      "entity": "ENTITY_NAME",
                      "query": {
                        "operator": "AND",
                        "conditions": [ { "field": "fieldName", "operator": "operatorType", "value": "fieldValue" } ],
                        "groups": []
                      },
                      "sourceUrl": "URL_ИЗ_ЗАПРОСА",
                      "inn": "ИНН_ИЗ_ЗАПРОСА",
                      "itemIndex": НОМЕР_ЭЛЕМЕНТА // Целое число, если применимо для OPEN_NTH_ITEM_FROM_LIST. Иначе null.
                    }
                    ```
                    - `query`: `null` для всех интентов, кроме `GENERATE_LIST_VIEW`.
                    - `sourceUrl`: заполняется только для `CREATE_ENTITY_FROM_URL` и `CREATE_ENTITY_FROM_HYBRID_SOURCE`. В остальных случаях `null`.
                    - `inn`: заполняется только для `GET_INN_REPORT`, если ИНН ЯВНО указан в запросе. В остальных случаях `null`.
                    - `itemIndex`: заполняется только для `OPEN_NTH_ITEM_FROM_LIST`. В остальных случаях `null`.

                    [EXAMPLES]
                    1. Запрос: "Открой поставщиков у которых ИНН или КПП содержат '77'"
                       Ответ:
                       ```json
                       { "intent": "GENERATE_LIST_VIEW", "entity": "supplier", "query": {"groups": [{"operator": "OR", "conditions": [{"field": "inn", "operator": "contains", "value": "77"}, {"field": "kpp", "operator": "contains", "value": "77"}]}]}, "sourceUrl": null, "inn": null, "itemIndex": null }
                       ```
                    2. Запрос: "Создай нового поставщика на основе файла"
                       Ответ:
                       ```json
                       { "intent": "CREATE_ENTITY_FROM_DOCUMENT", "entity": "supplier", "query": null, "sourceUrl": null, "inn": null, "itemIndex": null }
                       ```
                    3. Запрос: "Загрузи контрагента по документу"
                       Ответ:
                       ```json
                       { "intent": "CREATE_ENTITY_FROM_DOCUMENT", "entity": "supplier", "query": null, "sourceUrl": null, "inn": null, "itemIndex": null }
                       ```
                    4. Запрос: "Создай нового поставщика с сайта: https://www.example.com/supplier-info"
                       Ответ:
                       ```json
                       { "intent": "CREATE_ENTITY_FROM_URL", "entity": "supplier", "query": null, "sourceUrl": "https://www.example.com/supplier-info", "inn": null, "itemIndex": null }
                       ```
                    5. Запрос: "Используй эту ссылку для создания пользователя: http://another-example.org/user-data"
                       Ответ:
                       ```json
                       { "intent": "CREATE_ENTITY_FROM_URL", "entity": "user", "query": null, "sourceUrl": "http://another-example.org/user-data", "inn": null, "itemIndex": null }
                       ```
                    6. Запрос: "Создай нового поставщика по этому документу и информации с сайта https://company.com/about"
                       Ответ:
                       ```json
                       { "intent": "CREATE_ENTITY_FROM_HYBRID_SOURCE", "entity": "supplier", "query": null, "sourceUrl": "https://company.com/about", "inn": null, "itemIndex": null }
                       ```
                    7. Запрос: "Вот реквизиты во вложении, а вот их официальный сайт: http://official-site.ru. Сделай карточку контрагента."
                       Ответ:
                       ```json
                       { "intent": "CREATE_ENTITY_FROM_HYBRID_SOURCE", "entity": "supplier", "query": null, "sourceUrl": "http://official-site.ru", "inn": null, "itemIndex": null }
                       ```
                    8. Запрос: "Дай мне отчет по компании с ИНН 7736207543"
                       Ответ:
                       ```json
                       { "intent": "GET_INN_REPORT", "entity": null, "query": null, "sourceUrl": null, "inn": "7736207543", "itemIndex": null }
                       ```
                    9. Запрос: "Проверь контрагента 123456789012"
                       Ответ:
                       ```json
                       { "intent": "GET_INN_REPORT", "entity": "supplier", "query": null, "sourceUrl": null, "inn": "123456789012", "itemIndex": null }
                       ```
                    10. Запрос: "Создай нового поставщика"
                        Ответ:
                        ```json
                        { "intent": "CREATE_ENTITY_FROM_DOCUMENT", "entity": "supplier", "query": null, "sourceUrl": null, "inn": null, "itemIndex": null }
                        ```
                    11. Запрос: "Покажи всех контрагентов"
                        Ответ:
                        ```json
                        { "intent": "GENERATE_LIST_VIEW", "entity": "supplier", "query": null, "sourceUrl": null, "inn": null, "itemIndex": null }
                        ```
                    12. Запрос: "Сколько времени?"
                        Ответ:
                        ```json
                        { "intent": "UNKNOWN", "entity": null, "query": null, "sourceUrl": null, "inn": null, "itemIndex": null }
                        ```
                    13. Запрос: "Добавить поставщика"
                        Ответ:
                        ```json
                        { "intent": "CREATE_ENTITY_FROM_DOCUMENT", "entity": "supplier", "query": null, "sourceUrl": null, "inn": null, "itemIndex": null }
                        ```
                    14. Запрос: "Открой поставщиков у которых директор Иванова Татьяна Александровна или кпп 770704001"
                        Ответ:
                        ```json
                        { "intent": "GENERATE_LIST_VIEW", "entity": "supplier", "query": {"groups": [{"operator": "OR", "conditions": [{"field": "directorName", "operator": "contains", "value": "Иванова Татьяна Александровна"}, {"field": "kpp", "operator": "contains", "value": "770704001"}]}]}, "sourceUrl": null, "inn": null, "itemIndex": null }
                        ```
                    15. Запрос: "Покажи список заказов с суммой больше 1000 и статусом 'выполнен'"
                        Ответ:
                        ```json
                        { "intent": "GENERATE_LIST_VIEW", "entity": "order", "query": {"groups": [{"operator": "AND", "conditions": [{"field": "totalAmount", "operator": "greater_than", "value": 1000}, {"field": "status", "operator": "equals", "value": "выполнен"}]}]}, "sourceUrl": null, "inn": null, "itemIndex": null }
                        ```
                    16. Запрос: "Дай детали"
                        Ответ:
                        ```json
                        { "intent": "GET_INN_REPORT", "entity": null, "query": null, "sourceUrl": null, "inn": null, "itemIndex": null }
                        ```
                    17. Запрос: "Отобрази детали"
                        Ответ:
                        ```json
                        { "intent": "GET_INN_REPORT", "entity": null, "query": null, "sourceUrl": null, "inn": null, "itemIndex": null }
                        ```
                    18. Запрос: "Покажи подробную информацию"
                        Ответ:
                        ```json
                        { "intent": "GET_INN_REPORT", "entity": null, "query": null, "sourceUrl": null, "inn": null, "itemIndex": null }
                        ```
                    19. Запрос: "Нужна сводка по этой компании"
                        Ответ:
                        ```json
                        { "intent": "GET_INN_REPORT", "entity": null, "query": null, "sourceUrl": null, "inn": null, "itemIndex": null }
                        ```
                    20. Запрос: "Проверить"
                        Ответ:
                        ```json
                        { "intent": "GET_INN_REPORT", "entity": null, "query": null, "sourceUrl": null, "inn": null, "itemIndex": null }
                        ```
                    22. Запрос: "Открой клиента 3"
                        Ответ:
                        ```json
                        { "intent": "OPEN_NTH_ITEM_FROM_LIST", "entity": "customer", "query": null, "sourceUrl": null, "inn": null, "itemIndex": 3 }
                        ```
                    23. Запрос: "Покажи 5-й заказ"
                        Ответ:
                        ```json
                        { "intent": "OPEN_NTH_ITEM_FROM_LIST", "entity": "order", "query": null, "sourceUrl": null, "inn": null, "itemIndex": 5 }
                        ```
                    24. Запрос: "Разверни 1-й"
                        Ответ:
                        ```json
                        { "intent": "OPEN_NTH_ITEM_FROM_LIST", "entity": null, "query": null, "sourceUrl": null, "inn": null, "itemIndex": 1 }
                        ```
                    25. Запрос: "Выведи поставщика 2"
                        Ответ:
                        ```json
                        { "intent": "OPEN_NTH_ITEM_FROM_LIST", "entity": "supplier", "query": null, "sourceUrl": null, "inn": null, "itemIndex": 2 }
                        ```

                    [USER_QUERY]
                    %s

                    [RESPONSE]
                    """;

            String entitySynonymsContext = ontologyService.getAllEntityMetaForPrompt().entrySet().stream()
                    .map(entry -> {
                        String entityName = entry.getKey();
                        EntityMetaData meta = entry.getValue();
                        String synonyms = meta.getSynonyms() != null && !meta.getSynonyms().isEmpty() ?
                                " (синонимы: " + String.join(", ", meta.getSynonyms()) + ")" : "";
                        return String.format("- `%s`: %s%s", entityName, meta.getUserFriendlyName(), synonyms);
                    })
                    .collect(Collectors.joining("\n"));

            List<Map<String, Object>> simplifiedFields = allFields.stream()
                    .map(f -> {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("name", f.getName());
                        map.put("type", f.getType());
                        map.put("description", f.getDescription());
                        map.put("userFriendlyName", f.getUserFriendlyName());
                        map.put("synonyms", f.getSynonyms() != null ? f.getSynonyms() : Collections.emptyList());
                        return map;
                    })
                    .collect(Collectors.toList());
            String ontologyFieldsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(simplifiedFields);

            return String.format(promptTemplate, entitySynonymsContext, ontologyFieldsJson, userQuery);

        } catch (Exception e) {
            log.error("Ошибка при формировании промпта для извлечения Query: {}", e.getMessage(), e);
            throw new RuntimeException("Ошибка при формировании промпта для извлечения Query.", e);
        }
    }

    /**
     * Формирует промпт для LLM для выбора полей, которые нужно отобразить в списке (мягкий режим).
     *
     * @param entityName Имя сущности.
     * @param fields     Список всех доступных полей сущности.
     * @param userQuery  Исходный запрос пользователя.
     * @return Сформированный промпт.
     */
    public String buildFieldNameSelectionPrompt(String entityName, List<OntologyDto.EntitySchema.FieldSchema> fields, String userQuery) {
        try {
            String promptTemplate = """
                    [ROLE]
                    Ты — AI-классификатор полей. Твоя задача — проанализировать запрос пользователя и на основе контекста вернуть JSON-массив с техническими именами (`name`) полей.

                    [CONTEXT]
                    Для сущности '%s' доступны следующие поля. Каждое поле имеет:
                    - `name`: техническое имя (использовать в ответе).
                    - `description`: описание поля, помогает понять его суть.
                    - `userFriendlyName`: имя для пользователя.
                    - `synonyms`: другие названия для поля.
                    - `isDefaultInList`: флаг, указывающий, должно ли поле отображаться по умолчанию в списках.
                    - `isMandatoryInList`: флаг, указывающий, является ли поле обязательным для отображения в списках.
                    ```json
                    %s
                    ```

                    [SELECTION LOGIC]
                    Ты должен строго следовать этой логике для выбора полей из [CONTEXT]:
                    1.  **Определи тип запроса:**
                        *   **Конкретный:** Если [USER_QUERY] содержит названия полей (например, "покажи ИНН", "список контрагентов с их кодами").
                        *   **Общий:** Если [USER_QUERY] не содержит названий полей (например, "покажи список поставщиков", "дай всех").

                    2.  **Выполни действия в зависимости от типа запроса:**
                        *   **ЕСЛИ запрос Общий:**
                            -   Выбери ИСКЛЮЧИТЕЛЬНО те объекты, у которых свойство `"isDefaultInList": true`.
                        *   **ЕСЛИ запрос Конкретный:**
                            -   **Шаг А (Обязательные):** ВСЕГДА включай `name` всех объектов, у которых `"isMandatoryInList": true`.
                            -   **Шаг Б (Запрошенные):** Найди объекты, которые явно упоминаются в [USER_QUERY]. Для сопоставления используй `description`, `userFriendlyName` и `synonyms`. Возьми их `name`.
                            -   **Шаг В (Итог):** Твой финальный выбор — это **объединение** имен из Шага А и Шага Б (без дубликатов).

                    [OUTPUT FORMAT]
                    -   Твой ответ — это ВСЕГДА валидный JSON-массив строк.
                    -   Пример: `["name", "inn", "kpp"]`.
                    -   Не включай в ответ ничего, кроме самого массива.

                    [USER_QUERY]
                    "%s"

                    [RESPONSE]
                    """;

            List<Map<String, Object>> simplifiedFields = fields.stream()
                    .map(f -> {
                        Map<String, Object> map = new LinkedHashMap<>();
                        map.put("name", f.getName());
                        map.put("description", f.getDescription());
                        map.put("userFriendlyName", f.getUserFriendlyName());
                        map.put("synonyms", f.getSynonyms() != null ? f.getSynonyms() : Collections.emptyList());
                        map.put("isDefaultInList", f.isDefaultInList());
                        map.put("isMandatoryInList", f.isMandatoryInList());
                        map.put("isQueryable", f.isQueryable());
                        map.put("isSearchableInListApplet", isListFieldSearchable(f));
                        return map;
                    })
                    .collect(Collectors.toList());

            String ontologyFieldsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(simplifiedFields);

            return String.format(promptTemplate, entityName, ontologyFieldsJson, userQuery);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при формировании промпта для выбора полей", e);
        }
    }

    /**
     * Формирует промпт для LLM для извлечения данных из документа/текста.
     *
     * @param entityName   Имя сущности, для которой извлекаются данные.
     * @param documentText Текст документа.
     * @return Сформированный промпт.
     */
    public String buildDataExtractionPrompt(String entityName, String documentText) {
        try {
            OntologyDto.EntitySchema entitySchema = ontologyService.getEntitySchema(entityName);
            String userFriendlyName = entitySchema.getMeta().getUserFriendlyName();

            String promptTemplate = """
                    [ROLE]
                    Ты — высокоточный AI-ассистент по извлечению данных (Data Extraction). Твоя задача — проанализировать текст документа и извлечь из него информацию для создания сущности '%s' (%s).

                    [CONTEXT]
                    Сущность '%s' имеет следующие поля. Ты должен найти значения для них в тексте.
                    Вот схема полей в формате JSON. Обрати внимание на `name`, `type`, `description` и `synonyms` для точного сопоставления.
                    ```json
                    %s
                    ```

                    [INSTRUCTIONS]
                    1.  Внимательно прочитай [DOCUMENT_TEXT].
                    2.  Найди значения для каждого поля из [CONTEXT].
                    3.  Если значение для поля не найдено, НЕ включай это поле в итоговый JSON.
                    4.  Обрати особое внимание на поля с типом 'boolean'. Значения "активен", "да", "истина" соответствуют `true`. "Неактивен", "нет", "ложь" - `false`.
                    5.  Формат твоего ответа — это ВСЕГДА ТОЛЬКО валидный JSON-объект. Никакого дополнительного текста или пояснений.

                    [OUTPUT FORMAT]
                    Твой ответ — это JSON-объект, где ключи — это технические имена полей (`name`), а значения — извлеченные из текста данные.
                    Пример:
                    ```json
                    {
                      "name": "ООО Ромашка",
                      "inn": "7701234567",
                      "active": true
                    }
                    ```

                    [DOCUMENT_TEXT]
                    %s

                    [RESPONSE]
                    """;

            List<Map<String, Object>> simplifiedFields = entitySchema.getFields().stream()
                    .map(field -> uiComponentService.getConfig(field.getFormComponentId())
                            .map(config -> {
                                Map<String, Object> map = new LinkedHashMap<>();
                                map.put("name", field.getName());
                                map.put("type", field.getType());
                                map.put("description", field.getDescription());
                                map.put("userFriendlyName", field.getUserFriendlyName());
                                map.put("synonyms", field.getSynonyms());
                                map.put("required", config.path("required").asBoolean(false));
                                return map;
                            }))
                    .flatMap(Optional::stream)
                    .collect(Collectors.toList());

            String fieldsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(simplifiedFields);

            return String.format(promptTemplate, userFriendlyName, entityName, entityName, fieldsJson, documentText);

        } catch (JsonProcessingException e) {
            log.error("Критическая ошибка при формировании промпта для извлечения данных для сущности '{}'", entityName, e);
            throw new RuntimeException("Ошибка при формировании промпта для извлечения данных.", e);
        }
    }

    /**
     * Формирует промпт для LLM для суммаризации HTML-текста.
     *
     * @param htmlText HTML-текст для суммаризации.
     * @return Сформированный промпт.
     */
    public String buildHtmlSummaryPrompt(String htmlText) {
        return String.format("""
                [ROLE]
                Ты — AI-аналитик, который умеет быстро находить самую важную информацию в большом объеме текста и структурировать ее.

                [TASK]
                Проанализируй предоставленный текст, извлеченный с веб-страницы компании. Собери самую ценную и ключевую информацию (название, реквизиты, вид деятельности, контакты, важные факты) и оформи ее в виде краткой, но информативной сводки в формате Markdown. Используй заголовки, списки и выделение текста для лучшей читаемости.
                Только не надо писать блок с контактами контур фокуса: Телефон: 8 800 500-16-44
                                                        Email: focus@kontur.ru

                [TEXT_FROM_WEBPAGE]
                %s

                [RESPONSE]
                """, htmlText);
    }

    /**
     * Отправляет промпт в LLM и публикует событие LLMCallEvent.
     *
     * @param prompt    Промпт для отправки.
     * @param history   История сообщений для контекста (может быть null).
     * @param stream    Флаг для потоковой передачи.
     * @param messageId ID связанного сообщения для аудита.
     * @return Ответ от LLM.
     */
    public LLMResponseDto sendPromptToLlm(String prompt, List<ChatMessage> history, boolean stream, UUID messageId) {
        LLMResponseDto llmResponse = llmService.sendToSingleModel(prompt, null, true);
        if (messageId != null) {
            eventPublisher.publishEvent(new LlmCallEvent(this, prompt, llmResponse, messageId));
        }
        return llmResponse;
    }

    /**
     * Очищает "сырой" JSON-ответ от LLM от лишних символов (например, "```json").
     *
     * @param rawContent Сырой ответ от LLM.
     * @return Очищенная JSON-строка.
     */
    public String sanitizeLlmJsonResponse(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return "{}"; // Возвращаем пустой объект, если контент пуст
        }
        String cleaned = rawContent.trim();
        // Удаляем markdown-блоки
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private boolean isListFieldSearchable(OntologyDto.EntitySchema.FieldSchema field) {
        return uiComponentService.getConfig(field.getListComponentId())
                .map(config -> config.path("isSearchable").asBoolean(false))
                .orElse(false);
    }
}