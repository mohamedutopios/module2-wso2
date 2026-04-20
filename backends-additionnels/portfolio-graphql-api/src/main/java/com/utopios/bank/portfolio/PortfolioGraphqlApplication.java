package com.utopios.bank.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * Mini serveur GraphQL fait main (sans graphql-java pour simplifier).
 * Supporte les queries : portfolio, position, marketPrice, holdings.
 * Backend pédagogique pour démo GraphQL dans WSO2 APIM.
 */
@SpringBootApplication
public class PortfolioGraphqlApplication {
    public static void main(String[] args) {
        SpringApplication.run(PortfolioGraphqlApplication.class, args);
    }
}

@RestController
class GraphqlController {

    // ========== DONNÉES SIMULÉES ==========
    private final Map<String, Map<String, Object>> portfolios = new HashMap<>();
    private final Map<String, BigDecimal> marketPrices = Map.of(
        "AAPL", new BigDecimal("182.45"),
        "MSFT", new BigDecimal("418.30"),
        "GOOGL", new BigDecimal("175.20"),
        "TSLA", new BigDecimal("245.80"),
        "BNP.PA", new BigDecimal("68.40"),
        "AIR.PA", new BigDecimal("172.15"),
        "MC.PA", new BigDecimal("695.20"),
        "OR.PA", new BigDecimal("385.60"),
        "SAN.PA", new BigDecimal("98.75")
    );

    public GraphqlController() {
        // Portfolio de Sophie Bernard (cliente WEALTH)
        portfolios.put("CUST-003", Map.of(
            "customerId", "CUST-003",
            "totalValue", new BigDecimal("127500.00"),
            "positions", List.of(
                Map.of("symbol", "AAPL", "quantity", 120, "avgPrice", new BigDecimal("165.00"), "sector", "Technology"),
                Map.of("symbol", "MSFT", "quantity", 50, "avgPrice", new BigDecimal("380.00"), "sector", "Technology"),
                Map.of("symbol", "MC.PA", "quantity", 30, "avgPrice", new BigDecimal("720.00"), "sector", "Luxury"),
                Map.of("symbol", "BNP.PA", "quantity", 200, "avgPrice", new BigDecimal("55.00"), "sector", "Banking"),
                Map.of("symbol", "OR.PA", "quantity", 45, "avgPrice", new BigDecimal("350.00"), "sector", "Cosmetics")
            )
        ));

        // Portfolio de Thomas Leroy (PREMIUM)
        portfolios.put("CUST-006", Map.of(
            "customerId", "CUST-006",
            "totalValue", new BigDecimal("45200.00"),
            "positions", List.of(
                Map.of("symbol", "AAPL", "quantity", 40, "avgPrice", new BigDecimal("170.00"), "sector", "Technology"),
                Map.of("symbol", "GOOGL", "quantity", 25, "avgPrice", new BigDecimal("160.00"), "sector", "Technology"),
                Map.of("symbol", "AIR.PA", "quantity", 60, "avgPrice", new BigDecimal("165.00"), "sector", "Aerospace")
            )
        ));

        // Portfolio vide pour Marie (RETAIL, pas de trading)
        portfolios.put("CUST-001", Map.of(
            "customerId", "CUST-001",
            "totalValue", BigDecimal.ZERO,
            "positions", Collections.emptyList()
        ));
    }

    @PostMapping("/graphql")
    public ResponseEntity<Map<String, Object>> graphql(@RequestBody Map<String, Object> request) {
        String query = (String) request.get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) request.getOrDefault("variables", new HashMap<>());

        Map<String, Object> response = new LinkedHashMap<>();
        Map<String, Object> data = new LinkedHashMap<>();

        try {
            if (query.contains("portfolio(") || query.contains("portfolio (")) {
                String customerId = extractArgument(query, "customerId", variables);
                data.put("portfolio", buildPortfolio(customerId, query));
            } else if (query.contains("marketPrice(") || query.contains("marketPrice (")) {
                String symbol = extractArgument(query, "symbol", variables);
                data.put("marketPrice", buildMarketPrice(symbol));
            } else if (query.contains("allHoldings")) {
                data.put("allHoldings", portfolios.values().stream().toList());
            } else if (query.contains("__schema")) {
                data.put("__schema", getIntrospectionSchema());
            } else {
                response.put("errors", List.of(Map.of(
                    "message", "Unknown query. Available : portfolio, marketPrice, allHoldings"
                )));
                return ResponseEntity.ok(response);
            }

            response.put("data", data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("errors", List.of(Map.of("message", e.getMessage())));
            return ResponseEntity.ok(response);
        }
    }

    private String extractArgument(String query, String argName, Map<String, Object> variables) {
        // Extraction basique : "argName: \"value\"" ou "argName: $varName"
        int idx = query.indexOf(argName + ":");
        if (idx < 0) return null;
        String rest = query.substring(idx + argName.length() + 1).trim();
        if (rest.startsWith("\"")) {
            int end = rest.indexOf("\"", 1);
            return rest.substring(1, end);
        } else if (rest.startsWith("$")) {
            int end = rest.indexOf(")");
            if (end < 0) end = rest.indexOf(",");
            String varName = rest.substring(1, end).trim();
            return (String) variables.get(varName);
        }
        return null;
    }

    private Map<String, Object> buildPortfolio(String customerId, String query) {
        Map<String, Object> p = portfolios.get(customerId);
        if (p == null) return null;

        Map<String, Object> result = new LinkedHashMap<>();
        if (query.contains("customerId")) result.put("customerId", p.get("customerId"));
        if (query.contains("totalValue")) result.put("totalValue", p.get("totalValue"));
        if (query.contains("positions")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> positions = (List<Map<String, Object>>) p.get("positions");
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> pos : positions) {
                Map<String, Object> posResult = new LinkedHashMap<>();
                if (query.contains("symbol")) posResult.put("symbol", pos.get("symbol"));
                if (query.contains("quantity")) posResult.put("quantity", pos.get("quantity"));
                if (query.contains("avgPrice")) posResult.put("avgPrice", pos.get("avgPrice"));
                if (query.contains("sector")) posResult.put("sector", pos.get("sector"));
                if (query.contains("currentPrice") || query.contains("marketValue")) {
                    BigDecimal price = marketPrices.getOrDefault(pos.get("symbol"), BigDecimal.ZERO);
                    if (query.contains("currentPrice")) posResult.put("currentPrice", price);
                    if (query.contains("marketValue")) {
                        int qty = (Integer) pos.get("quantity");
                        posResult.put("marketValue", price.multiply(new BigDecimal(qty)));
                    }
                }
                filtered.add(posResult);
            }
            result.put("positions", filtered);
        }
        return result;
    }

    private Map<String, Object> buildMarketPrice(String symbol) {
        BigDecimal price = marketPrices.get(symbol);
        if (price == null) return null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbol", symbol);
        result.put("price", price);
        result.put("currency", symbol.endsWith(".PA") ? "EUR" : "USD");
        result.put("asOf", java.time.LocalDateTime.now().toString());
        return result;
    }

    private Map<String, Object> getIntrospectionSchema() {
        return Map.of("types", List.of("Portfolio", "Position", "MarketPrice"));
    }

    @GetMapping("/actuator/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "portfolio-graphql", "portfoliosCount", portfolios.size());
    }

    @GetMapping("/schema")
    public ResponseEntity<String> schema() {
        String sdl = """
            type Query {
                portfolio(customerId: String!): Portfolio
                marketPrice(symbol: String!): MarketPrice
                allHoldings: [Portfolio!]!
            }

            type Portfolio {
                customerId: String!
                totalValue: Float!
                positions: [Position!]!
            }

            type Position {
                symbol: String!
                quantity: Int!
                avgPrice: Float!
                sector: String
                currentPrice: Float
                marketValue: Float
            }

            type MarketPrice {
                symbol: String!
                price: Float!
                currency: String!
                asOf: String!
            }
            """;
        return ResponseEntity.ok()
                .header("Content-Type", "text/plain; charset=utf-8")
                .body(sdl);
    }
}
