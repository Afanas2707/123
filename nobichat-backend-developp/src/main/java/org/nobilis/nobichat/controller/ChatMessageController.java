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
import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.error.GeneralErrorResponse;
import org.nobilis.nobichat.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@SecurityRequirement(name = "tokenAuth")
@Tag(name = "Контроллер для запросов пользователя")
@Validated
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatService chatService;

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @Operation(
            summary = "Отправить сообщение в чат (Основной эндпоинт)",
            description = """
                    Центральный метод для всего диалогового взаимодействия. Его поведение зависит от текущего состояния сессии пользователя.
                    
                    **Возможные состояния и действия:**
                    
                    1.  **Свободный диалог** (нет активного сценария или конструктора):
                        *   `"Создай сценарий"` -> Переводит сессию в **Режим Конструктора**.
                        *   `"Запусти сценарий 'Имя'"` -> Переводит сессию в **Режим Исполнения**.
                    
                    2.  **Режим Конструктора** (создание/редактирование сценария):
                        *   `"Название: ... Описание: ..."` -> Обновляет черновик сценария и возвращает `uiSchema` с Markdown-отчетом.
                        *   `"Покажи"` -> Переводит сессию в **Режим Просмотра** и возвращает `uiSchema` для первого шага.
                        *   `"Опубликовать"` -> Завершает создание, публикует сценарий и возвращает сессию в свободный диалог.
                    
                    3.  **Режим Просмотра** (внутри конструктора):
                        *   `"Дальше" / "Назад"` -> Перемещает по шагам, возвращая `uiSchema` для соответствующего шага.
                        *   `"Измени название шага..."` -> Применяет изменения и **мгновенно возвращает обновленный `uiSchema`** текущего шага просмотра.
                    
                    4.  **Режим Исполнения** (прохождение сценария):
                        *   `"Дальше" / "Назад"` -> Перемещает по шагам, возвращая `uiSchema` для нового шага. `context` **не** требуется.
                        *   `"Сохранить"` -> **Сохраняет данные из формы.** При отправке этой команды, поле `context` **должно быть заполнено** данными с UI. Система сохраняет данные в БД и возвращает `uiSchema` **текущего** шага (возможно, с обновленным `sourceId`).
                        *   `"Любой другой текст"` -> Считается нераспознанной командой. Система остается на текущем шаге и возвращает подсказку.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ChatResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Bad Request (невалидный запрос)", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized (некорректный токен)", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class)))
            })
    public ResponseEntity<ChatResponseDto> processChatMessage(
            @Parameter(description = "ID сессии чата")
            @RequestHeader(name = "X-Chat-Session-Id", required = false) UUID sessionId,
            @RequestPart("requestDto") @Valid ChatRequest requestDto,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {

        ChatResponseDto response = chatService.processUserQuery(requestDto, attachments);
        return ResponseEntity.ok(response);
    }
}
