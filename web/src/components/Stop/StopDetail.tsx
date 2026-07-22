import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { Stop } from '../../types/transit';
import { StarRating } from '../Review/StarRating';
import { TransitBadge } from '../Transit/TransitBadge';
import { LoginModal } from '../Auth/LoginModal';
import { formatDate, formatRelativeMinutes } from '../../utils/format';
import { useAuth } from '../Auth/AuthContext';
import { useActivity } from '../../hooks/useActivity';
import { usePhotos } from '../../hooks/usePhotos';
import { submitActivity, type ActivityType } from '../../firebase/activity';
import { uploadStopPhoto } from '../../firebase/photos';
import { ref, get } from 'firebase/database';
import { database } from '../../firebase/config';
import {
  getRoutesAndScheduleServingStop,
  getNearbyStops,
  TransitlandRateLimitError,
  getStaticRoutesAndAgencies,
  type ServedRoute,
  type ServedDeparture,
} from '../../api/transitland';
import styles from './StopDetail.module.css';

interface StopDetailProps {
  stop: Stop;
}

const REPORT_TYPES: { type: ActivityType; label: string }[] = [
  { type: 'on_time', label: 'On time' },
  { type: 'late', label: 'Late' },
  { type: 'crowded', label: 'Crowded' },
  { type: 'empty', label: 'Empty' },
  { type: 'not_running', label: 'Not running' },
];

export function StopDetail({ stop }: StopDetailProps) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const activity = useActivity(stop.stopId);
  const photos = usePhotos(stop.stopId);
  const [showLogin, setShowLogin] = useState(false);
  const [pending, setPending] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [viewerIndex, setViewerIndex] = useState<number | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [servedRoutes, setServedRoutes] = useState<ServedRoute[]>([]);
  const [upcoming, setUpcoming] = useState<ServedDeparture[]>([]);
  const [scheduleLoading, setScheduleLoading] = useState(false);
  const [scheduleError, setScheduleError] = useState<string | null>(null);

  const [resolvedStop, setResolvedStop] = useState<{ stopId: string; name: string } | null>(null);
  const [resolvedStopLoading, setResolvedStopLoading] = useState(false);
  const [agencies, setAgencies] = useState<string[]>([]);

  // Load static agency and routes from Firebase if this is a Firebase stop
  useEffect(() => {
    let active = true;

    // Reset states for a new stop
    setServedRoutes([]);
    setAgencies([]);
    setUpcoming([]);
    setScheduleError(null);

    if (!stop.agencyId && (!stop.routeIds || Object.keys(stop.routeIds).length === 0)) {
      return;
    }

    async function loadFirebaseData() {
      try {
        let agencyName = '';
        if (stop.agencyId) {
          const agencySnap = await get(ref(database, `agencies/${stop.agencyId}`));
          if (agencySnap.exists() && active) {
            const agency = agencySnap.val();
            agencyName = agency.name || '';
            setAgencies((prev) => Array.from(new Set([...prev, agencyName])));
          }
        }

        if (stop.routeIds && active) {
          const routeIds = Object.keys(stop.routeIds);
          const routePromises = routeIds.map(async (id) => {
            const routeSnap = await get(ref(database, `routes/${id}`));
            if (routeSnap.exists()) {
              const route = routeSnap.val();
              return {
                routeId: route.routeId,
                onestopId: route.routeId,
                shortName: route.shortName,
                longName: route.longName,
                routeType: null,
                transitType: route.type,
                color: route.color || '#00A862',
                textColor: route.textColor || '#FFFFFF',
                agencyName: agencyName,
              } as ServedRoute;
            }
            return null;
          });
          const routes = await Promise.all(routePromises);
          if (active) {
            const validRoutes = routes.filter((r): r is ServedRoute => r !== null);
            setServedRoutes(validRoutes);
          }
        }
      } catch (err) {
        console.error('Failed to load Firebase agency/routes:', err);
      }
    }

    loadFirebaseData();

    return () => {
      active = false;
    };
  }, [stop.stopId, stop.agencyId]);

  useEffect(() => {
    let active = true;
    const isTransitlandId = !stop.crowdsourced && /^[rsdf]-/.test(stop.stopId);

    if (isTransitlandId) {
      setResolvedStop({ stopId: stop.stopId, name: stop.name });
      setResolvedStopLoading(false);
      return;
    }

    setResolvedStopLoading(true);
    setResolvedStop(null);

    // Call Transitland's getNearbyStops with a 200m radius
    getNearbyStops(stop.lat, stop.lng, 200)
      .then((nearby) => {
        if (!active) return;
        const publicStops = nearby.filter((s) => !s.crowdsourced && /^[rsdf]-/.test(s.stopId));
        if (publicStops.length > 0) {
          // Nearest public stop
          const nearest = publicStops[0];
          setResolvedStop({ stopId: nearest.stopId, name: nearest.name });
        } else {
          setResolvedStop(null);
        }
      })
      .catch((err) => {
        console.error('Failed to find nearby public stops:', err);
      })
      .finally(() => {
        if (active) setResolvedStopLoading(false);
      });

    return () => {
      active = false;
    };
  }, [stop.stopId, stop.lat, stop.lng, stop.crowdsourced]);

  useEffect(() => {
    if (!resolvedStop) {
      setUpcoming([]);
      setScheduleError(null);
      // Only clear routes/agencies if this is a Transitland stop with no resolved stop
      const isTransitlandId = !stop.crowdsourced && /^[rsdf]-/.test(stop.stopId);
      if (isTransitlandId) {
        setServedRoutes([]);
        setAgencies([]);
      }
      return;
    }
    let active = true;
    setScheduleLoading(true);
    setScheduleError(null);
    getRoutesAndScheduleServingStop(resolvedStop.stopId)
      .then((result) => {
        if (!active) return;
        setUpcoming(result.upcoming);

        if (result.routes.length > 0) {
          setServedRoutes((prev) => prev.length === 0 ? result.routes : prev);
          setAgencies((prev) => {
            if (prev.length > 0) return prev;
            return Array.from(
              new Set(
                result.routes
                  .map((r) => r.agencyName)
                  .filter((name): name is string => typeof name === 'string' && name.trim().length > 0)
              )
            );
          });
        } else {
          // If departures are empty, and it is a Transitland stop, fallback to static route_stops
          const isTransitlandId = !stop.crowdsourced && /^[rsdf]-/.test(stop.stopId);
          if (isTransitlandId) {
            const staticResult = getStaticRoutesAndAgencies(stop);
            setServedRoutes(staticResult.routes);
            setAgencies(
              Array.from(
                new Set(
                  staticResult.routes
                    .map((r) => r.agencyName)
                    .filter((name): name is string => typeof name === 'string' && name.trim().length > 0)
                )
              )
            );
          }
        }
      })
      .catch((err: unknown) => {
        if (!active) return;
        // Fallback to static route_stops for Transitland stop on API failure
        const isTransitlandId = !stop.crowdsourced && /^[rsdf]-/.test(stop.stopId);
        if (isTransitlandId) {
          const staticResult = getStaticRoutesAndAgencies(stop);
          setServedRoutes(staticResult.routes);
          setAgencies(
            Array.from(
              new Set(
                staticResult.routes
                  .map((r) => r.agencyName)
                  .filter((name): name is string => typeof name === 'string' && name.trim().length > 0)
              )
            )
          );
        }

        if (err instanceof TransitlandRateLimitError) {
          setScheduleError('Schedule info temporarily unavailable. Please try again in a moment.');
        } else {
          setScheduleError(null); // soft-fail
        }
        setUpcoming([]);
      })
      .finally(() => {
        if (active) setScheduleLoading(false);
      });
    return () => {
      active = false;
    };
  }, [resolvedStop, stop]);

  const avgRating = stop.ratingCount > 0 ? stop.ratingSum / stop.ratingCount : 0;

  const location = [stop.city, stop.state || stop.country]
    .filter(Boolean)
    .join(', ');

  const checkinCount = new Set(
    activity.filter((a) => a.type === 'checkin').map((a) => a.uid),
  ).size;
  const latestReport = activity.find((a) => a.type !== 'checkin');
  const reportLabel = REPORT_TYPES.find((r) => r.type === latestReport?.type)?.label;

  async function handleActivity(type: ActivityType) {
    if (!user) {
      setShowLogin(true);
      return;
    }
    setPending(type);
    try {
      await submitActivity(stop.stopId, type);
    } catch (err) {
      console.error('Failed to submit activity:', err);
    } finally {
      setPending(null);
    }
  }

  async function handlePhotoUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!user) {
      setShowLogin(true);
      return;
    }
    setUploading(true);
    try {
      await uploadStopPhoto(stop.stopId, file);
    } catch (err) {
      console.error('Failed to upload photo:', err);
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  }

  return (
    <div className={styles.container}>
      <h1 className={styles.name}>{stop.name}</h1>
      <p className={styles.location}>
        {[location, stop.code && `Stop #${stop.code}`].filter(Boolean).join(' • ') || '—'}
      </p>
      {agencies.length > 0 && (
        <p className={styles.agency}>
          <span className={styles.agencyLabel}>Service Provider:</span> {agencies.join(', ')}
        </p>
      )}

      <div className={styles.badges}>
        {(stop.transitTypes || []).map((type) => (
          <TransitBadge key={type} type={type} />
        ))}
      </div>

      {resolvedStop && (scheduleLoading || servedRoutes.length > 0 || scheduleError) && (
        <div className={styles.routesSection}>
          <h3 className={styles.sectionTitle}>Routes serving this stop</h3>
          {stop.crowdsourced && resolvedStop && (
            <div className={styles.linkedStopNote}>
              Schedules linked from nearby stop: <strong>{resolvedStop.name}</strong>
            </div>
          )}
          {scheduleLoading && (
            <div className={styles.scheduleLoading}>
              <span className={styles.spinner} aria-label="Loading routes" />
              Loading routes…
            </div>
          )}
          {!scheduleLoading && scheduleError && (
            <p className={styles.scheduleError}>{scheduleError}</p>
          )}
          {!scheduleLoading && !scheduleError && servedRoutes.length === 0 && (
            <p className={styles.scheduleEmpty}>No route data found for this stop.</p>
          )}
          {!scheduleLoading && servedRoutes.length > 0 && (
            <div className={styles.routeGrid}>
              {servedRoutes.map((route) => (
                <button
                  key={route.onestopId}
                  className={styles.routePill}
                  style={{ background: route.color }}
                  onClick={() => navigate(`/route/${encodeURIComponent(route.onestopId)}`)}
                  title={`${route.shortName || ''} ${route.longName || ''}`.trim()}
                >
                  <span
                    className={styles.routeShort}
                    style={{ color: route.textColor }}
                  >
                    {route.shortName || route.transitType}
                  </span>
                  {(route.longName || route.agencyName) && (
                    <span
                      className={styles.routeLong}
                      style={{ color: route.textColor }}
                    >
                      {route.longName || route.agencyName}
                    </span>
                  )}
                  {route.nextDepartureTime && (
                    <span
                      className={styles.routeNext}
                      style={{ color: route.textColor, opacity: 0.85 }}
                    >
                      Next {route.nextDepartureTime.slice(0, 5)}
                    </span>
                  )}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {resolvedStop && (scheduleLoading || upcoming.length > 0 || (scheduleError == null && !scheduleLoading && servedRoutes.length > 0)) && (
        <div className={styles.departuresSection}>
          <h3 className={styles.sectionTitle}>Upcoming departures</h3>
          {stop.crowdsourced && resolvedStop && (
            <div className={styles.linkedStopNote}>
              Schedules linked from nearby stop: <strong>{resolvedStop.name}</strong>
            </div>
          )}
          {scheduleLoading && (
            <div className={styles.scheduleLoading}>
              <span className={styles.spinner} aria-label="Loading departures" />
              Loading departures…
            </div>
          )}
          {!scheduleLoading && upcoming.length === 0 && (
            <p className={styles.scheduleEmpty}>No scheduled departures in the near future.</p>
          )}
          {!scheduleLoading && upcoming.length > 0 && (
            <ul className={styles.departureList}>
              {upcoming.map((dep, i) => (
                <li
                  key={`${dep.routeId}-${dep.departureTime}-${i}`}
                  className={styles.departureRow}
                  onClick={() => navigate(`/route/${encodeURIComponent(dep.routeOnestopId)}`)}
                >
                  <span
                    className={styles.departureRouteBadge}
                    style={{ background: dep.routeColor, color: '#FFFFFF' }}
                  >
                    {dep.routeShortName || dep.transitType}
                  </span>
                  <span className={styles.departureTime}>{dep.departureTime.slice(0, 5)}</span>
                  <span className={styles.departureHeadsign}>
                    {dep.headsign || dep.routeLongName || '—'}
                    {dep.isRealtime && <span className={styles.liveTag}>LIVE</span>}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      {stop.crowdsourced && resolvedStopLoading && (
        <div className={styles.scheduleLoading}>
          <span className={styles.spinner} aria-label="Finding nearby stop" />
          Finding nearby public transit stop…
        </div>
      )}

      {stop.crowdsourced && !resolvedStop && !resolvedStopLoading && (
        <p className={styles.communityStopNote}>
          Schedule unavailable: no nearby public transit stop found.
        </p>
      )}

      <div className={styles.ratingRow}>
        <StarRating rating={avgRating} size={20} />
        <span className={styles.ratingText}>
          {stop.ratingCount > 0
            ? `${avgRating.toFixed(1)} (${stop.ratingCount} reviews)`
            : 'No reviews yet'}
        </span>
      </div>

      {stop.desc && <p className={styles.desc}>{stop.desc}</p>}

      <div className={styles.activitySection}>
        {(checkinCount > 0 || latestReport) && (
          <div className={styles.activityStrip}>
            <span className={styles.activityDot} />
            <span>
              {checkinCount > 0 ? `${checkinCount} ${checkinCount === 1 ? 'person' : 'people'} here` : 'Live activity'}
              {latestReport && reportLabel
                ? ` · reported ${reportLabel} ${formatRelativeMinutes(latestReport.timestamp)}`
                : ''}
            </span>
          </div>
        )}
        <div className={styles.checkinRow}>
          <button
            className={styles.checkinBtn}
            onClick={() =>
              navigate('/plan', { state: { to: { name: stop.name, lat: stop.lat, lng: stop.lng } } })
            }
          >
            🧭 Directions
          </button>
          <button
            className={styles.checkinBtn}
            onClick={() => handleActivity('checkin')}
            disabled={pending === 'checkin'}
          >
            📍 I'm here
          </button>
          {REPORT_TYPES.map(({ type, label }) => (
            <button
              key={type}
              className={styles.reportChip}
              onClick={() => handleActivity(type)}
              disabled={pending === type}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className={styles.features}>
        <h3>Features</h3>
        <div className={styles.featureGrid}>
          {Object.entries(stop.features || {}).map(([key, val]) => (
            <span
              key={key}
              className={`${styles.feature} ${val ? styles.enabled : styles.disabled}`}
            >
              {key.replace(/([A-Z])/g, ' $1').replace(/^./, (s) => s.toUpperCase())}
            </span>
          ))}
        </div>
      </div>

      <div className={styles.photoSection}>
        <div className={styles.photoSectionHeader}>
          <h3>Photos</h3>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            capture="environment"
            hidden
            onChange={handlePhotoUpload}
          />
          <button
            className={styles.photoUploadBtn}
            onClick={() => (user ? fileInputRef.current?.click() : setShowLogin(true))}
            disabled={uploading}
          >
            {uploading ? 'Uploading…' : '📷 Add photo'}
          </button>
        </div>
        {photos.length === 0 ? (
          <p className={styles.photoEmpty}>No photos yet. Be the first to add one!</p>
        ) : (
          <div className={styles.photoStrip}>
            {photos.map((photo, i) => (
              <img
                key={photo.id}
                src={photo.data}
                alt="Stop"
                className={styles.photoThumb}
                onClick={() => setViewerIndex(i)}
              />
            ))}
          </div>
        )}
      </div>

      {stop.crowdsourced && (
        <span className={styles.crowdsourced}>Community-added stop</span>
      )}

      <p className={styles.meta}>
        {(stop.crowdsourced || stop.addedAt > 0) ? `Added ${formatDate(stop.addedAt)}` : 'Verified Public Stop'}
        {stop.verified && stop.crowdsourced && ' • Verified'}
      </p>

      {viewerIndex !== null && photos[viewerIndex] && (
        <div className={styles.viewerOverlay} onClick={() => setViewerIndex(null)}>
          <button className={styles.viewerClose} onClick={() => setViewerIndex(null)}>
            ×
          </button>
          {viewerIndex > 0 && (
            <button
              className={`${styles.viewerNav} ${styles.viewerPrev}`}
              onClick={(e) => {
                e.stopPropagation();
                setViewerIndex(viewerIndex - 1);
              }}
            >
              ‹
            </button>
          )}
          <img
            src={photos[viewerIndex].data}
            alt="Stop full view"
            className={styles.viewerImg}
            onClick={(e) => e.stopPropagation()}
          />
          {viewerIndex < photos.length - 1 && (
            <button
              className={`${styles.viewerNav} ${styles.viewerNext}`}
              onClick={(e) => {
                e.stopPropagation();
                setViewerIndex(viewerIndex + 1);
              }}
            >
              ›
            </button>
          )}
        </div>
      )}

      {showLogin && <LoginModal onClose={() => setShowLogin(false)} />}
    </div>
  );
}
