package com.hopoong.procureflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI procureflowOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("ProcureFlow AI API")
						.description("발주 · 입고 · 수입검사 · 재고")
						.version("v1"));
	}
}
