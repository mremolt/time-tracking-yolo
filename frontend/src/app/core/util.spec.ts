import { describe, expect, it } from 'vitest';
import { addDays, hm, mondayOf, parseDuration, signedHm } from './util';
import { DE } from './i18n-de';
import { EN } from './i18n-en';

describe('duration parsing', () => {
  it('accepts h:mm, decimals and minutes', () => {
    expect(parseDuration('1:30')).toBe(90);
    expect(parseDuration('1,5')).toBe(90);
    expect(parseDuration('1.5h')).toBe(90);
    expect(parseDuration('90m')).toBe(90);
    expect(parseDuration('2')).toBe(120);
  });
  it('rejects garbage', () => {
    expect(parseDuration('abc')).toBeNull();
    expect(parseDuration('')).toBeNull();
    expect(parseDuration('1:75')).toBeNull();
  });
});

describe('formatting', () => {
  it('formats minutes as h:mm', () => {
    expect(hm(0)).toBe('0:00');
    expect(hm(75)).toBe('1:15');
    expect(hm(-30)).toBe('-0:30');
    expect(signedHm(30)).toBe('+0:30');
  });
});

describe('dates', () => {
  it('finds the monday of a week and shifts days across month ends', () => {
    expect(mondayOf('2026-11-20')).toBe('2026-11-16');
    expect(mondayOf('2026-11-22')).toBe('2026-11-16');
    expect(addDays('2026-11-30', 1)).toBe('2026-12-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  });
});

describe('translations (D-12)', () => {
  it('German and English have exactly the same keys and no empty texts', () => {
    expect(Object.keys(DE).sort()).toEqual(Object.keys(EN).sort());
    for (const [k, v] of [...Object.entries(DE), ...Object.entries(EN)]) expect(v.trim(), k).not.toBe('');
  });
  it('placeholders are the same in both languages', () => {
    const ph = (s: string) => (s.match(/\{[a-zA-Z]+\}/g) ?? []).sort().join(',');
    for (const k of Object.keys(DE)) expect(ph(EN[k]), k).toBe(ph(DE[k]));
  });
});
