package org.nobilis.nobichat.feign;

import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ConfluenceClientConfig {

    @Value("${confluence.api.token}")
    private String confluenceApiToken;

    @Bean
    public RequestInterceptor bearerTokenRequestInterceptor() {
        return requestTemplate -> {
            String authToken = "Bearer " + confluenceApiToken;
            requestTemplate.header("Authorization", authToken);
            log.debug("Добавлен заголовок Bearer Token для запроса к Confluence.");
        };
    }
}