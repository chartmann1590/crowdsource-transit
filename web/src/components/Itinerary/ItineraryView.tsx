import { useState } from 'react';
import { isTransitLeg, type TransitLeg, type TripPlan, type WalkLeg } from '../../types/itinerary';
import { formatLocalTime, planTimes } from '../../utils/itineraryDisplay';
import styles from './ItineraryView.module.css';

/**
 * Leg-by-leg itinerary renderer shared by the planner and the share-link viewer.
 * Pure display: no data fetching, works logged-out.
 */
export function ItineraryView({ plan }: { plan: TripPlan }) {
  const { dep, arr, duration } = planTimes(plan);
  const transfers = Math.max(0, plan.legs.filter(isTransitLeg).length - 1);

  return (
    <div className={styles.container}>
      <div className={styles.summary}>
        <div className={styles.summaryTimes}>
          {dep} → {arr}
        </div>
        <div className={styles.summaryMeta}>
          {duration}
          {' · '}
          {transfers === 0 ? 'Direct' : `${transfers} transfer${transfers > 1 ? 's' : ''}`}
        </div>
      </div>
      <div className={styles.endpoints}>
        {plan.from.name || 'Origin'} → {plan.to.name || 'Destination'}
      </div>
      <ol className={styles.legs}>
        {plan.legs.map((leg, i) => (
          <li key={i} className={styles.leg}>
            {isTransitLeg(leg) ? <TransitLegView leg={leg} /> : <WalkLegView leg={leg} />}
          </li>
        ))}
      </ol>
    </div>
  );
}

function WalkLegView({ leg }: { leg: WalkLeg }) {
  return (
    <div className={styles.walkLeg}>
      <div className={styles.legHeader}>
        <span className={styles.walkIcon} aria-hidden>
          🚶
        </span>
        <span className={styles.legTitle}>Walk to {leg.to.name || 'destination'}</span>
        <span className={styles.legTime}>
          {formatLocalTime(leg.dep)}–{formatLocalTime(leg.arr)}
        </span>
      </div>
      <div className={styles.legMeta}>{leg.dist_m} m</div>
      {leg.steps && leg.steps.length > 0 && (
        <ul className={styles.walkSteps}>
          {leg.steps.map((step, i) => (
            <li key={i}>
              {step.text} ({step.dist_m} m)
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function TransitLegView({ leg }: { leg: TransitLeg }) {
  const [expanded, setExpanded] = useState(false);
  const intermediate = leg.stops.slice(1, -1);
  const label = leg.route.short || leg.route.long || leg.mode;

  return (
    <div className={styles.transitLeg}>
      <div className={styles.legHeader}>
        <span className={styles.routeBadge} style={{ backgroundColor: leg.route.color || '#00A862' }}>
          {label}
        </span>
        <span className={styles.legTitle}>→ {leg.trip.headsign}</span>
      </div>
      <div className={styles.boardAlight}>
        <div>
          <strong>Board:</strong> {leg.board.name} · departs {formatLocalTime(leg.board.dep_utc)}
        </div>
        {intermediate.length > 0 && (
          <button type="button" className={styles.stopsToggle} onClick={() => setExpanded((e) => !e)}>
            {expanded ? 'Hide stops' : `Ride ${leg.stops.length - 1} stops — show all`}
          </button>
        )}
        {expanded && (
          <ul className={styles.stopList}>
            {intermediate.map((stop) => (
              <li key={stop.id}>
                {stop.name} · {formatLocalTime(stop.arr_utc)}
              </li>
            ))}
          </ul>
        )}
        <div>
          <strong>Get off:</strong> {leg.alight.name} · arrives {formatLocalTime(leg.alight.arr_utc)}
        </div>
      </div>
      {leg.route.agency && <div className={styles.legMeta}>{leg.route.agency}</div>}
    </div>
  );
}
