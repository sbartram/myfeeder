/**
 * A number with an explicit sign: "+20", "−15" (U+2212 minus) or "0", with `digits` decimals.
 */
export function formatSigned(n: number, digits = 0): string {
  const s = Math.abs(n).toFixed(digits)
  if (n > 0) return `+${s}`
  if (n < 0) return `−${s}`
  return s
}

// RED stubs: replaced by the real implementations in the GREEN step.
export function isNegated(_text: string): boolean {
  return false
}

export function hinge(_noul: number): number {
  return -1
}

export function formatPreviewText(_noul: number, _weight: number): string {
  return ''
}
