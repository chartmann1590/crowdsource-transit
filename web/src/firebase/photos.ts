import { ref, push, get, remove, query, orderByChild } from 'firebase/database';
import { database, auth } from './config';
import { awardPoints, type AwardResult } from './gamification';

const MAX_PHOTOS_PER_STOP = 10;
const MAX_DIMENSION = 800;
const MAX_BASE64_LENGTH = 140_000; // ~100KB binary budget, base64 inflates ~1.37x

export interface StopPhoto {
  id: string;
  uid: string;
  data: string;
  timestamp: number;
}

/** Client-side compression to ~800px / ~100KB JPEG, returned as a base64 data URL. */
export async function compressImage(file: File, maxDim = MAX_DIMENSION): Promise<string> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, maxDim / Math.max(bitmap.width, bitmap.height));
  const w = Math.max(1, Math.round(bitmap.width * scale));
  const h = Math.max(1, Math.round(bitmap.height * scale));

  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Canvas not supported in this browser');
  ctx.drawImage(bitmap, 0, 0, w, h);

  let quality = 0.75;
  let dataUrl = canvas.toDataURL('image/jpeg', quality);
  while (dataUrl.length > MAX_BASE64_LENGTH && quality > 0.3) {
    quality -= 0.1;
    dataUrl = canvas.toDataURL('image/jpeg', quality);
  }
  return dataUrl;
}

/**
 * Uploads a photo for a stop, base64-encoded directly into RTDB. Enforces a
 * cap of 10 photos per stop by pruning the oldest entries client-side.
 * Awards +3 points on success.
 */
export async function uploadStopPhoto(stopId: string, file: File): Promise<AwardResult | null> {
  const user = auth.currentUser;
  if (!user) throw new Error('Not authenticated');

  const data = await compressImage(file);

  const photosRef = ref(database, `photos/${stopId}`);
  const existingSnap = await get(query(photosRef, orderByChild('timestamp')));
  const existing: Array<{ key: string; timestamp: number }> = [];
  existingSnap.forEach((child) => {
    existing.push({ key: child.key!, timestamp: (child.val()?.timestamp as number) ?? 0 });
  });

  if (existing.length >= MAX_PHOTOS_PER_STOP) {
    existing.sort((a, b) => a.timestamp - b.timestamp);
    const overflow = existing.length - MAX_PHOTOS_PER_STOP + 1;
    const toRemove = existing.slice(0, overflow);
    await Promise.all(toRemove.map((entry) => remove(ref(database, `photos/${stopId}/${entry.key}`))));
  }

  await push(photosRef, {
    uid: user.uid,
    data,
    timestamp: Date.now(),
  });

  return awardPoints('photo');
}
