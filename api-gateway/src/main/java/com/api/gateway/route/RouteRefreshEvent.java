package com.api.gateway.route;

import org.springframework.context.ApplicationEvent;

/**
 * Event published khi admin thay đổi route config.
 * DatabaseRouteLocator lắng nghe event này để invalidate cache.
 */
public class RouteRefreshEvent extends ApplicationEvent {

    public RouteRefreshEvent(Object source) {
        super(source);
    }
}
