'use strict';
/* Universal modulation: sources, routes and macros on top of the parameter registry.
   Destination ids:  eng:<param>  (instrument engine)   v:<level|vpan|vcut>  (per-voice strip)
                     trk:<vol|pan|rev|dly>  (track)      fx:<slotId>:<param>  (track insert effect)
   How a route is applied depends on the pair:
     note-start source (velocity, note, random)          -> added to the parameter when a note starts
     continuous source -> live destination (real AudioParam) -> audio-rate signal, every sample
     continuous source -> note-start destination          -> sampled when a note starts
   Every route goes source -> curve/polarity/range shaper -> optional smoothing -> gain -> AudioParam. */
const SRC_TYPES={
  lfo:{name:'LFO',scope:'track',bip:true},env:{name:'Envelope',scope:'voice',bip:false},mseg:{name:'Multi-stage envelope',scope:'voice',bip:false},
  step:{name:'Step modulator',scope:'track',bip:false},sh:{name:'Sample & hold',scope:'track',bip:true},follow:{name:'Envelope follower',scope:'track',bip:false}
};
const FIXED_SRC=[
  {id:'vel',name:'Velocity',scope:'note',bip:false},{id:'note',name:'Key tracking',scope:'note',bip:true},{id:'rand',name:'Note random',scope:'note',bip:true},
  {id:'mw',name:'Mod wheel',scope:'track',bip:false},{id:'bend',name:'Pitch bend',scope:'track',bip:true},{id:'at',name:'Aftertouch',scope:'track',bip:false},
  {id:'pat',name:'Poly aftertouch',scope:'voice',bip:false},{id:'cc',name:'MIDI CC',scope:'track',bip:false}
];
const MOD_COLORS=['#ffb454','#e8a0bb','#8fcaa2','#79c3e0','#a796e8','#ef9a6a','#c9d77a'];
function newModState(){return {sources:[],routes:[],macros:[]}}
function newSource(type){const n={lfo:'LFO',env:'Env',mseg:'MSEG',step:'Steps',sh:'S&H',follow:'Follower'}[type];
  const s={id:uid('s'),type,name:n,color:MOD_COLORS[Math.floor(Math.random()*MOD_COLORS.length)]};
  if(type==='lfo')Object.assign(s,{wave:'sine',sync:'free',rate:1.5,retrig:false,steps:Array.from({length:16},(_,i)=>Math.sin(i/16*Math.PI*2))});
  if(type==='env')Object.assign(s,{a:0.01,d:0.4,s:0.3,r:0.4});
  if(type==='mseg')Object.assign(s,{points:[[0,0],[0.05,1],[0.4,0.3],[1.2,0.6],[2,0]]});
  if(type==='step')Object.assign(s,{sync:'1/16',rate:4,steps:[1,0,0.5,0,0.8,0,0.3,0,1,0.2,0.6,0,0.8,0,0.4,0.1],smooth:0});
  if(type==='sh')Object.assign(s,{sync:'1/8',rate:4});
  if(type==='follow')Object.assign(s,{track:null,release:0.12,gain:3});
  return s}
function newRoute(src,dst,amt){return {id:uid('r'),src,dst,amt:amt==null?0.5:amt,on:true,curve:'lin',bip:false,min:0,max:1,smooth:0,cc:1,name:'',color:''}}
function newMacro(name){return {id:uid('m'),name:name||'Macro',value:0,cc:null,maps:[]}}
function srcInfo(tr,id){if(id.startsWith('macro:')){const m=tr.mod.macros.find(x=>'macro:'+x.id===id);return m?{id,name:m.name,scope:'track',bip:false,macro:m}:null}
  const f=FIXED_SRC.find(s=>s.id===id);if(f)return f;const s=tr.mod.sources.find(x=>x.id===id);if(!s)return null;
  const T=SRC_TYPES[s.type];return {id,name:s.name,scope:s.type==='lfo'&&s.retrig?'voice':T.scope,bip:T.bip,src:s}}
function allSources(tr){return FIXED_SRC.concat(tr.mod.sources.map(s=>srcInfo(tr,s.id)),tr.mod.macros.map(m=>srcInfo(tr,'macro:'+m.id)))}

/* ───────── destination registry ───────── */
function destDef(tr,dst){
  const [kind,a,b]=dst.split(':');
  if(kind==='lyr'){const i=+a,src=tr.params['L'+i+'src'],pre=presetById(src),E=pre&&ENG[pre.engine];const p=E&&E.params.find(x=>x.k===b);return p?{p,kind,i,pre,label:'Layer '+(i+1)+' · '+p.label,live:null}:null}
  if(kind==='macro'){const m=tr.mod.macros.find(x=>x.id===a);return m?{p:{k:a,label:m.name,type:'num',min:0,max:1,def:0,fmt:FMT.pct,log:false},kind,m,label:'Macro · '+m.name,live:null}:null}
  if(kind==='eng'){const p=ENG[tr.engine].params.find(x=>x.k===a);return p?{p,kind,label:p.label,live:p.live===1?'audio':p.live==='js'?'js':null}:null}
  if(kind==='v'){const p=VOICE_PARAMS.find(x=>x.k===a);return p?{p,kind,label:p.label,live:'audio'}:null}
  if(kind==='trk'){const p=TRACK_PARAMS.find(x=>x.k===a);return p?{p,kind,label:p.label,live:'audio'}:null}
  if(kind==='fx'){const f=tr.fx.find(x=>x.id===a);if(!f)return null;const p=FX[f.type].params.find(x=>x.k===b);return p?{p,kind,f,label:FX[f.type].name+' · '+p.label,live:p.live?'audio':null}:null}
  return null}
function allDests(tr){const out=[];ENG[tr.engine].params.forEach(p=>out.push('eng:'+p.k));VOICE_PARAMS.forEach(p=>out.push('v:'+p.k));TRACK_PARAMS.forEach(p=>out.push('trk:'+p.k));
  tr.fx.forEach(f=>FX[f.type].params.forEach(p=>out.push('fx:'+f.id+':'+p.k)));return out}
function getParamValue(tr,dst){const d=destDef(tr,dst);if(!d)return null;
  if(d.kind==='macro')return d.m.value;if(d.kind==='lyr'){const o=tr.layerOver&&tr.layerOver[d.i]||{};return o[d.p.k]!==undefined?o[d.p.k]:(d.pre.params[d.p.k]!==undefined?d.pre.params[d.p.k]:d.p.def)}if(d.kind==='eng')return tr.params[d.p.k];if(d.kind==='trk')return tr[d.p.k];if(d.kind==='fx')return d.f.params[d.p.k];return d.p.def}
/* Compatibility of a source with a destination: 'audio' | 'note' | 'js' | null (not allowed, with a reason). */
function routeMode(tr,r){const s=srcInfo(tr,r.src),d=destDef(tr,r.dst);if(!s||!d)return {mode:null,why:!s?'Source no longer exists':'Destination is not available on this instrument'};
  if(r.src.startsWith('follow')||s.src&&s.src.type==='follow'){if(s.src.track===tr.id)return {mode:null,why:'A track cannot follow itself'}}
  if(d.kind==='macro')return {mode:null,why:'Macros are controls, not modulation destinations'};
  if(d.kind==='lyr')return {mode:null,why:'Layer sound settings apply when notes start; modulate the layer level, pan or filter instead'};
  const trackLevel=d.kind==='trk'||d.kind==='fx';
  if(s.scope==='note')return trackLevel?{mode:null,why:'Note sources only affect notes, not track or effect controls'}:{mode:'note'};
  if(s.scope==='voice'){if(trackLevel)return {mode:null,why:'Per-voice sources cannot drive track or effect controls'};return d.live==='audio'?{mode:'audio'}:{mode:null,why:'Per-voice envelopes need a live destination'}}
  if(d.live==='audio')return {mode:'audio'};if(d.live==='js')return {mode:'js'};return trackLevel?{mode:null,why:'This control cannot be modulated'}:{mode:'note'}}
/* Track dependency graph for envelope followers: refuse routes that would make a loop between tracks. */
function followCreatesCycle(srcTrackId,dstTrackId){const edges={};S.tracks.forEach(t=>t.mod.sources.filter(s=>s.type==='follow'&&s.track).forEach(s=>{if(t.mod.routes.some(r=>r.src===s.id))(edges[s.track]=edges[s.track]||[]).push(t.id)}));
  (edges[srcTrackId]=edges[srcTrackId]||[]).push(dstTrackId);const seen=new Set(),walk=id=>{if(id===srcTrackId&&seen.size)return true;if(seen.has(id))return false;seen.add(id);return (edges[id]||[]).some(walk)};return walk(dstTrackId)}

/* ───────── JS-side source values (for note-start sampling, granular, and the UI) ───────── */
function syncRate(s){return s.sync&&s.sync!=='free'?divHz(s.sync):s.rate}
function lfoShape(s,ph,rt){ph=((ph%1)+1)%1;switch(s.wave){case 'sine':return Math.sin(ph*2*Math.PI);case 'triangle':return 1-4*Math.abs(ph-0.5);case 'sawtooth':return ph*2-1;case 'square':return ph<0.5?1:-1;
  case 'random':{const q=rt&&rt.mod&&rt.mod.rand[s.id]||[0];return q[Math.floor(ph*q.length)]||0}case 'custom':return s.steps[Math.floor(ph*s.steps.length)]||0}return 0}
function srcValueJS(tr,id,n,v,t){const rt=tr._rt;
  if(id==='vel')return v;if(id==='note')return clamp((n-60)/36,-1,1);if(id==='rand')return Math.random()*2-1;
  if(id==='mw')return tr._mw||0;if(id==='bend')return (tr._bend||0)/(tr.bendRange||2);if(id==='at')return tr._at||0;if(id==='pat')return 0;if(id==='cc')return 0;
  if(id.startsWith('cc'))return tr._cc&&tr._cc[+id.slice(2)]||0;
  const s=srcInfo(tr,id);if(!s)return 0;if(s.macro)return s.macro.value;const src=s.src,t0=rt&&rt.mod?rt.mod.t0:0,ph=(t-t0)*syncRate(src);
  if(src.type==='lfo')return src.retrig?lfoShape(src,0,rt):lfoShape(src,ph,rt);
  if(src.type==='step')return src.steps[Math.floor(((ph%1)+1)%1*src.steps.length)]||0;
  if(src.type==='sh'){const q=rt&&rt.mod&&rt.mod.rand[src.id]||[0];return q[Math.floor(((ph%1)+1)%1*q.length)]||0}
  if(src.type==='follow'){const o=S.tracks.find(x=>x.id===src.track);return o?clamp(trackLevel(o)*src.gain,0,1):0}
  return 0}
function routeValue(tr,r,n,v,t){let x=r.src==='cc'?(tr._cc&&tr._cc[r.cc]||0):srcValueJS(tr,r.src,n,v,t);const s=srcInfo(tr,r.src);if(s&&!s.bip&&r.bip)x=x*2-1;return shapeCurve(r.curve,x)*r.amt}
function applyNorm(p,base,delta,r){return fromNorm(p,clamp(toNorm(p,base)+delta,r.min,r.max))}
/* Parameter set for one note: base params plus note-start modulation. */
function noteParams(tr,n,v,t){const out=Object.assign({},tr.params);
  tr.mod.routes.forEach(r=>{if(!r.on||!r.dst.startsWith('eng:'))return;const m=routeMode(tr,r);if(m.mode!=='note')return;const d=destDef(tr,r.dst);out[d.p.k]=applyNorm(d.p,out[d.p.k],routeValue(tr,r,n,v,t),r)});
  return out}

/* ───────── audio-rate source nodes per track ───────── */
const SPP=256;
function tableBuffer(vals){const b=ctx.createBuffer(1,vals.length*SPP,ctx.sampleRate),d=b.getChannelData(0);vals.forEach((v,i)=>d.fill(v,i*SPP,(i+1)*SPP));return b}
function tableSource(vals,rate,t){const s=ctx.createBufferSource();s.buffer=tableBuffer(vals);s.loop=true;s.playbackRate.value=vals.length*SPP/ctx.sampleRate*rate;s.start(t);return s}
function buildSourceNode(tr,src,t,perVoice){
  const rt=tr._rt;
  if(src.type==='lfo'){const r=syncRate(src);if(src.wave==='random'){const q=rt.mod.rand[src.id]||(rt.mod.rand[src.id]=Array.from({length:16},()=>Math.random()*2-1));return tableSource(q,r,t)}
    if(src.wave==='custom')return tableSource(src.steps.map(x=>clamp(x,-1,1)),r,t);const o=ctx.createOscillator();o.type=src.wave;o.frequency.value=r;o.start(t);return o}
  if(src.type==='step'){let n=tableSource(src.steps.map(x=>clamp(x,0,1)),syncRate(src)/1,t);n.playbackRate.value=src.steps.length*SPP/ctx.sampleRate*(syncRate(src)/src.steps.length);
    if(src.smooth>0){const lp=F('lowpass',1/(2*Math.PI*Math.max(0.001,src.smooth)),0.7);n.connect(lp);return Object.assign(lp,{stop:t2=>n.stop(t2)})}return n}
  if(src.type==='sh'){const q=rt.mod.rand[src.id]||(rt.mod.rand[src.id]=Array.from({length:32},()=>Math.random()*2-1));const n=tableSource(q,syncRate(src)/32,t);n.playbackRate.value=SPP/ctx.sampleRate*syncRate(src);return n}
  if(src.type==='follow'){const o=S.tracks.find(x=>x.id===src.track);if(!o||!o._rt)return null;const ab=ctx.createWaveShaper();ab.curve=cache('abs',()=>{const c=new Float32Array(257);for(let i=0;i<257;i++)c[i]=Math.abs(i/128-1);return c});
    const lp=F('lowpass',1/(2*Math.PI*Math.max(0.005,src.release)),0.5),g=G(src.gain);o._rt.vol.connect(ab).connect(lp).connect(g);return g}
  return null}
function constNode(v){const c=ctx.createConstantSource();c.offset.value=v;c.start();return c}
function buildTrackMod(tr,t0){
  const rt=tr._rt;if(!rt)return;teardownTrackMod(tr);
  const m=rt.mod={t0:t0==null?ctx.currentTime:t0,src:{},rand:(rt.mod&&rt.mod.rand)||{},conns:[]};
  m.src.mw=constNode(tr._mw||0);m.src.bend=constNode((tr._bend||0)/(tr.bendRange||2));m.src.at=constNode(tr._at||0);m.cc={};
  tr.mod.macros.forEach(x=>m.src['macro:'+x.id]=constNode(x.value));
  tr.mod.sources.forEach(s=>{if(s.type==='env'||s.type==='mseg'||(s.type==='lfo'&&s.retrig))return;const n=buildSourceNode(tr,s,m.t0);if(n)m.src[s.id]=n});
  tr.mod.routes.forEach(r=>{if(!r.on)return;const d=destDef(tr,r.dst);if(!d||(d.kind!=='trk'&&d.kind!=='fx'))return;if(routeMode(tr,r).mode!=='audio')return;
    const node=trackSrcNode(tr,r);if(!node)return;let ap,per,extra=[];
    if(d.kind==='trk'){ap={vol:rt.vol.gain,pan:rt.pan.pan,rev:rt.rev.gain,dly:rt.dly.gain}[d.p.k];per=d.p.k==='pan'?2:1}
    else{const inst=d.f._inst;const a=inst&&inst.aps&&inst.aps[d.p.k];if(!a)return;ap=a.ap;per=a.perNorm;extra=a.extra||[]}
    const base=d.kind==='trk'?tr[d.p.k]:d.f.params[d.p.k];[ap,...extra].forEach(x=>m.conns.push(connectRoute(tr,r,node,x,per,d.p,base)))});
}
function trackSrcNode(tr,r){const m=tr._rt.mod;if(r.src==='cc'){if(!m.cc[r.cc])m.cc[r.cc]=constNode(tr._cc&&tr._cc[r.cc]||0);return m.cc[r.cc]}return m.src[r.src]||null}
function teardownTrackMod(tr){const m=tr._rt&&tr._rt.mod;if(!m)return;Object.values(m.src).forEach(n=>{try{n.stop&&n.stop()}catch(e){}try{n.disconnect()}catch(e){}});Object.values(m.cc||{}).forEach(n=>{try{n.stop();n.disconnect()}catch(e){}});m.conns.forEach(c=>c.forEach(n=>{try{n.disconnect()}catch(e){}}));m.src={};m.conns=[]}
/* src -> shaper (curve, polarity, clamp around base) -> smoothing -> gain(units per full range) -> AudioParam */
function connectRoute(tr,r,srcNode,ap,perNorm,p,base){
  const s=srcInfo(tr,r.src),bn=p?toNorm(p,base):0,sh=ctx.createWaveShaper(),c=new Float32Array(257);
  for(let i=0;i<257;i++){let x=i/128-1;if(s&&!s.bip&&r.bip)x=x*2-1;const y=shapeCurve(r.curve,x)*r.amt;c[i]=clamp(bn+y,r.min,r.max)-bn}
  sh.curve=c;let n=srcNode.connect(sh);const nodes=[sh];
  if(r.smooth>0){const lp=F('lowpass',1/(2*Math.PI*r.smooth/1000),0.7);n=n.connect(lp);nodes.push(lp)}
  const g=G(perNorm);n.connect(g).connect(ap);nodes.push(g);return nodes}
function setLiveSource(tr,key,v){const m=tr._rt&&tr._rt.mod;if(!m||!ctx)return;const n=key.startsWith('cc')?m.cc[+key.slice(2)]:m.src[key];if(n&&n.offset)n.offset.setTargetAtTime(v,ctx.currentTime,0.006)}

/* ───────── per-voice context ───────── */
function voiceCtx(tr,n,v,t){
  const M={binds:{},prev:null,legato:false,voiceNodes:[],n,v,t,release:[]};
  M.bind=(k,ap,perNorm)=>{(M.binds[k]=M.binds[k]||[]).push({ap,perNorm})};
  const js=tr.mod.routes.filter(r=>r.on&&r.dst.startsWith('eng:')&&routeMode(tr,r).mode==='js');
  if(js.length)M.js=(k,base)=>{const d=ENG[tr.engine].params.find(x=>x.k===k);let val=base;js.forEach(r=>{if(r.dst==='eng:'+k)val=applyNorm(d,val,routeValue(tr,r,n,v,ctx.currentTime),r)});return val};
  return M}
function voiceStrip(tr,M,out){const want=tr.mod.routes.filter(r=>r.on&&r.dst.startsWith('v:'));if(!want.length)return out;
  const lvl=G(1),pan=ctx.createStereoPanner(),lp=F('lowpass',20000,0.7);lvl.connect(pan).connect(lp).connect(out);
  M.bind('v:level',lvl.gain,1);M.bind('v:vpan',pan.pan,2);M.bind('v:vcut',lp.detune,Math.log2(20000/40)*1200);M.strip=[lvl,pan,lp];return lvl}
function voiceSourceNode(tr,M,id){const s=srcInfo(tr,id);if(!s)return null;const t=M.t;
  if(id==='pat'){if(!M.pat){M.pat=constNode(0);M.voiceNodes.push(M.pat)}return M.pat}
  const src=s.src;if(!src)return null;if(M['n'+id])return M['n'+id];let node=null;
  if(src.type==='env'){const c=ctx.createConstantSource();c.offset.value=0;c.start(t);const o=c.offset;o.setValueAtTime(0,t);o.linearRampToValueAtTime(1,t+Math.max(0.001,src.a));o.setTargetAtTime(src.s,t+src.a,Math.max(0.003,src.d/3));
    M.release.push(t2=>{hold(o,t2);o.setTargetAtTime(0,t2,Math.max(0.003,src.r/3))});node=c}
  else if(src.type==='mseg'){const c=ctx.createConstantSource();c.start(t);const o=c.offset;o.setValueAtTime(src.points[0][1],t);src.points.slice(1).forEach(([tt,lv])=>o.linearRampToValueAtTime(lv,t+tt));node=c}
  else if(src.type==='lfo')node=buildSourceNode(tr,src,t,true);
  if(node){M.voiceNodes.push(node);M['n'+id]=node}return node}
/* Connect live routes into this voice after the engine has registered its AudioParams. */
function attachVoice(tr,M,h){
  if(tr.mod.routes.some(r=>r.on&&r.dst==='v:vpitch'))(h.oscs||[]).forEach(o=>M.bind('v:vpitch',o.detune,2400));
  tr.mod.routes.forEach(r=>{if(!r.on)return;const d=destDef(tr,r.dst);if(!d||(d.kind!=='eng'&&d.kind!=='v'))return;if(routeMode(tr,r).mode!=='audio')return;
    const key=d.kind==='eng'?d.p.k:'v:'+d.p.k,b=M.binds[key];if(!b)return;
    let node;if(r.src==='vel'||r.src==='note'||r.src==='rand')return;const s=srcInfo(tr,r.src);
    node=s.scope==='voice'?voiceSourceNode(tr,M,r.src):trackSrcNode(tr,r);if(!node)return;
    const base=d.kind==='eng'?M.P[d.p.k]:d.p.def;b.forEach(x=>M.voiceNodes.push(...connectRoute(tr,r,node,x.ap,x.perNorm,d.p,base)))});
  const off=h.off;h.off=t2=>{M.release.forEach(f=>f(Math.max(t2,ctx.currentTime)));off(t2);if(!OFF){const life=4000+(ENG[tr.engine].params.find(p=>p.k==='aR')?tr.params.aR*1000:0);setTimeout(()=>M.voiceNodes.forEach(nd=>{try{nd.stop&&nd.stop()}catch(e){}try{nd.disconnect()}catch(e){}}),life)}};
  h.M=M;
}
/* Base value changed while notes are held: shift each live binding by the change. */
function pushLiveBase(tr,key,oldV,newV){if(!tr._rt)return;const p=ENG[tr.engine].params.find(x=>x.k===key);if(!p||p.live!==1)return;const dn=toNorm(p,newV)-toNorm(p,oldV);if(!dn)return;
  tr._rt.live.forEach(h=>{const b=h.M&&h.M.binds[key];if(!b)return;b.forEach(x=>{const v=x.ap.value+dn*x.perNorm;x.ap.setTargetAtTime(v,ctx.currentTime,0.01)})})}

/* ───────── setting any registered parameter (knobs, macros, MIDI learn, randomize all use this) ───────── */
function setParam(tr,dst,v,opts={}){
  const d=destDef(tr,dst);if(!d)return false;const p=d.p;
  if(d.kind==='lyr'){if(p.type==='num')v=clamp(+v,p.min,p.max);tr.layerOver=tr.layerOver||[{},{},{},{}];(tr.layerOver[d.i]=tr.layerOver[d.i]||{})[p.k]=v;if(!opts.quiet)markDirty();return true}
  if(d.kind==='macro'){applyMacro(tr,d.m,+v);(d.m.maps||[]).forEach(mp=>syncControl(tr,mp.dst));return true}
  if(p.type==='num'){v=clamp(+v,p.min,p.max);if(p.int)v=Math.round(v)}
  if(d.kind==='eng'){const old=tr.params[p.k];tr.params[p.k]=v;pushLiveBase(tr,p.k,old,v);if(tr._rt&&tr._rt.eng&&tr._rt.eng.update)tr._rt.eng.update(tr.params)}
  else if(d.kind==='trk'){tr[p.k]=v;applyMix()}
  else if(d.kind==='fx')setFxParam(d.f,p.k,v);
  if(!opts.quiet)markDirty();if(!opts.noModRebuild&&(d.kind==='trk'||d.kind==='fx')&&tr.mod.routes.some(r=>r.dst===dst&&r.on))scheduleModRebuild(tr);
  return true}
let modRebuildT=0;const modDirty=new Set();
function scheduleModRebuild(tr){modDirty.add(tr);clearTimeout(modRebuildT);modRebuildT=setTimeout(()=>{modDirty.forEach(t=>{if(t._rt)buildTrackMod(t)});modDirty.clear()},60)}
function applyMacro(tr,m,v){m.value=clamp(v,0,1);setLiveSource(tr,'macro:'+m.id,m.value);
  m.maps.forEach(mp=>{const d=destDef(tr,mp.dst);if(!d)return;const x=CURVES[mp.curve||'lin'](mp.inv?1-m.value:m.value);setParam(tr,mp.dst,fromNorm(d.p,mp.min+(mp.max-mp.min)*x),{quiet:true,noModRebuild:true})});markDirty()}

/* ───────── modulation patches (save/apply across instruments) ───────── */
function exportModPatch(tr){return JSON.parse(JSON.stringify(tr.mod))}
function applyModPatch(tr,patch){const p=JSON.parse(JSON.stringify(patch)),idMap={};
  p.sources.forEach(s=>{const nid=uid('s');idMap[s.id]=nid;s.id=nid});p.macros.forEach(m=>{const nid=uid('m');idMap['macro:'+m.id]='macro:'+nid;m.id=nid});
  const missing=[];
  p.routes.forEach(r=>{r.id=uid('r');if(idMap[r.src])r.src=idMap[r.src];if(!destDefOrFx(tr,r.dst)){r.on=false;r.missing=true;missing.push(r.dst)}});
  p.macros.forEach(m=>m.maps.forEach(mp=>{if(!destDefOrFx(tr,mp.dst)){mp.missing=true;missing.push(mp.dst)}}));
  tr.mod=p;if(tr._rt)buildTrackMod(tr);return missing}
const destDefOrFx=(tr,dst)=>!!destDef(tr,dst);
function routesFor(tr,dst){return tr.mod.routes.filter(r=>r.dst===dst)}
function macroMapsFor(tr,dst){const out=[];tr.mod.macros.forEach(m=>m.maps.forEach(mp=>{if(mp.dst===dst)out.push({m,mp})}));return out}
/* Value a modulated control shows right now (base plus continuous track-level sources), for the UI ring. */
function displayNorm(tr,dst){const d=destDef(tr,dst);if(!d)return null;const base=getParamValue(tr,dst);let n=toNorm(d.p,base),any=false;
  const t=ctx?ctx.currentTime:0;routesFor(tr,dst).forEach(r=>{if(!r.on)return;const s=srcInfo(tr,r.src);if(!s||s.scope!=='track')return;any=true;n=clamp(n+routeValue(tr,r,60,0.8,t),r.min,r.max)});return any?n:null}
