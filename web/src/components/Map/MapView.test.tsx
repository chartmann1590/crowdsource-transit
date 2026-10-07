import { act, cleanup, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Stop } from '../../types/transit';

/**
 * Smoke test for the MapLibre wrapper. maplibre-gl needs WebGL, which jsdom doesn't have,
 * so the library is replaced by a small recording fake that implements exactly the API
 * surface MapView uses. If a maplibre-gl upgrade renames/removes one of these calls the
 * TypeScript build catches it; this test catches MapView wiring regressions.
 */

type Handler = (e?: unknown) => void;

const h = vi.hoisted(() => {
  const state = {
    maps: [] as unknown[],
    popups: [] as { lngLat: unknown; content: HTMLElement | null; added: boolean }[],
    controls: [] as string[],
    workerUrl: null as string | null,
  };
  return { state };
});

vi.mock('maplibre-gl', () => {
  class FakeSource {
    data: unknown;
    constructor(spec: { data: unknown }) {
      this.data = spec.data;
    }
    setData(d: unknown) {
      this.data = d;
    }
  }
  class FakeMap {
    options: Record<string, unknown>;
    handlers = new Map<string, Handler[]>();
    sources = new Map<string, FakeSource>();
    layers: { id: string; before?: string }[] = [];
    removed = false;
    canvas = document.createElement('canvas');
    fitBounds = vi.fn();
    flyTo = vi.fn();
    setPaintProperty = vi.fn();
    constructor(options: Record<string, unknown>) {
      this.options = options;
      h.state.maps.push(this as never);
    }
    addControl(control: { kind: string }) {
      h.state.controls.push(control.kind);
      return this;
    }
    on(type: string, a: string | Handler, b?: Handler) {
      const key = typeof a === 'string' ? `${type}:${a}` : type;
      const fn = (typeof a === 'string' ? b : a) as Handler;
      this.handlers.set(key, [...(this.handlers.get(key) ?? []), fn]);
      return this;
    }
    fire(type: string, layer?: string, e?: unknown) {
      for (const fn of this.handlers.get(layer ? `${type}:${layer}` : type) ?? []) fn(e);
    }
    addSource(id: string, spec: { data: unknown }) {
      this.sources.set(id, new FakeSource(spec));
    }
    getSource(id: string) {
      return this.sources.get(id);
    }
    addLayer(layer: { id: string }, before?: string) {
      this.layers.push({ id: layer.id, before });
    }
    getLayer(id: string) {
      return this.layers.find((l) => l.id === id);
    }
    getCanvas() {
      return this.canvas;
    }
    getCenter() {
      return { lat: 1, lng: 2 };
    }
    remove() {
      this.removed = true;
    }
  }
  class LngLatBounds {
    points: unknown[] = [];
    extend(p: unknown) {
      this.points.push(p);
      return this;
    }
  }
  class Popup {
    entry = { lngLat: null as unknown, content: null as HTMLElement | null, added: false };
    constructor() {
      h.state.popups.push(this.entry);
    }
    setLngLat(l: unknown) {
      this.entry.lngLat = l;
      return this;
    }
    setDOMContent(c: HTMLElement) {
      this.entry.content = c;
      return this;
    }
    addTo() {
      this.entry.added = true;
      return this;
    }
  }
  class GeolocateControl {
    kind = 'geolocate';
  }
  class NavigationControl {
    kind = 'navigation';
  }
  const setWorkerUrl = (url: string) => {
    h.state.workerUrl = url;
  };
  const lib = { Map: FakeMap, LngLatBounds, Popup, GeolocateControl, NavigationControl, setWorkerUrl };
  return { default: lib, ...lib };
});

import { MapView } from './MapView';

interface FakeMap {
  options: Record<string, unknown>;
  sources: Map<string, { data: { features: { properties: Record<string, unknown>; geometry: { coordinates: unknown } }[] } }>;
  layers: { id: string; before?: string }[];
  removed: boolean;
  fitBounds: ReturnType<typeof vi.fn>;
  flyTo: ReturnType<typeof vi.fn>;
  fire: (type: string, layer?: string, e?: unknown) => void;
}

function makeStop(over: Partial<Stop>): Stop {
  return {
    stopId: 's1',
    agencyId: 'a1',
    name: 'Main St',
    lat: 37.78,
    lng: -122.41,
    country: 'US',
    state: 'CA',
    city: 'San Francisco',
    transitTypes: ['bus'],
    routeIds: {},
    agencyNames: ['Muni'],
    ratingSum: 0,
    ratingCount: 0,
    commentCount: 0,
    ...over,
  } as Stop;
}

const lastMap = () => h.state.maps[h.state.maps.length - 1] as unknown as FakeMap;

describe('MapView', () => {
  it('points MapLibre at the bundled worker (required since maplibre-gl v6)', () => {
    expect(h.state.workerUrl).toEqual(expect.stringContaining('maplibre-gl-worker'));
  });

  beforeEach(() => {
    h.state.maps.length = 0;
    h.state.popups.length = 0;
    h.state.controls.length = 0;
  });
  afterEach(() => cleanup());

  it('creates one map with the given camera and adds controls', () => {
    render(<MapView stops={[]} initialLat={40.7} initialLng={-74} initialZoom={11} />);
    expect(h.state.maps).toHaveLength(1);
    const map = lastMap();
    expect(map.options.center).toEqual([-74, 40.7]);
    expect(map.options.zoom).toBe(11);
    expect(map.options.container).toBeInstanceOf(HTMLDivElement);
    expect(h.state.controls).toEqual(['geolocate', 'navigation']);
  });

  it('on load adds stop + itinerary layers and pushes stop features', () => {
    const stops = [
      makeStop({ stopId: 'a', name: 'A', transitTypes: ['subway'] }),
      makeStop({ stopId: 'b', name: 'B', lat: 37.79, lng: -122.4 }),
      makeStop({ stopId: 'nolat', lat: 0, lng: 0 }),
    ];
    render(<MapView stops={stops} selectedStopId="b" activeStopIds={new Set(['a'])} />);
    const map = lastMap();
    act(() => map.fire('load'));

    const ids = map.layers.map((l) => l.id);
    expect(ids).toEqual(
      expect.arrayContaining(['stops-halo-layer', 'stops-layer', 'itinerary-transit-layer', 'itinerary-walk-layer', 'walk-steps-layer']),
    );
    // Itinerary lines sit under the stop markers.
    expect(map.layers.find((l) => l.id === 'itinerary-transit-layer')?.before).toBe('stops-halo-layer');

    const features = map.sources.get('stops-source')!.data.features;
    expect(features.map((f) => f.properties.stopId)).toEqual(['a', 'b']); // stops without coords are skipped
    expect(features[0].properties).toMatchObject({ active: true, selected: false, color: '#8B2FC9' });
    expect(features[1].properties).toMatchObject({ active: false, selected: true, color: '#00A862' });
    expect(features[1].geometry.coordinates).toEqual([-122.4, 37.79]);
  });

  it('draws polylines and fits the camera to them', () => {
    const polylines = [
      { points: [[-122.4, 37.7], [-122.3, 37.8]] as [number, number][], color: '#f00', dashed: false },
      { points: [[-122.3, 37.8]] as [number, number][], color: '#0f0', dashed: true }, // too short, dropped
    ];
    render(
      <MapView stops={[]} polylines={polylines} fitToPolylines walkStepMarkers={[{ lat: 37.75, lng: -122.35 }]} />,
    );
    const map = lastMap();
    act(() => map.fire('load'));

    const lines = map.sources.get('itinerary-source')!.data.features;
    expect(lines).toHaveLength(1);
    expect(lines[0].properties).toEqual({ color: '#f00', dashed: false });
    const steps = map.sources.get('walk-steps-source')!.data.features;
    expect(steps[0].geometry.coordinates).toEqual([-122.35, 37.75]);
    expect(map.fitBounds).toHaveBeenCalledTimes(1);
  });

  it('clicking a stop opens a popup and reports the stop id', () => {
    const onStopClick = vi.fn();
    render(<MapView stops={[makeStop({})]} onStopClick={onStopClick} />);
    const map = lastMap();
    act(() => map.fire('load'));
    act(() =>
      map.fire('click', 'stops-layer', {
        lngLat: { lng: -122.41, lat: 37.78 },
        features: [{ properties: { stopId: 's1', name: 'Main St', agencyNames: 'Muni' } }],
      }),
    );
    expect(onStopClick).toHaveBeenCalledWith('s1');
    expect(h.state.popups).toHaveLength(1);
    expect(h.state.popups[0].added).toBe(true);
    expect(h.state.popups[0].content?.textContent).toBe('Main StMuni');
  });

  it('reports user-initiated moves only', () => {
    const onMapMove = vi.fn();
    render(<MapView stops={[]} onMapMove={onMapMove} />);
    const map = lastMap();
    act(() => map.fire('moveend', undefined, {}));
    expect(onMapMove).not.toHaveBeenCalled();
    act(() => map.fire('moveend', undefined, { originalEvent: new Event('mouseup') }));
    expect(onMapMove).toHaveBeenCalledWith(1, 2);
  });

  it('removes the map on unmount', () => {
    const { unmount } = render(<MapView stops={[]} />);
    const map = lastMap();
    unmount();
    expect(map.removed).toBe(true);
  });
});
