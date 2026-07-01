import { Link } from 'react-router-dom';
import { useAuth } from '../Auth/AuthContext';
import styles from './Navbar.module.css';

export function Navbar() {
  const { user, signOut } = useAuth();

  return (
    <nav className={styles.nav}>
      <div className={styles.inner}>
        <Link to="/" className={styles.logo}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="2" />
            <circle cx="12" cy="12" r="3" fill="currentColor" />
            <line x1="12" y1="2" x2="12" y2="7" stroke="currentColor" strokeWidth="2" />
            <line x1="12" y1="17" x2="12" y2="22" stroke="currentColor" strokeWidth="2" />
            <line x1="2" y1="12" x2="7" y2="12" stroke="currentColor" strokeWidth="2" />
            <line x1="17" y1="12" x2="22" y2="12" stroke="currentColor" strokeWidth="2" />
          </svg>
          <span>CrowdTransit</span>
        </Link>
        <div className={styles.links}>
          <Link to="/search" className={styles.link}>Search</Link>
          <Link to="/about" className={styles.link}>About</Link>
          {user ? (
            <div className={styles.userMenu}>
              <Link to="/profile" className={styles.avatar}>
                {user.displayName?.charAt(0).toUpperCase() || 'R'}
              </Link>
              <button className={styles.signOutBtn} onClick={signOut}>
                Sign Out
              </button>
            </div>
          ) : null}
        </div>
      </div>
    </nav>
  );
}
