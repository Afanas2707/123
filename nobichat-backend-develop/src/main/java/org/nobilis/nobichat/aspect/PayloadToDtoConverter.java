package org.nobilis.nobichat.aspect;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Collection;

@Component
@RequiredArgsConstructor
@Slf4j
public class PayloadToDtoConverter {

    private final ObjectMapper objectMapper;

    /**
     * Конвертирует "грязный" Payload объект (с JsonNullable) в "чистый" DTO объект.
     * В итоговый DTO попадут только те поля, которые были явно переданы в JSON.
     * Остальные поля в DTO будут null.
     *
     * @param payload "Грязный" объект-источник.
     * @param dtoClass Класс "чистого" DTO, экземпляр которого нужно создать.
     * @return Экземпляр "чистого" DTO.
     * @param <P> Тип Payload.
     * @param <D> Тип DTO.
     */
    public <P, D> D convert(P payload, Class<D> dtoClass) {
        try {
            D dto = dtoClass.getDeclaredConstructor().newInstance();

            for (Field payloadField : payload.getClass().getDeclaredFields()) {
                payloadField.setAccessible(true);
                Object payloadValue = payloadField.get(payload);

                if (!(payloadValue instanceof JsonNullable)) {
                    continue;
                }

                JsonNullable<?> nullableValue = (JsonNullable<?>) payloadValue;

                if (nullableValue.isPresent()) {
                    Object unwrappedValue = nullableValue.get();
                    try {
                        Field dtoField = dtoClass.getDeclaredField(payloadField.getName());
                        dtoField.setAccessible(true);

                        JavaType targetType = objectMapper.getTypeFactory().constructType(dtoField.getGenericType());

                        Object convertedValue = objectMapper.convertValue(unwrappedValue, targetType);

                        dtoField.set(dto, convertedValue);
                    } catch (NoSuchFieldException e) {
                        log.warn("Поле '{}' из Payload не найдено в DTO '{}'", payloadField.getName(), dtoClass.getSimpleName());
                    }
                }
            }
            return dto;
        } catch (Exception e) {
            log.error("Ошибка при конвертации Payload в DTO", e);
            throw new RuntimeException("Не удалось сконвертировать Payload в DTO", e);
        }
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (value instanceof Collection && Collection.class.isAssignableFrom(targetType)) {
            return objectMapper.convertValue(value, targetType);
        }

        if (!targetType.isPrimitive() && !targetType.isAssignableFrom(String.class) && !targetType.getName().startsWith("java.lang")) {
            return objectMapper.convertValue(value, targetType);
        }

        return value;
    }
}