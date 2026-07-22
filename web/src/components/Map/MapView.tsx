import { useEffect, useRef, useCallback } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import type { Stop } from '../../types/transit';
import { TRANSIT_COLORS } from '../../utils/transit-colors';
import '../../styles/maplibre-overrides.css';

/** A polyline to draw on the map (itinerary legs). Dashed = walking, solid = transit. */
export interface MapPolyline {
  /** [lng, lat] pairs in travel order. */
  points: [number, number][];
  color: string;
  dashed: boolean;
}

interface MapViewProps {
  stops: Stop[];
  selectedStopId?: string | null;
  activeStopIds?: Set<string>;
  polylines?: MapPolyline[];
  /** Fit the camera to the polylines whenever they change. */
  fitToPolylines?: boolean;
  onStopClick?: (stopId: string) => void;
  onMapMove?: (lat: number, lng: number) => void;
  initialLat?: number;
  initialLng?: number;
  initialZoom?: number;
}

const STOP_MARKERS_SOURCE = 'stops-source';
const STOP_HALO_LAYER = 'stops-halo-layer';
const STOP_MARKERS_LAYER = 'stops-layer';
const ITINERARY_SOURCE = 'itinerary-source';
const ITINERARY_TRANSIT_LAYER = 'itinerary-transit-layer';
const ITINERARY_WALK_LAYER = 'itinerary-walk-layer';
const SELECTED_COLOR = '#00A862';

export function MapView({
  stops,
  selectedStopId,
  activeStopIds,
  polylines,
  fitToPolylines = false,
  onStopClick,
  onMapMove,
  initialLat = 37.7749,
  initialLng = -122.4194,
  initialZoom = 13,
}: MapViewProps) {
  const mapContainer = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const hasUserInteracted = useRef(false);
  const mapLoaded = useRef(false);

  const updatePolylines = useCallback(() => {
    const map = mapRef.current;
    if (!map || !mapLoaded.current) return;

    if (!map.getSource(ITINERARY_SOURCE)) {
      map.addSource(ITINERARY_SOURCE, {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] },
      });
      // Below the stops layer so markers stay tappable/visible.
      map.addLayer(
        {
          id: ITINERARY_TRANSIT_LAYER,
          type: 'line',
          source: ITINERARY_SOURCE,
          filter: ['==', ['get', 'dashed'], false],
          layout: { 'line-cap': 'round', 'line-join': 'round' },
          paint: { 'line-width': 5, 'line-color': ['get', 'color'] },
        },
        STOP_HALO_LAYER,
      );
      map.addLayer(
        {
          id: ITINERARY_WALK_LAYER,
          type: 'line',
          source: ITINERARY_SOURCE,
          filter: ['==', ['get', 'dashed'], true],
          layout: { 'line-cap': 'round', 'line-join': 'round' },
          paint: { 'line-width': 4, 'line-color': ['get', 'color'], 'line-dasharray': [0.8, 1.6] },
        },
        STOP_HALO_LAYER,
      );
    }

    const lines = (polylines ?? []).filter((l) => l.points.length >= 2);
    const source = map.getSource(ITINERARY_SOURCE) as maplibregl.GeoJSONSource;
    source.setData({
      type: 'FeatureCollection',
      features: lines.map((line) => ({
        type: 'Feature' as const,
        geometry: { type: 'LineString' as const, coordinates: line.points },
        properties: { color: line.color, dashed: line.dashed },
      })),
    });

    if (fitToPolylines && lines.length > 0) {
      const bounds = new maplibregl.LngLatBounds();
      for (const line of lines) for (const p of line.points) bounds.extend(p);
      map.fitBounds(bounds, { padding: 60, maxZoom: 16 });
    }
  }, [polylines, fitToPolylines]);

  const updateStopsSource = useCallback(() => {
    const map = mapRef.current;
    if (!map) return;

    const source = map.getSource(STOP_MARKERS_SOURCE) as maplibregl.GeoJSONSource | undefined;
    if (!source) return;

    const features = stops
      .filter((s) => s.lat && s.lng)
      .map((stop) => ({
        type: 'Feature' as const,
        geometry: {
          type: 'Point' as const,
          coordinates: [stop.lng, stop.lat],
        },
        properties: {
          stopId: stop.stopId,
          name: stop.name,
          agencyNames: (stop.agencyNames || []).join(', '),
          transitType: stop.transitTypes?.[0] || 'bus',
          color: stop.stopId === selectedStopId
            ? SELECTED_COLOR
            : TRANSIT_COLORS[stop.transitTypes?.[0] || 'bus'] || '#00A862',
          selected: stop.stopId === selectedStopId,
          active: activeStopIds?.has(stop.stopId) ?? false,
        },
      }));

    source.setData({ type: 'FeatureCollection', features });
  }, [stops, selectedStopId, activeStopIds]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    if (hasUserInteracted.current) return;
    map.flyTo({ center: [initialLng, initialLat], zoom: initialZoom });
  }, [initialLat, initialLng, initialZoom]);

  useEffect(() => {
    if (!mapContainer.current) return;

    const map = new maplibregl.Map({
      container: mapContainer.current,
      style: 'https://tiles.openfreemap.org/styles/liberty',
      center: [initialLng, initialLat],
      zoom: initialZoom,
    });

    map.addControl(
      new maplibregl.GeolocateControl({
        positionOptions: { enableHighAccuracy: true },
        trackUserLocation: true,
      }),
      'bottom-right',
    );
    map.addControl(new maplibregl.NavigationControl(), 'bottom-right');

    map.on('moveend', (e) => {
      if (!e.originalEvent) return;
      hasUserInteracted.current = true;
      const center = map.getCenter();
      onMapMove?.(center.lat, center.lng);
    });

    map.on('load', () => {
      map.addSource(STOP_MARKERS_SOURCE, {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] },
      });

      // Pulsing halo behind markers with recent live activity
      map.addLayer({
        id: STOP_HALO_LAYER,
        type: 'circle',
        source: STOP_MARKERS_SOURCE,
        filter: ['==', ['get', 'active'], true],
        paint: {
          'circle-radius': 14,
          'circle-color': '#00A862',
          'circle-opacity': 0.25,
          'circle-stroke-width': 0,
        },
      });

      map.addLayer({
        id: STOP_MARKERS_LAYER,
        type: 'circle',
        source: STOP_MARKERS_SOURCE,
        paint: {
          'circle-radius': [
            'case',
            ['boolean', ['get', 'selected'], false],
            11,
            7,
          ],
          'circle-color': ['get', 'color'],
          'circle-stroke-width': 2,
          'circle-stroke-color': '#ffffff',
          'circle-opacity': 0.95,
          'circle-radius-transition': { duration: 300, delay: 0 },
        },
      });

      map.on('click', STOP_MARKERS_LAYER, (e) => {
        const props = e.features?.[0]?.properties;
        if (props?.stopId) {
          const content = document.createElement('div');
          const nameEl = document.createElement('div');
          nameEl.textContent = props.name;
          nameEl.style.fontWeight = '700';
          content.appendChild(nameEl);
          if (props.agencyNames) {
            const agencyEl = document.createElement('div');
            agencyEl.textContent = props.agencyNames;
            agencyEl.style.fontSize = '12px';
            agencyEl.style.opacity = '0.75';
            content.appendChild(agencyEl);
          }
          new maplibregl.Popup()
            .setLngLat(e.lngLat)
            .setDOMContent(content)
            .addTo(map);
          onStopClick?.(props.stopId);
        }
      });

      map.on('mouseenter', STOP_MARKERS_LAYER, () => {
        map.getCanvas().style.cursor = 'pointer';
      });
      map.on('mouseleave', STOP_MARKERS_LAYER, () => {
        map.getCanvas().style.cursor = '';
      });

      mapLoaded.current = true;
      updateStopsSource();
      updatePolylines();
    });

    mapRef.current = map;

    let rafId: number;
    function animateHalo() {
      const m = mapRef.current;
      if (m && m.getLayer(STOP_HALO_LAYER)) {
        const t = (Date.now() % 1600) / 1600;
        const wave = Math.sin(t * Math.PI * 2) * 0.5 + 0.5; // 0..1
        const radius = 10 + wave * 10;
        const opacity = 0.35 - wave * 0.25;
        m.setPaintProperty(STOP_HALO_LAYER, 'circle-radius', radius);
        m.setPaintProperty(STOP_HALO_LAYER, 'circle-opacity', opacity);
      }
      rafId = requestAnimationFrame(animateHalo);
    }
    rafId = requestAnimationFrame(animateHalo);

    return () => {
      cancelAnimationFrame(rafId);
      map.remove();
    };
  }, []);

  useEffect(() => {
    updateStopsSource();
  }, [updateStopsSource]);

  useEffect(() => {
    updatePolylines();
  }, [updatePolylines]);

  return <div ref={mapContainer} style={{ width: '100%', height: '100%' }} />;
}
