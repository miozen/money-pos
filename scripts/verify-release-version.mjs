import { readFileSync } from 'node:fs';

const packageFile = new URL('../money-pos-web/package.json', import.meta.url);
const { version } = JSON.parse(readFileSync(packageFile, 'utf8'));

if (!/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/.test(version)) {
  throw new Error(`money-pos-web/package.json version must be SemVer, received: ${version}`);
}

console.log(version);
