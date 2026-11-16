import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { Server } from '@modelcontextprotocol/sdk/server/index.js';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { ListToolsRequestSchema, CallToolRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve } from 'node:path';

const root = fileURLToPath(new URL('../../', import.meta.url));
const config = JSON.parse(readFileSync(resolve(root, 'run-mcpfabric/config/mcpfabric.config.json'), 'utf8'));
if (config.host !== '127.0.0.1' || config.requireAuth !== true || !config.token) {
  throw new Error('The project bridge must use 127.0.0.1 and bearer authentication.');
}
const inputFields = {
  kind: { type: 'string', enum: ['key', 'mouse'] },
  key: { type: 'string', description: 'A KeyMapping name (key.myriad.skill_e) or raw key.keyboard.escape. Named mappings temporarily borrow an unused key and restore the original binding.' },
  button: { type: 'integer', minimum: 0, maximum: 2 },
  x: { type: 'number', description: 'GUI-scaled mouse X; required with y when a screen is open.' },
  y: { type: 'number' },
  holdTicks: { type: 'integer', minimum: 1, maximum: 20, default: 1 },
  captureFrames: { type: 'integer', minimum: 0, maximum: 40, default: 0 },
  intervalTicks: { type: 'integer', minimum: 1, maximum: 10, default: 1 },
};
const tools = [
  { name: 'myriad_state', method: 'myriad.state', description: 'Read MYRIAD character, mana, learned skills, cooldowns, position and observed native input event counters.', properties: {}, readOnly: true },
  { name: 'myriad_input', method: 'myriad.input', description: 'Test client only. Tap a mod KeyMapping or mouse button through Minecraft native handlers. Auto-release after holdTicks, restore bindings, and optionally capture the effect. Preserves server skill checks.', properties: inputFields, required: ['kind'] },
  { name: 'myriad_release_inputs', method: 'myriad.release', description: 'Release keys/mouse buttons held by the test adapter and restore temporary bindings.', properties: {} },
  { name: 'myriad_capture', method: 'myriad.capture', description: 'Start a bounded PNG frame sequence. Poll myriad_capture_status for paths and per-frame skill state.', properties: { captureFrames: { ...inputFields.captureFrames, minimum: 1, default: 20 }, intervalTicks: inputFields.intervalTicks } },
  { name: 'myriad_capture_status', method: 'myriad.captureStatus', description: 'Read progress and PNG paths of the latest frame capture; view the files to evaluate the effect.', properties: { captureId: { type: 'string' } }, required: ['captureId'], readOnly: true },
];

const upstream = new Client({ name: 'myriad-mcpfabric-proxy', version: '1.0.0' });
await upstream.connect(new StdioClientTransport({
  command: process.execPath, args: [resolve(root, 'tools/mcpfabric/upstream-server.mjs')], cwd: root, stderr: 'inherit',
}));
const server = new Server({ name: 'myriad-mcpfabric', version: '1.0.0' }, {
  capabilities: { tools: {} },
  instructions: 'Use get_status and myriad_state first. MYRIAD input uses native client events with bounded auto-release. Capture multiple frames around transient effects; a state change alone is not visual acceptance. Stop acting when aiPaused is true. Operate the project test world only.',
});
server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [...(await upstream.listTools()).tools, ...tools.map(t => ({
    name: t.name, description: t.description,
    inputSchema: { type: 'object', properties: t.properties, required: t.required ?? [], additionalProperties: false },
    annotations: { readOnlyHint: !!t.readOnly, destructiveHint: false, openWorldHint: false },
  }))],
}));
server.setRequestHandler(CallToolRequestSchema, async request => {
  const tool = tools.find(t => t.name === request.params.name);
  if (!tool) return upstream.callTool(request.params);
  try {
    const response = await fetch(`http://127.0.0.1:${config.port}/rpc`, {
      method: 'POST', headers: { Authorization: `Bearer ${config.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ method: tool.method, params: request.params.arguments ?? {} }),
      signal: AbortSignal.timeout(15000),
    });
    const envelope = await response.json();
    if (!response.ok || !envelope.ok) throw new Error(JSON.stringify(envelope.error ?? { status: response.status }));
    return { content: [{ type: 'text', text: JSON.stringify(envelope.result) }], structuredContent: envelope.result };
  } catch (error) {
    return { content: [{ type: 'text', text: String(error.message) }], isError: true };
  }
});
let closing = false;
async function close() {
  if (closing) return;
  closing = true;
  await upstream.close();
  await server.close();
}
const shutdown = () => void close().finally(() => process.exit(0));
server.onclose = shutdown;
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
await server.connect(new StdioServerTransport());
