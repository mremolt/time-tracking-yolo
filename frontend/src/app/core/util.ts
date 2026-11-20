/** Minutes as h:mm. */
export function hm(min: number | null | undefined): string {
  if (min == null) return '';
  const sign = min < 0 ? '-' : '';
  const a = Math.abs(Math.round(min));
  return `${sign}${Math.floor(a / 60)}:${String(a % 60).padStart(2, '0')}`;
}
export function signedHm(min: number): string { return (min > 0 ? '+' : '') + hm(min); }
export function iso(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
export function parseIso(s: string): Date { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); }
export function addDays(s: string, n: number): string { const d = parseIso(s); d.setDate(d.getDate() + n); return iso(d); }
export function mondayOf(s: string): string { const d = parseIso(s); const wd = (d.getDay() + 6) % 7; d.setDate(d.getDate() - wd); return iso(d); }
export function today(): string { return iso(new Date()); }
export function timeOf(dt?: string | null): string { return dt ? dt.substring(11, 16) : ''; }
export function monthStart(s: string): string { return s.substring(0, 8) + '01'; }
export function monthEnd(s: string): string { const d = parseIso(s); return iso(new Date(d.getFullYear(), d.getMonth() + 1, 0)); }
/** "1:30", "1,5", "1.5", "90m" => minutes; null when invalid. */
export function parseDuration(v: string): number | null {
  const s = (v ?? '').trim().toLowerCase();
  if (!s) return null;
  let m = /^(\d+):([0-5]\d)$/.exec(s);
  if (m) return +m[1] * 60 + +m[2];
  m = /^(\d+)\s*m(in)?$/.exec(s);
  if (m) return +m[1];
  m = /^(\d+)([.,](\d+))?\s*h?$/.exec(s);
  if (m) return Math.round(parseFloat(`${m[1]}.${m[3] ?? '0'}`) * 60);
  return null;
}
