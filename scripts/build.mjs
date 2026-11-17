import { cp, mkdir, rm, writeFile } from 'node:fs/promises';
await rm('dist', { recursive: true, force: true });
await mkdir('dist', { recursive: true });
await cp('app', 'dist', { recursive: true });
await writeFile('dist/version.json', JSON.stringify({ version: '0.1.0', sha: process.env.GITHUB_SHA || 'local' }));
