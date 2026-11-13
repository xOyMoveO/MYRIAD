import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const root = fileURLToPath(new URL('../../', import.meta.url));
const client = new Client({ name: 'myriad-mcpfabric-check', version: '1.0.0' });
const transport = new StdioClientTransport({
  command: process.execPath,
  args: [resolve(root, 'tools/mcpfabric/start-server.mjs')],
  cwd: root,
  stderr: 'inherit',
});

try {
  await client.connect(transport);
  const { tools } = await client.listTools();
  const required = ['get_status', 'list_capabilities', 'screenshot', 'look', 'get_ui', 'run_command', 'run_script',
    'myriad_state', 'myriad_input', 'myriad_capture', 'myriad_capture_status', 'myriad_release_inputs'];
  const missing = required.filter(name => !tools.some(tool => tool.name === name));
  if (missing.length) throw new Error(`Missing MCP tools: ${missing.join(', ')}`);
  console.log(`MCP handshake OK; ${tools.length} tools available.`);
  if (!process.argv.includes('--tools-only')) {
    const status = await client.callTool({ name: 'get_status', arguments: {} });
    if (status.isError) throw new Error(JSON.stringify(status.content));
    console.log('Bridge status:', JSON.stringify(status.content));
  }
  if (process.argv.includes('--capture')) {
    const result = await client.callTool({ name: 'screenshot', arguments: {} });
    if (result.isError) throw new Error(JSON.stringify(result.content));
    const frame = result.content.find(item => item.type === 'image');
    if (!frame) throw new Error('Screenshot did not return image content.');
    const output = resolve(root, 'run-mcpfabric/screenshots/mcp-connection.png');
    mkdirSync(resolve(root, 'run-mcpfabric/screenshots'), { recursive: true });
    writeFileSync(output, Buffer.from(frame.data, 'base64'));
    console.log(`Captured: ${output}`);
  }
} finally {
  await client.close();
}
