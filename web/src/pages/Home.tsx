import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { MapView } from '../components/Map/MapView';
import { StopList } from '../components/Stop/StopList';
import { Navbar } from '../components/UI/Navbar';
import { useNearbyStops } from '../hooks/useNearbyStops';
import styles from './Home.module.css';

const DEFAULT_LAT = 37.7749;
const DEFAULT_LNG = -122.4194;

export function Home() {
  const navigate = useNavigate();
  const [selectedStopId, setSelectedStopId] = useState<string | null>(null);
  const [mapCenter, setMapCenter] = useState({ lat: DEFAULT_LAT, lng: DEFAULT_LNG });
  const [hasGpsFix, setHasGpsFix] = useState(false);
  const gpsWatchId = useRef<number | null>(null);

  useEffect(() => {
    if (!navigator.geolocation) return;

    gpsWatchId.current = navigator.geolocation.watchPosition(
      (pos) => {
        const lat = pos.coords.latitude;
        const lng = pos.coords.longitude;
        setMapCenter({ lat, lng });
        setHasGpsFix(true);
        if (gpsWatchId.current != null) {
          navigator.geolocation.clearWatch(gpsWatchId.current);
          gpsWatchId.current = null;
        }
      },
      () => {},
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 60000 },
    );

    return () => {
      if (gpsWatchId.current != null) {
        navigator.geolocation.clearWatch(gpsWatchId.current);
      }
    };
  }, []);

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
