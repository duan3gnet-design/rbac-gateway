package com.auth.service.event;

/**
 * Kafka event khi một Action thay đổi.
 * Topic: rbac.action.events
 *
 * @param eventType CREATED | UPDATED | DELETED
 * @param id        action id
 * @param name      tên action (e.g. "READ")
 */
public record ActionEvent(
        RbacEventType eventType,
        Long id,
        String name,
        String oldName
) {}
