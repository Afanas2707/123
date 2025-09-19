package org.nobilis.nobichat.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.nobilis.nobichat.dto.entities.CreateEntityRequestDto;
import org.nobilis.nobichat.dto.entities.UpdateEntityRequestDto;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Аспект для проверки прав доступа к полям на основе UI-схемы, сгенерированной в чате.
 * Перехватывает вызовы методов, аннотированных {@link ProtectByUISchema}.
 * <p>
 * Работает в двух режимах:
 * 1.  <b>Явный:</b> Если в аннотации указан параметр {@code fields}, аспект проверяет,
 *     что все перечисленные поля присутствуют в UI-схеме текущей сессии.
 *     Используется для простых действий (например, переключатель статуса).
 * 2.  <b>Неявный (по телу запроса):</b> Если параметр {@code fields} не указан,
 *     аспект ищет в аргументах метода объект-Payload (с полями {@link JsonNullable})
 *     и проверяет, что все *фактически переданные* в JSON поля разрешены в UI-схеме.
 *     Используется для сложных форм (создание/обновление).
 * <p>
 * Для работы аспекта метод контроллера должен принимать параметр с заголовком
 * {@code X-Chat-Session-Id}, который используется для идентификации сессии.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class UISchemaProtectionAspect {

    private static final Set<String> BYPASS_ROLES = Set.of("ROLE_ADMIN", "ROLE_GIGA_ADMIN", "ROLE_EDITOR");

    private final FieldPermissionService fieldPermissionService;

    @Pointcut("@annotation(protectByUISchema)")
    public void callAtProtectByUISchema(ProtectByUISchema protectByUISchema) {}

    @Before("callAtProtectByUISchema(protectByUISchema)")
    public void before(JoinPoint joinPoint, ProtectByUISchema protectByUISchema) {
        if (currentUserHasBypassRole()) {
            log.info("Пользователь с ролью из BYPASS_ROLES. Проверка UI-схемы пропущена.");
            return;
        }

        UUID sessionId = findArgument(joinPoint, UUID.class, "sessionId");
        if (sessionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Отсутствует обязательный заголовок X-Chat-Session-Id.");
        }

        Set<String> permittedFieldsAndActions = fieldPermissionService.getPermittedFieldsAndActions(sessionId);

        if (StringUtils.hasText(protectByUISchema.requiredAction())) {
            String requiredAction = protectByUISchema.requiredAction();
            if (!permittedFieldsAndActions.contains(requiredAction)) {
                log.warn("Запрещено действие '{}' для сессии {}. Отсутствует в UI-схеме.", requiredAction, sessionId);
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Действие '" + protectByUISchema.operationType() + "' запрещено, так как оно не является частью сгенерированного UI.");
            }
            log.info("Проверка действия '{}' для сессии {} пройдена успешно.", requiredAction, sessionId);
        }

        Set<String> fieldsToCheck = getFieldsToCheck(joinPoint);
        if (fieldsToCheck.isEmpty() && !StringUtils.hasText(protectByUISchema.requiredAction())) {
            log.warn("Не найдено полей для проверки прав в методе: {}", joinPoint.getSignature().getName());
            return;
        }

        for (String fieldPath : fieldsToCheck) {
            String rootFieldToCheck = getRootFromPath(fieldPath);
            if (!permittedFieldsAndActions.contains(rootFieldToCheck)) {
                log.warn("Попытка несанкционированного доступа к полю/отношению: '{}' в сессии {}. Оно отсутствует в сгенерированной UI-схеме.", rootFieldToCheck, sessionId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Доступ к данным '" + rootFieldToCheck + "' запрещен, так как они не являются частью сгенерированного UI.");
            }
        }

        if (!fieldsToCheck.isEmpty()) {
            log.info("Проверка полей для сессии {} пройдена успешно. Запрошено: {}", sessionId, fieldsToCheck);
        }
    }

    /**
     * Определяет, какие поля нужно проверить, анализируя аргументы метода.
     */
    private Set<String> getFieldsToCheck(JoinPoint joinPoint) {
        UpdateEntityRequestDto updateRequest = findArgument(joinPoint, UpdateEntityRequestDto.class, "request");
        if (updateRequest != null && updateRequest.getFields() != null) {
            return updateRequest.getFields().keySet();
        }

        CreateEntityRequestDto createRequest = findArgument(joinPoint, CreateEntityRequestDto.class, "request");
        if (createRequest != null && createRequest.getFields() != null) {
            return createRequest.getFields().keySet();
        }

        List<String> fieldList = findArgument(joinPoint, List.class, "fields");
        if (fieldList != null && !fieldList.isEmpty()) {
            return new HashSet<>(fieldList);
        }

        return Collections.emptySet();
    }

    private String getRootFromPath(String path) {
        if (path == null || path.isEmpty()) return "";
        int dotIndex = path.indexOf('.');
        return (dotIndex == -1) ? path : path.substring(0, dotIndex);
    }

    private boolean currentUserHasBypassRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return false;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(BYPASS_ROLES::contains);
    }

    private <T> T findArgument(JoinPoint joinPoint, Class<T> clazz, String name) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameterNames.length; i++) {
            if (name.equals(parameterNames[i]) && args[i] != null && clazz.isAssignableFrom(args[i].getClass())) {
                return (T) args[i];
            }
        }
        return null;
    }
}