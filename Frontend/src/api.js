export const API_BASE = (import.meta.env?.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '');
export class ApiError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
export function createApi(token = '', onExpired = () => {}) {
  return async (path, { method = 'GET', body, signal } = {}) => {
    const response = await fetch(`${API_BASE}${path}`, {
      method, signal,
      headers: { Accept: 'application/json', ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    });
    const text = await response.text();
    let data; try { data = text ? JSON.parse(text) : null; } catch { data = null; }
    if (!response.ok) {
      if (response.status === 401 && token) onExpired();
      const detail = Array.isArray(data?.detail)
        ? data.detail.map(x => `${x.loc?.slice(1).join('.') || 'Dato'}: ${x.msg}`).join(' · ')
        : typeof data?.detail === 'string' ? data.detail : `No se pudo completar la solicitud (${response.status}).`;
      throw new ApiError(response.status, detail);
    }
    return data;
  };
}
export function dashboardSocketUrl() {
  if (import.meta.env?.VITE_WS_URL) return import.meta.env.VITE_WS_URL;
  const url = new URL(API_BASE, window.location.origin);
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  url.pathname = url.pathname.replace(/\/api\/v1$/, '') + '/ws/dashboard';
  url.search = ''; url.hash = '';
  return url.href;
}
