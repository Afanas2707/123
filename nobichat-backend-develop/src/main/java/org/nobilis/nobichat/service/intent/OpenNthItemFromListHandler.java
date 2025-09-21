package org.nobilis.nobichat.service.intent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nobilis.nobichat.dto.ChatContext;
import org.nobilis.nobichat.dto.chat.ChatResponseDto;
import org.nobilis.nobichat.dto.chat.softMode.IntentAndQueryResponse;
import org.nobilis.nobichat.dto.entities.EntitiesSearchRequestDto;
import org.nobilis.nobichat.dto.entities.PaginatedEntitiesResponseDto;
import org.nobilis.nobichat.model.Template;
import org.nobilis.nobichat.repository.TemplateRepository;
import org.nobilis.nobichat.service.DynamicEntityQueryService;
import org.nobilis.nobichat.service.DynamicViewGeneratorService;
import org.nobilis.nobichat.service.OntologyService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.UUID;

/**
 * Обработчик намерения "OPEN_NTH_ITEM_FROM_LIST".
 * Позволяет открыть карточку конкретного элемента по его номеру в текущем списке.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenNthItemFromListHandler implements IntentHandler {

    private final ObjectMapper objectMapper;
    private final DynamicViewGeneratorService dynamicViewGeneratorService;
    private final OntologyService ontologyService;
    private final DynamicEntityQueryService dynamicEntityQueryService;
    private final TemplateRepository templateRepository;

    @Override
    public String getIntentType() {
        return "OPEN_NTH_ITEM_FROM_LIST";
    }

    @Override
    public ChatResponseDto handle(IntentAndQueryResponse intent, ChatContext context) {
        // Получаем текущую UI-схему сессии для анализа и в случае ошибки
        JsonNode currentSchema = context.getCurrentUiMessage() != null && context.getCurrentUiMessage().getTemplate() != null
                ? context.getCurrentUiMessage().getTemplate().getSchema() : null;

        // Проверка, что LLM вернула корректный номер элемента
        if (intent.getItemIndex() == null || intent.getItemIndex() <= 0) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Пожалуйста, укажите корректный номер элемента для открытия (например, 'Открой 3-й').", null);
        }

        // 1. Проверяем, что сейчас открыт список
        if (currentSchema == null) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Я не могу определить, какой список вы сейчас просматриваете. Пожалуйста, сначала откройте список сущностей.", null);
        }

        JsonNode viewNode = currentSchema.path("view");
        JsonNode appletNode = viewNode.at("/applets/0"); // Предполагаем, что ListApplet находится в applets[0]

        String currentViewId = viewNode.path("id").asText();
        String currentAppletType = appletNode.path("type").asText();

        // Проверяем, что это вид списка (ListView) или ListApplet внутри какой-либо формы
        boolean isListView = currentViewId.endsWith(".list.view") || "ListApplet".equals(currentAppletType);

        if (!isListView) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Сейчас открыта не страница со списком. Эта команда работает только при просмотре списка.", null);
        }

        String listEntityName = viewNode.path("entity").asText(null); // Сущность текущего списка
        if (!StringUtils.hasText(listEntityName)) {
            return new ChatResponseDto(context.getSession().getId(), currentSchema, "Не удалось определить, какой список вы просматриваете.", null);
        }
        // Если LLM явно указала сущность, используем ее, иначе - из текущего UI (для гибкости)
        if (StringUtils.hasText(intent.getEntity()) && ontologyService.entityExists(intent.getEntity())) {
            listEntityName = intent.getEntity();
        }

        EntitiesSearchRequestDto.QueryDto currentListQuery = null;
        // Парсим существующие фильтры (query) из текущей UI-схемы
        JsonNode queryNode = appletNode.at("/source/body/query");
        if (queryNode.isObject() && !queryNode.isEmpty()) {
            try {
                currentListQuery = objectMapper.treeToValue(queryNode, EntitiesSearchRequestDto.QueryDto.class);
            } catch (JsonProcessingException e) {
                log.error("Ошибка при парсинге query из текущей UI-схемы для list view: {}", e.getMessage(), e);
                // Продолжаем без query, если не удалось распарсить
            }
        }

        // Парсим параметры сортировки из текущей UI-схемы
        String sortBy = appletNode.at("/source/sortBy").asText(null);
        String sortOrder = appletNode.at("/source/sortOrder").asText(null);

        Sort sort = Sort.unsorted();
        if (StringUtils.hasText(sortBy)) {
            Sort.Direction direction = Sort.Direction.fromString(
                    StringUtils.hasText(sortOrder) ? sortOrder : "ASC"
            );
            sort = Sort.by(direction, sortBy);
        } else {
            // Дефолтная сортировка, если не указана (аналогично EntityDataController)
            sort = Sort.by(Sort.Direction.ASC, "id");
        }

        // 2. Вычисляем параметры для запроса конкретного элемента
        int itemIndex = intent.getItemIndex(); // 1-based index
        int pageSize = 1; // Нам нужен только один элемент
        int pageNumber = itemIndex; // Для 1-го элемента page=1, для 3-го page=3

        // 3. Получаем ID нужного элемента
        // Spring PageRequest 0-based, поэтому (itemIndex - 1)
        Pageable pageable = PageRequest.of(pageNumber - 1, pageSize, sort);
        PaginatedEntitiesResponseDto paginatedResult = dynamicEntityQueryService.findEntities(
                listEntityName,
                Collections.singletonList("id"), // Нам нужен только ID для открытия карточки
                currentListQuery,
                pageable
        );

        if (paginatedResult.getContent().isEmpty()) {
            String userFriendlyEntityName = ontologyService.getEntityMetaData(listEntityName).getUserFriendlyNamePluralGenitive();
            return new ChatResponseDto(context.getSession().getId(), currentSchema,
                    String.format("Элемент номер %d не найден в текущем списке %s. Возможно, такого номера нет или список отфильтрован.", itemIndex, userFriendlyEntityName), null);
        }

        // Получаем ID найденного элемента
        UUID targetEntityId = UUID.fromString(String.valueOf(paginatedResult.getContent().get(0).get("id")));
        log.info("Найден элемент ID: {} для открытия FormView.", targetEntityId);

        // 4. Генерируем FormView для найденного элемента
        JsonNode formViewSchema = dynamicViewGeneratorService.generateFormView(listEntityName, targetEntityId);
        String successMessage = String.format("Открываю карточку %s.", ontologyService.getEntityMetaData(listEntityName).getUserFriendlyNameAccusativeLowercase());

        // Сохраняем сгенерированный шаблон и привязываем его к сообщению чата
        Template generatedTemplate = Template.builder()
                .schema(formViewSchema)
                .mode(context.getChatMode())
                .build();
        templateRepository.save(generatedTemplate);
        context.getChatMessage().setTemplate(generatedTemplate);

        return new ChatResponseDto(context.getSession().getId(), formViewSchema, successMessage, null);
    }
}