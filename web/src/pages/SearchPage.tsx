import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Navbar } from '../components/UI/Navbar';
import { SearchBar } from '../components/UI/SearchBar';
import { StopCard } from '../components/Stop/StopCard';
import { LoadingSpinner } from '../components/UI/LoadingSpinner';
import { searchStops } from '../firebase/stops';
import type { Stop } from '../types/transit';
import styles from './SearchPage.module.css';

export function SearchPage() {
  const navigate = useNavigate();
  const [results, setResults] = useState<Stop[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);

  async function handleSearch(query: string) {
    setLoading(true);
    setSearched(true);
    try {
      const stops = await searchStops(query);
      setResults(stops);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className={styles.container}>
      <Navbar />
      <div className={styles.content}>
        <div className={styles.searchSection}>
          <h1>Search Stops</h1>
          <SearchBar onSearch={handleSearch} placeholder="Search by name, city, or stop code..." />
        </div>
        <div className={styles.results}>
          {loading ? (
            <LoadingSpinner />
          ) : searched && results.length === 0 ? (
            <p className={styles.empty}>No stops found. Try a different search.</p>
          ) : (
            results.map((stop) => (
              <StopCard
                key={stop.stopId}
                stop={stop}
                onClick={() => {}}
                onViewDetail={() => navigate(`/stop/${stop.stopId}`)}
              />
            ))
          )}
        </div>
      </div>
    </div>
  );
}
