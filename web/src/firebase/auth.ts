import {
  signInAnonymously,
  signInWithPopup,
  GoogleAuthProvider,
  signOut as fbSignOut,
  linkWithPopup,
  type User,
} from 'firebase/auth';
import { ref, set, get } from 'firebase/database';
import { auth, database } from './config';

export async function signInAnon() {
  await signInAnonymously(auth);
}

export async function signInWithGoogle() {
  const provider = new GoogleAuthProvider();
  if (auth.currentUser?.isAnonymous) {
    await linkWithPopup(auth.currentUser, provider);
  } else {
    await signInWithPopup(auth, provider);
  }
}

export async function signOut() {
  await fbSignOut(auth);
}

export async function ensureUserProfile(u: User) {
  const userRef = ref(database, `users/${u.uid}`);
  const snap = await get(userRef);
  if (!snap.exists()) {
    const name = u.displayName || 'Rider';
    const initials = name.split(' ').slice(0, 2).map((w: string) => w[0]?.toUpperCase() ?? 'R').join('');
    await set(userRef, {
      userId: u.uid,
      displayName: name,
      isAnonymous: u.isAnonymous,
      avatarInitials: initials,
      joinedAt: Date.now(),
      stats: { reviewCount: 0, stopsAdded: 0, helpfulVotes: 0, reportCount: 0 },
      badges: {},
      lastActiveAt: Date.now(),
    });
  }
}
