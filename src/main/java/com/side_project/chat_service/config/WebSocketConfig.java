package com.side_project.chat_service.config;

import java.security.Principal;
import java.util.Map;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * WebSocket mot chieu: server day xuong client. Khong co @MessageMapping nao,
 * client gui tin bang REST (ADR-0001).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setHandshakeHandler(new UserIdHandshakeHandler())
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[] { 25_000, 25_000 })
                .setTaskScheduler(heartbeatScheduler());
        registry.setUserDestinationPrefix("/user");
    }

    /** Thieu 3 gioi han nay thi mot tab treo co the an het heap cua ca service. */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(10_000)
                .setSendBufferSizeLimit(512_000)
                .setMessageSizeLimit(64_000);
    }

    private static ThreadPoolTaskScheduler heartbeatScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    private static class UserIdHandshakeHandler extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(ServerHttpRequest request,
                                          WebSocketHandler wsHandler,
                                          Map<String, Object> attributes) {
            // shortcut: tin header X-User-Id, thay bang JWT khi bat auth
            String userId = request.getHeaders().getFirst("X-User-Id");
            if (userId == null || userId.isBlank()) {
                // shortcut: fallback userId qua query param de test tu browser (browser khong dat duoc header o WS handshake); bo khi bat auth
                userId = queryParam(request.getURI().getQuery(), "userId");
            }
            if (userId == null || userId.isBlank()) {
                return null;
            }
            String id = userId;
            return () -> id;
        }

        private static String queryParam(String query, String name) {
            if (query == null) {
                return null;
            }
            for (String pair : query.split("&")) {
                if (pair.startsWith(name + "=")) {
                    return pair.substring(name.length() + 1);
                }
            }
            return null;
        }
    }
}
