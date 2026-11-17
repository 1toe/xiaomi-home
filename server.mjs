import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join } from 'node:path';

const port = Number(process.env.PORT || 4173);
const base = process.env.HA_URL?.replace(/\/$/, '');
const token = process.env.HA_TOKEN;
const host = `localhost:${port}`;
const json = (res, status, data) => { res.writeHead(status, {'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}); res.end(JSON.stringify(data)); };
const validEntity = value => /^light\.[a-z0-9_]+$/.test(value || '');
async function requestHA(path, options = {}) {
  if (!base || !token) throw new Error('Configura HA_URL y HA_TOKEN en el servidor.');
  const url = new URL(base); if (!['http:','https:'].includes(url.protocol) || url.username || url.password) throw new Error('HA_URL no es válida.');
  const response = await fetch(base + path, { ...options, redirect:'error', signal:AbortSignal.timeout(8000), headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'} });
  if (!response.ok) throw new Error(`Home Assistant respondió ${response.status}.`);
  return response.json();
}
http.createServer(async (req,res) => {
  if (![host, `127.0.0.1:${port}`, `[::1]:${port}`].includes(req.headers.host)) return json(res,403,{error:'Host no permitido.'});
  const url = new URL(req.url, `http://${host}`);
  if (url.pathname.startsWith('/api/')) {
    if (req.headers.origin && req.headers.origin !== `http://${req.headers.host}`) return json(res,403,{error:'Origen no permitido.'});
    try {
      if (req.method === 'GET' && url.pathname === '/api/state') {
        const entity=url.searchParams.get('entity'); if(!validEntity(entity)) return json(res,400,{error:'Ingresa una entidad light válida.'});
        const state=await requestHA('/api/states/'+entity); return json(res,200,{state:state.state,attributes:state.attributes});
      }
      if (req.method === 'POST' && url.pathname === '/api/light' && req.headers['content-type'] === 'application/json') {
        let raw=''; for await (const chunk of req) {raw+=chunk;if(raw.length>2048)return json(res,413,{error:'Solicitud demasiado grande.'})}
        const {entity,on,color,brightness,temperature,mode='color'}=JSON.parse(raw); if(!validEntity(entity)||typeof on!=='boolean'||!/^#[0-9a-f]{6}$/i.test(color)||!Number.isInteger(brightness)||brightness<1||brightness>100||!['color','temperature','power'].includes(mode)||(mode==='temperature'&&(!Number.isInteger(temperature)||temperature<1000||temperature>40000)))return json(res,400,{error:'Datos inválidos.'});
        const data={entity_id:entity}; if(on&&mode!=='power'){data.brightness_pct=brightness;if(mode==='temperature')data.color_temp_kelvin=temperature;else data.rgb_color=[1,3,5].map(i=>parseInt(color.slice(i,i+2),16))}
        await requestHA('/api/services/light/'+(on?'turn_on':'turn_off'),{method:'POST',body:JSON.stringify(data)});return json(res,200,{ok:true});
      }
      return json(res,404,{error:'Ruta no encontrada.'});
    } catch { return json(res,502,{error:'No se pudo completar la conexión. Revisa la configuración y la entidad.'}); }
  }
  if (req.method !== 'GET') return json(res,405,{error:'Método no permitido.'});
  const files={'/':'index.html','/app.js':'app.js','/style.css':'style.css','/version.json':'version.json'}; const file=files[url.pathname]; if(!file)return json(res,404,{error:'No encontrado.'});
  try {const body=await readFile(join('dist',file));res.writeHead(200,{'Content-Type':({'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.json':'application/json'})[extname(file)],'X-Content-Type-Options':'nosniff'});res.end(body)} catch {json(res,404,{error:'Ejecuta npm run build primero.'})}
}).listen(port,'127.0.0.1',()=>console.log(`Luz: http://${host}`));
