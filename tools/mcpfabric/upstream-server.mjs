import { readFileSync, mkdirSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const root = fileURLToPath(new URL('../../', import.meta.url));
const configPath = resolve(root, 'run-mcpfabric/config/mcpfabric.config.json');
const config = JSON.parse(readFileSync(configPath, 'utf8'));
if (config.host !== '127.0.0.1' || config.requireAuth !== true || !config.token) {
  throw new Error('The project bridge must use 127.0.0.1 and bearer authentication.');
}

// Use this project's client only; never discover a different running Minecraft instance.
process.env.MCPFABRIC_URL = `http://127.0.0.1:${config.port}`;
process.env.MCPFABRIC_TOKEN = config.token;
process.env.MCPFABRIC_TRANSPORT = 'stdio';
process.env.MCPFABRIC_DATA_DIR = resolve(root, '.local-tools/mcpfabric/data');
process.env.MCPFABRIC_TOOLSETS = 'player,vision,chat,events,session,ui,operator,script';
process.env.MCPFABRIC_AGENT = 'false';
process.env.MCPFABRIC_SCRIPTS = 'true';
process.env.MCPFABRIC_CHANNEL = 'false';
mkdirSync(process.env.MCPFABRIC_DATA_DIR, { recursive: true });
process.chdir(root);
await import(pathToFileURL(resolve(root, 'tools/mcpfabric/node_modules/mcpfabric-mcp-server/dist/index.js')).href);
