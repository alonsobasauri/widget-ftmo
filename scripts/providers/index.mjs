import * as ftmo from "./ftmo.mjs";
import * as blueguardian from "./blueguardian.mjs";

// FTMO is tried first: its pattern (numeric login + hyphenated UUID) is the more
// specific of the two, and Blue Guardian's bare 24-hex token is the greedier match.
export const providers = [ftmo, blueguardian];

export function byId(providerId) {
  return providers.find((p) => p.id === providerId) ?? null;
}

export function parseShare(input) {
  for (const p of providers) {
    const identity = p.parse(input);
    if (identity) return identity;
  }
  return null;
}
