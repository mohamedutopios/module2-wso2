package com.utopios.bank.events;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
@EnableScheduling
public class EventsApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(EventsApiApplication.class, args);
    }
}

/**
 * Handler WebSocket qui diffuse des événements bancaires simulés.
 * Chaque channel envoie un événement toutes les 3-5 secondes.
 */
@Component
class EventsWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, Set<WebSocketSession>> channels = new ConcurrentHashMap<>();
    private final Random rand = new Random();

    private final String[] customers = {"CUST-001", "CUST-002", "CUST-003", "CUST-004"};
    private final String[] ibans = {
        "FR7630006000011234567890189", "FR7612345678901234567890123",
        "FR7630003000003456789012391", "FR7620041000018765432109876"
    };
    private final String[] fraudReasons = {
        "Transaction inhabituelle hors pays de résidence",
        "Montant atypique par rapport à l'historique",
        "Multiples transactions refusées en peu de temps",
        "Pattern correspondant à fraude connue",
        "Connexion depuis IP suspecte"
    };

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String channel = extractChannel(session);
        channels.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(session);
        System.out.println("WS client connected to " + channel + " - total: " + channels.get(channel).size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        channels.values().forEach(sessions -> sessions.remove(session));
    }

    private String extractChannel(WebSocketSession session) {
        return session.getUri() != null ? session.getUri().getPath() : "/unknown";
    }

    @Scheduled(fixedRate = 4000)
    public void pushTransactionEvents() {
        broadcast("/ws/transactions", String.format("""
            {"eventId":"EVT-%s","eventType":"TRANSACTION_CREATED","transactionId":"TX-%06d","fromIban":"%s","toIban":"%s","amount":%.2f,"timestamp":"%s"}""",
            UUID.randomUUID().toString().substring(0, 8),
            rand.nextInt(999999),
            ibans[rand.nextInt(ibans.length)],
            ibans[rand.nextInt(ibans.length)],
            rand.nextDouble() * 500,
            LocalDateTime.now()
        ));
    }

    @Scheduled(fixedRate = 7000)
    public void pushFraudAlerts() {
        String[] severities = {"LOW", "MEDIUM", "HIGH", "CRITICAL"};
        broadcast("/ws/fraud-alerts", String.format("""
            {"alertId":"ALERT-%s","severity":"%s","customerId":"%s","reason":"%s","transactionId":"TX-%06d","detectedAt":"%s"}""",
            UUID.randomUUID().toString().substring(0, 8),
            severities[rand.nextInt(severities.length)],
            customers[rand.nextInt(customers.length)],
            fraudReasons[rand.nextInt(fraudReasons.length)],
            rand.nextInt(999999),
            LocalDateTime.now()
        ));
    }

    private void broadcast(String channelPath, String message) {
        Set<WebSocketSession> sessions = channels.get(channelPath);
        if (sessions == null) return;
        TextMessage msg = new TextMessage(message);
        sessions.forEach(s -> {
            try {
                if (s.isOpen()) s.sendMessage(msg);
            } catch (Exception e) {
                // ignore closed sessions
            }
        });
    }
}

@Configuration
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {
    private final EventsWebSocketHandler handler;
    WebSocketConfig(EventsWebSocketHandler handler) { this.handler = handler; }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/transactions").setAllowedOrigins("*");
        registry.addHandler(handler, "/ws/fraud-alerts").setAllowedOrigins("*");
        registry.addHandler(handler, "/ws/accounts/**").setAllowedOrigins("*");
    }
}

@RestController
class HealthController {
    @GetMapping("/actuator/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "events-api",
            "channels", List.of("/ws/transactions", "/ws/fraud-alerts", "/ws/accounts/{customerId}/updates")
        );
    }
}
