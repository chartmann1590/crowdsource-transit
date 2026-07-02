import { ref, push } from 'firebase/database';
import { database } from './config';
import type { CrowdsourcedSubmission } from '../types/transit';
import { awardPoints } from './gamification';

export async function submitCrowdsource(data: Omit<CrowdsourcedSubmission, 'submissionId'>) {
  const result = await push(ref(database, 'crowdsourcedSubmissions'), data);
  await awardPoints('newStop');
  return result;
}
