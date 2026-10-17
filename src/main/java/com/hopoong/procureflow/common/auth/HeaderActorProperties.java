package com.hopoong.procureflow.common.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 인증 도입 전 임시 요청자 헤더 설정. enabled=true(local 전용)일 때만 X-Org-Id / X-User-Id 를 쓴다. */
@ConfigurationProperties("procureflow.auth.header-actor")
public record HeaderActorProperties(boolean enabled) {
}
