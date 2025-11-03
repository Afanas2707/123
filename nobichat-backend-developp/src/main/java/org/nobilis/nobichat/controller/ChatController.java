package org.nobilis.nobichat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.nobilis.nobichat.dto.PromptsHistoryDto;
import org.nobilis.nobichat.dto.ScenarioInfoListDto;
import org.nobilis.nobichat.dto.ScenarioNameListDto;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.UserChatSessionListDto;
import org.nobilis.nobichat.dto.error.GeneralErrorResponse;
import org.nobilis.nobichat.service.AttachmentService;
import org.nobilis.nobichat.service.ChatService;
import org.nobilis.nobichat.service.HistoryService;
import org.nobilis.nobichat.service.ScenarioManagementService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@SecurityRequirement(name = "tokenAuth")
@Tag(name = "Контроллер для запросов пользователя")
@Validated
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final HistoryService historyService;
    private final AttachmentService attachmentService;
    private final ScenarioManagementService scenarioManagementService;

    @GetMapping("/sessions/{sessionId}/continue")
    @Operation(
            summary = "Возобновить сессию",
            description = """
                    Возвращает респонс, который был последним в данной сессии.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ChatResponseDto.class))),
                    @ApiResponse(responseCode = "404", description = "Not Found (сессия с таким ID не найдена)", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden (попытка доступа к чужой сессии)", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class)))
            })
    public ResponseEntity<ChatResponseDto> resumeChatSession(
            @Parameter(description = "ID сессии, которую нужно возобновить", required = true)
            @PathVariable UUID sessionId) {

        ChatResponseDto response = chatService.resumeSession(sessionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/sessions/{sessionId}/prompts")
    @Operation(summary = "Получить историю сообщений для определённой сессии. Сообщения отсортированы в хронологическом порядке (от старых к новым)",
            description = """
                       Возвращает полный список сообщений (запросов пользователя и ответов AI) для указанной сессии.
                       Сообщения отсортированы в хронологическом порядке (от старых к новым).
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PromptsHistoryDto.PromptsHistoryResponseDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class)))
            })
    public ResponseEntity<PromptsHistoryDto.PromptsHistoryResponseDto> getSessionPrompts(
            @Parameter(description = "ID сессии, для которой нужно получить историю", required = true)
            @PathVariable UUID sessionId) {

        PromptsHistoryDto.PromptsHistoryResponseDto history = historyService.getSessionHistory(sessionId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/sessions")
    @Operation(summary = "Получить список всех сессий текущего пользователя",
            description = "Возвращает список всех чат-сессий, начатых текущим пользователем, отсортированных по дате последнего обновления.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class)))
            })
    public ResponseEntity<UserChatSessionListDto> getCurrentUserSessions() {
        UserChatSessionListDto sessions = historyService.getSessionsForCurrentUser();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/sessions/{sessionId}/prompts/{promptId}/attachments/{attachmentId}")
    @Operation(summary = "Скачать прикрепленный файл",
            description = "Позволяет скачать файл, прикрепленный к конкретному сообщению в чате. Доступ разрешен только владельцу сессии.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class)))
            })
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

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Удалить сессию чата",
            description = "Удаляет указанную сессию чата вместе со всеми связанными с ней сообщениями. Пользователь может удалить только свою собственную сессию.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No Content (сессия успешно удалена)"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class)))
            })
    public ResponseEntity<Void> deleteChatSession(@PathVariable UUID sessionId) {
        historyService.deleteUserChatSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/scenarios")
    @Operation(
            summary = "Получить список названий всех сценариев",
            description = "Возвращает массив с названиями всех опубликованных и доступных для запуска сценариев.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ScenarioNameListDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class)))
            })
    public ResponseEntity<ScenarioNameListDto> getAllScenarioNames() {
        ScenarioNameListDto response = scenarioManagementService.getAllScenarioNames();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/scenarios/v2")
    @Operation(
            summary = "Получить список всех сценариев",
            description = "Возвращает массив объектов с ID и названиями всех опубликованных и доступных для запуска сценариев.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = ScenarioInfoListDto.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "403", description = "Forbidden", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Not Found", content = @Content(schema = @Schema(implementation = GeneralErrorResponse.class)))
            })
    public ResponseEntity<ScenarioInfoListDto> getAllScenarioInfo() {
        ScenarioInfoListDto response = scenarioManagementService.getAllScenarioInfo();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/scenarios/{scenarioId}")
    @Operation(
            summary = "Удалить опубликованный сценарий",
            description = "Удаляет сценарий по его уникальному идентификатору",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No Content (сценарий успешно удален)"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized"),
                    @ApiResponse(responseCode = "404", description = "Not Found (сценарий с таким ID не найден)")
            })
    public ResponseEntity<Void> deleteScenario(
            @Parameter(description = "ID сценария, который нужно удалить", required = true)
            @PathVariable UUID scenarioId) {

        scenarioManagementService.deleteScenario(scenarioId);
        return ResponseEntity.noContent().build();
    }
}
