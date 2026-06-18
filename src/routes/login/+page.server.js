import { fail, redirect } from '@sveltejs/kit';
import { env } from '$env/dynamic/private';
import { getDb } from '$lib/db';

/** @type {import('./$types').Actions} */
export const actions = {
  login: async ({ request, cookies }) => {
    const data = await request.formData();
    const username = data.get('username');
    const password = data.get('password');

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
      const expectedUsername = env.ADMIN_USERNAME || 'admin';
      const expectedPassword = env.ADMIN_PASSWORD || 'sunrise@213';
      if (username === expectedUsername && password === expectedPassword) {
        authenticated = true;
        role = 'admin';
      }
    }

    if (authenticated) {
      cookies.set('admin_session', role, {
        path: '/',
        httpOnly: true,
        sameSite: 'strict',
        secure: process.env.NODE_ENV === 'production',
        maxAge: 60 * 60 * 24 // 1 day
      });
      cookies.set('username', username, {
        path: '/',
        httpOnly: true,
        sameSite: 'strict',
        secure: process.env.NODE_ENV === 'production',
        maxAge: 60 * 60 * 24 // 1 day
      });
      throw redirect(303, '/');
    }

    return fail(400, {
      username,
      error: 'Invalid username or password.'
    });
  },
  logout: async ({ cookies }) => {
    cookies.delete('admin_session', { path: '/' });
    cookies.delete('username', { path: '/' });
    throw redirect(303, '/login');
  }
};
