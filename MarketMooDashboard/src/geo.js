// Geometry helpers for the map page (no external library needed).
const R = 6371.0088

/** GeoJSON polygon approximating a circle of radiusKm around (lat, lon). */
export function circle(lat, lon, radiusKm, steps = 64) {
  const coords = []
  const dLat = (radiusKm / R) * (180 / Math.PI)
  const dLon = dLat / Math.cos((lat * Math.PI) / 180)
  for (let i = 0; i <= steps; i++) {
    const a = (i / steps) * 2 * Math.PI
    coords.push([lon + dLon * Math.cos(a), lat + dLat * Math.sin(a)])
  }
  return { type: 'Polygon', coordinates: [coords] }
}

export const featureCollection = (features) => ({ type: 'FeatureCollection', features })
export const point = (lat, lon, properties = {}) => ({ type: 'Feature', properties, geometry: { type: 'Point', coordinates: [lon, lat] } })

export function haversineKm(lat1, lon1, lat2, lon2) {
  const p1 = (lat1 * Math.PI) / 180, p2 = (lat2 * Math.PI) / 180
  const dLat = p2 - p1, dLon = ((lon2 - lon1) * Math.PI) / 180
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dLon / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(a))
}
