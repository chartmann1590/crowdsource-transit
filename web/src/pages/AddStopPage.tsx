import { useAuth } from '../components/Auth/AuthContext';
import { Navbar } from '../components/UI/Navbar';
import { LoginModal } from '../components/Auth/LoginModal';
import { useState } from 'react';
import styles from './AddStopPage.module.css';

export function AddStopPage() {
  const { user } = useAuth();
  const [showLogin, setShowLogin] = useState(false);

  if (!user) {
    return (
      <div className={styles.container}>
        <Navbar />
        <div className={styles.content}>
          <div className={styles.promptCard}>
            <h2>Sign in to add a stop</h2>
            <p>Help the community by adding transit stops.</p>
            <button className={styles.signInBtn} onClick={() => setShowLogin(true)}>
              Sign In
            </button>
          </div>
          {showLogin && <LoginModal onClose={() => setShowLogin(false)} />}
        </div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <Navbar />
      <div className={styles.content}>
        <h1>Add a Stop</h1>
        <p className={styles.comingSoon}>
          Stop submission form coming soon. In the meantime, use the app to explore existing stops.
        </p>
      </div>
    </div>
  );
}
