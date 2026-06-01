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

assert.ok(
  source.includes('rounded-[10px]') &&
    source.includes('min-h-[68px]') &&
    source.includes('px-4 py-3') &&
    source.includes('max-w-[606.667px]'),
  'WorkflowNodeCard should match the official compact card dimensions.'
);

assert.ok(
  source.includes('group-hover:hidden') &&
    source.includes('group-hover:block') &&
    source.includes('ml-7 w-full overflow-hidden text-ellipsis whitespace-nowrap text-left text-xs'),
  'WorkflowNodeCard should match the official icon hover and summary layout.'
);
