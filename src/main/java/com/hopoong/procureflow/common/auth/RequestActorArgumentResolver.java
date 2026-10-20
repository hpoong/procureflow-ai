package com.hopoong.procureflow.common.auth;

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

    /** DB 의 org_id·사용자 식별자 컬럼 길이(varchar(64))와 같아야 한다. */
    static final int MAX_ID_LENGTH = 64;

    // TODO: 헤더 연동 전 임시 고정값. 연동 시 제거
    private static final String FIXED_ORG_ID = "Org-001";
    private static final String FIXED_USER_ID = "User-001";

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
        // TODO: 임시로 헤더 대신 고정값을 쓴다. 연동 시 아래 return 으로 되돌린다.
        if (true) {
            return new RequestActor(FIXED_ORG_ID, FIXED_USER_ID);
        }
        return new RequestActor(parseId(webRequest.getHeader(ORG_HEADER)), parseId(webRequest.getHeader(USER_HEADER)));
    }

    private String parseId(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(CommonErrorCode.AUTH_REQUIRED);
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_ID_LENGTH) {
            throw new BusinessException(CommonErrorCode.AUTH_REQUIRED);
        }
        return trimmed;
    }
}
