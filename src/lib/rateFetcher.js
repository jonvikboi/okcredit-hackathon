import zlib from 'zlib';
import { getDb } from './db.js';

const WS_URL = "ws://ambicaaspot.com:1001/bullion?user=ambicaa&auth=1&type=web";

function decodePayload(encoded) {
  try {
    const compressed = Buffer.from(encoded, 'base64');
    const decompressed = zlib.gunzipSync(compressed);
    return JSON.parse(decompressed.toString('utf-8'));
  } catch (e) {
    console.error("Decode Error:", e);
    return null;
  }
}

/**
 * Opens a WebSocket connection to the Ambicaa live rates feed,
 * waits for one valid rate tick (max 20 seconds), saves to MongoDB, and resolves.
 *
 * Designed to be called from a short-lived serverless function (Vercel Cron).
 */
export async function fetchAndUpdateRates() {
  return new Promise((resolve, reject) => {
    console.log("Connecting to Ambicaa Live Feed WebSocket...");
    let ws;
    let settled = false;

    function finish(fn) {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      try { ws && ws.close(); } catch (e) {}
      fn();
    }

    // Give the websocket up to 20 seconds to receive a valid tick
    const timeout = setTimeout(() => {
      finish(() => reject(new Error("Timeout waiting for rates from WebSocket (20s)")));
    }, 20000);

    try {
      ws = new WebSocket(WS_URL);
    } catch (err) {
      clearTimeout(timeout);
      return reject(err);
    }

    ws.onopen = () => {
      // SignalR handshake
      ws.send('{"protocol":"json","version":1}\x1e');
    };

    ws.onmessage = async (event) => {
      if (settled) return;

      const parts = event.data.split('\x1e');
      for (const part of parts) {
        if (!part.trim()) continue;
        try {
          const data = JSON.parse(part);

          // Handshake response — reply with subscription
          if (Object.keys(data).length === 0) {
            const subscribe = {
              arguments: ["ambicaa"],
              invocationId: "0",
              target: "client",
              type: 1
            };
            ws.send(JSON.stringify(subscribe) + '\x1e');
            continue;
          }

          if (data.target && ['workerPublish', 'workerPublishCoin', 'referanceDetails', 'symbolDetails'].includes(data.target)) {
            const encoded = data.arguments[0];
            const decoded = decodePayload(encoded);
            if (!decoded || !decoded.refProduct) continue;

            const products = [];
            for (let item of decoded.refProduct) {
              if (typeof item === 'string') {
                try { item = JSON.parse(item); } catch (e) { continue; }
              }
              if (item && typeof item === 'object') products.push(item);
            }

            if (products.length === 0) continue;

            // Fetch existing rates from DB to preserve them if this tick doesn't have both gold & silver
            let gold24k = 15485;
            let silver = 266.16;
            let ratesUpdated = false;

            try {
              const db = await getDb();
              const existing = await db.collection('rates').findOne({ id: "latest_rates" });
              if (existing) {
                gold24k = existing.gold24k || gold24k;
                silver = existing.silver || silver;
              }
            } catch (dbErr) {
              console.warn("Failed to query existing rates from DB:", dbErr.message);
            }

            for (const item of products) {
              const name = item.Name || item.symbol || "";
              const ask = parseFloat(item.Ask);
              if (isNaN(ask)) continue;

              if (['GOLD26JUNFUT', '117574919', 'GOLD26AUGFUT', '119445255'].includes(name)) {
                gold24k = Math.round(ask / 10);
                ratesUpdated = true;
              } else if (['SILVER26JULFUT', '118822407', 'SILVER26SEPFUT', '120761607'].includes(name)) {
                silver = Math.round((ask / 1000) * 100) / 100;
                ratesUpdated = true;
              }
            }

            if (!ratesUpdated) continue; // wait for a tick that actually has gold/silver

            const nowStr = new Date().toISOString();
            const logTimestamp = new Date().toLocaleDateString('en-IN') + ' ' + new Date().toLocaleTimeString('en-IN');

            const logs = products.map(item => {
              const symbol = item.Name || item.symbol || "";
              let resolvedName = symbol;
              if (symbol === '117574919') resolvedName = 'GOLD26JUNFUT (24K)';
              else if (symbol === '119445255') resolvedName = 'GOLD26AUGFUT (24K)';
              else if (symbol === '118822407') resolvedName = 'SILVER26JULFUT';
              else if (symbol === '120761607') resolvedName = 'SILVER26SEPFUT';

              return {
                timestamp: logTimestamp,
                symbol: resolvedName,
                bid: parseFloat(item.Bid) || 0.0,
                ask: parseFloat(item.Ask) || 0.0,
                ltp: parseFloat(item.LTP) || 0.0
              };
            });

            const finalRates = {
              id: "latest_rates",
              gold24k,
              gold22k: Math.round(gold24k * (22 / 24)),
              gold18k: Math.round(gold24k * (18 / 24)),
              silver,
              timestamp: nowStr,
              logs: logs.slice(0, 15)
            };

            try {
              const db = await getDb();
              await db.collection('rates').updateOne(
                { id: "latest_rates" },
                { $set: finalRates },
                { upsert: true }
              );
              console.log("MongoDB Rates updated successfully from websocket.");
            } catch (dbErr) {
              console.error("Failed to save rates to MongoDB:", dbErr);
              finish(() => reject(dbErr));
              return;
            }

            finish(() => resolve(finalRates));
            return;
          }
        } catch (err) {
          console.error("Error processing websocket message chunk:", err);
        }
      }
    };

    ws.onerror = (err) => {
      console.error("WebSocket rate fetcher connection error:", err);
      finish(() => reject(new Error("WebSocket connection error")));
    };

    ws.onclose = () => {
      console.log("WebSocket rate fetcher connection closed.");
      // If it closed before we settled, reject
      finish(() => reject(new Error("WebSocket closed before receiving rates")));
    };
  });
}
