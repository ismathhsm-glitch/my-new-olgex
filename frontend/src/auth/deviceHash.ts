const STORAGE_KEY = 'olgax.device.seed'

export async function getDeviceHash(): Promise<string> {
  let seed = localStorage.getItem(STORAGE_KEY)
  if (!seed) {
    seed = crypto.randomUUID()
    localStorage.setItem(STORAGE_KEY, seed)
  }
  const bytes = new TextEncoder().encode(seed)
  const digest = await crypto.subtle.digest('SHA-256', bytes)
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
}
