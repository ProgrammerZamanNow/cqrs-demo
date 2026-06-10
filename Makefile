COMPOSE ?= podman compose
CONNECTOR := product-postgres-source

.PHONY: up down build clean register unregister status topics psql os-count logs bench

up:
	$(COMPOSE) up -d

build:
	$(COMPOSE) up -d --build

down:
	$(COMPOSE) down

clean:
	$(COMPOSE) down -v

# daftarkan Debezium connector (jalankan setelah stack up & backend selesai migrasi)
register:
	curl -fsS -X POST -H "Content-Type: application/json" \
		--data @debezium/products-connector.json \
		http://localhost:8083/connectors | jq .

unregister:
	curl -fsS -X DELETE http://localhost:8083/connectors/$(CONNECTOR) && echo "deleted"

status:
	@curl -fsS http://localhost:8083/connectors/$(CONNECTOR)/status | jq '{connector: .connector.state, tasks: [.tasks[] | {id, state}]}'

topics:
	podman exec -it cqrs-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

psql:
	psql postgresql://postgres:postgres@localhost:5433/product

os-count:
	@curl -fsS http://localhost:9200/products/_count | jq .

# benchmark 3 engine (PG naif / PG+trigram / OpenSearch) × search/facet
# OpenSearch di-warmup khusus dulu (cache dingin)
bench:
	python3 bench.py

logs:
	$(COMPOSE) logs -f --tail=100
