import { useParams, useNavigate } from 'react-router-dom';
import { Navbar } from '../components/UI/Navbar';
import styles from './RoutePage.module.css';

export function RoutePage() {
  const { routeId } = useParams<{ routeId: string }>();
  const navigate = useNavigate();

  return (
    <div className={styles.container}>
      <Navbar />
      <div className={styles.content}>
        <button className={styles.backBtn} onClick={() => navigate('/')}>
          ← Back to Map
        </button>
        <h1>Route {routeId}</h1>
        <p className={styles.comingSoon}>Route details coming soon.</p>
      </div>
    </div>
  );
}
