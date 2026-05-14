'use strict';

const LANGS = [
  { code: 'en',    name: 'English',            dir: 'ltr' },
  { code: 'ar',    name: 'العربية',             dir: 'rtl' },
  { code: 'de',    name: 'Deutsch',             dir: 'ltr' },
  { code: 'es',    name: 'Español',             dir: 'ltr' },
  { code: 'fr',    name: 'Français',            dir: 'ltr' },
  { code: 'hi',    name: 'हिन्दी',              dir: 'ltr' },
  { code: 'id',    name: 'Bahasa Indonesia',    dir: 'ltr' },
  { code: 'ja',    name: '日本語',              dir: 'ltr' },
  { code: 'ko',    name: '한국어',              dir: 'ltr' },
  { code: 'pt-BR', name: 'Português (Brasil)',  dir: 'ltr' },
  { code: 'ru',    name: 'Русский',             dir: 'ltr' },
  { code: 'zh-CN', name: '简体中文',            dir: 'ltr' },
  { code: 'zh-TW', name: '繁體中文',            dir: 'ltr' },
];

const _cache = {};

async function _load(code) {
  if (_cache[code]) return _cache[code];
  try {
    const res = await fetch(`i18n/${code}.json`);
    if (!res.ok) throw new Error(res.status);
    _cache[code] = await res.json();
  } catch {
    if (code !== 'en') return _load('en');
    _cache[code] = {};
  }
  return _cache[code];
}

function _apply(t, code, dir) {
  document.documentElement.lang = code;
  document.documentElement.dir  = dir;

  const titleMeta = document.querySelector('meta[name="i18n-title"]');
  if (titleMeta && t[titleMeta.content]) document.title = t[titleMeta.content];

  const descMeta = document.querySelector('meta[name="description"]');
  if (descMeta && t.idx_desc) descMeta.setAttribute('content', t.idx_desc);

  document.querySelectorAll('[data-i18n]').forEach(el => {
    const v = t[el.dataset.i18n];
    if (v !== undefined) el.textContent = v;
  });

  document.querySelectorAll('[data-i18n-html]').forEach(el => {
    const v = t[el.dataset.i18nHtml];
    if (v !== undefined) el.innerHTML = v;
  });

  document.querySelectorAll('[data-i18n-attr]').forEach(el => {
    el.dataset.i18nAttr.split(';').forEach(pair => {
      const sep = pair.indexOf(':');
      if (sep < 1) return;
      const attr = pair.slice(0, sep).trim();
      const key  = pair.slice(sep + 1).trim();
      const v    = t[key];
      if (v !== undefined) el.setAttribute(attr, v);
    });
  });

  const picker = document.getElementById('lang-picker');
  if (picker) picker.value = code;

  localStorage.setItem('lang', code);
}

async function applyLang(code) {
  const t    = await _load(code);
  const meta = LANGS.find(l => l.code === code) || LANGS[0];
  _apply(t, code, meta.dir);
}

function buildPicker() {
  const picker = document.getElementById('lang-picker');
  if (!picker) return;
  picker.innerHTML = LANGS.map(l =>
    `<option value="${l.code}">${l.name}</option>`
  ).join('');
  picker.addEventListener('change', e => applyLang(e.target.value));
}

document.addEventListener('DOMContentLoaded', () => {
  buildPicker();
  const saved   = localStorage.getItem('lang') || '';
  const browser = navigator.language || 'en';
  const resolve = c => LANGS.find(l => l.code === c);
  const lang =
    (saved   && resolve(saved))   ? saved   :
    (browser && resolve(browser)) ? browser :
    LANGS.find(l => l.code === browser.split('-')[0])?.code || 'en';
  applyLang(lang);
});
