package com.auth.service.event;

/**
 * Kafka event khi một Resource thay đổi.
 * Topic: rbac.resource.events
 *
 * @param eventType CREATED | UPDATED | DELETED
 * @param id        resource id
 * @param name      tên resource (e.g. "products")
 */
public record ResourceEvent(
        RbacEventType eventType,
        Long id,
        String name,
        String oldName
) {}
