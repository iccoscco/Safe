import test from 'node:test';
import assert from 'node:assert/strict';
import { createApi, ApiError } from '../src/api.js';

test('cliente API autentica, envía JSON y gestiona expiración y validación', async () => {
  const original = globalThis.fetch;
  let expired = 0;
  try {
    globalThis.fetch = async (url, options) => {
      assert.equal(url, '/api/v1/participants');
      assert.equal(options.headers.Authorization, 'Bearer token-prueba');
      assert.equal(options.headers['Content-Type'], 'application/json');
      assert.deepEqual(JSON.parse(options.body), { consent_version: 'v1' });
      return new Response(JSON.stringify({ code: 'P1' }), { status: 201 });
    };
    const api = createApi('token-prueba', () => expired++);
    assert.deepEqual(await api('/participants', { method: 'POST', body: { consent_version: 'v1' } }), { code: 'P1' });
    globalThis.fetch = async () => new Response(JSON.stringify({ detail: 'Token vencido' }), { status: 401 });
    await assert.rejects(api('/participants'), e => e instanceof ApiError && e.status === 401 && e.message === 'Token vencido');
    assert.equal(expired, 1);
    await assert.rejects(createApi()('/auth/login'), ApiError);
    assert.equal(expired, 1);
    globalThis.fetch = async () => new Response(JSON.stringify({ detail: [{ loc: ['body', 'email'], msg: 'Inválido' }] }), { status: 422 });
    await assert.rejects(api('/auth/staff'), { message: 'email: Inválido' });
    globalThis.fetch = async () => { throw new TypeError('NetworkError'); };
    await assert.rejects(api('/participants'), TypeError);
  } finally { globalThis.fetch = original; }
});
