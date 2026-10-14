// Reference check; no npm dependencies. Run alongside the Java shared-vector test.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const colors = ['red', 'blue', 'yellow', 'green'];
// Matches the FE function pinned by the adopted contract.
function next(random, previous) {
  for (let attempt = 0; attempt < 10; attempt++) {
    const word = colors[Math.floor(random() * colors.length)];
    let ink = word;
    if (random() < 0.75) {
      const others = colors.filter(color => color !== word);
      ink = others[Math.floor(random() * others.length)];
    }
    if (!previous || previous.word !== word || previous.ink !== ink) return { word, ink };
  }
  const fallback = colors.find(color => color !== previous?.ink);
  return { word: previous?.word ?? fallback, ink: fallback };
}
let x = 123456789;
const random = () => {
  x ^= x << 13;
  x ^= x >>> 17;
  x ^= x << 5;
  return (x >>> 0) / 4294967296;
};
const [header, ...rows] = readFileSync(new URL('./color-word-generator-v1.csv', import.meta.url), 'utf8').trim().split(/\r?\n/);
assert.equal(header, 'seed,sequence,word,ink');
assert.ok(rows.length >= 4, 'Expected at least four shared contract vectors');
let previous;
for (const [index, row] of rows.entries()) {
  const fields = row.split(',');
  assert.equal(fields.length, 4);
  const [seed, sequence, word, ink] = fields;
  assert.equal(Number(seed), 123456789);
  assert.equal(Number(sequence), index + 1);
  previous = next(random, previous);
  assert.deepEqual(previous, { word, ink });
}
console.log(`Shared contract vectors: ${rows.length} matched`);
