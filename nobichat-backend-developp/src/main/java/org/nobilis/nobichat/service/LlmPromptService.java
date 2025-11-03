package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.llm.LLMResponseDto;
import org.nobilis.nobichat.dto.ontology.OntologyDto;
import org.nobilis.nobichat.events.event.LlmCallEvent;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.Scenario;
import org.nobilis.nobichat.repository.ScenarioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmPromptService {

    private final LLMService llmService;
    private final ApplicationEventPublisher eventPublisher;
    private final ScenarioRepository scenarioRepository;

    public LLMResponseDto sendPromptToLlm(String prompt, List<ChatMessage> history, boolean stream, UUID messageId) {
        LLMResponseDto llmResponse = llmService.sendToSingleModel(prompt, null, true);
        if (messageId != null) {
            eventPublisher.publishEvent(new LlmCallEvent(this, prompt, llmResponse, messageId));
        }
        return llmResponse;
    }

    public String buildFreeModeExtractionPrompt(String userQuery) {
        String scenariosContext = buildAvailableScenariosContext();
        String promptTemplate = """
                [ROLE]
                Ты — AI-классификатор команд главного меню.
                [CONTEXT]
                1.  **Возможные интенты:**
                    *   `LAUNCH_SCENARIO`: Запустить существующий сценарий.
                    *   `START_SCENARIO_CONSTRUCTOR`: Начать создание нового сценария.
                    *   `UNKNOWN`: Другое.
                2.  **Доступные сценарии:**
                    %s
                [TASK]
                Классифицируй [USER REQUEST] и верни JSON. Для `LAUNCH_SCENARIO` найди `scenarioName`.
                [TARGET SCHEMA]
                { "intent": "...", "scenarioName": "...", "scenarioDraftText": "..." }
                [USER REQUEST]
                %s
                [RESPONSE]
                """;
        return String.format(promptTemplate, scenariosContext, userQuery);
    }

    public String buildConstructorModeExtractionPrompt(String userQuery) {
        String promptTemplate = """
                [ROLE]
                Ты — AI-классификатор команд редактора сценариев.
                [CONTEXT]
                1.  **Возможные интенты:**
                    *   `START_SCENARIO_PREVIEW`: Войти в режим просмотра (ключевые слова: "покажи", "просмотр", "сохранить", "готово").
                    *   `PUBLISH_SCENARIO`: Окончательно опубликовать сценарий (ключевые слова: "опубликовать", "завершить окончательно").
                [TASK]
                Классифицируй [USER REQUEST] в JSON. Если интент не распознан, считай, что пользователь продолжает редактирование, и верни пустой JSON.
                [TARGET SCHEMA]
                { "intent": "..." }
                [USER REQUEST]
                %s
                [RESPONSE]
                """;
        return String.format(promptTemplate, userQuery);
    }

    public String buildExecutionModeExtractionPrompt(String userQuery) {
        String promptTemplate = """
                [ROLE]
                Ты — AI-классификатор команд в режиме исполнения сценария.

                [TASK]
                Твоя задача — проанализировать [USER REQUEST] и классифицировать его как одну из трех команд.
                1.  **Команда Навигации:**
                    *   Если пользователь говорит "дальше", "следующий", "вперед" -> верни интент `NAVIGATE_EXECUTION` с `direction: "NEXT"`.
                    *   Если пользователь говорит "назад", "предыдущий", "вернись" -> верни интент `NAVIGATE_EXECUTION` с `direction: "BACK"`.
                2.  **Команда Сохранения:**
                    *   Если пользователь говорит "сохранить", "применить", "записать", "сохрани" -> верни интент `SAVE_SCENARIO_DATA`.
                3.  **Неизвестная Команда:**
                    *   Для любого другого текста (включая белиберду) -> верни интент `UNKNOWN_EXECUTION_COMMAND`.
                
                [TARGET SCHEMA]
                ```json
                {
                  "intent": "NAVIGATE_EXECUTION | SAVE_SCENARIO_DATA | UNKNOWN_EXECUTION_COMMAND",
                  "direction": "NEXT | BACK | null"
                }
                ```

                [EXAMPLES]
                1.  Запрос: "дальше"
                    Ответ: { "intent": "NAVIGATE_EXECUTION", "direction": "NEXT" }
                2.  Запрос: "Сохранить"
                    Ответ: { "intent": "SAVE_SCENARIO_DATA" }
                3.  Запрос: "привет"
                    Ответ: { "intent": "UNKNOWN_EXECUTION_COMMAND" }
                
                [USER REQUEST]
                %s

                [RESPONSE]
                """;

        return String.format(promptTemplate, userQuery);
    }

    public String buildIsEditCommandPrompt(String userQuery) {
        String promptTemplate = """
                [ROLE]
                Ты — бинарный классификатор намерения редактирования.
                
                [TASK]
                Проанализируй [USER REQUEST]. Если он содержит явное намерение что-то изменить, добавить или удалить (например, "измени название", "добавь поле", "удали шаг"), верни JSON `{"isEditCommand": true}`.
                В противном случае, включая бессмысленный набор символов, верни `{"isEditCommand": false}`.
                
                [OUTPUT FORMAT]
                                Твой ответ должен быть **СТРОГО** только валидным JSON-объектом. Ничего больше.

                [EXAMPLES]
                1. Запрос: "поменяй описание шага 2 на 'новое описание'" -> {"isEditCommand": true}
                2. Запрос: "добавь шаг" -> {"isEditCommand": true}
                3. Запрос: "привет" -> {"isEditCommand": false}
                4. Запрос: "ываофыова" -> {"isEditCommand": false}
                5. Запрос: "Удали шаг <какой-то шаг>" -> {"isEditCommand": true}

                [USER REQUEST]
                %s

                [RESPONSE]
                """;
        return String.format(promptTemplate, userQuery);
    }

    public String buildPreviewModeExtractionPrompt(String userQuery) {
        String promptTemplate = """
                [ROLE]
                Ты — AI-классификатор команд режима просмотра.
                [CONTEXT]
                1.  **Возможные интенты:**
                    *   `NAVIGATE_PREVIEW`: Переместиться по шагам ("дальше", "назад").
                    *   `PUBLISH_SCENARIO`: Окончательно опубликовать сценарий ("опубликовать").
                [TASK]
                Классифицируй [USER REQUEST] в JSON. Для `NAVIGATE_PREVIEW` определи `direction`. Если интент не распознан, считай, что пользователь хочет выйти из просмотра и редактировать, верни пустой JSON.
                [TARGET SCHEMA]
                { "intent": "...", "direction": "NEXT | BACK | null" }
                [USER REQUEST]
                %s
                [RESPONSE]
                """;
        return String.format(promptTemplate, userQuery);
    }

    private String buildAvailableScenariosContext() {
        List<Scenario> scenarios = scenarioRepository.findAll();
        if (scenarios.isEmpty()) {
            return "В системе нет доступных для запуска сценариев.";
        }
        return "В системе доступны следующие сценарии:\n" +
                scenarios.stream()
                        .map(s -> String.format("- Название: \"%s\", ID: \"%s\"", s.getDefinition().getName(), s.getId()))
                        .collect(Collectors.joining("\n"));
    }

    private String buildDetailedEntityContext(OntologyDto ontologyDto) {
        if (ontologyDto == null || ontologyDto.getEntities() == null) {
            return "Нет доступных сущностей.";
        }

        return ontologyDto.getEntities().entrySet().stream()
                .map(entry -> {
                    String entityName = entry.getKey();
                    OntologyDto.EntitySchema schema = entry.getValue();
                    String fields = schema.getFields().stream()
                            .map(f -> String.format("   - %s (Тип: %s, Описание: %s, Синонимы: %s)",
                                    f.getName(), f.getType(), f.getDescription(),
                                    String.join(", ", f.getSynonyms())))
                            .collect(Collectors.joining("\n"));

                    return String.format("--- Entity: %s (User Name: %s) ---\nFields:\n%s\n",
                            entityName, schema.getMeta().getUserFriendlyName(), fields);
                })
                .collect(Collectors.joining("\n\n"));
    }

    public String sanitizeLlmJsonResponse(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return "{}";
        }
        String cleaned = rawContent.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        if (!cleaned.contains("{") && cleaned.contains("=")) {
            String[] parts = cleaned.split("=", 2);
            if (parts.length == 2) {
                return String.format("{\"%s\": %s}", parts[0].trim(), parts[1].trim());
            }
        }

        return cleaned.trim();
    }

    public String buildScenarioEnrichmentPrompt(String userInput, String currentScenarioJson, String ontologyContext) {
        String promptTemplate = """
            [ROLE]
            Ты — AI-инженер-аналитик. Твоя задача — пошагово создавать JSON-определение бизнес-сценария 'ScenarioDefinition', строго следуя правилам и используя предоставленные контексты.

            [TARGET SCHEMA]
            Твоей конечной целью всегда является валидный JSON, соответствующий этой структуре:
            {
              "name": "string", "description": "string",
              "steps": [
                {
                  "name": "string", "description": "string", "stepIndex": "integer",
                  "template": "form | list",
                  "entity": "string (technical name from ontology)",
                  "entityFields": ["string (technical field name)", ...]
                }
              ]
            }

            [ONTOLOGY CONTEXT]
            Это твой ИСТОЧНИК ПРАВДЫ для сущностей и полей.
            ---
            %s
            ---

            [CURRENT STATE]
            Это JSON-структура сценария, которую мы уже собрали.
            ```json
            %s
            ```

            [USER INPUT]
            Это новая информация от пользователя, которую нужно интегрировать в [CURRENT STATE].
            > "%s"

            [TASK AND RULES]
            Твоя задача — создать **НОВЫЙ ПОЛНЫЙ JSON**, который является результатом обновления [CURRENT STATE] на основе [USER INPUT]. Ты должен СТРОГО следовать этим правилам:

            1.  **Извлечение, а не Игнорирование:** Ты должен попытаться извлечь значение для КАЖДОГО поля, упомянутого пользователем.

            2.  **Маппинг Шаблона (`template`):**
                *   "форма", "карточка" -> **"form"**
                *   "список", "таблица" -> **"list"**
                *   **Если шаблон не похож ни на один из них, вставь то, что написал пользователь, КАК ЕСТЬ (например, "super-form").**

            3.  **Маппинг Сущности (`entity`):**
                *   Найди в [ONTOLOGY CONTEXT] техническое имя сущности, соответствующее упоминанию пользователя (например, "клиент" -> "customer").
                *   **КРИТИЧЕСКИ ВАЖНО:** Если ты НЕ МОЖЕШЬ найти точное соответствие в онтологии, ты ОБЯЗАН вставить в поле `entity` то слово, которое использовал пользователь, **КАК ЕСТЬ** (например, "customerTYPO"). **НЕ ОСТАВЛЯЙ ПОЛЕ ПУСТЫМ (`null`)**.

            4.  **Маппинг Полей (`entityFields`):**
                *   Для КАЖДОГО поля, упомянутого пользователем, найди его техническое имя в [ONTOLOGY CONTEXT] (например, "Наименование клиента" -> "name").
                *   **КРИТИЧЕСКИ ВАЖНО:** Если ты НЕ МОЖЕШЬ найти техническое имя для какого-то поля, ты ОБЯЗАН вставить в массив `entityFields` то название, которое использовал пользователь, **КАК ЕСТЬ** (например, "nameTYPO"). **НЕ ИГНОРИРУЙ НЕИЗВЕСТНЫЕ ПОЛЯ**.
                *   Финальный массив `entityFields` должен содержать смесь из найденных технических имен и ненайденных пользовательских имен.

            5.  **Сохранение Контекста:** Не удаляй уже существующую информацию из [CURRENT STATE], только дополняй или изменяй ее в соответствии с [USER INPUT].

            [EXAMPLE OF CORRECT (even if invalid) BEHAVIOR]
            -   **ЕСЛИ [USER INPUT]:** "Шаг для сущности `qwerty`, поля `ИНН` и `неверное поле`."
            -   **ТЫ ДОЛЖЕН СГЕНЕРИРОВАТЬ ТАКОЙ ФРАГМЕНТ JSON:**
                ```json
                {
                  ...
                  "entity": "qwerty",
                  "entityFields": ["inn", "неверное поле"]
                }
                ```

            [OUTPUT FORMAT]
            -   Твой ответ — это ВСЕГДА только полный и валидный JSON-объект. Не добавляй ничего, кроме самого JSON.

            [RESPONSE]
            """;
        return String.format(promptTemplate, ontologyContext, currentScenarioJson, userInput);
    }
}