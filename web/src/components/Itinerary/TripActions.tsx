import { useState } from 'react';
import { useAuth } from '../Auth/AuthContext';
import { saveTrip } from '../../firebase/savedTrips';
import type { TripPlan } from '../../types/itinerary';
import { itineraryToText } from '../../utils/itineraryText';
import { buildShareUrl } from '../../utils/shareLink';
import styles from './TripActions.module.css';

/** Share/save action row for an itinerary (planner + share-link viewer). */
export function TripActions({ plan }: { plan: TripPlan }) {
  const { user } = useAuth();
  const [status, setStatus] = useState<string | null>(null);

  const flash = (message: string) => {
    setStatus(message);
    setTimeout(() => setStatus(null), 2500);
  };

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(await buildShareUrl(plan));
      flash('Link copied!');
    } catch {
      flash("Couldn't copy the link.");
    }
  };

  const copyText = async () => {
    try {
      const url = await buildShareUrl(plan);
      await navigator.clipboard.writeText(itineraryToText(plan, url));
      flash('Directions copied!');
    } catch {
      flash("Couldn't copy the directions.");
    }
  };

  const save = async () => {
    try {
      await saveTrip(plan);
      flash('Trip saved!');
    } catch (e) {
      flash(e instanceof Error ? e.message : "Couldn't save the trip.");
    }
  };

  return (
    <div className={styles.actions}>
      <button type="button" onClick={copyLink}>
        🔗 Copy link
      </button>
      <button type="button" onClick={copyText}>
        📋 Copy as text
      </button>
      {user && (
        <button type="button" onClick={save}>
          ⭐ Save trip
        </button>
      )}
      {status && <span className={styles.status}>{status}</span>}
    </div>
  );
}
