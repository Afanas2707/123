package org.nobilis.nobichat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.ScenarioInfoListDto;
import org.nobilis.nobichat.dto.ScenarioNameListDto;
import org.nobilis.nobichat.model.Scenario;
import org.nobilis.nobichat.repository.ScenarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScenarioManagementService {

    private final ScenarioRepository scenarioRepository;

    @Transactional(readOnly = true)
    public ScenarioNameListDto getAllScenarioNames() {
        List<String> names = scenarioRepository.findAllScenarioNames();
        return new ScenarioNameListDto(names);
    }

    @Transactional(readOnly = true)
    public ScenarioInfoListDto getAllScenarioInfo() {
        List<Scenario> scenarios = scenarioRepository.findAll();

        List<ScenarioInfoListDto.ScenarioInfo> scenarioInfos = scenarios.stream()
                .map(scenario -> ScenarioInfoListDto.ScenarioInfo.builder()
                        .id(scenario.getId())
                        .name(scenario.getDefinition().getName())
                        .build())
                .collect(Collectors.toList());

        return new ScenarioInfoListDto(scenarioInfos);
    }

    @Transactional
    public void deleteScenario(UUID scenarioId) {
        log.info("Попытка удаления сценария с ID: {}", scenarioId);
        if (!scenarioRepository.existsById(scenarioId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Сценарий с ID " + scenarioId + " не найден.");
        }

        scenarioRepository.deleteById(scenarioId);
        log.info("Сценарий с ID {} успешно удален.", scenarioId);
    }
}
