import { useCallback, useState } from 'react';
import { planTrip } from '../routing/planTrip';
import type { PlanRequest } from '../routing/types';
import { TransitlandRateLimitError } from '../api/transitland';
import type { TripPlan } from '../types/itinerary';

interface PlanTripState {
  plans: TripPlan[];
  loading: boolean;
  planned: boolean;
  error: string | null;
}

export function usePlanTrip() {
  const [state, setState] = useState<PlanTripState>({
    plans: [],
    loading: false,
    planned: false,
    error: null,
  });

  const plan = useCallback(async (req: PlanRequest) => {
    setState({ plans: [], loading: true, planned: false, error: null });
    try {
      const plans = await planTrip(req);
      setState({ plans, loading: false, planned: true, error: null });
    } catch (e) {
      const message =
        e instanceof TransitlandRateLimitError
          ? 'Transit data is busy right now — please try again in a minute.'
          : "Couldn't plan this trip. Check your connection and try again.";
      setState({ plans: [], loading: false, planned: true, error: message });
    }
  }, []);

  const reset = useCallback(() => {
    setState({ plans: [], loading: false, planned: false, error: null });
  }, []);

  return { ...state, plan, reset };
}
