package com.hopoong.procureflow.common.auth;

import java.util.UUID;

public record RequestActor(UUID orgId, UUID userId) {
}
