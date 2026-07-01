export type TransitType = 'bus' | 'train' | 'subway' | 'ferry' | 'tram' | 'cable_car' | 'monorail' | 'funicular';

export interface Agency {
  agencyId: string;
  name: string;
  shortName: string;
  country: string;
  state: string;
  city: string;
  lat: number;
  lng: number;
  transitTypes: TransitType[];
  ratingSum: number;
  ratingCount: number;
  verified: boolean;
  active: boolean;
}

export interface Stop {
  stopId: string;
  agencyId: string;
  name: string;
  desc?: string;
  lat: number;
  lng: number;
  code?: string;
  country: string;
  state: string;
  city: string;
  transitTypes: TransitType[];
  routeIds: Record<string, boolean>;
  ratingSum: number;
  ratingCount: number;
  commentCount: number;
  features: {
    shelter: boolean;
    bench: boolean;
    lighting: boolean;
    elevator: boolean;
    escalator: boolean;
    ticketMachine: boolean;
    bikeParking: boolean;
    parking: boolean;
  };
  crowdsourced: boolean;
  verified: boolean;
  addedBy: string | null;
  addedAt: number;
  lastUpdated: number;
  active: boolean;
}

export interface Route {
  routeId: string;
  agencyId: string;
  shortName: string;
  longName: string;
  type: TransitType;
  color: string;
  textColor: string;
  country: string;
  state: string;
  city: string;
  ratingSum: number;
  ratingCount: number;
  active: boolean;
}

export interface Rating {
  userId: string;
  displayName: string;
  isAnonymous: boolean;
  targetType: 'stop' | 'route' | 'agency';
  targetId: string;
  overall: number;
  subcategories?: {
    cleanliness?: number;
    safety?: number;
    accessibility?: number;
    reliability?: number;
  };
  transitType?: TransitType;
  createdAt: number;
  updatedAt: number;
}

export interface Comment {
  commentId: string;
  userId: string;
  displayName: string;
  isAnonymous: boolean;
  avatarInitials: string;
  targetType: 'stop' | 'route' | 'agency';
  targetId: string;
  text: string;
  transitType?: TransitType;
  routeId?: string;
  rating?: number;
  helpfulCount: number;
  flagged: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface UserProfile {
  userId: string;
  displayName: string;
  isAnonymous: boolean;
  avatarInitials: string;
  joinedAt: number;
  stats: {
    reviewCount: number;
    stopsAdded: number;
    helpfulVotes: number;
    reportCount: number;
  };
  badges: Record<string, boolean>;
  lastActiveAt: number;
}

export interface Report {
  reportId: string;
  userId: string;
  isAnonymous: boolean;
  stopId?: string;
  routeId?: string;
  agencyId?: string;
  type: 'delay' | 'closure' | 'accessibility_issue' | 'safety_concern' | 'route_change' | 'new_stop_nearby' | 'other';
  severity: 'low' | 'moderate' | 'high' | 'critical';
  title: string;
  description: string;
  createdAt: number;
  expiresAt: number;
  confirmCount: number;
  resolved: boolean;
  active: boolean;
}

export interface CrowdsourcedSubmission {
  submissionId: string;
  submittedBy: string;
  submittedAt: number;
  status: 'pending' | 'approved' | 'rejected';
  stopData: Partial<Stop>;
  verificationCount: number;
  rejectionCount: number;
  promoted: boolean;
}
