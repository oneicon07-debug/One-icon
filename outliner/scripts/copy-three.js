// Copies the exact three.js files the app needs into the app's assets so it works offline.
// Usage: node copy-three.js <path-to-node_modules/three> <destination-dir>
// Follows relative imports automatically and fails the build if anything is missing.
const fs = require('fs');
const path = require('path');

const src = path.resolve(process.argv[2] || '');
const dst = path.resolve(process.argv[3] || '');

if (!process.argv[2] || !process.argv[3] || !fs.existsSync(src)) {
  console.error('Usage: node copy-three.js <node_modules/three> <dest>');
  process.exit(1);
}

const entries = [
  'build/three.module.js',
  'examples/jsm/controls/OrbitControls.js',
  'examples/jsm/loaders/GLTFLoader.js',
  'examples/jsm/loaders/OBJLoader.js',
  'examples/jsm/utils/BufferGeometryUtils.js'
];

const seen = new Set();
const relImport = /(?:from\s*|import\s*)['"](\.{1,2}\/[^'"]+)['"]/g;

function copy(rel) {
  rel = path.posix.normalize(rel);
  if (seen.has(rel)) return;
  seen.add(rel);

  const from = path.join(src, rel);
  const to = path.join(dst, rel);
  if (!fs.existsSync(from)) {
    console.error('MISSING in three package:', rel);
    process.exit(1);
  }
  fs.mkdirSync(path.dirname(to), { recursive: true });
  fs.copyFileSync(from, to);
  console.log('copied', rel);

  const code = fs.readFileSync(from, 'utf8');
  let m;
  while ((m = relImport.exec(code))) {
    copy(path.posix.join(path.posix.dirname(rel), m[1]));
  }
}

entries.forEach(copy);
console.log('done:', seen.size, 'files ->', dst);
