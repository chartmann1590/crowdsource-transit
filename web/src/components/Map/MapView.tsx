import { useEffect, useRef, useCallback } from 'react';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import type { Stop } from '../../types/transit';
import { TRANSIT_COLORS } from '../../utils/transit-colors';
import '../../styles/maplibre-overrides.css';

interface MapViewProps {
  stops: Stop[];
  selectedStopId?: string | null;
  onStopClick?: (stopId: string) => void;
  onMapMove?: (lat: number, lng: number) => void;
  initialLat?: number;
  initialLng?: number;
  initialZoom?: number;
}

const STOP_MARKERS_SOURCE = 'stops-source';
const STOP_MARKERS_LAYER = 'stops-layer';

export function MapView({
  stops,
  selectedStopId,
  onStopClick,
  onMapMove,
  initialLat = 37.7749,
  initialLng = -122.4194,
  initialZoom = 13,
}: MapViewProps) {
  const mapContainer = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const hasUserInteracted = useRef(false);

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
          transitType: stop.transitTypes?.[0] || 'bus',
          color: TRANSIT_COLORS[stop.transitTypes?.[0] || 'bus'] || '#1565C0',
          selected: stop.stopId === selectedStopId,
        },
      }));

    source.setData({ type: 'FeatureCollection', features });
  }, [stops, selectedStopId]);

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

      map.addLayer({
        id: STOP_MARKERS_LAYER,
        type: 'circle',
        source: STOP_MARKERS_SOURCE,
        paint: {
          'circle-radius': [
            'case',
            ['boolean', ['get', 'selected'], false],
            10,
            7,
          ],
          'circle-color': ['get', 'color'],
          'circle-stroke-width': 2,
          'circle-stroke-color': '#ffffff',
          'circle-opacity': 0.95,
        },
      });

      map.on('click', STOP_MARKERS_LAYER, (e) => {
        const props = e.features?.[0]?.properties;
        if (props?.stopId) {
          new maplibregl.Popup()
            .setLngLat(e.lngLat)
            .setText(props.name)
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

      updateStopsSource();
    });

    mapRef.current = map;

    return () => {
      map.remove();
    };
  }, []);

  useEffect(() => {
    updateStopsSource();
  }, [updateStopsSource]);

  return <div ref={mapContainer} style={{ width: '100%', height: '100%' }} />;
}
