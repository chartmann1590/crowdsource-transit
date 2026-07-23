import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { ItineraryView } from '../components/Itinerary/ItineraryView';
import { TripActions } from '../components/Itinerary/TripActions';
import { MapView } from '../components/Map/MapView';
import { LoadingSpinner } from '../components/UI/LoadingSpinner';
import { Navbar } from '../components/UI/Navbar';
import { searchStops } from '../firebase/stops';
import { usePlanTrip } from '../hooks/usePlanTrip';
import { isTransitLeg, type TripPlan } from '../types/itinerary';
import { planPolylines, planTimes, planWalkStepMarkers } from '../utils/itineraryDisplay';
import styles from './PlanPage.module.css';

interface PlannerPlace {
  name: string;
  lat: number;
  lng: number;
}

interface Suggestion extends PlannerPlace {
  detail: string;
}

/**
 * Optional prefill via location state: { to } from stop pages (destination only), or
 * { from, to, autoPlan } when reopening a saved trip (both endpoints, and immediately
 * search for the current closest options rather than a stale saved schedule).
 */
interface PlanLocationState {
  from?: PlannerPlace;
  to?: PlannerPlace;
  autoPlan?: boolean;
}

export function PlanPage() {
  const location = useLocation();
  const locationState = location.state as PlanLocationState | null;

  const [origin, setOrigin] = useState<PlannerPlace | null>(locationState?.from ?? null);
  const [destination, setDestination] = useState<PlannerPlace | null>(locationState?.to ?? null);
  const [editing, setEditing] = useState<'from' | 'to'>('from');
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [selectedPlan, setSelectedPlan] = useState<TripPlan | null>(null);
  const [locationError, setLocationError] = useState<string | null>(null);
  const [locating, setLocating] = useState(false);
  const [timeMode, setTimeMode] = useState<'now' | 'depart' | 'arrive'>('now');
  const [timeValue, setTimeValue] = useState('');
  // How far the planner will look for a boarding/alighting stop. Persisted so users in
  // low-density areas (whose nearest served stop can be > default) set it once.
  const [maxWalkM, setMaxWalkM] = useState<number>(() => {
    const saved = Number(localStorage.getItem('maxWalkToStopM'));
    return Number.isFinite(saved) && saved > 0 ? saved : 1600;
  });
  const { plans, loading, planned, error, plan } = usePlanTrip();
  const searchTimer = useRef<ReturnType<typeof setTimeout>>(undefined);

  // Bias stop search toward whichever endpoint is already chosen, so e.g. searching
  // "Schenectady" from a trip that starts in Schenectady doesn't surface a same-named
  // street clear across the country.
  const searchBias = origin ?? destination;

  useEffect(() => {
    clearTimeout(searchTimer.current);
    if (query.length < 2) {
      setSuggestions([]);
      return;
    }
    searchTimer.current = setTimeout(() => {
      void searchStops(query, searchBias ?? undefined).then((stops) => {
        setSuggestions(
          stops.map((s) => ({
            name: s.name,
            detail: [s.state, s.country].filter(Boolean).join(', '),
            lat: s.lat,
            lng: s.lng,
          })),
        );
      });
    }, 300);
    return () => clearTimeout(searchTimer.current);
  }, [query, searchBias]);

  const applyPlace = useCallback(
    (place: PlannerPlace) => {
      if (editing === 'from') setOrigin(place);
      else setDestination(place);
      setQuery('');
      setSuggestions([]);
    },
    [editing],
  );

  const useMyLocation = useCallback(() => {
    setLocationError(null);
    if (!navigator.geolocation) {
      setLocationError("This browser doesn't support location — search for a stop instead.");
      return;
    }
    setLocating(true);

    let settled = false;
    const onSuccess = (pos: GeolocationPosition) => {
      if (settled) return;
      settled = true;
      setLocating(false);
      applyPlace({ name: 'My location', lat: pos.coords.latitude, lng: pos.coords.longitude });
    };
    const finalError = (err: GeolocationPositionError) => {
      if (settled) return;
      settled = true;
      setLocating(false);
      setLocationError(
        err.code === err.PERMISSION_DENIED
          ? 'Location permission denied — allow location access for this site in your browser settings, or search for a stop instead.'
          : "Couldn't get your location — search for a stop instead.",
      );
    };

    // Network/WiFi-based positioning (enableHighAccuracy: false) can resolve to
    // wherever the ISP's geolocation database places its network exchange — often a
    // major hub city hundreds of miles away, not the user's actual location. Try a
    // real GPS-grade fix first; only fall back to the network-based one (which returns
    // fast but can be wildly wrong) if the GPS attempt genuinely fails or times out.
    navigator.geolocation.getCurrentPosition(
      onSuccess,
      () => {
        if (settled) return;
        navigator.geolocation.getCurrentPosition(onSuccess, finalError, {
          enableHighAccuracy: false,
          timeout: 6_000,
          maximumAge: 5 * 60_000,
        });
      },
      { enableHighAccuracy: true, timeout: 8_000, maximumAge: 5 * 60_000 },
    );
    // Belt-and-suspenders: some browser/OS combinations swallow the error callback
    // entirely (e.g. Chromium on Windows with OS-level location services disabled) and
    // just hang forever instead of calling back. Force a visible failure so the button
    // never gets stuck silently on "Finding your location…". Sized to outlast both the
    // GPS attempt and its network-based fallback.
    setTimeout(() => {
      if (settled) return;
      settled = true;
      setLocating(false);
      setLocationError(
        "Couldn't get your location — check that location is turned on for your browser and device, or search for a stop instead.",
      );
    }, 16_000);
  }, [applyPlace]);

  const findRoutes = useCallback(() => {
    if (!origin || !destination) return;
    setSelectedPlan(null);
    const timeMs = timeMode !== 'now' && timeValue ? new Date(timeValue).getTime() : undefined;
    void plan({
      from: { name: origin.name, lat: origin.lat, lng: origin.lng },
      to: { name: destination.name, lat: destination.lat, lng: destination.lng },
      maxWalkToStopM: maxWalkM,
      ...(timeMode === 'depart' && timeMs !== undefined ? { departAtMs: timeMs } : {}),
      ...(timeMode === 'arrive' && timeMs !== undefined ? { arriveByMs: timeMs } : {}),
    });
  }, [origin, destination, timeMode, timeValue, maxWalkM, plan]);

  // Reopening a saved trip: saved trips store origin/destination only, never times
  // (schedules go stale), so jump straight into a fresh search for the current
  // closest options instead of showing whatever was true when it was saved.
  useEffect(() => {
    if (locationState?.autoPlan && locationState.from && locationState.to) {
      findRoutes();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className={styles.container}>
      <Navbar />
      <div className={styles.content}>
        <div className={styles.plannerPanel}>
          <h1>Plan a trip</h1>
          <div className={styles.endpoints}>
            <button
              type="button"
              className={`${styles.endpoint} ${editing === 'from' ? styles.endpointActive : ''}`}
              onClick={() => setEditing('from')}
            >
              <span className={styles.endpointLabel}>From</span>
              {origin?.name ?? 'Choose…'}
            </button>
            <button
              type="button"
              className={`${styles.endpoint} ${editing === 'to' ? styles.endpointActive : ''}`}
              onClick={() => setEditing('to')}
            >
              <span className={styles.endpointLabel}>To</span>
              {destination?.name ?? 'Choose…'}
            </button>
            <button
              type="button"
              className={styles.swap}
              title="Swap origin and destination"
              onClick={() => {
                setOrigin(destination);
                setDestination(origin);
              }}
            >
              ⇅
            </button>
          </div>

          <input
            className={styles.search}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={editing === 'from' ? 'Search starting stop or place…' : 'Search destination…'}
          />
          {locationError && <p className={styles.error}>{locationError}</p>}
          <ul className={styles.suggestions}>
            <li>
              <button type="button" onClick={useMyLocation} disabled={locating}>
                📍 {locating ? 'Finding your location…' : 'Use my location'}
              </button>
            </li>
            {(suggestions.length > 0 || query.length >= 2) &&
              suggestions.map((s, i) => (
                <li key={`${s.lat},${s.lng},${i}`}>
                  <button type="button" onClick={() => applyPlace(s)}>
                    {s.name}
                    {s.detail && <span className={styles.suggestionDetail}> · {s.detail}</span>}
                  </button>
                </li>
              ))}
          </ul>

          <div className={styles.timeMode}>
            <select
              className={styles.timeModeSelect}
              value={timeMode}
              onChange={(e) => setTimeMode(e.target.value as 'now' | 'depart' | 'arrive')}
            >
              <option value="now">Leave now</option>
              <option value="depart">Depart at…</option>
              <option value="arrive">Arrive by…</option>
            </select>
            {timeMode !== 'now' && (
              <input
                type="datetime-local"
                className={styles.timeModeInput}
                value={timeValue}
                onChange={(e) => setTimeValue(e.target.value)}
              />
            )}
          </div>

          <label className={styles.walkRadius}>
            <span>Max walk to a stop</span>
            <select
              className={styles.walkRadiusSelect}
              value={maxWalkM}
              onChange={(e) => {
                const v = Number(e.target.value);
                setMaxWalkM(v);
                localStorage.setItem('maxWalkToStopM', String(v));
              }}
            >
              <option value={800}>800 m (~10 min)</option>
              <option value={1600}>1.6 km (~20 min)</option>
              <option value={3000}>3 km (~35 min)</option>
              <option value={5000}>5 km (~60 min)</option>
            </select>
          </label>

          <button
            type="button"
            className={styles.planButton}
            disabled={!origin || !destination || loading || (timeMode !== 'now' && !timeValue)}
            onClick={findRoutes}
          >
            {loading ? 'Planning…' : 'Find routes'}
          </button>

          {loading && <LoadingSpinner />}
          {error && <p className={styles.error}>{error}</p>}
          {planned && !error && plans.length === 0 && !loading && (
            <p className={styles.empty}>
              No transit routes found for this trip. Try different endpoints or a shorter distance.
            </p>
          )}

          {selectedPlan ? (
            <div>
              <button type="button" className={styles.backToResults} onClick={() => setSelectedPlan(null)}>
                ← All options
              </button>
              <TripActions plan={selectedPlan} />
              <ItineraryView plan={selectedPlan} />
            </div>
          ) : (
            <ul className={styles.results}>
              {plans.map((p, i) => {
                const { dep, arr, duration } = planTimes(p);
                const transitLegs = p.legs.filter(isTransitLeg);
                return (
                  <li key={i}>
                    <button type="button" className={styles.resultCard} onClick={() => setSelectedPlan(p)}>
                      <span className={styles.resultTimes}>
                        {dep} → {arr}
                      </span>
                      <span className={styles.resultRoutes}>
                        {transitLegs.map((leg, j) => (
                          <span key={j} className={styles.resultBadge} style={{ backgroundColor: leg.route.color }}>
                            {leg.route.short || leg.route.long || leg.mode}
                          </span>
                        ))}
                      </span>
                      <span className={styles.resultMeta}>
                        {duration} ·{' '}
                        {transitLegs.length <= 1 ? 'Direct' : `${transitLegs.length - 1} transfer${transitLegs.length > 2 ? 's' : ''}`}
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
        <div className={styles.mapPanel}>
          <MapView
            stops={[]}
            polylines={selectedPlan ? planPolylines(selectedPlan) : []}
            fitToPolylines={!!selectedPlan}
            walkStepMarkers={selectedPlan ? planWalkStepMarkers(selectedPlan) : []}
            initialLat={origin?.lat ?? 37.7749}
            initialLng={origin?.lng ?? -122.4194}
          />
        </div>
      </div>
    </div>
  );
}
