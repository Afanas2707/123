package org.nobilis.nobichat.feign;

import feign.Logger;
import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class KonturFocusFeignClientConfiguration {

    @Value("${kontur.focus.api.key}")
    private String konturFocusApiKey;

    @Bean
    public RequestInterceptor konturFocusRequestInterceptor() {
        return template -> {
            template.query("key", konturFocusApiKey);
        };
    }

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
}
