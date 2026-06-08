
import websocket
import json
import gzip
import base64
import csv
import time
import os
from datetime import datetime
from pymongo import MongoClient

# Load environment variables from .env file
def load_env():
    if os.path.exists(".env"):
        with open(".env", "r") as f:
            for line in f:
                if line.strip() and not line.startswith("#"):
                    parts = line.strip().split("=", 1)
                    if len(parts) == 2:
                        os.environ[parts[0].strip()] = parts[1].strip().strip('"').strip("'")

load_env()
mongo_uri = os.environ.get("MONGODB_URI")
db = None
if mongo_uri:
    try:
        mongo_client = MongoClient(mongo_uri)
        db = mongo_client.okcredit_inventory
        print("Connected to MongoDB for Live Rates update!")
    except Exception as e:
        print("Failed to connect to MongoDB:", e)

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


def save_to_mongodb(products):
    if not db:
        return
    try:
        # Default rates fallback
        gold24k = 15485
        silver = 266.16
        rates_updated = False
        
        for item in products:
            name = item.get("Name", item.get("symbol", ""))
            try:
                ask = float(item.get("Ask", 0))
            except ValueError:
                continue
                
            if name in ['GOLD26JUNFUT', '117574919', 'GOLD26AUGFUT', '119445255']:
                gold24k = round(ask / 10)
                rates_updated = True
            elif name in ['SILVER26JULFUT', '118822407', 'SILVER26SEPFUT', '120761607']:
                silver = round((ask / 1000) * 100) / 100
                rates_updated = True
                
        if not rates_updated:
            # Check if rates document already exists to preserve them
            existing = db.rates.find_one({"id": "latest_rates"})
            if existing:
                gold24k = existing.get("gold24k", gold24k)
                silver = existing.get("silver", silver)
                
        # Parse logs of recent ticks
        logs = []
        for item in products:
            symbol = item.get("Name", item.get("symbol", ""))
            resolved_name = symbol
            if symbol == '117574919': resolved_name = 'GOLD26JUNFUT (24K)'
            elif symbol == '119445255': resolved_name = 'GOLD26AUGFUT (24K)'
            elif symbol == '118822407': resolved_name = 'SILVER26JULFUT'
            elif symbol == '120761607': resolved_name = 'SILVER26SEPFUT'
            
            try:
                bid = float(item.get("Bid", 0)) if item.get("Bid") else 0.0
                ask = float(item.get("Ask", 0)) if item.get("Ask") else 0.0
                ltp = float(item.get("LTP", 0)) if item.get("LTP") else 0.0
            except ValueError:
                bid, ask, ltp = 0.0, 0.0, 0.0
                
            logs.append({
                "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                "symbol": resolved_name,
                "bid": bid,
                "ask": ask,
                "ltp": ltp
            })
            
        # Push to MongoDB
        db.rates.update_one(
            {"id": "latest_rates"},
            {"$set": {
                "gold24k": gold24k,
                "gold22k": round(gold24k * (22 / 24)),
                "gold18k": round(gold24k * (18 / 24)),
                "silver": silver,
                "timestamp": datetime.now().isoformat(),
                "logs": logs[:15] # keep last 15 ticks
            }},
            upsert=True
        )
    except Exception as e:
        print("MongoDB Rates Update Error:", e)


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
                    save_to_mongodb(products)

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