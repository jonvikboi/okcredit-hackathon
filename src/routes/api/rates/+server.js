import fs from 'fs';
import path from 'path';
import { getDb } from '$lib/db';

// Default fallback rates based on recent logs in June 2026
const DEFAULT_RATES = {
  gold24k: 15485, // ₹15,485 per gram
  gold22k: 14195, // ₹14,195 per gram
  gold18k: 11614, // ₹11,614 per gram
  silver: 266.16, // ₹266.16 per gram (₹266,160 per kg)
  timestamp: new Date().toISOString(),
  logs: []
};

/** @type {import('./$types').RequestHandler} */
export async function GET() {
  try {
    // 1. Try to query the latest rates from MongoDB (for Vercel serverless compatibility)
    try {
      const db = await getDb();
      const ratesCollection = db.collection('rates');
      const latestRates = await ratesCollection.findOne({ id: 'latest_rates' });
      
      if (latestRates) {
        const { _id, id, ...ratesData } = latestRates;
        return new Response(JSON.stringify({
          success: true,
          source: 'mongodb',
          ...ratesData
        }), {
          headers: {
            'Content-Type': 'application/json',
            'Cache-Control': 'no-cache'
          }
        });
      }
    } catch (dbError) {
      console.warn('MongoDB rates query failed, falling back to local files:', dbError.message);
    }

    const csvPath = path.resolve('ambicaa_rates.csv');
    
    if (!fs.existsSync(csvPath)) {
      return new Response(JSON.stringify({
        success: true,
        source: 'fallback',
        ...DEFAULT_RATES
      }), {
        headers: { 'Content-Type': 'application/json' }
      });
    }

    // Read last part of the file (last 20KB is plenty for the latest logs)
    const fd = fs.openSync(csvPath, 'r');
    const stat = fs.fstatSync(fd);
    const size = stat.size;
    
    let content = '';
    if (size > 0) {
      const bufferSize = Math.min(size, 20000);
      const buffer = Buffer.alloc(bufferSize);
      fs.readSync(fd, buffer, 0, bufferSize, size - bufferSize);
      content = buffer.toString('utf-8');
    }
    fs.closeSync(fd);

    if (!content.trim()) {
      return new Response(JSON.stringify({
        success: true,
        source: 'fallback-empty-csv',
        ...DEFAULT_RATES
      }), {
        headers: { 'Content-Type': 'application/json' }
      });
    }

    const lines = content.split('\n').filter(l => l.trim());
    const rates = {};
    const auditLogs = [];
    const maxAuditCount = 15;

    // Parse lines from bottom to top to get the most recent price for each instrument and audit logs
    for (let i = lines.length - 1; i >= 0; i--) {
      const parts = lines[i].split(',');
      if (parts.length < 4) continue;

      const timestamp = parts[0].trim();
      const symbol = parts[1].trim();
      const bid = parseFloat(parts[2]);
      const ask = parseFloat(parts[3]);
      const ltp = parseFloat(parts[4]);

      if (isNaN(ask)) continue;

      if (!rates[symbol]) {
        rates[symbol] = { timestamp, bid, ask, ltp };
      }

      if (auditLogs.length < maxAuditCount) {
        let resolvedName = symbol;
        if (symbol === '117574919') resolvedName = 'GOLD26JUNFUT (24K)';
        else if (symbol === '119445255') resolvedName = 'GOLD26AUGFUT (24K)';
        else if (symbol === '118822407') resolvedName = 'SILVER26JULFUT';
        else if (symbol === '120761607') resolvedName = 'SILVER26SEPFUT';

        auditLogs.push({
          timestamp,
          symbol: resolvedName,
          bid,
          ask,
          ltp
        });
      }
    }

    // Resolve Gold 24K Futures (which are quoted per 10g in INR)
    let gold24kRate10g = null;
    const goldKeys = ['GOLD26JUNFUT', '117574919', 'GOLD26AUGFUT', '119445255'];
    for (const key of goldKeys) {
      if (rates[key] && rates[key].ask) {
        gold24kRate10g = rates[key].ask;
        break;
      }
    }

    // Resolve Silver Futures (quoted per 1kg in INR)
    let silverRateKg = null;
    const silverKeys = ['SILVER26JULFUT', '118822407', 'SILVER26SEPFUT', '120761607'];
    for (const key of silverKeys) {
      if (rates[key] && rates[key].ask) {
        silverRateKg = rates[key].ask;
        break;
      }
    }

    // Calculate rates
    const gold24k = gold24kRate10g ? Math.round(gold24kRate10g / 10) : DEFAULT_RATES.gold24k;
    const gold22k = Math.round(gold24k * (22 / 24));
    const gold18k = Math.round(gold24k * (18 / 24));
    const silver = silverRateKg ? Math.round((silverRateKg / 1000) * 100) / 100 : DEFAULT_RATES.silver;

    // Use latest timestamp from parsed data or fallback
    let dataTimestamp = new Date().toISOString();
    const firstKey = Object.keys(rates)[0];
    if (firstKey && rates[firstKey].timestamp) {
      dataTimestamp = rates[firstKey].timestamp;
    }

    return new Response(JSON.stringify({
      success: true,
      source: 'csv',
      gold24k,
      gold22k,
      gold18k,
      silver,
      timestamp: dataTimestamp,
      logs: auditLogs
    }), {
      headers: {
        'Content-Type': 'application/json',
        'Cache-Control': 'no-cache'
      }
    });

  } catch (error) {
    return new Response(JSON.stringify({
      success: false,
      error: error.message,
      ...DEFAULT_RATES
    }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    });
  }
}
