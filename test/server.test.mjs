import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { spawn } from 'node:child_process';
import { once } from 'node:events';

const calls=[];
const mock=http.createServer((req,res)=>{let body='';req.on('data',x=>body+=x);req.on('end',()=>{calls.push({url:req.url,body,auth:req.headers.authorization});res.setHeader('Content-Type','application/json');res.end(req.method==='GET'?JSON.stringify({state:'on',attributes:{friendly_name:'Salon'}}):'[]')})});
await new Promise(resolve=>mock.listen(0,'127.0.0.1',resolve));
const port=43817;
const child=spawn(process.execPath,['server.mjs'],{env:{...process.env,PORT:String(port),HA_URL:`http://127.0.0.1:${mock.address().port}`,HA_TOKEN:'test-token'}});
await once(child.stdout,'data');
after(()=>{child.kill();mock.close()});
const url=`http://127.0.0.1:${port}`;
test('read and control a light through Home Assistant',async()=>{
  const state=await fetch(url+'/api/state?entity=light.salon'); assert.equal((await state.json()).attributes.friendly_name,'Salon');
  const response=await fetch(url+'/api/light',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({entity:'light.salon',on:true,color:'#FF8000',brightness:40})});
  assert.equal(response.status,200); assert.deepEqual(JSON.parse(calls.at(-1).body),{entity_id:'light.salon',rgb_color:[255,128,0],brightness_pct:40}); assert.equal(calls.at(-1).auth,'Bearer test-token');
});
test('reject invalid entities and cross-origin commands',async()=>{
  assert.equal((await fetch(url+'/api/state?entity=switch.test')).status,400);
  assert.equal((await fetch(url+'/api/light',{method:'POST',headers:{'Content-Type':'application/json',Origin:'https://example.com'},body:'{}'})).status,403);
});
test('send temperature and preserve settings when powering on',async()=>{
  const command={entity:'light.salon',on:true,color:'#FF8000',brightness:40,mode:'temperature',temperature:2700};
  const send=body=>fetch(url+'/api/light',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
  assert.equal((await send(command)).status,200);
  assert.deepEqual(JSON.parse(calls.at(-1).body),{entity_id:'light.salon',brightness_pct:40,color_temp_kelvin:2700});
  assert.equal((await send({...command,mode:'power'})).status,200);
  assert.deepEqual(JSON.parse(calls.at(-1).body),{entity_id:'light.salon'});
  assert.equal((await send({...command,temperature:0})).status,400);
});
