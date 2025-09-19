package org.nobilis.nobichat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class IntentHandlerRegistry {
    private final Map<String, IntentHandler> handlers;

    public IntentHandlerRegistry(List<IntentHandler> handlers) {
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(IntentHandler::getIntentType, Function.identity()));
        log.info("Зарегистрировано {} обработчиков намерений: {}", handlers.size(), this.handlers.keySet());
    }

    /**
     * Возвращает обработчик по заданному типу намерения.
     *
     * @param intentType тип намерения
     * @return Optional с обработчиком, если найден
     */
    public Optional<IntentHandler> getHandler(String intentType) {
        return Optional.ofNullable(handlers.get(intentType));
    }
}
