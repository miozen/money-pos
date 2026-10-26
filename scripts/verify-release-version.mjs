import { readFileSync } from 'node:fs';

const packageFile = new URL('../money-pos-web/package.json', import.meta.url);
const lockFile = new URL('../money-pos-web/package-lock.json', import.meta.url);
const { version } = JSON.parse(readFileSync(packageFile, 'utf8'));
const lock = JSON.parse(readFileSync(lockFile, 'utf8'));

if (!/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/.test(version)) {
  throw new Error(`money-pos-web/package.json version must be SemVer, received: ${version}`);
}

if (lock.version !== version || lock.packages?.['']?.version !== version) {
  throw new Error(`money-pos-web/package-lock.json version must match package.json (${version})`);
}

console.log(version);
