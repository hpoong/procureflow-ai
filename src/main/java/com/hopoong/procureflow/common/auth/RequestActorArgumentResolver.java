package com.hopoong.procureflow.common.auth;

import java.util.UUID;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.hopoong.procureflow.common.error.BusinessException;
import com.hopoong.procureflow.common.error.CommonErrorCode;

public class RequestActorArgumentResolver implements HandlerMethodArgumentResolver {

    static final String ORG_HEADER = "X-Org-Id";
    static final String USER_HEADER = "X-User-Id";

    private final HeaderActorProperties properties;

    public RequestActorArgumentResolver(HeaderActorProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return RequestActor.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        if (!properties.enabled()) {
            throw new BusinessException(CommonErrorCode.AUTH_REQUIRED);
        }
        return new RequestActor(parseUuid(webRequest.getHeader(ORG_HEADER)), parseUuid(webRequest.getHeader(USER_HEADER)));
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(CommonErrorCode.AUTH_REQUIRED);
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.AUTH_REQUIRED);
        }
    }
}
