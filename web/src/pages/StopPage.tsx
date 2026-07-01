import { useParams, useNavigate } from 'react-router-dom';
import { MapView } from '../components/Map/MapView';
import { StopDetail } from '../components/Stop/StopDetail';
import { ReviewList } from '../components/Review/ReviewList';
import { ReviewForm } from '../components/Review/ReviewForm';
import { Navbar } from '../components/UI/Navbar';
import { LoadingSpinner } from '../components/UI/LoadingSpinner';
import { useStop } from '../hooks/useStop';
import { useComments } from '../hooks/useComments';
import { useState } from 'react';
import styles from './StopPage.module.css';

export function StopPage() {
  const { stopId } = useParams<{ stopId: string }>();
  const navigate = useNavigate();
  const { stop, loading } = useStop(stopId ?? null);
  const { comments, loading: commentsLoading } = useComments('stop', stopId ?? null);
  const [showForm, setShowForm] = useState(false);

  if (loading) return <LoadingSpinner />;
  if (!stop) {
    return (
      <div className={styles.container}>
        <Navbar />
        <div className={styles.notFound}>
          <h2>Stop not found</h2>
          <button onClick={() => navigate('/')}>Back to Map</button>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <Navbar />
      <div className={styles.layout}>
        <div className={styles.sidebar}>
          <button className={styles.backBtn} onClick={() => navigate('/')}>
            ← Back to Map
          </button>
          <StopDetail stop={stop} />

          <div className={styles.reviewSection}>
            <div className={styles.sectionHeader}>
              <h3>Reviews ({comments.length})</h3>
              <button
                className={styles.writeBtn}
                onClick={() => setShowForm(!showForm)}
              >
                {showForm ? 'Cancel' : 'Write Review'}
              </button>
            </div>
            {showForm && (
              <ReviewForm
                targetType="stop"
                targetId={stop.stopId}
                onSuccess={() => setShowForm(false)}
                onCancel={() => setShowForm(false)}
              />
            )}
            <ReviewList comments={comments} loading={commentsLoading} />
          </div>
        </div>
        <main className={styles.mapContainer}>
          <MapView
            stops={[stop]}
            initialLat={stop.lat}
            initialLng={stop.lng}
            initialZoom={16}
          />
        </main>
      </div>
    </div>
  );
}
