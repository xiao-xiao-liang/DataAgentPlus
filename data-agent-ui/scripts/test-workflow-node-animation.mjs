import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const source = readFileSync(new URL('../src/views/Home/index.tsx', import.meta.url), 'utf8');

assert.ok(
  source.includes('grid-rows-[1fr] opacity-100') &&
    source.includes('grid-rows-[0fr] opacity-0') &&
    source.includes('transition-[grid-template-rows,opacity]'),
  'WorkflowNodeCard should animate body expansion with grid rows and opacity.'
);

assert.ok(
  source.includes('min-h-0 overflow-hidden') && source.includes('transition-[padding]'),
  'WorkflowNodeCard should animate inner body spacing without unmounting content immediately.'
);

assert.ok(
  !source.includes('hasBody && isOpen && ('),
  'WorkflowNodeCard body should stay mounted so collapse animation can play.'
);
