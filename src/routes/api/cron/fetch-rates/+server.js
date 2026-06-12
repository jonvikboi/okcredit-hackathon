import { fetchAndUpdateRates } from '$lib/rateFetcher';
import { env } from '$env/dynamic/private';

/**
 * POST /api/cron/fetch-rates
 *
 * This endpoint is called automatically by Vercel Cron every minute.
 * Vercel automatically sets the Authorization header with the CRON_SECRET.
 *
 * It opens a WebSocket connection to the Ambicaa live rates feed,
 * waits for one data tick, saves the rates to MongoDB, and exits.
 *
 * Protected by CRON_SECRET so it cannot be triggered by arbitrary users.
 */
export async function POST({ request }) {
  // Verify the request is coming from Vercel Cron (or local dev)
  const authHeader = request.headers.get('authorization');
  const cronSecret = env.CRON_SECRET;

  // In production, always require the secret
  if (cronSecret && authHeader !== `Bearer ${cronSecret}`) {
    return new Response(JSON.stringify({ error: 'Unauthorized' }), {
      status: 401,
      headers: { 'Content-Type': 'application/json' }
    });
  }

  const startTime = Date.now();

  try {
    console.log('[Cron] fetch-rates: starting...');
    const rates = await fetchAndUpdateRates();
    const elapsed = Date.now() - startTime;

    console.log(`[Cron] fetch-rates: success in ${elapsed}ms — gold24k=${rates.gold24k}, silver=${rates.silver}`);

    return new Response(JSON.stringify({
      success: true,
      elapsed_ms: elapsed,
      gold24k: rates.gold24k,
      gold22k: rates.gold22k,
      gold18k: rates.gold18k,
      silver: rates.silver,
      timestamp: rates.timestamp
    }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' }
    });
  } catch (error) {
    const elapsed = Date.now() - startTime;
    console.error(`[Cron] fetch-rates: failed after ${elapsed}ms —`, error.message);

    return new Response(JSON.stringify({
      success: false,
      elapsed_ms: elapsed,
      error: error.message
    }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    });
  }
}
