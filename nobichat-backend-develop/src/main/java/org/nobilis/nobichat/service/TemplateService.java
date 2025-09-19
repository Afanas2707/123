package org.nobilis.nobichat.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.constants.ChatMode;
import org.nobilis.nobichat.dto.template.CreateTemplateRequestDto;
import org.nobilis.nobichat.exception.ResourceNotFoundException;
import org.nobilis.nobichat.model.Template;
import org.nobilis.nobichat.repository.ChatMessageRepository;
import org.nobilis.nobichat.repository.TemplateRepository;
import org.nobilis.nobichat.specification.TemplateSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final ChatMessageRepository chatMessageRepository;

    private String extractViewId(JsonNode schema) {
        if (schema != null && schema.hasNonNull("view") && schema.get("view").hasNonNull("id")) {
            return schema.get("view").get("id").asText();
        }
        return null;
    }

    private String extractViewVersion(JsonNode schema) {
        if (schema != null && schema.hasNonNull("view") && schema.get("view").hasNonNull("version")) {
            return schema.get("view").get("version").asText();
        }
        return null;
    }

    @Transactional
    public Integer bulkReplaceTemplateUsage(UUID newTemplateId,
                                            UUID oldTemplateId,
                                            ChatMode mode,
                                            String viewId,
                                            String viewVersion) {
        log.info("""
                Запущена операция массовой замены шаблонов с использованием JPA Specifications.
                Новый Template ID: {}
                Критерии фильтрации: oldTemplateId={}, mode={}, viewId='{}', viewVersion='{}'
                """, newTemplateId, oldTemplateId, mode, viewId, viewVersion);
        if (!templateRepository.existsById(newTemplateId)) {
            throw new ResourceNotFoundException("Целевой шаблон с id: " + newTemplateId + " не найден. Операция отменена.");
        }

        Specification<Template> spec = TemplateSpecifications.hasId(oldTemplateId)
                .and(TemplateSpecifications.hasMode(mode))
                .and(TemplateSpecifications.hasViewId(viewId))
                .and(TemplateSpecifications.hasViewVersion(viewVersion));

        List<Template> templatesToReplace = templateRepository.findAll(spec);

        if (templatesToReplace.isEmpty()) {
            log.info("Не найдено шаблонов, соответствующих критериям. Обновление не требуется.");
            return 0;
        }

        List<UUID> templateIdsToReplace = templatesToReplace.stream()
                .map(Template::getId)
                .collect(Collectors.toList());

        log.info("Найдено {} уникальных шаблонов для замены: {}", templateIdsToReplace.size(), templateIdsToReplace);

        Integer updatedCount = chatMessageRepository.updateTemplateForMessages(newTemplateId, templateIdsToReplace);
        log.warn("Массовая замена завершена. Обновлено {} записей в chat_messages.", updatedCount);
        return updatedCount;
    }

    @Transactional(readOnly = true)
    public List<Template> getAllTemplateVersionsByAppletId(String viewId, ChatMode mode) {
        if (mode == null) {
            List<Template> templates = templateRepository.findByViewIdInSchemaOrderByVersionDesc(viewId);
            if (templates.isEmpty()) {
                throw new ResourceNotFoundException("Шаблоны с viewId: " + viewId + " не найдены");
            }
            return templates;
        }
        return templateRepository.findByViewIdInSchemaAndModeOrderByVersionDesc(viewId, mode.name());
    }

    @Transactional(readOnly = true)
    public Template getLatestTemplateByAppletId(String viewId, ChatMode mode) {
        List<Template> templates;
        if (mode == null) {
            templates = getAllTemplateVersionsByAppletId(viewId, null);
        } else {
            templates = getAllTemplateVersionsByAppletId(viewId, mode);
        }

        if (templates.isEmpty()) {
            throw new ResourceNotFoundException("Шаблоны с viewId: " + viewId + (mode != null ? " и mode: " + mode : "") + " не найдены.");
        }
        return templates.get(0);
    }

    @Transactional(readOnly = true)
    public Template getTemplateByAppletIdAndVersion(String viewId, String viewVersion, ChatMode mode) {
        Optional<Template> template;
        if (mode == null) {
            template = templateRepository.findByViewIdAndVersionInSchema(viewId, viewVersion);
        } else {
            template = templateRepository.findByViewIdAndVersionInSchemaAndMode(viewId, viewVersion, mode.name());
        }
        return template.orElseThrow(() -> new ResourceNotFoundException(
                "Шаблон с viewId: " + viewId + " и версией: " + viewVersion + (mode != null ? " и mode: " + mode : "") + " не найден"));
    }

    @Transactional(readOnly = true)
    public Template getTemplateByAppletId(String viewId) {
        return templateRepository.findByViewIdInSchema(viewId)
                .orElseThrow(() -> new ResourceNotFoundException("Шаблон с viewId: " + viewId + " не найден"));
    }

    @Transactional(readOnly = true)
    public List<Template> getAllTemplates(ChatMode mode) {
        if (mode == null) {
            return templateRepository.findAll();
        }
        return templateRepository.findAllByMode(mode);
    }

    @Transactional(readOnly = true)
    public List<Template> getAllLatestTemplates(ChatMode mode) {
        if (mode == null) {
            return templateRepository.findAllLatestVersionsOfTemplatesInSchema();
        }
        return templateRepository.findAllLatestVersionsOfTemplatesInSchemaAndMode(mode.name());
    }

    @Transactional(readOnly = true)
    public Template getTemplateById(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Шаблон с id: " + id + " не найден"));
    }

    @Transactional
    public Template createTemplate(CreateTemplateRequestDto createDto) {
        JsonNode schema = createDto.getSchema();

        String viewId = extractViewId(schema);
        String viewVersion = extractViewVersion(schema);

        if (viewId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Поле 'view.id' в schema является обязательным и не может быть пустым.");
        }
        if (viewVersion == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Поле 'view.version' в schema является обязательным и не может быть пустым.");
        }

        if (createDto.getMode() == ChatMode.STRICT) {
            templateRepository.findByViewIdAndVersionInSchema(viewId, viewVersion)
                    .ifPresent(existingTemplate -> {
                        String errorMessage = String.format(
                                "Шаблон в режиме STRICT с viewId '%s' и версией '%s' уже существует (ID: %s).",
                                viewId, viewVersion, existingTemplate.getId()
                        );
                        log.warn(errorMessage);
                        throw new ResponseStatusException(HttpStatus.CONFLICT, errorMessage);
                    });
        }

        Template template = Template.builder()
                .schema(createDto.getSchema())
                .mode(createDto.getMode())
                .build();

        return templateRepository.save(template);
    }

    @Transactional
    public void deleteTemplate(UUID id) {
        if (!templateRepository.existsById(id)) {
            throw new ResourceNotFoundException("Шаблон с id: " + id + " не найден");
        }
        templateRepository.deleteById(id);
    }

    public List<Template> getAllLatestStrictTemplates() { return templateRepository.findAllLatestStrict(); }
    public List<Template> getAllStrictTemplateVersionsByAppletId(String viewId) { return templateRepository.findAllStrictByViewId(viewId); }
    public Template getStrictTemplateByAppletIdAndVersion(String viewId, String viewVersion) {
        return templateRepository.findStrictByViewIdAndVersion(viewId, viewVersion)
                .orElseThrow(() -> new ResourceNotFoundException("Строгий шаблон с viewId: " + viewId + " и версией: " + viewVersion + " не найден"));
    }
    public Template getLatestStrictTemplateByAppletId(String viewId) {
        return templateRepository.findLatestStrictByViewId(viewId)
                .orElseThrow(() -> new ResourceNotFoundException("Строгий шаблон с viewId: " + viewId + " не найден"));
    }

    public List<Template> getAllLatestSoftTemplates() { return templateRepository.findAllLatestSoft(); }
    public List<Template> getAllSoftTemplateVersionsByAppletId(String viewId) { return templateRepository.findAllSoftByViewId(viewId); }
    public List<Template> getSoftTemplatesByAppletIdAndVersion(String viewId, String viewVersion) { return templateRepository.findSoftByViewIdAndVersion(viewId, viewVersion); }
    public List<Template> getLatestSoftTemplatesByAppletId(String viewId) { return templateRepository.findLatestSoftByViewId(viewId); }
}