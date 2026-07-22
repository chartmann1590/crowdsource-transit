import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { ItineraryView } from '../components/Itinerary/ItineraryView';
import { MapView } from '../components/Map/MapView';
import { LoadingSpinner } from '../components/UI/LoadingSpinner';
import { Navbar } from '../components/UI/Navbar';
import { searchStops } from '../firebase/stops';
import { usePlanTrip } from '../hooks/usePlanTrip';
import { isTransitLeg, type TripPlan } from '../types/itinerary';
import { planPolylines, planTimes } from '../utils/itineraryDisplay';
import styles from './PlanPage.module.css';

interface PlannerPlace {
  name: string;
  lat: number;
  lng: number;
}

interface Suggestion extends PlannerPlace {
  detail: string;
}

/** Optional prefill via location state: { to: { name, lat, lng } } from stop pages. */
interface PlanLocationState {
  to?: PlannerPlace;
}

export function PlanPage() {
  const location = useLocation();
  const prefill = (location.state as PlanLocationState | null)?.to;

  const [origin, setOrigin] = useState<PlannerPlace | null>(null);
  const [destination, setDestination] = useState<PlannerPlace | null>(prefill ?? null);
  const [editing, setEditing] = useState<'from' | 'to'>('from');
  const [query, setQuery] = useState('');
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [selectedPlan, setSelectedPlan] = useState<TripPlan | null>(null);
  const { plans, loading, planned, error, plan } = usePlanTrip();
  const searchTimer = useRef<ReturnType<typeof setTimeout>>(undefined);

  useEffect(() => {
    clearTimeout(searchTimer.current);
    if (query.length < 2) {
      setSuggestions([]);
      return;
    }
    searchTimer.current = setTimeout(() => {
      void searchStops(query).then((stops) => {
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
  }, [query]);

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
    navigator.geolocation?.getCurrentPosition(
      (pos) => applyPlace({ name: 'My location', lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => {},
      { enableHighAccuracy: true, timeout: 10_000 },
    );
  }, [applyPlace]);

  const findRoutes = useCallback(() => {
    if (!origin || !destination) return;
    setSelectedPlan(null);
    void plan({
      from: { name: origin.name, lat: origin.lat, lng: origin.lng },
      to: { name: destination.name, lat: destination.lat, lng: destination.lng },
    });
  }, [origin, destination, plan]);

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
          {(suggestions.length > 0 || query.length >= 2) && (
            <ul className={styles.suggestions}>
              <li>
                <button type="button" onClick={useMyLocation}>
                  📍 Use my location
                </button>
              </li>
              {suggestions.map((s, i) => (
                <li key={`${s.lat},${s.lng},${i}`}>
                  <button type="button" onClick={() => applyPlace(s)}>
                    {s.name}
                    {s.detail && <span className={styles.suggestionDetail}> · {s.detail}</span>}
                  </button>
                </li>
              ))}
            </ul>
          )}

          <button
            type="button"
            className={styles.planButton}
            disabled={!origin || !destination || loading}
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
            initialLat={origin?.lat ?? 37.7749}
            initialLng={origin?.lng ?? -122.4194}
          />
        </div>
      </div>
    </div>
  );
}
