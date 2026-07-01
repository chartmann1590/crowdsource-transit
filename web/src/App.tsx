import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './components/Auth/AuthContext';
import { Home } from './pages/Home';
import { StopPage } from './pages/StopPage';
import { ProfilePage } from './pages/ProfilePage';
import { AddStopPage } from './pages/AddStopPage';
import { AboutPage } from './pages/AboutPage';
import { SearchPage } from './pages/SearchPage';
import { RoutePage } from './pages/RoutePage';

function App() {
  return (
    <AuthProvider>
      <BrowserRouter basename="/crowdsource-transit">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/stop/:stopId" element={<StopPage />} />
          <Route path="/route/:routeId" element={<RoutePage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/add-stop" element={<AddStopPage />} />
          <Route path="/about" element={<AboutPage />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
