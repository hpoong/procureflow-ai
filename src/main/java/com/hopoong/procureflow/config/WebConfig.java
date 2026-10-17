package com.hopoong.procureflow.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.hopoong.procureflow.common.auth.HeaderActorProperties;
import com.hopoong.procureflow.common.auth.RequestActorArgumentResolver;

@Configuration
@EnableConfigurationProperties(HeaderActorProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final HeaderActorProperties headerActorProperties;

    public WebConfig(HeaderActorProperties headerActorProperties) {
        this.headerActorProperties = headerActorProperties;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequestActorArgumentResolver(headerActorProperties));
    }
}
