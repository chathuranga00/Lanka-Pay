/**
 * Simulated guardian-managed family accounts for minors.
 *
 * <p>Data Minimisation & Safety Rules:
 * <ul>
 *   <li>Only verified adult guardians can create and manage family member profiles.</li>
 *   <li>Profiles collect ONLY minimal data: first name, broad age band, and optional avatar initials.</li>
 *   <li>Children have NO independent phone registration, NO lookup discoverability in registries,
 *       and CANNOT receive payments from strangers.</li>
 *   <li>Only the guardian can fund the simulated balance.</li>
 *   <li>Spending is strictly controlled by daily/monthly limits, allowed categories, freeze controls,
 *       and optional allowance schedules.</li>
 *   <li>Deleting a profile purges data according to retention rules (no geolocation, no photos).</li>
 * </ul>
 *
 * <p><strong>LEGAL NOTICE:</strong>
 * Real-world production deployment requires independent legal counsel regarding minors' data
 * privacy regulations (e.g. COPPA, GDPR-K, and applicable national laws) and implementation of
 * verifiable parental consent (VPC).
 */
package com.lankapay.family;
