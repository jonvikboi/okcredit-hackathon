import zlib from 'zlib';
import { getDb } from './db.js';

const WS_URL = "ws://ambicaaspot.com:1001/bullion?user=ambicaa&auth=1&type=web";

// Deduplicate concurrent websocket rate fetches
let activePromise = null;

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

export async function fetchAndUpdateRates() {
  if (activePromise) {
    return activePromise;
  }

  activePromise = new Promise((resolve, reject) => {
    console.log("Connecting to Ambicaa Live Feed WebSocket...");
    let ws;
    
    // Safety timeout of 3.5 seconds
    const timeout = setTimeout(() => {
      console.warn("WebSocket rates fetch timed out.");
      if (ws) {
        try { ws.close(); } catch(e) {}
      }
      reject(new Error("Timeout waiting for rates from WebSocket"));
    }, 3500);

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
      const parts = event.data.split('\x1e');
      for (const part of parts) {
        if (!part.trim()) continue;
        try {
          const data = JSON.parse(part);
          
          // Handshake response is usually an empty object
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
            if (decoded && decoded.refProduct) {
              const products = [];
              for (let item of decoded.refProduct) {
                if (typeof item === 'string') {
                  try { item = JSON.parse(item); } catch (e) { continue; }
                }
                if (item && typeof item === 'object') {
                  products.push(item);
                }
              }

              if (products.length > 0) {
                // Fetch existing rates first to fall back if needed
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

                for (let item of products) {
                  const name = item.Name || item.symbol || "";
                  let ask = parseFloat(item.Ask);
                  if (isNaN(ask)) continue;

                  if (['GOLD26JUNFUT', '117574919', 'GOLD26AUGFUT', '119445255'].includes(name)) {
                    gold24k = Math.round(ask / 10);
                    ratesUpdated = true;
                  } else if (['SILVER26JULFUT', '118822407', 'SILVER26SEPFUT', '120761607'].includes(name)) {
                    silver = Math.round((ask / 1000) * 100) / 100;
                    ratesUpdated = true;
                  }
                }

                if (ratesUpdated) {
                  const nowStr = new Date().toISOString();
                  const logTimestamp = new Date().toLocaleDateString('en-IN') + ' ' + new Date().toLocaleTimeString('en-IN');
                  
                  // Construct logs
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
                  }

                  clearTimeout(timeout);
                  try { ws.close(); } catch(e) {}
                  resolve(finalRates);
                  return;
                }
              }
            }
          }
        } catch (err) {
          console.error("Error processing websocket message chunk:", err);
        }
      }
    };

    ws.onerror = (err) => {
      console.error("WebSocket rate fetcher connection error:", err);
      clearTimeout(timeout);
      try { ws.close(); } catch(e) {}
      reject(err);
    };

    ws.onclose = () => {
      console.log("WebSocket rate fetcher connection closed.");
    };
  }).finally(() => {
    activePromise = null;
  });

  return activePromise;
}
