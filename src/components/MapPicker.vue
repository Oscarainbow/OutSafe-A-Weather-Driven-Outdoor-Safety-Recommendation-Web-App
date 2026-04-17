<script setup>
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const props = defineProps({
  lat: { type: Number, default: 41.4993 },
  lon: { type: Number, default: -81.6944 },
  zoom: { type: Number, default: 10 },
  /** 铺满父容器（用于全屏地图布局） */
  fullscreen: { type: Boolean, default: false },
})

const emit = defineEmits(['select'])
const mapContainer = ref(null)
let map = null
let marker = null
let resizeObserver = null

function wrapLongitude(lng) {
  if (lng >= -180 && lng <= 180) return lng
  return ((((lng + 180) % 360) + 360) % 360) - 180
}

function clampLatitude(lat) {
  return Math.min(90, Math.max(-90, lat))
}

function normalizeLatLng(lat, lng) {
  return { lat: clampLatitude(lat), lon: wrapLongitude(lng) }
}

function invalidate() {
  if (map) {
    map.invalidateSize()
  }
}

function popupHtml(lat, lon) {
  return `<div class="map-popup-inner"><strong>Selected Location</strong><br/><span class="map-popup-coords">${lat.toFixed(
    5
  )}, ${lon.toFixed(5)}</span><br/><span class="map-popup-hint">Confirm on the left panel</span></div>`
}

function initMap() {
  if (!mapContainer.value) return
  map = L.map(mapContainer.value, { zoomControl: false }).setView([props.lat, props.lon], props.zoom)
  
  // Add zoom control to bottom left instead of top left
  L.control.zoom({
    position: 'bottomleft'
  }).addTo(map)

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© OpenStreetMap',
  }).addTo(map)

  const icon = L.icon({
    iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
    iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
    shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
    iconSize: [25, 41],
    iconAnchor: [12, 41],
  })
  marker = L.marker([props.lat, props.lon], { icon }).addTo(map)
  marker.bindPopup('', { closeButton: true, autoPan: true, className: 'outsafe-map-popup' })

  map.on('click', (e) => {
    const { lat, lng } = e.latlng
    const { lat: nLat, lon: nLon } = normalizeLatLng(lat, lng)
    marker.setLatLng([nLat, nLon])
    marker.setPopupContent(popupHtml(nLat, nLon))
    marker.openPopup()
    emit('select', { lat: nLat, lon: nLon })
  })

  nextTick(() => invalidate())
}

function updateMarker(lat, lon) {
  if (!marker || !map) return
  const { lat: nLat, lon: nLon } = normalizeLatLng(lat, lon)
  marker.setLatLng([nLat, nLon])
  map.setView([nLat, nLon], map.getZoom())
  marker.setPopupContent(popupHtml(nLat, nLon))
}

watch(
  () => [props.lat, props.lon],
  ([lat, lon]) => {
    if (map && marker) updateMarker(lat, lon)
  }
)

watch(
  () => props.fullscreen,
  () => nextTick(() => invalidate())
)

onMounted(() => {
  initMap()
  if (mapContainer.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => invalidate())
    resizeObserver.observe(mapContainer.value)
  }
  window.addEventListener('resize', invalidate)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', invalidate)
  resizeObserver?.disconnect()
  resizeObserver = null
  if (map) {
    map.remove()
    map = null
    marker = null
  }
})
</script>

<template>
  <div class="map-wrap" :class="{ 'map-wrap--fullscreen': fullscreen }">
    <div ref="mapContainer" class="map-container" role="application" aria-label="Map Picker" />
  </div>
</template>

<style scoped>
.map-wrap {
  position: relative;
  width: 100%;
}

.map-wrap--fullscreen {
  position: absolute;
  inset: 0;
  height: 100%;
}

.map-container {
  width: 100%;
  height: 280px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--bg);
}

.map-wrap--fullscreen .map-container {
  height: 100%;
  border-radius: 0;
}

:deep(.leaflet-container) {
  font-family: inherit;
  background: var(--bg);
}

:deep(.leaflet-control-zoom) {
  border: none !important;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.35) !important;
}

:deep(.leaflet-control-zoom a) {
  background: rgba(26, 35, 50, 0.92) !important;
  color: var(--text) !important;
  border-color: rgba(255, 255, 255, 0.12) !important;
}

:deep(.leaflet-bar a:hover) {
  background: rgba(40, 52, 70, 0.95) !important;
}

:deep(.leaflet-control-attribution) {
  background: rgba(15, 20, 25, 0.75) !important;
  color: var(--muted) !important;
  font-size: 10px;
}

:deep(.leaflet-control-attribution a) {
  color: var(--accent) !important;
}

:deep(.outsafe-map-popup .leaflet-popup-content-wrapper) {
  background: rgba(22, 29, 39, 0.95);
  color: var(--text);
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.45);
}

:deep(.outsafe-map-popup .leaflet-popup-tip) {
  background: rgba(22, 29, 39, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

:deep(.outsafe-map-popup .leaflet-popup-close-button) {
  color: var(--muted) !important;
  padding: 8px 10px 0 0 !important;
}

:deep(.map-popup-inner) {
  font-size: 13px;
  line-height: 1.45;
  min-width: 160px;
}

:deep(.map-popup-coords) {
  font-family: ui-monospace, monospace;
  font-size: 13px;
  color: var(--accent, #58a6ff);
}

:deep(.map-popup-hint) {
  font-size: 11px;
  color: var(--muted);
}
</style>
