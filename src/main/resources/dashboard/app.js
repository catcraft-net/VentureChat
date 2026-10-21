'use strict';
const $ = id => document.getElementById(id);
let token = '', offset = 0, filters = new URLSearchParams(), contextMode = false;
async function api(path) {
  const controller = new AbortController(); const timer = setTimeout(() => controller.abort(), 5000);
  try {
    const response = await fetch(path, {headers: {Authorization: `Bearer ${token}`}, signal: controller.signal, cache: 'no-store'});
    if (!response.ok) throw new Error(response.status === 401 ? 'Access denied. Check your staff token.' : 'Request failed. Check your filters or try again.');
    return await response.json();
  } finally {clearTimeout(timer);}
}
function showError(error) {$('error').textContent = error.message;}
function render(events) {
  $('rows').replaceChildren(); $('note').textContent = events.length ? '' : 'No messages match these filters.';
  for (const event of events) {
    const row = document.createElement('tr');
    for (const value of [new Date(event.timestamp).toLocaleString(), event.realm, event.channel + (event.private ? ' · private' : ''), event.senderName + (event.recipientId ? ` → ${event.recipientId}` : ''), event.message]) {
      const cell = document.createElement('td'); cell.textContent = value; row.appendChild(cell);
    }
    const cell = document.createElement('td');const button = document.createElement('button');button.className = 'context';button.textContent = 'Context';button.setAttribute('aria-label', `View conversation around ${event.senderName}'s message`);
    button.onclick = async () => {try {const result = await api(`/api/context?id=${encodeURIComponent(event.id)}`);contextMode = true;render(result);$('resulttitle').textContent = 'Surrounding conversation';$('page').textContent = 'Search again to return to results';$('previous').disabled = true;$('next').disabled = true;} catch(e) {showError(e);}};
    cell.appendChild(button);row.appendChild(cell);$('rows').appendChild(row);
  }
  if (!contextMode) {$('page').textContent = `Page ${offset / 50 + 1}`;$('previous').disabled = offset === 0;$('next').disabled = events.length < 50 || offset >= 10000;}
}
async function search() {
  $('error').textContent = '';contextMode = false;$('resulttitle').textContent = 'Messages';
  const params = new URLSearchParams(filters);params.set('offset', offset);params.set('limit', 50);
  try {
    const [events, status] = await Promise.all([api(`/api/messages?${params}`), api('/api/status')]);
    render(events);$('health').textContent = `${status.queued} queued · ${status.dropped} dropped · ${status.failures} write failures` + (status.lastFailure ? ' · Storage recovering' : '');
  } catch(e) {showError(e);}
}
$('unlock').onclick = async () => {
  token = $('token').value;$('token').value = '';try {await api('/api/status');$('login').hidden = true;$('workspace').hidden = false;$('logout').hidden = false;await search();} catch(e) {token = '';showError(e);}
};
$('token').addEventListener('keydown', e => {if(e.key === 'Enter') $('unlock').click();});
$('logout').onclick = () => {token = '';$('rows').replaceChildren();$('workspace').hidden = true;$('logout').hidden = true;$('login').hidden = false;$('error').textContent = '';};
$('search').onsubmit = e => {
  e.preventDefault();filters = new URLSearchParams();
  for (const [key, value] of new FormData(e.target)) if(value) filters.set(key, key === 'from' || key === 'to' ? new Date(value).getTime() : value);
  offset = 0;search();
};
$('previous').onclick = () => {offset = Math.max(0, offset - 50);search();};
$('next').onclick = () => {offset = Math.min(10000, offset + 50);search();};
