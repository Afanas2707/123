package org.nobilis.nobichat.aspect;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DtoFieldFilterService {

    private final ObjectMapper objectMapper;

    /**
     * Рекурсивно фильтрует DTO, оставляя только поля, указанные в fieldsToKeep.
     * Поддерживает вложенность через точку (например, "contacts.fullName").
     *
     * @param dtoObject    Объект DTO для фильтрации.
     * @param fieldsToKeep Множество имен полей, которые нужно оставить.
     * @return Map<String, Object> с отфильтрованными данными.
     */
    public Map<String, Object> filter(Object dtoObject, Set<String> fieldsToKeep) {
        if (dtoObject == null) {
            return new HashMap<>();
        }
        Map<String, Object> sourceMap = objectMapper.convertValue(dtoObject, new TypeReference<>() {});
        return filterRecursively(sourceMap, fieldsToKeep, "");
    }

    private Map<String, Object> filterRecursively(Map<String, Object> sourceMap, Set<String> fieldsToKeep, String currentPath) {
        Map<String, Object> filteredMap = new HashMap<>();

        for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
            String fieldName = entry.getKey();
            Object fieldValue = entry.getValue();
            String fullPath = currentPath.isEmpty() ? fieldName : currentPath + "." + fieldName;

            if (isPathRequested(fullPath, fieldsToKeep)) {
                if (fieldValue instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nestedMap = (Map<String, Object>) fieldValue;
                    Map<String, Object> filteredNestedMap = filterRecursively(nestedMap, fieldsToKeep, fullPath);
                    if (!filteredNestedMap.isEmpty()) {
                        filteredMap.put(fieldName, filteredNestedMap);
                    }
                } else if (fieldValue instanceof Collection) {
                    Collection<?> collection = (Collection<?>) fieldValue;
                    List<Object> filteredList = new ArrayList<>();
                    for (Object item : collection) {
                        if (item instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> itemMap = (Map<String, Object>) item;
                            Map<String, Object> filteredItem = filterRecursively(itemMap, fieldsToKeep, fullPath);
                            if (!filteredItem.isEmpty()) {
                                filteredList.add(filteredItem);
                            }
                        } else {
                            filteredList.add(item);
                        }
                    }
                    if (!filteredList.isEmpty()) {
                        filteredMap.put(fieldName, filteredList);
                    }
                } else {
                    filteredMap.put(fieldName, fieldValue);
                }
            }
        }
        return filteredMap;
    }

    /**
     * Проверяет, соответствует ли текущий путь или любой из его дочерних путей
     * запрошенным полям.
     * Например, если fieldsToKeep = {"contacts.name"}, то isPathRequested("contacts", ...) вернет true.
     */
    private boolean isPathRequested(String currentPath, Set<String> fieldsToKeep) {
        if (fieldsToKeep.contains(currentPath)) {
            return true;
        }
        String prefix = currentPath + ".";
        for (String field : fieldsToKeep) {
            if (field.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
