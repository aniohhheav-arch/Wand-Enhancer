'use strict';
/* Sampler: user-imported audio lives in IndexedDB (deduplicated by content hash); projects and
   presets reference samples by id. A missing sample is reported by name and the zone stays silent;
   it is never replaced by a different sound. */
let idbP=null;
function idb(){if(idbP)return idbP;idbP=new Promise((res,rej)=>{if(!window.indexedDB){rej(new Error('IndexedDB is not available'));return}
  const r=indexedDB.open('soundcraft',1);r.onupgradeneeded=()=>{if(!r.result.objectStoreNames.contains('samples'))r.result.createObjectStore('samples',{keyPath:'id'})};r.onsuccess=()=>res(r.result);r.onerror=()=>rej(r.error)});return idbP}
async function idbReq(mode,fn){const db=await idb();return new Promise((res,rej)=>{const tx=db.transaction('samples',mode),st=tx.objectStore('samples'),r=fn(st);tx.oncomplete=()=>res(r&&r.result);tx.onerror=()=>rej(tx.error);tx.onabort=()=>rej(tx.error||new Error('storage aborted'))})}
const getSample=id=>idbReq('readonly',st=>st.get(id));
const putSample=rec=>idbReq('readwrite',st=>st.put(rec));
const listSamples=()=>idbReq('readonly',st=>st.getAll());
const deleteSample=id=>idbReq('readwrite',st=>st.delete(id));
async function hashBytes(buf){try{const h=await crypto.subtle.digest('SHA-256',buf);return [...new Uint8Array(h)].slice(0,12).map(b=>b.toString(16).padStart(2,'0')).join('')}
  catch(e){let h=2166136261;const u=new Uint8Array(buf);for(let i=0;i<u.length;i+=Math.max(1,u.length>>16))h=Math.imul(h^u[i],16777619);return 'f'+(h>>>0).toString(16)+u.length.toString(16)}}
const SAMPLE_BUF=new Map(),SAMPLE_META=new Map(),SAMPLE_MISSING=new Set();
let decCtx=null;function decoderCtx(){if(ctx&&!OFF)return ctx;return decCtx||(decCtx=new (window.OfflineAudioContext||window.webkitOfflineAudioContext)(1,1,48000))}
async function importSampleFile(file){
  if(file.size>200*1024*1024)throw new Error(file.name+' is larger than 200 MB');
  const bytes=await file.arrayBuffer();let buf;
  try{buf=await decoderCtx().decodeAudioData(bytes.slice(0))}catch(e){throw new Error(file.name+' is not an audio format this system can decode (try WAV, MP3, OGG or FLAC)')}
  const id='smp_'+await hashBytes(bytes);const existing=await getSample(id).catch(()=>null);
  if(!existing)await putSample({id,name:file.name,type:file.type||'audio',size:bytes.byteLength,added:Date.now(),duration:buf.duration,bytes});
  SAMPLE_BUF.set(id,buf);SAMPLE_META.set(id,{name:file.name,duration:buf.duration});SAMPLE_MISSING.delete(id);return {id,name:file.name,duration:buf.duration,dup:!!existing}}
async function loadSample(id){
  if(SAMPLE_BUF.has(id))return SAMPLE_BUF.get(id);
  let rec=null;try{rec=await getSample(id)}catch(e){errLog.push('samples: '+e.message)}
  if(!rec){SAMPLE_MISSING.add(id);return null}
  try{const b=await decoderCtx().decodeAudioData(rec.bytes.slice(0));SAMPLE_BUF.set(id,b);SAMPLE_META.set(id,{name:rec.name,duration:b.duration});SAMPLE_MISSING.delete(id);return b}
  catch(e){SAMPLE_MISSING.add(id);errLog.push('decode '+id+': '+e.message);return null}}
async function loadTrackSamples(tr){if(!tr.zones)return [];const miss=[];for(const z of tr.zones){if(!z.sample)continue;const b=await loadSample(z.sample);if(!b)miss.push(z.name||z.sample)}return miss}
async function loadProjectSamples(){const miss=[];for(const t of S.tracks)if(t.engine==='sampler')miss.push(...await loadTrackSamples(t));miss.push(...await loadTakes());return miss}
function reversedBuf(id){const k=id+'#rev';if(SAMPLE_BUF.has(k))return SAMPLE_BUF.get(k);const b=SAMPLE_BUF.get(id);if(!b)return null;
  const r=new AudioBuffer({length:b.length,numberOfChannels:b.numberOfChannels,sampleRate:b.sampleRate});for(let c=0;c<b.numberOfChannels;c++){const s=b.getChannelData(c),d=r.getChannelData(c);for(let i=0;i<s.length;i++)d[i]=s[s.length-1-i]}SAMPLE_BUF.set(k,r);return r}
function newZone(sample,name,o={}){return Object.assign({id:uid('z'),sample,name:name||'Zone',root:60,lo:0,hi:127,vlo:0,vhi:127,start:0,end:1,loop:false,ls:0,le:1,reverse:false,tune:0,vol:1,pan:0,group:0},o)}
const ZONE_KEYS=['sample','name','root','lo','hi','vlo','vhi','start','end','loop','ls','le','reverse','tune','vol','pan','group'];
function sanitizeZone(z){const o=newZone(String(z.sample||''),String(z.name||'Zone').slice(0,60));
  ['root','lo','hi','vlo','vhi'].forEach(k=>o[k]=clamp(Math.round(+z[k]||0),0,127));if(z.hi==null)o.hi=127;if(z.vhi==null)o.vhi=127;if(z.root==null)o.root=60;
  ['start','end','ls','le'].forEach(k=>{if(z[k]!=null)o[k]=clamp(+z[k]||0,0,1)});if(o.end<=o.start)o.end=1;if(o.le<=o.ls)o.le=1;
  o.loop=!!z.loop;o.reverse=!!z.reverse;o.tune=clamp(+z.tune||0,-1200,1200);o.vol=clamp(z.vol==null?1:+z.vol,0,2);o.pan=clamp(+z.pan||0,-1,1);o.group=clamp(Math.round(+z.group||0),0,15);return o}
ENG.sampler={name:'Sampler',kind:KIND.sampler,family:'sampler',
  desc:'Play your own recordings across the keyboard. Zones have key and velocity ranges, root key, start/end, loop points, reverse, tuning, level and pan; zones in the same group alternate round-robin and different groups layer.',
  params:[P('tune','Pitch','Global',-24,24,0,{int:true,fmt:FMT.st,live:1}),P('startRand','Start random','Global',0,1,0,{fmt:FMT.pct}),C('rr','Round robin','Global',[['cycle','Cycle'],['random','Random']],'cycle'),
    P('velAmp','Velocity → level','Global',0,1,0.7,{fmt:FMT.pct}),P('cutoff','Cutoff','Filter',40,20000,20000,{log:true,fmt:FMT.hz,live:1}),P('reso','Resonance','Filter',0.1,20,0.7,{log:true,fmt:FMT.dec,live:1}),
    ...ENV_GROUP('a','Amp',0.002,0.3,1,0.15),...OUT_GROUP()],
  on(tr,n,v,t,out,P,M){
    const vel=Math.round(v*127),zs=(tr.zones||[]).filter(z=>n>=z.lo&&n<=z.hi&&vel>=z.vlo&&vel<=z.vhi);if(!zs.length)return null;
    const rt=tr._rt;rt.rr=rt.rr||{};const groups={};zs.forEach(z=>(groups[z.group]=groups[z.group]||[]).push(z));
    const amp=G(0),fl=F('lowpass',P.cutoff,P.reso);M.bind('cutoff',fl.detune,Math.log2(20000/40)*1200);M.bind('reso',fl.Q,20);fl.connect(amp).connect(outStage(P,M,out));const srcs=[];
    Object.keys(groups).forEach(gk=>{const list=groups[gk];let z;if(list.length===1)z=list[0];else if(P.rr==='random')z=list[Math.floor(Math.random()*list.length)];else{const k=gk+':'+n;rt.rr[k]=((rt.rr[k]||0)+1)%list.length;z=list[rt.rr[k]]}
      const buf=z.reverse?reversedBuf(z.sample):SAMPLE_BUF.get(z.sample);if(!buf){if(!tr._missingWarned){tr._missingWarned=1;toast('Sample "'+(z.name||z.sample)+'" is missing on this device. Re-import it in the sampler; the zone stays silent until then.')}return}
      const s=ctx.createBufferSource();s.buffer=buf;s.playbackRate.value=Math.pow(2,(n-z.root)/12+z.tune/1200);s._d=P.tune*100;s.detune.value=s._d+(tr._bend||0)*100;M.bind('tune',s.detune,4800);
      const d=buf.duration,st=clamp(z.start+Math.random()*P.startRand*0.1,0,0.999)*d,en=Math.max(st+0.001,z.end*d);
      if(z.loop){s.loop=true;s.loopStart=z.ls*d;s.loopEnd=Math.max(z.ls*d+0.001,z.le*d);s.start(t,st)}else s.start(t,st,en-st);
      const g=G(z.vol),pn=ctx.createStereoPanner();pn.pan.value=z.pan;s.connect(g).connect(pn).connect(fl);srcs.push(s)});
    if(!srcs.length)return null;adsr(amp.gain,t,P,'a',0.8*(1-P.velAmp+P.velAmp*v));return voice(amp,srcs,P.aR)}};
/* Draw a waveform overview into a canvas. */
function drawWave(cv,buf,z,col){const dpr=window.devicePixelRatio||1,w=cv.clientWidth,h=cv.clientHeight;cv.width=w*dpr;cv.height=h*dpr;const c=cv.getContext('2d');c.scale(dpr,dpr);c.clearRect(0,0,w,h);
  if(!buf){c.fillStyle='rgba(239,122,133,.9)';c.font='13px IBM Plex Sans,sans-serif';c.fillText('Sample missing on this device',10,h/2);return}
  const d=buf.getChannelData(0),step=Math.max(1,Math.floor(d.length/w));c.fillStyle=col||'#79c3e0';
  for(let x=0;x<w;x++){let mn=1,mx=-1;for(let i=x*step;i<(x+1)*step&&i<d.length;i++){const v=d[i];if(v<mn)mn=v;if(v>mx)mx=v}c.fillRect(x,(1-mx)*h/2,1,Math.max(1,(mx-mn)*h/2))}
  if(z){c.fillStyle='rgba(15,14,38,.55)';c.fillRect(0,0,z.start*w,h);c.fillRect(z.end*w,0,w-z.end*w,h);c.fillStyle='#ffb454';c.fillRect(z.start*w,0,2,h);c.fillRect(z.end*w-2,0,2,h);
    if(z.loop){c.fillStyle='rgba(143,202,162,.25)';c.fillRect(z.ls*w,0,(z.le-z.ls)*w,h);c.fillStyle='#8fcaa2';c.fillRect(z.ls*w,0,1,h);c.fillRect(z.le*w-1,0,1,h)}}}
