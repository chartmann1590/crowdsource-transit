import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../components/Auth/AuthContext';
import { Navbar } from '../components/UI/Navbar';
import { LoginModal } from '../components/Auth/LoginModal';
import { useGamification } from '../hooks/useGamification';
import { useLeaderboard } from '../hooks/useLeaderboard';
import { getLevel, getLevelProgress, BADGES } from '../firebase/gamification';
import { useSavedTrips } from '../hooks/useSavedTrips';
import { deleteTrip } from '../firebase/savedTrips';
import { getDistanceUnit, setDistanceUnit, type DistanceUnit } from '../utils/units';
import styles from './ProfilePage.module.css';

type Tab = 'profile' | 'leaderboard';

/** Distance-units setting; localStorage-backed so it works logged-out too. */
function UnitsSetting() {
  const [unit, setUnit] = useState<DistanceUnit>(() => getDistanceUnit());
  const choose = (u: DistanceUnit) => {
    setUnit(u);
    setDistanceUnit(u);
  };
  return (
    <div className={styles.settingRow}>
      <span>Distance units</span>
      <div className={styles.unitToggle}>
        <button
          type="button"
          className={unit === 'imperial' ? styles.unitActive : styles.unitBtn}
          onClick={() => choose('imperial')}
        >
          Miles
        </button>
        <button
          type="button"
          className={unit === 'metric' ? styles.unitActive : styles.unitBtn}
          onClick={() => choose('metric')}
        >
          Kilometers
        </button>
      </div>
    </div>
  );
}

export function ProfilePage() {
  const navigate = useNavigate();
  const { user, signOut } = useAuth();
  const [showLogin, setShowLogin] = useState(false);
  const [tab, setTab] = useState<Tab>('profile');
  const stats = useGamification();
  const { entries, loading: leaderboardLoading } = useLeaderboard();
  const { trips: savedTrips } = useSavedTrips();

  if (!user) {
    return (
      <div className={styles.container}>
        <Navbar />
        <div className={styles.content}>
          <div className={styles.promptCard}>
            <h2>Sign in to view your profile</h2>
            <p>Track your reviews, contributions, and more.</p>
            <button className={styles.signInBtn} onClick={() => setShowLogin(true)}>
              Sign In
            </button>
            <UnitsSetting />
          </div>
          {showLogin && <LoginModal onClose={() => setShowLogin(false)} />}
        </div>
      </div>
    );
  }

  const initials = (user.displayName || 'R')
    .split(' ')
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase() ?? 'R')
    .join('');

  const level = getLevel(stats.points);
  const { next, progress } = getLevelProgress(stats.points);
  const ringDeg = Math.round(progress * 360);
  const earnedBadges = Object.keys(stats.badges).filter((id) => stats.badges[id]);

  return (
    <div className={styles.container}>
      <Navbar />
      <div className={styles.content}>
        <div className={styles.tabs}>
          <button
            className={`${styles.tab} ${tab === 'profile' ? styles.tabActive : ''}`}
            onClick={() => setTab('profile')}
          >
            Profile
          </button>
          <button
            className={`${styles.tab} ${tab === 'leaderboard' ? styles.tabActive : ''}`}
            onClick={() => setTab('leaderboard')}
          >
            Leaderboard
          </button>
        </div>

        {tab === 'profile' ? (
          <div className={styles.profileCard}>
            <div
              className={styles.avatarRing}
              style={{
                background: `conic-gradient(var(--color-primary) ${ringDeg}deg, var(--color-outline) ${ringDeg}deg)`,
              }}
            >
              <div className={styles.avatar}>{initials}</div>
            </div>
            <h2>{user.displayName || 'Rider'}</h2>
            <p className={styles.email}>{user.email || 'Anonymous Rider'}</p>

            <div className={styles.levelBadge}>{level.name}</div>
            <p className={styles.pointsText}>
              {stats.points} pts
              {next ? ` · ${next.min - stats.points} to ${next.name}` : ' · Max level!'}
            </p>

            <div className={styles.statsGrid}>
              <div className={styles.statCard}>
                <span className={styles.statValue}>{stats.reviewCount}</span>
                <span className={styles.statLabel}>Reviews</span>
              </div>
              <div className={styles.statCard}>
                <span className={styles.statValue}>{stats.stopsAdded}</span>
                <span className={styles.statLabel}>Stops Added</span>
              </div>
              <div className={styles.statCard}>
                <span className={styles.statValue}>{stats.photoCount}</span>
                <span className={styles.statLabel}>Photos</span>
              </div>
              <div className={styles.statCard}>
                <span className={styles.statValue}>{stats.streakCount}</span>
                <span className={styles.statLabel}>Day Streak 🔥</span>
              </div>
            </div>

            <div className={styles.badgesSection}>
              <h3>Saved trips</h3>
              {savedTrips.length === 0 ? (
                <p className={styles.leaderboardStatus}>No saved trips yet. Plan a trip and save it!</p>
              ) : (
                <ol className={styles.leaderboardList}>
                  {savedTrips.map((trip) => (
                    <li key={trip.tripId} className={styles.leaderboardRow}>
                      <button
                        className={styles.savedTripOpen}
                        onClick={() => {
                          // Saved trips store origin/destination only, never times, so
                          // reopening one re-plans fresh for accurate, current options.
                          navigate('/plan', {
                            state: {
                              from: { name: trip.fromName, lat: trip.fromLat, lng: trip.fromLng },
                              to: { name: trip.toName, lat: trip.toLat, lng: trip.toLng },
                              autoPlan: true,
                            },
                          });
                        }}
                      >
                        {trip.fromName || 'Origin'} → {trip.toName || 'Destination'}
                      </button>
                      <button
                        className={styles.savedTripDelete}
                        title="Delete saved trip"
                        onClick={() => void deleteTrip(trip.tripId)}
                      >
                        ✕
                      </button>
                    </li>
                  ))}
                </ol>
              )}
            </div>

            <div className={styles.badgesSection}>
              <h3>Badges</h3>
              <div className={styles.badgesGrid}>
                {Object.entries(BADGES).map(([id, def]) => {
                  const earned = earnedBadges.includes(id);
                  return (
                    <div
                      key={id}
                      className={`${styles.badgeTile} ${earned ? styles.badgeEarned : styles.badgeLocked}`}
                      title={def.description}
                    >
                      <span className={styles.badgeIcon}>{def.icon}</span>
                      <span className={styles.badgeLabel}>{def.label}</span>
                    </div>
                  );
                })}
              </div>
            </div>

            <div className={styles.badgesSection}>
              <h3>Settings</h3>
              <UnitsSetting />
            </div>

            <button className={styles.signOutBtn} onClick={signOut}>
              Sign Out
            </button>
          </div>
        ) : (
          <div className={styles.leaderboardCard}>
            <h2>Top Riders</h2>
            {leaderboardLoading ? (
              <p className={styles.leaderboardStatus}>Loading...</p>
            ) : entries.length === 0 ? (
              <p className={styles.leaderboardStatus}>No contributors yet. Be the first!</p>
            ) : (
              <ol className={styles.leaderboardList}>
                {entries.map((entry, i) => (
                  <li
                    key={entry.uid}
                    className={`${styles.leaderboardRow} ${entry.uid === user.uid ? styles.leaderboardRowSelf : ''}`}
                  >
                    <span className={styles.rank}>{i + 1}</span>
                    <span className={styles.leaderboardName}>{entry.displayName}</span>
                    <span className={styles.leaderboardLevel}>{getLevel(entry.points).name}</span>
                    <span className={styles.leaderboardPoints}>{entry.points} pts</span>
                  </li>
                ))}
              </ol>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
