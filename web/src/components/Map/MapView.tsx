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

function buildMarkerElement(transitType: string, isSelected: boolean): HTMLDivElement {
  const el = document.createElement('div');
  const color = TRANSIT_COLORS[transitType] || '#1565C0';
  const size = isSelected ? 22 : 16;
  el.style.cssText = `
    width: ${size}px;
    height: ${size}px;
    background: ${color};
    border: 2px solid white;
    border-radius: 50%;
    cursor: pointer;
    box-shadow: 0 2px 6px rgba(0,0,0,0.5);
    transition: all 0.15s ease;
    z-index: ${isSelected ? 10 : 1};
  `;
  return el;
}

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
  const markersRef = useRef<maplibregl.Marker[]>([]);
  const hasUserInteracted = useRef(false);

  const clearMarkers = useCallback(() => {
    markersRef.current.forEach((m) => m.remove());
    markersRef.current = [];
  }, []);

  const renderMarkers = useCallback(() => {
    const map = mapRef.current;
    if (!map) return;
    clearMarkers();

    stops.forEach((stop) => {
      if (!stop.lat || !stop.lng) return;
      const primaryType = stop.transitTypes?.[0] || 'bus';
      const isSelected = stop.stopId === selectedStopId;

      const el = buildMarkerElement(primaryType, isSelected);

      const popup = new maplibregl.Popup({ offset: 14, closeButton: false })
        .setText(stop.name);

      const marker = new maplibregl.Marker({ element: el })
        .setLngLat([stop.lng, stop.lat])
        .setPopup(popup)
        .addTo(map);

      el.addEventListener('click', (e) => {
        e.stopPropagation();
        onStopClick?.(stop.stopId);
      });
      markersRef.current.push(marker);
    });
  }, [stops, selectedStopId, onStopClick, clearMarkers]);

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

    map.on('load', () => renderMarkers());
    mapRef.current = map;

    return () => {
      clearMarkers();
      map.remove();
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    if (map.isStyleLoaded()) {
      renderMarkers();
    } else {
      map.once('idle', () => renderMarkers());
    }
  }, [renderMarkers]);

  return <div ref={mapContainer} style={{ width: '100%', height: '100%' }} />;
}
