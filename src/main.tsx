import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './utils/chartSetup';
import './index.css';
import App from './App.tsx';
import { appliquerPreferences } from './utils/uiPreferences';

// Mode compact et animations choisis dans Paramètres > Préférences.
appliquerPreferences();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);