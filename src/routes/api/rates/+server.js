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

/**
 * GET /api/rates
 *
 * Returns the latest gold/silver rates.
 * Reads from MongoDB which is kept fresh by the Vercel Cron at /api/cron/fetch-rates.
 * Falls back to hardcoded defaults if MongoDB is unavailable.
 *
 * This endpoint is intentionally fast and does NOT open any WebSocket connection.
 */
export async function GET() {
  try {
    const db = await getDb();
    const ratesCollection = db.collection('rates');
    const latestRates = await ratesCollection.findOne({ id: 'latest_rates' });

    if (latestRates) {
      const { _id, id, ...ratesData } = latestRates;
      const ageInMs = Date.now() - new Date(latestRates.timestamp).getTime();
      const ageInMins = Math.round(ageInMs / 60000);

      return new Response(JSON.stringify({
        success: true,
        source: 'mongodb',
        stale: ageInMs > 5 * 60 * 1000, // flag as stale if older than 5 minutes
        age_minutes: ageInMins,
        ...ratesData
      }), {
        headers: {
          'Content-Type': 'application/json',
          'Cache-Control': 'no-cache'
        }
      });
    }

    // MongoDB had no rates yet — return defaults with a note
    return new Response(JSON.stringify({
      success: true,
      source: 'fallback',
      stale: true,
      ...DEFAULT_RATES
    }), {
      headers: {
        'Content-Type': 'application/json',
        'Cache-Control': 'no-cache'
      }
    });

  } catch (error) {
    console.error('[GET /api/rates] MongoDB error:', error.message);

    // Return hardcoded defaults so the UI never breaks
    return new Response(JSON.stringify({
      success: true,
      source: 'fallback',
      stale: true,
      ...DEFAULT_RATES
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    });
  }
}
