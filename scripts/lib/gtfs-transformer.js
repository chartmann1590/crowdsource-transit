const ROUTE_TYPE_MAP = {
  '0': 'tram',
  '1': 'subway',
  '2': 'train',
  '3': 'bus',
  '4': 'ferry',
  '5': 'cable_car',
  '6': 'tram',
  '7': 'funicular',
  '11': 'bus',
  '12': 'monorail',
  '100': 'train',
  '101': 'train',
  '102': 'train',
  '200': 'bus',
  '400': 'subway',
  '401': 'subway',
  '700': 'bus',
  '900': 'tram',
  '1000': 'ferry',
  '1100': 'train',
};

const TRANSIT_TYPE_COLORS = {
  bus: '#4CAF50',
  train: '#2196F3',
  subway: '#9C27B0',
  ferry: '#00BCD4',
  tram: '#FF9800',
  cable_car: '#795548',
  monorail: '#607D8B',
  funicular: '#E91E63',
};

function sanitizeKey(str) {
  return str
    .toLowerCase()
    .replace(/[.$#\[\]\/]/g, '_')
    .replace(/\s+/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '');
}

function transformAgency(gtfsAgency, agencyId, metadata) {
  return {
    agencyId,
    name: gtfsAgency.agency_name || '',
    shortName: (gtfsAgency.agency_name || '').split(' ').slice(-2).join(' '),
    url: gtfsAgency.agency_url || '',
    timezone: gtfsAgency.agency_timezone || 'UTC',
    lang: gtfsAgency.agency_lang || 'en',
    phone: gtfsAgency.agency_phone || '',
    country: metadata.country || 'US',
    state: metadata.state || '',
    city: metadata.city || '',
    lat: metadata.lat || 0,
    lng: metadata.lng || 0,
    transitTypes: metadata.transitTypes || ['bus'],
    gtfsFeedUrl: metadata.feedUrl || '',
    lastUpdated: Date.now(),
    ratingSum: 0,
    ratingCount: 0,
    verified: true,
    active: true,
  };
}

function transformStop(gtfsStop, agencyId, metadata) {
  const lat = parseFloat(gtfsStop.stop_lat);
  const lng = parseFloat(gtfsStop.stop_lon);

  if (isNaN(lat) || isNaN(lng) || lat === 0 || lng === 0) {
    return null;
  }

  const stopId = `${agencyId}_${sanitizeKey(gtfsStop.stop_id)}`;

  return {
    stopId,
    agencyId,
    gtfsStopId: gtfsStop.stop_id || '',
    name: (gtfsStop.stop_name || '').trim(),
    desc: (gtfsStop.stop_desc || '').trim(),
    lat,
    lng,
    code: gtfsStop.stop_code || '',
    url: gtfsStop.stop_url || '',
    locationType: parseInt(gtfsStop.location_type || '0'),
    wheelchairBoarding: parseInt(gtfsStop.wheelchair_boarding || '0'),
    country: metadata.country || 'US',
    state: metadata.state || '',
    city: metadata.city || '',
    timezone: gtfsStop.stop_timezone || metadata.timezone || 'UTC',
    transitTypes: metadata.transitTypes || ['bus'],
    routeIds: {},
    agencyIds: { [agencyId]: true },
    ratingSum: 0,
    ratingCount: 0,
    commentCount: 0,
    features: {
      shelter: false,
      bench: false,
      lighting: false,
      elevator: false,
      escalator: false,
      ticketMachine: false,
      bikeParking: false,
      parking: false,
    },
    crowdsourced: false,
    verified: true,
    addedBy: null,
    addedAt: Date.now(),
    lastUpdated: Date.now(),
    active: true,
    reportCount: 0,
  };
}

function transformRoute(gtfsRoute, agencyId, metadata) {
  const transitType = ROUTE_TYPE_MAP[gtfsRoute.route_type] || 'bus';
  const routeId = `${agencyId}_${sanitizeKey(gtfsRoute.route_id)}`;

  return {
    routeId,
    agencyId,
    gtfsRouteId: gtfsRoute.route_id || '',
    shortName: (gtfsRoute.route_short_name || '').trim(),
    longName: (gtfsRoute.route_long_name || '').trim(),
    desc: (gtfsRoute.route_desc || '').trim(),
    type: transitType,
    color: `#${gtfsRoute.route_color || TRANSIT_TYPE_COLORS[transitType].replace('#', '')}`,
    textColor: `#${gtfsRoute.route_text_color || 'FFFFFF'}`,
    url: gtfsRoute.route_url || '',
    country: metadata.country || 'US',
    state: metadata.state || '',
    city: metadata.city || '',
    agencyName: metadata.agencyName || '',
    stopCount: 0,
    ratingSum: 0,
    ratingCount: 0,
    lastUpdated: Date.now(),
    active: true,
  };
}

module.exports = {
  transformAgency,
  transformStop,
  transformRoute,
  sanitizeKey,
  ROUTE_TYPE_MAP,
  TRANSIT_TYPE_COLORS,
};
