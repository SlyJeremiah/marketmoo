import { describe, expect, it } from 'vitest'
import { circle, haversineKm, point } from './geo'
import { describeError } from './api'

describe('geo helpers', () => {
  it('circle is closed and every vertex is about the radius from the centre', () => {
    const c = circle(-20.5, 28.6, 20)
    const ring = c.coordinates[0]
    expect(ring[0]).toEqual(ring[ring.length - 1])
    for (const [lon, lat] of ring) expect(Math.abs(haversineKm(-20.5, 28.6, lat, lon) - 20)).toBeLessThan(0.3)
  })

  it('haversine Gwanda to Beitbridge is a plausible straight-line distance', () => {
    const d = haversineKm(-20.93, 29.0, -22.2, 29.99)
    expect(d).toBeGreaterThan(150)
    expect(d).toBeLessThan(190)
  })

  it('point builds GeoJSON in lon,lat order', () => {
    expect(point(-20.9, 29.1).geometry.coordinates).toEqual([29.1, -20.9])
  })
})

describe('describeError', () => {
  it('prefers detail', () => expect(describeError({ detail: 'No.' })).toBe('No.'))
  it('joins field errors', () => expect(describeError({ centre_lat: ['Bad.'], disease: ['Required.'] })).toBe('centre_lat: Bad.; disease: Required.'))
  it('handles junk', () => expect(describeError(null)).toBe(''))
})
