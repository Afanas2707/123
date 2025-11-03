package org.nobilis.nobichat.service.intent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.ScenarioDefinition;
import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.model.ChatMessage;
import org.nobilis.nobichat.model.Scenario;
import org.nobilis.nobichat.model.UserChatSession;
import org.nobilis.nobichat.repository.ScenarioRepository;
import org.nobilis.nobichat.service.DynamicEntityQueryService; // <-- ДОБАВЛЕНА ЗАВИСИМОСТЬ
import org.nobilis.nobichat.service.ScenarioHelperService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class LaunchScenarioHandler implements IntentHandler {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioHelperService scenarioHelperService;
    private final DynamicEntityQueryService dynamicEntityQueryService;

    @Override
    public String getIntentType() {
        return "LAUNCH_SCENARIO";
    }

    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatRequest request, UserChatSession session, ChatMessage message) {
        UUID scenarioId = intent.getScenarioId();
        String scenarioName = intent.getScenarioName();

        if (scenarioId == null && !StringUtils.hasText(scenarioName)) {
            return ChatResponseDto.builder()
                    .sessionId(session.getId())
                    .message("Не удалось определить, какой сценарий запустить. Пожалуйста, укажите его название или ID.")
                    .build();
        }

        try {
            Scenario scenario;
            if (scenarioId != null) {
                log.info("Поиск сценария по ID: {}", scenarioId);
                scenario = scenarioRepository.findById(scenarioId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сценарий с ID " + scenarioId + " не найден."));
            } else {
                log.info("Поиск сценария по названию в definition: '{}'", scenarioName);
                scenario = scenarioRepository.findByNameInDefinition(scenarioName)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Сценарий с названием '" + scenarioName + "' не найден."));
            }

            ScenarioDefinition.ScenarioStep firstStep = scenario.getDefinition().getSteps().stream()
                    .min(Comparator.comparingInt(ScenarioDefinition.ScenarioStep::getStepIndex))
                    .orElseThrow(() -> new IllegalStateException("Сценарий '" + scenario.getDefinition().getName() + "' не содержит шагов."));

            UUID sourceIdForFirstStep = null;
            if ("form".equalsIgnoreCase(firstStep.getTemplate())) {
                sourceIdForFirstStep = dynamicEntityQueryService.findLastCreatedEntityId(firstStep.getEntity())
                        .orElse(null);
            }

            session.setActiveScenarioId(scenario.getId());
            session.setCurrentStepName(firstStep.getName());
            session.setScenarioContextJson("{}");

            log.info("Запущен сценарий '{}' (ID: {}) для сессии {}. Первый шаг: {}",
                    scenario.getDefinition().getName(),
                    scenario.getId(),
                    session.getId(),
                    firstStep.getName());

            return scenarioHelperService.createResponseForStep(session, scenario.getDefinition(), firstStep, sourceIdForFirstStep, false);

        } catch (Exception e) {
            log.error("Ошибка при запуске сценария по запросу '{}'", request.getMessage(), e);
            return ChatResponseDto.builder()
                    .sessionId(session.getId())
                    .message("Произошла ошибка при запуске сценария: " + e.getMessage())
                    .build();
        }
    }
}