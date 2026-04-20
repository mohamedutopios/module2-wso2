#!/bin/bash
if [ "$1" = "--reset" ]; then
  echo "Reset complet : conteneurs + images + volumes"
  docker compose down -v
  docker rmi utopiosbank/accounts-api:1.0.0 utopiosbank/transactions-api:1.0.0 \
             utopiosbank/customers-api:1.0.0 utopiosbank/credit-scoring-api:1.0.0 \
             utopiosbank/notifications-api:1.0.0 utopiosbank/portfolio-graphql-api:1.0.0 \
             utopiosbank/async-events-api:1.0.0 2>/dev/null || true
  echo "✅ Reset effectué"
else
  docker compose down
  echo "✅ Stack arrêtée (les images restent pour un démarrage rapide)"
fi
