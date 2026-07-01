import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { MapView } from '../components/Map/MapView';
import { StopList } from '../components/Stop/StopList';
import { Navbar } from '../components/UI/Navbar';
import { useNearbyStops } from '../hooks/useNearbyStops';
import styles from './Home.module.css';

const DEFAULT_LAT = 37.7749;
const DEFAULT_LNG = -122.4194;

type GeoStatus = 'pending' | 'success' | 'denied' | 'error' | 'unsupported';

export function Home() {
  const navigate = useNavigate();
  const [selectedStopId, setSelectedStopId] = useState<string | null>(null);
  const [mapCenter, setMapCenter] = useState({ lat: DEFAULT_LAT, lng: DEFAULT_LNG });
  const [hasGpsFix, setHasGpsFix] = useState(false);
  const [geoStatus, setGeoStatus] = useState<GeoStatus>('pending');
  const gpsWatchId = useRef<number | null>(null);

  const requestLocation = useCallback(() => {
    if (!navigator.geolocation) {
      setGeoStatus('unsupported');
      return;
    }

    setGeoStatus('pending');

    if (gpsWatchId.current != null) {
      navigator.geolocation.clearWatch(gpsWatchId.current);
      gpsWatchId.current = null;
    }

    gpsWatchId.current = navigator.geolocation.watchPosition(
      (pos) => {
        const lat = pos.coords.latitude;
        const lng = pos.coords.longitude;
        setMapCenter({ lat, lng });
        setHasGpsFix(true);
        setGeoStatus('success');
        if (gpsWatchId.current != null) {
          navigator.geolocation.clearWatch(gpsWatchId.current);
          gpsWatchId.current = null;
        }
      },
      (err) => {
        setGeoStatus(err.code === err.PERMISSION_DENIED ? 'denied' : 'error');
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 60000 },
    );
  }, []);

  useEffect(() => {
    requestLocation();
    return () => {
      if (gpsWatchId.current != null) {
        navigator.geolocation.clearWatch(gpsWatchId.current);
      }
    };
  }, [requestLocation]);

  const { stops: nearbyStops, loading } = useNearbyStops(
    mapCenter.lat,
    mapCenter.lng,
    hasGpsFix ? 1 : 0,
  );

  return (
    <div className={styles.container}>
      <Navbar />
      <div className={styles.layout}>
        <aside className={styles.sidebar}>
          <div className={styles.sidebarHeader}>
            <h2>Nearby Stops</h2>
            <span className={styles.count}>
              {loading ? 'Loading...' : `${nearbyStops.length} found`}
            </span>
          </div>
          {geoStatus !== 'success' && (
            <div className={styles.geoBanner}>
              {geoStatus === 'pending' && <p>Finding your location…</p>}
              {geoStatus === 'denied' && (
                <>
                  <p>
                    Location access was denied, so we're showing stops near San Francisco
                    instead of your actual location.
                  </p>
                  <button onClick={requestLocation} className={styles.geoRetryButton}>
                    Enable location
                  </button>
                </>
              )}
              {geoStatus === 'error' && (
                <>
                  <p>
                    Couldn't determine your location, so we're showing stops near San
                    Francisco instead.
                  </p>
                  <button onClick={requestLocation} className={styles.geoRetryButton}>
                    Try again
                  </button>
                </>
              )}
              {geoStatus === 'unsupported' && (
                <p>Your browser doesn't support location - showing stops near San Francisco.</p>
              )}
            </div>
          )}
          <StopList
            stops={nearbyStops}
            selectedStopId={selectedStopId}
            onSelectStop={setSelectedStopId}
            onViewDetail={(id) => navigate(`/stop/${id}`)}
          />
        </aside>

        <main className={styles.mapContainer}>
          <MapView
            stops={nearbyStops}
            selectedStopId={selectedStopId}
            onStopClick={(id) => setSelectedStopId(id)}
            onMapMove={(lat, lng) => setMapCenter({ lat, lng })}
            initialLat={mapCenter.lat}
            initialLng={mapCenter.lng}
          />
        </main>
      </div>
    </div>
  );
}
