import { Navbar } from '../components/UI/Navbar';
import styles from './AboutPage.module.css';

export function AboutPage() {
  return (
    <div className={styles.container}>
      <Navbar />
      <div className={styles.hero}>
        <h1>About CrowdTransit</h1>
        <p>Community-powered transit information for everyone.</p>
      </div>
      <div className={styles.content}>
        <section className={styles.section}>
          <h2>Our Mission</h2>
          <p>
            CrowdTransit is a community-driven platform that helps transit riders
            find and share real-world information about stops, routes, and transit
            experiences. We believe that the best transit data comes from the people
            who use it every day.
          </p>
        </section>

        <section className={styles.section}>
          <h2>Features</h2>
          <ul>
            <li>Interactive map of transit stops with real-time navigation</li>
            <li>Detailed stop information including features and transit types</li>
            <li>Community reviews and ratings for stops and routes</li>
            <li>Anonymous posting option for privacy-conscious users</li>
            <li>Crowdsourced stop additions and edits</li>
          </ul>
        </section>

        <section className={styles.section}>
          <h2>Get the App</h2>
          <p>
            CrowdTransit is free, with no ads and no in-app purchases. Get live stops,
            schedules, and agency info for your city, right from your phone.
          </p>
          <video
            className={styles.promoVideo}
            src="/crowdsource-transit/press-kit/promo-video.mp4"
            poster="/crowdsource-transit/press-kit/screenshot-stop-detail.png"
            controls
            playsInline
          />
          <div className={styles.screenshotStrip}>
            <img src="/crowdsource-transit/press-kit/screenshot-map.png" alt="Map of nearby transit stops in CrowdTransit" />
            <img src="/crowdsource-transit/press-kit/screenshot-stop-detail.png" alt="Stop detail screen showing service provider and live schedule" />
            <img src="/crowdsource-transit/press-kit/screenshot-route-detail.png" alt="Route detail screen listing every stop on a route" />
            <img src="/crowdsource-transit/press-kit/screenshot-live-activity.png" alt="Live activity check-in and route status reporting" />
          </div>
        </section>

        <section className={styles.section}>
          <h2>Open Source</h2>
          <p>
            CrowdTransit is open source and built with modern web technologies.
            Check out the project on GitHub to contribute or report issues.
          </p>
        </section>

        <section className={styles.section}>
          <h2>Data Sources &amp; Disclaimer</h2>
          <p>
            CrowdTransit is an independent, community-built project. It is not affiliated with,
            endorsed by, or operated by any government entity or transit agency. Stop, route, and
            schedule information is sourced from the official public GTFS feeds that transit
            agencies publish via the{' '}
            <a href="https://www.transit.land" target="_blank" rel="noopener noreferrer">
              Transitland
            </a>{' '}
            open data platform. For the authoritative, official version of any agency's
            schedules, visit that agency's own website.
          </p>
        </section>
      </div>
    </div>
  );
}
