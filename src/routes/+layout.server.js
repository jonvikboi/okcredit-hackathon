import { redirect } from '@sveltejs/kit';

/** @type {import('./$types').LayoutServerLoad} */
export function load({ cookies, url }) {
  const adminSession = cookies.get('admin_session');
  const username = cookies.get('username');
  const isAuthenticated = !!adminSession;

  // If not authenticated and not on the login page, redirect to /login
  if (!isAuthenticated && url.pathname !== '/login') {
    throw redirect(303, '/login');
  }

  // If authenticated and on the login page, redirect to main page /
  if (isAuthenticated && url.pathname === '/login') {
    throw redirect(303, '/');
  }

  return {
    isAuthenticated,
    role: adminSession || null,
    username: username || null
  };
}
