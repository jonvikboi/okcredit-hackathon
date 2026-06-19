import { getDb } from '$lib/db';
import { env } from '$env/dynamic/private';

/**
 * POST /api/login
 *
 * Verifies credentials from MongoDB and returns authentication result.
 */
export async function POST({ request }) {
  try {
    const { username, password } = await request.json();

    if (!username || !password) {
      return new Response(JSON.stringify({
        success: false,
        error: 'Username and password are required.'
      }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' }
      });
    }

    let authenticated = false;
    let role = 'admin';

    try {
      const db = await getDb();
      const dbUser = await db.collection('users').findOne({ username });
      if (dbUser && dbUser.password === password) {
        authenticated = true;
        role = dbUser.role || 'user';
      }
    } catch (dbError) {
      console.error('Database authentication failed, falling back to static config:', dbError);
    }

    // Secondary fallback to static config if DB didn't authenticate or failed
    if (!authenticated) {
      const expectedUsername = env.ADMIN_USERNAME || 'admin';
      const expectedPassword = env.ADMIN_PASSWORD || 'sunrise@213';
      if (username === expectedUsername && password === expectedPassword) {
        authenticated = true;
        role = 'admin';
      }
    }

    if (authenticated) {
      return new Response(JSON.stringify({
        success: true,
        username,
        role
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    } else {
      return new Response(JSON.stringify({
        success: false,
        error: 'Invalid username or password.'
      }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' }
      });
    }
  } catch (error) {
    console.error('[POST /api/login] error:', error);
    return new Response(JSON.stringify({
      success: false,
      error: 'An unexpected server error occurred.'
    }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' }
    });
  }
}
