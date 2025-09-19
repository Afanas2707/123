package org.nobilis.nobichat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.dto.error.GeneralErrorResponse;
import org.nobilis.nobichat.dto.llm.LLMMultipleRequestDto;
import org.nobilis.nobichat.dto.llm.LLMPromptRequestDto;
import org.nobilis.nobichat.dto.llm.LLMResponseDto;
import org.nobilis.nobichat.events.event.LlmCallEvent;
import org.nobilis.nobichat.service.LLMService;
import org.nobilis.nobichat.util.AiProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@SecurityRequirement(name = "tokenAuth")
@RequestMapping("/api/llm")
@Tag(name = "Контроллер для запросов в LLM")
@Validated
@RequiredArgsConstructor
public class LLMController {

    private final LLMService llmService;
    private final AiProperties aiProperties;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping("")
    @Operation(summary = "Получить список всех доступных моделей",
            description = "Возвращает полный список имен моделей, которые можно использовать в других эндпоинтах.")
    public List<String> getAvailableModels() {
        return aiProperties.getModels();
    }

    @PostMapping
    @Operation(summary = "Отправка промпта одной модели на выбор",
            description = "Отправляет промпт указанной модели. Если модель не указана в параметрах запроса, используется модель по умолчанию.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public LLMResponseDto sendToSingleModel(@RequestBody @Valid LLMPromptRequestDto prompt,
                                            @Parameter(description = "ID модели для запроса")
                                            @RequestParam(name = "model", required = false) String modelName,
                                            @Parameter(description = "Использовать ли RateLimiter. По умолчанию true. 20 запросов в минуту для бесплатных моделей. И 50 запросов в день")
                                            @RequestParam(name = "useRateLimiter", defaultValue = "true") Boolean useRateLimiter) {
        String promptText = prompt.getPrompt();
        LLMResponseDto response = llmService.sendToSingleModel(promptText, modelName, useRateLimiter);

        eventPublisher.publishEvent(new LlmCallEvent(this, promptText, response, null));

        return response;
    }

    @PostMapping("/batch")
    @Operation(summary = "Отправка промпта указанному списку моделей",
            description = "Принимает промпт и список имен моделей, отправляет запрос параллельно каждой из них.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public List<LLMResponseDto> sendToSpecificModels(@RequestBody @Valid LLMMultipleRequestDto request,
                                                     @Parameter(description = "Использовать ли RateLimiter. По умолчанию true. 20 запросов в минуту для бесплатных моделей. И 50 запросов в день")
                                                     @RequestParam(name = "useRateLimiter", defaultValue = "true") Boolean useRateLimiter) {
        String prompt = request.getPrompt();
        List<LLMResponseDto> responses = llmService.sendToSpecificModels(prompt, request.getModels(), useRateLimiter);

        responses.forEach(response -> {
            eventPublisher.publishEvent(new LlmCallEvent(this, prompt, response, null));
        });

        return responses;
    }

    @PostMapping("/freeGigaBatch")
    @Operation(summary = "Отправка промпта ВСЕМ бесплатным моделям одновременно",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public List<LLMResponseDto> sendToMultipleModels(@RequestBody @Valid LLMPromptRequestDto promptDto,
                                                     @Parameter(description = "Использовать ли RateLimiter. По умолчанию true. 20 запросов в минуту для бесплатных моделей. И 50 запросов в день")
                                                     @RequestParam(name = "useRateLimiter", defaultValue = "true") Boolean useRateLimiter) {
        String prompt = promptDto.getPrompt();
        List<LLMResponseDto> responses = llmService.sendToAll(prompt, useRateLimiter);

        responses.forEach(response -> {
            eventPublisher.publishEvent(new LlmCallEvent(this, prompt, response, null));
        });

        return responses;
    }
}