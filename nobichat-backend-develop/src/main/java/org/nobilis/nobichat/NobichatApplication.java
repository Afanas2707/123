package org.nobilis.nobichat;

import org.nobilis.nobichat.util.AiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@EnableConfigurationProperties(AiProperties.class)
@EnableScheduling
@EnableAsync
@EnableTransactionManagement
@EnableFeignClients
@SpringBootApplication
public class NobichatApplication {

	public static void main(String[] args) {
		SpringApplication.run(NobichatApplication.class, args);
	}
}
