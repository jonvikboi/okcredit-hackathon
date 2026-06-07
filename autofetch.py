
import websocket
import json
import gzip
import base64
import csv
import time
from datetime import datetime

URL = "ws://ambicaaspot.com:1001/bullion?user=ambicaa&auth=1&type=web"
CSV_FILE = "ambicaa_rates.csv"


def decode_payload(encoded):
    try:
        compressed = base64.b64decode(encoded)
        decompressed = gzip.decompress(compressed)
        return json.loads(decompressed.decode("utf-8"))
    except Exception as e:
        print("Decode Error:", e)
        return None


def save_to_csv(products):
    with open(CSV_FILE, "a", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)

        for item in products:
            writer.writerow([
                datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                item.get("Name", item.get("symbol", "")),
                item.get("Bid", ""),
                item.get("Ask", ""),
                item.get("LTP", ""),
                item.get("High", ""),
                item.get("Low", "")
            ])


def print_products(products):
    print("\n" + "=" * 120)
    print("TIME:", datetime.now().strftime("%H:%M:%S"))
    print("=" * 120)

    for item in products:
        name = item.get("Name", item.get("symbol", "UNKNOWN"))

        print(
            f"{name:20} | "
            f"Bid={item.get('Bid','')} | "
            f"Ask={item.get('Ask','')} | "
            f"LTP={item.get('LTP','')}"
        )


def parse_ref_products(ref_products):
    parsed = []

    for item in ref_products:

        # Item is usually a JSON string
        if isinstance(item, str):
            try:
                item = json.loads(item)
            except Exception:
                continue

        if isinstance(item, dict):
            parsed.append(item)

    return parsed


def process_message(msg):

    for part in msg.split("\x1e"):

        if not part.strip():
            continue

        try:
            data = json.loads(part)

            target = data.get("target")

            if target not in [
                "workerPublish",
                "workerPublishCoin",
                "contactDetails",
                "cityDetails",
                "referanceDetails",
                "symbolDetails"
            ]:
                continue

            encoded = data["arguments"][0]

            decoded = decode_payload(encoded)

            if not decoded:
                continue

            # Uncomment for debugging
            # print("\nTARGET:", target)
            # print(json.dumps(decoded, indent=2)[:1000])

            if "refProduct" in decoded:

                products = parse_ref_products(
                    decoded["refProduct"]
                )

                if products:
                    print_products(products)
                    save_to_csv(products)

        except Exception as e:
            print("Processing Error:", e)


def connect():

    ws = websocket.create_connection(URL)

    print("Connected")

    # SignalR handshake
    ws.send('{"protocol":"json","version":1}\x1e')

    handshake = ws.recv()
    print("Handshake:", handshake)

    # Subscribe
    subscribe = {
        "arguments": ["ambicaa"],
        "invocationId": "0",
        "target": "client",
        "type": 1
    }

    ws.send(json.dumps(subscribe) + "\x1e")

    print("Subscribed")

    while True:

        try:
            message = ws.recv()

            if not message:
                break

            process_message(message)

        except Exception as e:
            print("Receive Error:", e)
            break

if __name__ == "__main__":

    print("Starting Ambicaa Live Feed...\n")

    while True:

        try:
            connect()

        except KeyboardInterrupt:
            print("\nStopped by user.")
            break

        except Exception as e:
            print("Connection Error:", e)

        print("Reconnecting in 5 seconds...")
        time.sleep(5)