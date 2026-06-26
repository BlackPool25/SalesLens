#!/usr/bin/env python3
"""
Kafka Test Producer — publishes fake sales events to the `sales.live` topic.

Usage:
    python3 scripts/kafka_test_producer.py
    python3 scripts/kafka_test_producer.py --count 50
    python3 scripts/kafka_test_producer.py --count 5 --dry-run
    python3 scripts/kafka_test_producer.py --bootstrap-servers kafka:29092
"""

import argparse
import json
import random
import sys
import time
import uuid
from datetime import datetime, timezone, timedelta

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

TOPIC = "sales.live"
DEFAULT_COUNT = 100
DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092"
PROGRESS_INTERVAL = 10

SOURCE_SYSTEMS = ["pos_terminal_01", "pos_terminal_02", "pos_terminal_03"]

SALESPEOPLE = ["SP-07", "SP-12", "SP-19", "SP-23", "SP-31"]

REGIONS = ["East", "West", "Central", "South", "Northwest"]

PRODUCT_SKUS = [
    "SKU-ALPHA-001", "SKU-BRAVO-002", "SKU-CHARLIE-003", "SKU-DELTA-004",
    "SKU-ECHO-005", "SKU-FOXTROT-006", "SKU-GOLF-007", "SKU-HOTEL-008",
    "SKU-INDIA-009", "SKU-JULIETT-010", "SKU-KILO-011", "SKU-LIMA-012",
    "SKU-MIKE-013", "SKU-NOVEMBER-014", "SKU-OSCAR-015", "SKU-PAPA-016",
    "SKU-QUEBEC-017", "SKU-ROMEO-018", "SKU-SIERRA-019", "SKU-TANGO-020",
]

# Starting customer reference — will increment by 1 for each event
CUSTOMER_START = 9912


# ---------------------------------------------------------------------------
# Event generation
# ---------------------------------------------------------------------------

def generate_events(count: int) -> list[dict]:
    """
    Build a list of *count* fake sales events with sequential timestamps
    going backwards from *now*, one second apart.
    """
    now = datetime.now(timezone.utc)
    events: list[dict] = []

    for i in range(count):
        customer_num = CUSTOMER_START + i
        quantity = random.randint(1, 10)
        unit_price = round(random.uniform(10.0, 500.0), 2)
        total_amount = round(quantity * unit_price, 2)

        event = {
            "event_id": str(uuid.uuid4()),
            "source_system": random.choice(SOURCE_SYSTEMS),
            "event_time": (now - timedelta(seconds=i)).isoformat(),
            "customer_ref": f"C-{customer_num}",
            "product_ref": random.choice(PRODUCT_SKUS),
            "salesperson_ref": random.choice(SALESPEOPLE),
            "quantity": quantity,
            "unit_price": unit_price,
            "total_amount": total_amount,
            "currency": "USD",
            "region": random.choice(REGIONS),
        }
        events.append(event)

    return events


# ---------------------------------------------------------------------------
# Publishing
# ---------------------------------------------------------------------------

def publish_events(
    events: list[dict],
    *,
    bootstrap_servers: str,
    dry_run: bool = False,
) -> None:
    """Send *events* to Kafka, or print them in dry-run mode."""

    if dry_run:
        print(f"[DRY-RUN] Would publish {len(events)} messages to topic '{TOPIC}':")
        print()
        for idx, event in enumerate(events, start=1):
            print(f"  [{idx:>4}] {json.dumps(event)}")
        print()
        print(f"[DRY-RUN] Total: {len(events)} messages.")
        return

    try:
        from kafka import KafkaProducer
    except ImportError:
        print("ERROR: kafka-python is not installed.", file=sys.stderr)
        print("       Run: pip install kafka-python", file=sys.stderr)
        sys.exit(1)

    producer = KafkaProducer(
        bootstrap_servers=bootstrap_servers,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        acks="all",
        retries=3,
    )

    sent_count = 0
    for event in events:
        future = producer.send(TOPIC, value=event)
        # Block until acknowledged (or exception)
        future.get(timeout=10)
        sent_count += 1

        if sent_count % PROGRESS_INTERVAL == 0:
            print(f"  Progress: {sent_count}/{len(events)} messages sent.")

    producer.flush()
    producer.close()
    print(f"Done. Published {sent_count} messages to topic '{TOPIC}'.")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Publish fake sales events to the sales.live Kafka topic.",
    )
    parser.add_argument(
        "--count",
        type=int,
        default=DEFAULT_COUNT,
        help=f"Number of events to generate (default: {DEFAULT_COUNT})",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print generated events without sending to Kafka",
    )
    parser.add_argument(
        "--bootstrap-servers",
        type=str,
        default=DEFAULT_BOOTSTRAP_SERVERS,
        help=f"Kafka bootstrap server(s) (default: {DEFAULT_BOOTSTRAP_SERVERS})",
    )
    return parser.parse_args(argv)


def main() -> None:
    args = parse_args()
    events = generate_events(args.count)

    publish_events(
        events,
        bootstrap_servers=args.bootstrap_servers,
        dry_run=args.dry_run,
    )


if __name__ == "__main__":
    main()
