import { describe, it, beforeAll, afterAll, beforeEach } from 'vitest';
import {
  initializeTestEnvironment,
  assertFails,
  RulesTestEnvironment,
} from '@firebase/rules-unit-testing';
import { readFileSync } from 'fs';
import {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  collection,
  getDocs,
} from 'firebase/firestore';

const PROJECT_ID = 'lankapay-rules-test';
const FIRESTORE_PORT = 8085;
const FIRESTORE_HOST = '127.0.0.1';

/**
 * Verified collections required by security specification:
 * - users
 * - linkedBankAccounts
 * - transactions
 * - idempotencyKeys
 * - otpChallenges
 * - auditLogs
 */
const PROTECTED_COLLECTIONS = [
  'users',
  'linkedBankAccounts',
  'transactions',
  'idempotencyKeys',
  'otpChallenges',
  'auditLogs',
] as const;

describe('Firestore Client Lockdown Security Rules (@firebase/rules-unit-testing)', () => {
  let testEnv: RulesTestEnvironment;

  beforeAll(async () => {
    testEnv = await initializeTestEnvironment({
      projectId: PROJECT_ID,
      firestore: {
        host: FIRESTORE_HOST,
        port: FIRESTORE_PORT,
        rules: readFileSync('firestore.rules', 'utf8'),
      },
    });
  });

  afterAll(async () => {
    if (testEnv) {
      await testEnv.cleanup();
    }
  });

  beforeEach(async () => {
    if (testEnv) {
      await testEnv.clearFirestore();
    }
  });

  describe('Unauthenticated Client Requests (Direct mobile/web SDK)', () => {
    for (const col of PROTECTED_COLLECTIONS) {
      describe(`Collection: /${col}`, () => {
        it(`strictly DENIES direct read on /${col}/doc1`, async () => {
          const db = testEnv.unauthenticatedContext().firestore();
          const docRef = doc(db, col, 'doc1');
          await assertFails(getDoc(docRef));
        });

        it(`strictly DENIES direct list/query on /${col}`, async () => {
          const db = testEnv.unauthenticatedContext().firestore();
          const colRef = collection(db, col);
          await assertFails(getDocs(colRef));
        });

        it(`strictly DENIES direct write (create/set) on /${col}/doc1`, async () => {
          const db = testEnv.unauthenticatedContext().firestore();
          const docRef = doc(db, col, 'doc1');
          await assertFails(setDoc(docRef, { payload: 'tampered-data', role: 'ADMIN' }));
        });

        it(`strictly DENIES direct update on /${col}/doc1`, async () => {
          const db = testEnv.unauthenticatedContext().firestore();
          const docRef = doc(db, col, 'doc1');
          await assertFails(updateDoc(docRef, { status: 'COMPLETED' }));
        });

        it(`strictly DENIES direct delete on /${col}/doc1`, async () => {
          const db = testEnv.unauthenticatedContext().firestore();
          const docRef = doc(db, col, 'doc1');
          await assertFails(deleteDoc(docRef));
        });
      });
    }
  });

  describe('Authenticated Regular Client Requests (Tenant token: alice-user)', () => {
    for (const col of PROTECTED_COLLECTIONS) {
      describe(`Collection: /${col}`, () => {
        it(`strictly DENIES authenticated client read on /${col}/doc1`, async () => {
          const db = testEnv.authenticatedContext('alice-user').firestore();
          const docRef = doc(db, col, 'doc1');
          await assertFails(getDoc(docRef));
        });

        it(`strictly DENIES authenticated client list/query on /${col}`, async () => {
          const db = testEnv.authenticatedContext('alice-user').firestore();
          const colRef = collection(db, col);
          await assertFails(getDocs(colRef));
        });

        it(`strictly DENIES authenticated client write on /${col}/doc1`, async () => {
          const db = testEnv.authenticatedContext('alice-user').firestore();
          const docRef = doc(db, col, 'doc1');
          await assertFails(setDoc(docRef, { userId: 'alice-user', balance: '1000000.00' }));
        });

        it(`strictly DENIES authenticated client update on /${col}/doc1`, async () => {
          const db = testEnv.authenticatedContext('alice-user').firestore();
          const docRef = doc(db, col, 'doc1');
          await assertFails(updateDoc(docRef, { status: 'COMPLETED' }));
        });

        it(`strictly DENIES authenticated client delete on /${col}/doc1`, async () => {
          const db = testEnv.authenticatedContext('alice-user').firestore();
          const docRef = doc(db, col, 'doc1');
          await assertFails(deleteDoc(docRef));
        });
      });
    }
  });

  describe('Adversarial Client Requests with forged claims (role: admin)', () => {
    for (const col of PROTECTED_COLLECTIONS) {
      describe(`Collection: /${col}`, () => {
        it(`strictly DENIES forged admin claim read on /${col}/doc1`, async () => {
          const db = testEnv.authenticatedContext('attacker-user', { role: 'admin', admin: true }).firestore();
          const docRef = doc(db, col, 'doc1');
          await assertFails(getDoc(docRef));
        });

        it(`strictly DENIES forged admin claim write on /${col}/doc1`, async () => {
          const db = testEnv.authenticatedContext('attacker-user', { role: 'admin', admin: true }).firestore();
          const docRef = doc(db, col, 'doc1');
          await assertFails(setDoc(docRef, { elevated: true }));
        });
      });
    }
  });
});
