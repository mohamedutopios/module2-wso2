#!/bin/bash
set -e

echo "╔════════════════════════════════════════════════════════════╗"
echo "║  Stack Design & Création API — 7 microservices + WSO2     ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""

# Vérifier que les 7 dossiers backends sont présents
MISSING=0
for svc in accounts-api transactions-api customers-api credit-scoring-api notifications-api portfolio-graphql-api async-events-api; do
  if [ ! -d "$svc" ]; then
    echo "❌ Dossier manquant : $svc"
    MISSING=1
  fi
done

if [ $MISSING -eq 1 ]; then
  echo ""
  echo "Copier d'abord les backends manquants :"
  echo "  cp -r ../utopios-backends/{accounts-api,transactions-api,customers-api,credit-scoring-api,notifications-api} ."
  echo "  cp -r backends-additionnels/{portfolio-graphql-api,async-events-api} ."
  exit 1
fi

command -v docker >/dev/null || { echo "❌ Docker manquant"; exit 1; }
docker info >/dev/null 2>&1 || { echo "❌ Docker Desktop pas lancé"; exit 1; }

echo "=== Build de 7 microservices (10-15 min au premier lancement) ==="
docker compose build

echo ""
echo "=== Démarrage de la stack ==="
docker compose up -d

echo ""
echo "=== Attente des services ==="
for i in $(seq 1 50); do
  all_up=true
  for svc in wso2am accounts-api transactions-api customers-api credit-scoring-api notifications-api portfolio-graphql-api async-events-api; do
    status=$(docker inspect --format='{{.State.Health.Status}}' $svc 2>/dev/null || echo "starting")
    if [ "$status" != "healthy" ]; then
      all_up=false
    fi
  done
  printf "  [%02d/50] " $i
  for svc in wso2am accounts-api transactions-api customers-api credit-scoring-api notifications-api portfolio-graphql-api async-events-api; do
    s=$(docker inspect --format='{{.State.Health.Status}}' $svc 2>/dev/null || echo "?")
    short=${svc:0:6}
    printf "%s:%s " $short $s
  done
  echo ""
  if $all_up; then
    echo "  ✅ Tout est prêt"
    break
  fi
  sleep 15
done

echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║                   ✅ Stack démarrée                        ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "🔐 WSO2 Portails (admin / admin) :"
echo "   Publisher  : https://localhost:9443/publisher"
echo "   DevPortal  : https://localhost:9443/devportal"
echo "   Admin      : https://localhost:9443/admin"
echo ""
echo "🏦 Microservices REST :"
echo "   Accounts        : http://localhost:8081/swagger-ui.html"
echo "   Transactions    : http://localhost:8082/swagger-ui.html"
echo "   Customers       : http://localhost:8083/swagger-ui.html"
echo "   Credit Scoring  : http://localhost:8084/swagger-ui.html"
echo "   Notifications   : http://localhost:8085/swagger-ui.html"
echo ""
echo "📊 Backend GraphQL :"
echo "   Portfolio       : http://localhost:8086/graphql"
echo "                     http://localhost:8086/schema  (SDL)"
echo ""
echo "📡 Backend WebSocket streaming :"
echo "   Events          : ws://localhost:8087/ws/fraud-alerts"
echo "                     ws://localhost:8087/ws/transactions"
echo ""
echo "📡 WebSocket via WSO2 Gateway :"
echo "   WS  : ws://localhost:9021/events/1.0.0/..."
echo "   WSS : wss://localhost:9099/events/1.0.0/..."
echo ""
