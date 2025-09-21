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
import org.nobilis.nobichat.dto.chat.AddChatMessageRequestDto;
import org.nobilis.nobichat.dto.chat.SwitchToPromptRequestDto;
import org.nobilis.nobichat.dto.chat.ChatMessageDto;
import org.nobilis.nobichat.dto.chat.ChatMessageListDto;
import org.nobilis.nobichat.dto.chat.ChatMessageListDtoV2;
import org.nobilis.nobichat.dto.chat.ChatRequest;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.NavigateRequestDto;
import org.nobilis.nobichat.dto.chat.NavigationResponseDto;
import org.nobilis.nobichat.dto.chat.UserChatSessionListDto;
import org.nobilis.nobichat.dto.error.GeneralErrorResponse;
import org.nobilis.nobichat.service.AttachmentService;
import org.nobilis.nobichat.service.ChatService;
import org.nobilis.nobichat.service.HistoryService;
import org.nobilis.nobichat.service.NavigationService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@SecurityRequirement(name = "tokenAuth")
@Tag(name = "Контроллер для запросов пользователя")
@Validated
@RequiredArgsConstructor
public class ChatController {

    private final NavigationService navigationService;
    private final ChatService chatService;
    private final HistoryService historyService;
    private final AttachmentService attachmentService;

    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @Operation(summary = "Отправка промпта пользователя в чат. В ответ получает шаблон, который надо будет отрисовывать на UI",
            description = """
                    Отправляет сообщение в чат в строгом режиме.
                    - Если `sessionId` равен `null` или отсутствует, будет создана новая задача чата.
                    - Если `sessionId` указан, будет отправлен новый промпт в уже существующую задачу чата.
                    - Если `useCache` равен `false`, кэш будет проигнорирован и результат будет пересчитан.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(schema = @Schema(implementation = ChatResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<ChatResponseDto> processChatMessage(
            @RequestPart("requestDto") @Valid ChatRequest requestDto,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {

        ChatResponseDto response = chatService.processUserQuery(requestDto, attachments);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions/{sessionId}/prompts/{promptId}/attachments/{attachmentId}")
    @Operation(
            summary = "Скачать прикрепленный файл",
            description = """
                    Позволяет скачать файл, прикрепленный к конкретному сообщению в чате.
                                        
                    Для выполнения запроса необходимо указать идентификаторы сессии, сообщения (промпта) и самого файла (аттача).
                                        
                    Система выполняет проверку прав доступа:
                    - Пользователь должен быть владельцем сессии, к которой относится файл.
                    - Идентификаторы сессии, сообщения и файла должны образовывать корректную иерархию.
                                        
                    В случае успеха возвращает бинарные данные файла.
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "OK. Файл успешно найден и отправлен.",
                            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                                    schema = @Schema(type = "string", format = "binary", description = "Бинарное содержимое файла."))
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Доступ запрещен. Пользователь не является владельцем сессии или пытается получить доступ к чужому файлу.",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Ресурс не найден. Указанная комбинация `sessionId`, `promptId` и `attachmentId` не существует.",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))
                    )
            }
    )
    public ResponseEntity<Resource> downloadAttachment(@PathVariable UUID sessionId,
                                                       @PathVariable UUID promptId,
                                                       @PathVariable UUID attachmentId) {
        AttachmentService.DownloadableAttachment downloadableAttachment =
                attachmentService.getAttachmentForDownload(sessionId, promptId, attachmentId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadableAttachment.getOriginalFileName() + "\"")
                .contentType(MediaType.parseMediaType(downloadableAttachment.getContentType()))
                .contentLength(downloadableAttachment.getFileSize())
                .body(downloadableAttachment.getResource());
    }


    @PreAuthorize("hasAnyRole('ADMIN', 'GIGA_ADMIN', 'EDITOR')")
    @PostMapping(path = "/soft-mode", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Динамическая генерация UI в 'мягком' режиме на основе промпта",
            description = """
                    Отправляет сообщение в чат для динамической генерации интерфейса.
                    Система анализирует запрос, определяет сущность и требуемые поля, 
                    а затем конструирует UI-схему на лету.
                    - Если `sessionId` равен `null` или отсутствует, будет создана новая задача чата.
                    - Если `sessionId` указан, будет отправлен новый промпт в уже существующую задачу чата.
                    - Если `useCache` равен `false`, кэш будет проигнорирован и результат будет пересчитан.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ChatResponseDto.class))),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<ChatResponseDto> processSoftModeChatMessage(
            @RequestPart("requestDto") @Valid ChatRequest requestDto,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        ChatResponseDto response = chatService.processSoftModeQuery(requestDto, attachments);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions")
    @Operation(summary = "Получить все прошлые сессии текущего пользователя",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<UserChatSessionListDto> getCurrentUserSessions() {
        UserChatSessionListDto sessions = historyService.getSessionsForCurrentUser();
        return ResponseEntity.ok(sessions);
    }

    @Deprecated
    @GetMapping("/sessions/{sessionId}/prompts")
    @Operation(summary = "Получить историю сообщений для определённой сессии. Сообщения отсортированы в хронологическом порядке (от старых к новым)",
            description = "Возвращает полный список сообщений (запросов пользователя и ответов AI) для указанной сессии. " +
                    "Сообщения отсортированы в хронологическом порядке (от старых к новым).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<ChatMessageListDto> getSessionMessages(@PathVariable UUID sessionId) {
        ChatMessageListDto messages = historyService.getMessagesForSession(sessionId);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/sessions/{sessionId}/prompts/v2")
    @Operation(summary = "Получить историю сообщений для определённой сессии. Сообщения отсортированы в хронологическом порядке (от старых к новым)",
            description = "Возвращает полный список сообщений (запросов пользователя и ответов AI) для указанной сессии. " +
                    "Сообщения отсортированы в хронологическом порядке (от старых к новым).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<ChatMessageListDtoV2> getSessionMessagesV2(@PathVariable UUID sessionId) {
        ChatMessageListDtoV2 messages = historyService.getMessagesForSessionV2(sessionId);
        return ResponseEntity.ok(messages);
    }

    @PostMapping(path = "/sessions/{sessionId}/newPrompt", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @Operation(summary = "Добавление сообщения в историю сессии чата",
            description = """
                    Позволяет вручную добавить сообщение в историю существующей сессии чата.
                    """,
            responses = {
                    @ApiResponse(responseCode = "201", description = "CREATED"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<ChatMessageDto> addChatMessageToHistory(
            @RequestPart("requestDto") @Valid AddChatMessageRequestDto requestDto,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {

        ChatMessageDto createdMessage = historyService.addMessageToSession(requestDto, attachments);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdMessage);
    }


    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Удалить сессию чата и всю ее историю",
            description = "Удаляет указанную сессию чата вместе со всеми связанными с ней сообщениями. " +
                    "Пользователь может удалить только свою собственную сессию.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "NO_CONTENT"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<Void> deleteChatSession(@PathVariable UUID sessionId) {
        historyService.deleteUserChatSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{sessionId}/history/navigate")

    @Operation(summary = "Перемещение по истории UI-состояний сессии",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<NavigationResponseDto> navigateHistory(
            @Parameter(description = "ID сессии чата", required = true)
            @PathVariable UUID sessionId,

            @Parameter(description = "Объект с направлением навигации", required = true)
            @Valid @RequestBody NavigateRequestDto request) {

        NavigationResponseDto responseDto = navigationService.navigate(sessionId, request.getDirection());

        return ResponseEntity.ok(responseDto);
    }

    @PostMapping("/sessions/{sessionId}/history/switch")
    @Operation(summary = "Прямое переключение на определенное UI-состояние в истории",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<NavigationResponseDto> switchToHistoryPrompt(
            @Parameter(description = "ID сессии чата", required = true)
            @PathVariable UUID sessionId,

            @Parameter(description = "Объект с ID целевого промпта", required = true)
            @Valid @RequestBody SwitchToPromptRequestDto request) {

        NavigationResponseDto responseDto = navigationService.switchTo(sessionId, request.getPromptId());

        return ResponseEntity.ok(responseDto);
    }

    @GetMapping("/sessions/{sessionId}/active-ui")
    @Operation(summary = "Получить текущее активное UI-состояние для сессии",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad request",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Отсутствует или некорректный заголовок Authorization",
                            content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
            })
    public ResponseEntity<NavigationResponseDto> getActiveUiForSession(@PathVariable UUID sessionId) {
        Optional<NavigationResponseDto> responseDto = historyService.getActiveUiState(sessionId);

        return responseDto
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}
