import { useEffect, useRef } from 'react';
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
  const markersRef = useRef<Map<string, maplibregl.Marker>>(new Map());
  const hasUserInteracted = useRef(false);

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

    mapRef.current = map;

    return () => {
      markersRef.current.forEach((m) => m.remove());
      map.remove();
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    const checkStyle = () => {
      if (!map.isStyleLoaded()) {
        setTimeout(checkStyle, 100);
        return;
      }

      markersRef.current.forEach((m) => m.remove());
      markersRef.current.clear();

      stops.forEach((stop) => {
        if (!stop.lat || !stop.lng) return;

        const primaryType = stop.transitTypes?.[0] || 'bus';
        const color = TRANSIT_COLORS[primaryType] || '#1565C0';
        const isSelected = stop.stopId === selectedStopId;

        const el = document.createElement('div');
        el.style.cssText = `
          width: ${isSelected ? '20px' : '14px'};
          height: ${isSelected ? '20px' : '14px'};
          background: ${color};
          border: 2px solid white;
          border-radius: 50%;
          cursor: pointer;
          box-shadow: 0 2px 6px rgba(0,0,0,0.5);
          transition: all 0.2s ease;
        `;

        const marker = new maplibregl.Marker({ element: el })
          .setLngLat([stop.lng, stop.lat])
          .addTo(map);

        el.addEventListener('click', () => onStopClick?.(stop.stopId));
        markersRef.current.set(stop.stopId, marker);
      });
    };

    checkStyle();
  }, [stops, selectedStopId, onStopClick]);

  return <div ref={mapContainer} style={{ width: '100%', height: '100%' }} />;
}
