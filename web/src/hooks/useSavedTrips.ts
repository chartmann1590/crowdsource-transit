import { useEffect, useState } from 'react';
import { useAuth } from '../components/Auth/AuthContext';
import { observeSavedTrips, type SavedTrip } from '../firebase/savedTrips';

export function useSavedTrips() {
  const { user } = useAuth();
  const [trips, setTrips] = useState<SavedTrip[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!user) {
      setTrips([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    const unsubscribe = observeSavedTrips(user.uid, (t) => {
      setTrips(t);
      setLoading(false);
    });
    return unsubscribe;
  }, [user]);

  return { trips, loading };
}
