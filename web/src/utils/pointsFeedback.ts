export interface PointsFeedbackPayload {
  points: number;
  totalPoints: number;
  newBadges: string[];
  leveledUp: boolean;
}

type Listener = (payload: PointsFeedbackPayload) => void;

const listeners = new Set<Listener>();

export function onPointsAwarded(fn: Listener): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

export function firePointsAwarded(payload: PointsFeedbackPayload): void {
  listeners.forEach((fn) => fn(payload));
}
