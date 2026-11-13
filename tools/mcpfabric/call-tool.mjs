import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const root = fileURLToPath(new URL('../../', import.meta.url));
const name = process.argv[2];
if (!name) throw new Error('Usage: node tools/mcpfabric/call-tool.mjs TOOL [JSON_ARGUMENTS]');
const client = new Client({ name: 'myriad-client-cli', version: '1.0.0' });
try {
  await client.connect(new StdioClientTransport({
    command: process.execPath, args: [resolve(root, 'tools/mcpfabric/start-server.mjs')], cwd: root, stderr: 'pipe',
  }));
  const result = await client.callTool({ name, arguments: JSON.parse(process.argv[3] ?? '{}') });
  const images = result.content?.filter(item => item.type === 'image') ?? [];
  if (images.length) {
    const dir = resolve(root, 'run-mcpfabric/screenshots');
    mkdirSync(dir, { recursive: true });
    for (const [i, image] of images.entries()) {
      const path = resolve(dir, `${name}-${Date.now()}-${i}.png`);
      writeFileSync(path, Buffer.from(image.data, 'base64'));
      console.log(JSON.stringify({ image: path }));
    }
  }
  const value = result.structuredContent ?? result.content?.filter(item => item.type === 'text');
  console.log(JSON.stringify(value));
  if (result.isError) process.exitCode = 1;
} finally {
  await client.close();
}
