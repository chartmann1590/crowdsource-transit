import { useEffect, useMemo, useState } from 'react';
import { ItineraryView } from '../components/Itinerary/ItineraryView';
import { TripActions } from '../components/Itinerary/TripActions';
import { MapView } from '../components/Map/MapView';
import { Navbar } from '../components/UI/Navbar';
import type { TripPlan } from '../types/itinerary';
import { planPolylines } from '../utils/itineraryDisplay';
import { decodeTripPlan, UnsupportedTripPlanVersionError } from '../utils/tripCodec';
import styles from './TripViewerPage.module.css';

const ANDROID_PACKAGE = 'com.charles.crowdtransit.app';

function isAndroid(): boolean {
  return /android/i.test(navigator.userAgent);
}

/**
 * Read-only viewer for shared trips (/trip#d=<blob>). Works logged-out — the whole
 * itinerary is self-contained in the URL hash.
 */
export function TripViewerPage() {
  const [plan, setPlan] = useState<TripPlan | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const match = window.location.hash.match(/[#&]d=([^&]+)/);
    if (!match) {
      setError('This link is missing its trip data.');
      return;
    }
    decodeTripPlan(match[1])
      .then(setPlan)
      .catch((e) => {
        setError(
          e instanceof UnsupportedTripPlanVersionError
            ? 'This trip link was made with a newer version of CrowdTransit — please update.'
            : "This trip link couldn't be read.",
        );
      });
  }, []);

  const polylines = useMemo(() => (plan ? planPolylines(plan) : []), [plan]);

  const openInApp = () => {
    if (!plan) return;
    const blob = window.location.hash.match(/[#&]d=([^&]+)/)?.[1] ?? '';
    // intent:// opens the app when installed, else falls back to the Play listing.
    window.location.href =
      `intent://trip?d=${blob}#Intent;scheme=crowdtransit;package=${ANDROID_PACKAGE};` +
      `S.browser_fallback_url=${encodeURIComponent(`https://play.google.com/store/apps/details?id=${ANDROID_PACKAGE}`)};end`;
  };

  return (
    <div className={styles.container}>
      <Navbar />
      <div className={styles.content}>
        {error && <p className={styles.error}>{error}</p>}
        {plan && (
          <>
            <div className={styles.viewerPanel}>
              <h1>Shared trip</h1>
              <TripActions plan={plan} />
              {isAndroid() && (
                <button type="button" className={styles.openInApp} onClick={openInApp}>
                  📱 Open in the CrowdTransit app
                </button>
              )}
              <ItineraryView plan={plan} />
            </div>
            <div className={styles.mapPanel}>
              <MapView
                stops={[]}
                polylines={polylines}
                fitToPolylines
                initialLat={plan.from.lat}
                initialLng={plan.from.lng}
              />
            </div>
          </>
        )}
      </div>
    </div>
  );
}
