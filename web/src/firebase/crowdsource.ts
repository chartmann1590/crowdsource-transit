import { ref, push } from 'firebase/database';
import { database } from './config';
import type { CrowdsourcedSubmission } from '../types/transit';

export async function submitCrowdsource(data: Omit<CrowdsourcedSubmission, 'submissionId'>) {
  return push(ref(database, 'crowdsourcedSubmissions'), data);
}
