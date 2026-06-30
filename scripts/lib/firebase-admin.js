const { initializeApp, cert, getApps } = require('firebase-admin/app');
const { getDatabase } = require('firebase-admin/database');
const path = require('path');
require('dotenv').config({ path: path.join(__dirname, '../.env') });

let app;
if (getApps().length === 0) {
  app = initializeApp({
    credential: cert(path.join(__dirname, '../service-account.json')),
    databaseURL: process.env.FIREBASE_DATABASE_URL,
  });
} else {
  app = getApps()[0];
}

const db = getDatabase(app);
module.exports = { db };
