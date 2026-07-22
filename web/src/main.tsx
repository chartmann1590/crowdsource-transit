import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.tsx'
import './styles/design-tokens.css'
import './styles/globals.css'

const urlParams = new URLSearchParams(window.location.search);
const redirectPath = urlParams.get('path');
if (redirectPath) {
  const segment = window.location.pathname.split('/').filter(Boolean);
  const repo = segment[0];
  // redirectPath may itself carry a query string (encoded by 404.html); the hash
  // survives the bounce natively — keep both so trip share links (/trip#d=...) work
  // on cold loads.
  window.history.replaceState(null, '', '/' + repo + redirectPath + window.location.hash);
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
