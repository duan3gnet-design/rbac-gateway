package com.auth.service.event;

/**
 * Kafka event khi một Permission thay đổi.
 * Topic: rbac.permission.events
 *
 * @param eventType CREATED | UPDATED | DELETED
 * @param id        permission id
 * @param resource  tên resource (e.g. "products")
 * @param action    tên action   (e.g. "READ")
 * @param code      permission code = "resource:ACTION"
 */
public record PermissionEvent(
        RbacEventType eventType,
        Long id,
        String resource,
        String action,
        String code,
        String oldResource,
        String oldAction
) {}
