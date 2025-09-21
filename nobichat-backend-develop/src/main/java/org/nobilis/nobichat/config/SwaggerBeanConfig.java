package org.nobilis.nobichat.config;

import io.swagger.v3.oas.models.media.Encoding;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.support.MultipartResolutionDelegate;

import java.util.ArrayList;
import java.util.Optional;
/**
 * Конфигурация для исправления и настройки поведения springdoc-openapi.
 * <p>
 * Этот класс содержит обходные пути для двух известных проблем:
 * <ol>
 *     <li>
 *         <b>Некорректное отображение JSON-объектов в multipart/form-data запросах.</b>
 *         {@link #operationCustomizer} принудительно устанавливает Content-Type 'application/json'
 *         для параметров типа DTO, аннотированных как {@code @RequestPart}. Это позволяет
 *         Swagger UI корректно отрисовать схему JSON-объекта вместо простого текстового поля.
 *         <p>
 *         Решение основано на: <a href="https://github.com/springdoc/springdoc-openapi/issues/964#issuecomment-2168351468">
 *         springdoc-openapi issue #964</a> И <a href="https://github.com/swagger-api/swagger-ui/issues/6462#issuecomment-1069115246">
 *  *         swagger-ui issue #6462</a>
 *     </li>
 *     <li>
 *         <b>Предотвращение ошибки 415 Unsupported Media Type.</b>
 *         Конструктор добавляет 'application/octet-stream' в список поддерживаемых типов для
 *         стандартного {@code MappingJackson2HttpMessageConverter}. Это позволяет серверу принимать
 *         запросы с телом в формате JSON, но с некорректным заголовком
 *         Content-Type 'application/octet-stream', избегая немедленного отказа с ошибкой 415.
 *     </li>
 * </ol>
 */
@Configuration
public class SwaggerBeanConfig {

    public SwaggerBeanConfig(MappingJackson2HttpMessageConverter converter) {
        var supportedMediaTypes = new ArrayList<>(converter.getSupportedMediaTypes());
        supportedMediaTypes.add(new MediaType("application", "octet-stream"));
        converter.setSupportedMediaTypes(supportedMediaTypes);
    }

    @Bean
    public OperationCustomizer operationCustomizer(ConversionService conversionService, ObjectProvider<GroupedOpenApi> groupedOpenApis) {
        OperationCustomizer customizer = (operation, handlerMethod) -> {
            Optional.ofNullable(operation.getRequestBody())
                    .map(RequestBody::getContent)
                    .filter(content -> content.containsKey(MediaType.MULTIPART_FORM_DATA_VALUE))
                    .map(content -> content.get(MediaType.MULTIPART_FORM_DATA_VALUE))
                    .ifPresent(multipartFormData -> {
                        for (MethodParameter methodParameter : handlerMethod.getMethodParameters()) {
                            if (MultipartResolutionDelegate.isMultipartArgument(methodParameter)) {
                                // ignore MultipartFile parameters
                                continue;
                            }
                            RequestPart requestPart = methodParameter.getParameterAnnotation(RequestPart.class);
                            if (requestPart == null) {
                                // ignore parameters without @RequestPart annotation
                                continue;
                            }
                            if (conversionService.canConvert(TypeDescriptor.valueOf(String.class), new TypeDescriptor(methodParameter))) {
                                // ignore parameters that can be converted from String to a basic type by ObjectToStringHttpMessageConverter
                                continue;
                            }
                            String parameterName = requestPart.name();
                            if (!StringUtils.hasText(parameterName)) {
                                parameterName = methodParameter.getParameterName();
                            }
                            if (!StringUtils.hasText(parameterName)) {
                                parameterName = methodParameter.getParameter().getName();
                            }
                            if (StringUtils.hasText(parameterName)) {
                                multipartFormData.addEncoding(parameterName, new Encoding().contentType(MediaType.APPLICATION_JSON_VALUE));
                            }
                        }
                    });
            return operation;
        };
        groupedOpenApis.forEach(groupedOpenApi -> groupedOpenApi.getOperationCustomizers().add(customizer));
        return customizer;
    }
}
