import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { MapView } from '../components/Map/MapView';
import { StopList } from '../components/Stop/StopList';
import { Navbar } from '../components/UI/Navbar';
import { useNearbyStops } from '../hooks/useNearbyStops';
import styles from './Home.module.css';

export function Home() {
  const navigate = useNavigate();
  const [selectedStopId, setSelectedStopId] = useState<string | null>(null);
  const [mapCenter, setMapCenter] = useState({ lat: 37.7749, lng: -122.4194 });

  useEffect(() => {
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setMapCenter({ lat: pos.coords.latitude, lng: pos.coords.longitude });
      },
      () => {},
    );
  }, []);

  const { stops: nearbyStops, loading } = useNearbyStops(mapCenter.lat, mapCenter.lng);

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
