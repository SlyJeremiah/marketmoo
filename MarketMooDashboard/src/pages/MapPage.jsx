import { useEffect, useRef, useState } from 'react'
import maplibregl from 'maplibre-gl'
import 'maplibre-gl/dist/maplibre-gl.css'
import { api } from '../api'
import { circle, featureCollection, point } from '../geo'
import { Loading, Page } from '../ui.jsx'

const STYLE = {
  version: 8,
  sources: { osm: { type: 'raster', tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'], tileSize: 256, attribution: '© OpenStreetMap contributors', maxzoom: 18 } },
  layers: [{ id: 'osm', type: 'raster', source: 'osm' }],
}

export default function MapPage() {
  const el = useRef(null)
  const mapRef = useRef(null)
  const [state, setState] = useState({ loading: true, error: null, data: null })
  const [show, setShow] = useState({ listings: true, zones: true, reports: true })

  const load = async () => {
    setState((s) => ({ ...s, loading: true, error: null }))
    try {
      const [listings, outbreaks, reports] = await Promise.all([api('/v1/manager/listings'), api('/v1/manager/outbreaks'), api('/v1/manager/reports')])
      setState({ loading: false, error: null, data: { listings: listings.results, outbreaks: outbreaks.results, reports: reports.results } })
    } catch (e) { setState({ loading: false, error: e.message, data: null, reload: load }) }
  }
  useEffect(() => { load() }, [])

  useEffect(() => {
    const map = new maplibregl.Map({ container: el.current, style: STYLE, center: [29.9, -20.4], zoom: 6 })
    map.addControl(new maplibregl.NavigationControl(), 'top-right')
    mapRef.current = map
    return () => map.remove()
  }, [])

  useEffect(() => {
    const map = mapRef.current
    const d = state.data
    if (!map || !d) return
    const draw = () => {
      const set = (id, data, layers) => {
        if (map.getSource(id)) map.getSource(id).setData(data)
        else { map.addSource(id, { type: 'geojson', data }); layers.forEach((l) => map.addLayer({ ...l, source: id })) }
      }
      const zones = featureCollection(d.outbreaks.filter((o) => o.status === 'verified').flatMap((o) => [
        { type: 'Feature', properties: { kind: 'surveillance', name: o.disease }, geometry: circle(o.centre_lat, o.centre_lon, o.surveillance_radius_km) },
        { type: 'Feature', properties: { kind: 'control', name: o.disease }, geometry: circle(o.centre_lat, o.centre_lon, o.control_radius_km) },
      ]))
      set('zones', zones, [
        { id: 'zones-fill', type: 'fill', paint: { 'fill-color': ['match', ['get', 'kind'], 'control', '#c0392b', '#e67e22'], 'fill-opacity': 0.18 } },
        { id: 'zones-line', type: 'line', paint: { 'line-color': ['match', ['get', 'kind'], 'control', '#c0392b', '#e67e22'], 'line-width': 1.5 } },
      ])
      set('listings', featureCollection(d.listings.map((l) => point(l.public_lat, l.public_lon, { status: l.status, label: `${l.species} ${l.breed} x${l.qty}` }))), [
        { id: 'listings-pts', type: 'circle', paint: { 'circle-radius': 6, 'circle-stroke-width': 1.5, 'circle-stroke-color': '#fff', 'circle-color': ['match', ['get', 'status'], 'live', '#1f7a3a', 'pending', '#e67e22', '#888888'] } },
      ])
      set('reports', featureCollection(d.reports.map((r) => point(r.lat, r.lon, { label: `${r.species} ${r.suspected}`, status: r.status }))), [
        { id: 'reports-pts', type: 'circle', paint: { 'circle-radius': 7, 'circle-color': '#7b1fa2', 'circle-stroke-width': 2, 'circle-stroke-color': '#fff' } },
      ])
      const vis = (layer, on) => map.getLayer(layer) && map.setLayoutProperty(layer, 'visibility', on ? 'visible' : 'none')
      vis('listings-pts', show.listings); vis('zones-fill', show.zones); vis('zones-line', show.zones); vis('reports-pts', show.reports)
    }
    if (map.isStyleLoaded()) draw(); else map.once('load', draw)
  }, [state.data, show])

  useEffect(() => {
    const map = mapRef.current
    if (!map) return
    const popup = new maplibregl.Popup({ closeButton: false, closeOnClick: false })
    const on = (layer) => {
      map.on('mousemove', layer, (e) => { map.getCanvas().style.cursor = 'pointer'; popup.setLngLat(e.lngLat).setText(e.features[0].properties.label || e.features[0].properties.name).addTo(map) })
      map.on('mouseleave', layer, () => { map.getCanvas().style.cursor = ''; popup.remove() })
    }
    const t = setTimeout(() => ['listings-pts', 'reports-pts', 'zones-fill'].forEach((l) => map.getLayer(l) && on(l)), 800)
    return () => { clearTimeout(t); popup.remove() }
  }, [state.data])

  return (
    <Page title="Map" subtitle="Listings use their blurred public position. Report positions are precise and visible to managers only.">
      <Loading state={{ ...state, reload: load }} />
      <div className="map-tools">
        {[['listings', 'Listings'], ['zones', 'Disease zones'], ['reports', 'Farmer reports (private)']].map(([k, label]) => (
          <label key={k} className="check"><input type="checkbox" checked={show[k]} onChange={(e) => setShow({ ...show, [k]: e.target.checked })} /> {label}</label>
        ))}
        <span className="legend"><i style={{ background: '#1f7a3a' }} />live <i style={{ background: '#e67e22' }} />pending <i style={{ background: '#7b1fa2' }} />report <i style={{ background: '#c0392b' }} />control zone</span>
      </div>
      <div ref={el} className="map" />
    </Page>
  )
}
