import { useAuth } from '../components/Auth/AuthContext';
import { Navbar } from '../components/UI/Navbar';
import { LoginModal } from '../components/Auth/LoginModal';
import { useState } from 'react';
import styles from './ProfilePage.module.css';

export function ProfilePage() {
  const { user, signOut } = useAuth();
  const [showLogin, setShowLogin] = useState(false);

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

  return (
    <div className={styles.container}>
      <Navbar />
      <div className={styles.content}>
        <div className={styles.profileCard}>
          <div className={styles.avatar}>{initials}</div>
          <h2>{user.displayName || 'Rider'}</h2>
          <p className={styles.email}>{user.email || 'Anonymous Rider'}</p>
          <button className={styles.signOutBtn} onClick={signOut}>
            Sign Out
          </button>
        </div>
      </div>
    </div>
  );
}
