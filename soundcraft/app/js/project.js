'use strict';
/* Project model (schema v2), persistence, snapshots, backups and undo. */
const SCHEMA=2;
const TRACK_COLORS=['#e8a0bb','#ffb454','#8fcaa2','#79c3e0','#a796e8','#ef9a6a','#c9d77a','#6fb7b4'];
const FAMILY_COLOR={keys:'#79c3e0',brass:'#ffb454',strings:'#ef9a6a',woodwinds:'#8fcaa2',synths:'#a796e8',bass:'#e8a0bb',drums:'#ef9a6a',experimental:'#6fb7b4',fusion:'#c9d77a',sampler:'#79c3e0'};
function store(k,v){try{localStorage.setItem(k,v);return true}catch(e){return false}}
function read(k){try{return localStorage.getItem(k)}catch(e){return null}}
/* ───────── preferences ───────── */
const PREF_KEY='soundcraft.prefs.v2';
const PREF={weather:'rain',lamp:true,amb:false,ambVol:0.35,motion:'full',fur:'ginger',sens:0.5,view:'studio',theme:'auto',scale:1,latency:'interactive',sampleRate:'auto',sinkId:'',favs:[],recent:[],tourDone:false,keysOpen:true,dock:'roll',splitH:300};
try{Object.assign(PREF,JSON.parse(read(PREF_KEY)||'{}'))}catch(e){}
function savePrefs(){store(PREF_KEY,JSON.stringify(PREF))}
/* ───────── state ───────── */
const S={bpm:112,swing:0,metro:false,quant:1,master:0.8,returns:null,masterFx:[],loop:{on:true,start:0,end:4},markers:[],scale:{root:0,type:'off'},tracks:[],sel:0,ccMap:{},zoom:40,
  octave:4,vel:0.8,rec:false,playing:false,sustain:false,drawLen:1,midiCh:0,dl:null,view:'studio',cursor:0,selClip:null};
const selTrack=()=>S.tracks[S.sel];
function defaultReturns(){return {rev:Object.assign(newFx('reverb',{mix:1,size:2.8}),{id:'ret-rev',vol:0.7,mute:false}),dly:Object.assign(newFx('delay',{mix:1}),{id:'ret-dly',vol:0.55,mute:false})}}
S.returns=defaultReturns();
function newPattern(name,len){return {id:uid('p'),name:name||'A',len:len||4,notes:[]}}
function newTrack(src,o={}){
  const pre=typeof src==='string'?presetById(src):src;const E=ENG[pre&&pre.engine]?ENG[pre.engine]:ENG.va;const engine=pre&&ENG[pre.engine]?pre.engine:'va';
  const pat=newPattern('A',4);
  const tr={id:uid('t'),name:o.name||(pre&&pre.name)||E.name,color:o.color||FAMILY_COLOR[(pre&&pre.family)||E.family]||TRACK_COLORS[S.tracks.length%8],engine,preset:pre&&pre.id||null,
    params:Object.assign(defaultsOf(E.params),pre&&pre.params||{}),mod:pre&&pre.mod?sanitizeMod(JSON.parse(JSON.stringify(pre.mod))):newModState(),
    arp:Object.assign({on:false,mode:'up',rate:'1/16',oct:1,gate:0.6},pre&&pre.arp||{}),poly:pre&&pre.poly||16,bendRange:pre&&pre.bendRange||2,oct:pre&&pre.oct||E.oct||4,
    vol:o.vol!=null?o.vol:0.8,pan:o.pan||0,rev:o.rev!=null?o.rev:(pre&&pre.rev!=null?pre.rev:0.15),dly:o.dly!=null?o.dly:0.05,mute:false,solo:false,fx:[],
    patterns:[pat],clips:[],curPat:pat.id,input:{group:null,lo:0,hi:127,vlo:0,vhi:127,ch:0,tr:0}};
  if(engine==='sampler')tr.zones=(pre&&pre.zones||[]).map(sanitizeZone);if(engine==='fusion')tr.layerOver=pre&&pre.layerOver||[{},{},{},{}];
  if(pre&&pre.fx)tr.fx=sanitizeFxList(pre.fx);if(pre&&pre.input)Object.assign(tr.input,pre.input);
  tr.params=sanitizeParams(E.params,tr.params).o;return tr}
/* Load an instrument preset onto an existing track (keeps notes, clips and mixer). */
function applyInstrument(tr,pre){const E=ENG[pre.engine];if(!E)throw new Error('This preset needs an engine that is not available: '+pre.engine);
  disposeTrack(tr);tr.engine=pre.engine;tr.preset=pre.id||null;tr.params=sanitizeParams(E.params,Object.assign(defaultsOf(E.params),pre.params||{})).o;
  tr.mod=pre.mod?sanitizeMod(JSON.parse(JSON.stringify(pre.mod))):newModState();tr.arp=Object.assign({on:false,mode:'up',rate:'1/16',oct:1,gate:0.6},pre.arp||{});tr.poly=pre.poly||16;tr.oct=pre.oct||E.oct||4;
  if(pre.engine==='sampler')tr.zones=(pre.zones||[]).map(sanitizeZone);else delete tr.zones;if(pre.engine==='fusion')tr.layerOver=pre.layerOver||[{},{},{},{}];else delete tr.layerOver;
  if(ctx){buildTrack(tr);applyMix()}if(tr.engine==='sampler')loadTrackSamples(tr).then(m=>{if(m.length)toast(m.length+' sample'+(m.length>1?'s are':' is')+' missing: '+m.slice(0,3).join(', '))});
  if(pre.id){PREF.recent=[pre.id].concat(PREF.recent.filter(x=>x!==pre.id)).slice(0,16);savePrefs()}}
const curPattern=tr=>tr.patterns.find(p=>p.id===tr.curPat)||tr.patterns[0];
const songEnd=()=>Math.max(4,...S.tracks.flatMap(t=>t.clips.map(c=>c.start+c.len)),S.loop.end);
/* ───────── demo song ───────── */
function demoProject(){
  const d=newTrack('drums-circuit',{name:'Drums',vol:0.85}),b=newTrack('bass-acid',{name:'Acid bass',vol:0.75}),pd=newTrack('syn-nebula',{name:'Nebula pad',vol:0.6,rev:0.45}),k=newTrack('keys-tine',{name:'Tine keys',vol:0.7,rev:0.25,dly:0.2}),c=newTrack('syn-vox',{name:'Choir',vol:0.5,rev:0.5});
  const roots=[33,29,36,31],chords=[[57,60,64],[53,57,60],[55,60,64],[55,59,62]],tops=[76,77,76,74],D=curPattern(d).notes,B=curPattern(b).notes,PD=curPattern(pd).notes,K=curPattern(k).notes,CH=curPattern(c).notes;
  for(let bar=0;bar<4;bar++){const o=bar*16;[0,7,10].concat(bar===3?[14]:[]).forEach(s=>D.push({p:36,s:o+s,l:1,v:1}));[4,12].forEach(s=>D.push({p:39,s:o+s,l:1,v:0.9}));
    for(let s=0;s<16;s+=2)if(s!==14)D.push({p:42,s:o+s,l:1,v:s%4?0.5:0.85});D.push({p:46,s:o+14,l:1,v:0.7});if(bar===0)D.push({p:49,s:0,l:1,v:0.55});if(bar===3){D.push({p:60,s:o+15,l:1,v:0.7});D.push({p:41,s:o+13,l:1,v:0.8})}
    const r=roots[bar];[[0,1,1],[2,1,0.8],[3,1,0.9,12],[6,2.5,0.85],[8,1,1],[10,1,0.8],[11,1,0.9,12],[14,1,0.8]].forEach(([s,l,v,up])=>B.push({p:r+(up||0),s:o+s,l,v}));
    chords[bar].forEach(p=>PD.push({p,s:o,l:16,v:0.7}));const cc=chords[bar],arp=[cc[0]+12,cc[1]+12,cc[2]+12,cc[0]+24,cc[2]+12,cc[1]+12];[0,3,6,8,11,14].forEach((s,i)=>K.push({p:arp[i],s:o+s,l:2,v:0.55+(i%3===0?0.25:0)}));CH.push({p:tops[bar],s:o,l:16,v:0.6})}
  const verse=[d,b,pd,k],all=[d,b,pd,k,c];
  [d,b,pd,k,c].forEach(t=>{const p=curPattern(t);p.len=4});
  pd.clips.push({id:uid('c'),pat:curPattern(pd).id,start:0,len:4});k.clips.push({id:uid('c'),pat:curPattern(k).id,start:0,len:4});
  verse.forEach(t=>t.clips.push({id:uid('c'),pat:curPattern(t).id,start:4,len:4}));all.forEach(t=>t.clips.push({id:uid('c'),pat:curPattern(t).id,start:8,len:8}));
  d.clips.push({id:uid('c'),pat:curPattern(d).id,start:0,len:4});
  return {v:SCHEMA,bpm:112,swing:0.12,quant:1,master:0.72,returns:defaultReturns(),masterFx:[newFx('comp',{threshold:-16,ratio:3,makeup:2})],loop:{on:true,start:8,end:16},
    markers:[{id:uid('k'),bar:0,name:'Intro'},{id:uid('k'),bar:4,name:'Verse'},{id:uid('k'),bar:8,name:'Chorus'}],scale:{root:9,type:'minor'},tracks:[d,b,pd,k,c],zoom:40};
}
function blankProject(){const t=[newTrack('drums-circuit',{name:'Drums'}),newTrack('bass-synth',{name:'Bass'}),newTrack('syn-pad',{name:'Pad'})];
  return {v:SCHEMA,bpm:100,swing:0,quant:1,master:0.8,returns:defaultReturns(),masterFx:[],loop:{on:true,start:0,end:4},markers:[],scale:{root:0,type:'off'},tracks:t,zoom:40}}
function projectFromTemplate(tpl){
  const d=tpl.data||tpl;const tracks=(d.tracks||[]).map(t=>{if(t.preset&&!t.engine){const tr=newTrack(t.preset,{name:t.name});return tr}const tr=newTrack(Object.assign({},t),{name:t.name,color:t.color,vol:t.vol,pan:t.pan,rev:t.rev,dly:t.dly});tr.fx=sanitizeFxList(t.fx);return tr});
  const r=defaultReturns();if(d.returns){if(d.returns.rev)Object.assign(r.rev.params,d.returns.rev);if(d.returns.dly)Object.assign(r.dly.params,d.returns.dly)}
  const mf=(d.masterFx||[]).map(x=>Array.isArray(x)?newFx(x[0],x[1]):newFx(x.type,x.params)).filter(f=>FX[f.type]);let bar=0;
  return {v:SCHEMA,bpm:d.bpm||120,swing:d.swing||0,quant:1,master:0.8,returns:r,masterFx:mf,loop:{on:true,start:0,end:4},markers:(d.markers||[]).map(m=>{const o={id:uid('k'),bar:typeof m==='string'?bar:m.bar,name:typeof m==='string'?m:m.name};bar+=8;return o}),scale:{root:0,type:'off'},tracks,zoom:40}}
/* ───────── migration ───────── */
const V1_MAP={nebula:['syn-nebula',['bright','drift','bloom']],glass:['keys-ep',null],acid:['bass-acid',['cutoff','reso','envAmt']],growl:['bass-growl',null],vox:['syn-vox',['vowel','breath','ensemble']],circuit:['drums-circuit',['punch','snap','crush']],chip:['syn-chip',null],prism:['keys-prism',['shimmer','decay','sparkle']]};
function migrate(d){
  if(!d||typeof d!=='object')throw new Error('not a project');if(!Array.isArray(d.tracks))throw new Error('no tracks');
  if(d.v>SCHEMA)throw new Error('made by a newer Soundcraft (schema '+d.v+'); this version reads schema '+SCHEMA);
  if(!d.v||d.v<2){const bars=[1,2,4,8].includes(+d.bars)?+d.bars:4;
    d.tracks=d.tracks.filter(t=>V1_MAP[t.inst]).map(t=>{const [pid,keys]=V1_MAP[t.inst],tr=newTrack(pid,{name:t.name,vol:t.vol,pan:t.pan!=null?(+t.pan)*2-1:0,rev:t.rev,dly:t.dly});
      if(keys&&Array.isArray(t.params))keys.forEach((k,i)=>{if(t.params[i]!=null)tr.params[k]=clamp(+t.params[i],0,1)});tr.mute=!!t.mute;tr.solo=!!t.solo;
      const p=curPattern(tr);p.len=bars;p.notes=(t.notes||[]).map(n=>({p:+n.p,s:+n.s,l:+n.l||1,v:+n.v||0.8}));tr.clips=[{id:uid('c'),pat:p.id,start:0,len:bars}];return tr});
    return {v:SCHEMA,bpm:d.bpm,swing:d.swing||0,metro:!!d.metro,quant:d.quant!=null?d.quant:1,master:d.master!=null?d.master:0.8,returns:defaultReturns(),masterFx:[],loop:{on:true,start:0,end:bars},markers:[],scale:{root:0,type:'off'},tracks:d.tracks,zoom:40,migratedFrom:1}}
  return d}
/* ───────── load / snapshot ───────── */
const missingReport=[];
function loadProject(raw){
  const d=migrate(JSON.parse(JSON.stringify(raw)));const warn=[];
  S.tracks.forEach(disposeTrack);S.masterFx.forEach(f=>f._inst&&f._inst.dispose&&f._inst.dispose());
  S.bpm=clamp(+d.bpm||112,40,240);S.swing=clamp(+d.swing||0,0,0.5);S.metro=!!d.metro;S.quant=d.quant!=null?+d.quant:1;S.master=d.master!=null?clamp(+d.master,0,1):0.8;S.ccMap=d.ccMap||{};S.zoom=clamp(+d.zoom||40,10,160);
  const r=defaultReturns();if(d.returns){['rev','dly'].forEach(k=>{const x=d.returns[k];if(x&&FX[x.type]){r[k].params=sanitizeParams(FX[x.type].params,x.params).o;r[k].vol=clamp(x.vol==null?r[k].vol:+x.vol,0,1);r[k].mute=!!x.mute}})}S.returns=r;
  S.masterFx=sanitizeFxList(d.masterFx,warn);S.loop=Object.assign({on:true,start:0,end:4},d.loop||{});S.markers=(d.markers||[]).filter(m=>m&&isFinite(m.bar)).map(m=>({id:m.id||uid('k'),bar:+m.bar,name:String(m.name||'Section').slice(0,40)}));
  S.scale=Object.assign({root:0,type:'off'},d.scale||{});
  S.tracks=d.tracks.map(t=>{if(!ENG[t.engine]){warn.push('Track "'+t.name+'" uses an engine this version does not have ('+t.engine+'). It was replaced by an empty Analog Synth track so your notes are kept; reopen the project in a newer Soundcraft to restore the sound.');t.engine='va';t.params={}}
    const E=ENG[t.engine],tr=Object.assign(newTrack({engine:t.engine,params:{}},{}),t);tr.params=sanitizeParams(E.params,Object.assign(defaultsOf(E.params),t.params)).o;tr.mod=sanitizeMod(t.mod);tr.fx=sanitizeFxList(t.fx,warn);
    tr.fx.forEach((f,i)=>{if(t.fx[i]&&t.fx[i].id)f.id=t.fx[i].id});tr.mod.routes.forEach(rt=>{if(rt.dst.startsWith('fx:')){/* fx ids preserved above */}});
    tr.patterns=(t.patterns||[]).map(p=>({id:p.id||uid('p'),name:String(p.name||'A').slice(0,30),len:clamp(+p.len||4,0.25,64),notes:(p.notes||[]).map(n=>({p:+n.p,s:+n.s,l:+n.l||1,v:clamp(+n.v||0.8,0.01,1),pr:n.pr!=null?clamp(+n.pr,0,1):undefined})).filter(n=>isFinite(n.p)&&isFinite(n.s))}));
    if(!tr.patterns.length)tr.patterns=[newPattern('A',4)];tr.clips=(t.clips||[]).filter(c=>tr.patterns.some(p=>p.id===c.pat)).map(c=>({id:c.id||uid('c'),pat:c.pat,start:Math.max(0,+c.start||0),len:Math.max(0.25,+c.len||1)}));
    if(!tr.patterns.some(p=>p.id===tr.curPat))tr.curPat=tr.patterns[0].id;if(tr.engine==='sampler')tr.zones=(t.zones||[]).map(sanitizeZone);if(tr.engine==='fusion')tr.layerOver=t.layerOver||[{},{},{},{}];
    tr.input=Object.assign({group:null,lo:0,hi:127,vlo:0,vhi:127,ch:0,tr:0},t.input||{});tr.poly=clamp(+t.poly||16,1,64);return tr});
  if(!S.tracks.length)S.tracks.push(newTrack('syn-poly'));S.sel=0;S.octave=selTrack().oct||4;S.selClip=null;
  if(ctx){GR.revFx&&rebuildReturns();S.tracks.forEach(buildTrack);rebuildMasterChain();applyMix()}
  missingReport.length=0;missingReport.push(...warn);
  loadProjectSamples().then(m=>{if(m.length){missingReport.push(m.length+' sample'+(m.length>1?'s are':' is')+' missing on this device: '+m.slice(0,4).join(', ')+'. Their zones stay silent until you re-import them.');showMissing()}});
  if(warn.length)setTimeout(showMissing,50);
  return warn}
function rebuildReturns(){if(!ctx)return;[['rev','revIn','revFx','revRet'],['dly','dlyIn','dlyFx','dlyRet']].forEach(([k,i,f,r])=>{try{GR[i].disconnect();GR[f].output.disconnect()}catch(e){}GR[f]=makeFx(S.returns[k]);GR[i].connect(GR[f].input);GR[f].output.connect(GR[r])})}
function snapshot(){return JSON.parse(JSON.stringify({v:SCHEMA,bpm:S.bpm,swing:S.swing,metro:S.metro,quant:S.quant,master:S.master,returns:S.returns,masterFx:S.masterFx,loop:S.loop,markers:S.markers,scale:S.scale,ccMap:S.ccMap,zoom:S.zoom,tracks:S.tracks},(k,v)=>k[0]==='_'?undefined:v))}
/* ───────── library storage ───────── */
const LIB_KEY='soundcraft.library.v2',CUR_KEY='soundcraft.current.v2',SESSION_KEY='soundcraft.session.open';
let LIB={},curId=null,storageOk=true,dirty=false;
function writeLib(){storageOk=store(LIB_KEY,JSON.stringify(LIB))&&store(CUR_KEY,curId||'');return storageOk}
let saveTimer=0;
function markDirty(){dirty=true;setSaveState('dirty');clearTimeout(saveTimer);saveTimer=setTimeout(saveNow,700)}
function saveNow(){clearTimeout(saveTimer);const e=LIB[curId];if(!e)return false;const data=snapshot();e.data=data;e.edited=Date.now();e.bpm=S.bpm;
  const lastB=e.backups&&e.backups[0];if(!lastB||Date.now()-lastB.date>120000){e.backups=[{date:Date.now(),data}].concat(e.backups||[]).slice(0,5);if(window.native)window.native.writeBackup(curId,e.title,JSON.stringify(data)).catch(err=>errLog.push('backup: '+err.message))}
  let ok=writeLib();if(!ok){e.backups=(e.backups||[]).slice(0,1);ok=writeLib()}dirty=!ok;setSaveState(ok?'saved':'err');return ok}
function setSaveState(s){const el=$('#saveState');if(!el)return;el.classList.toggle('err',s==='err');el.textContent=s==='dirty'?'Unsaved changes…':s==='err'?'Not saved: storage blocked or full':'Saved'}
function newEntry(title,data){const id=uid('p'),now=Date.now();LIB[id]={id,title,color:TRACK_COLORS[Object.keys(LIB).length%TRACK_COLORS.length],created:now,edited:now,bpm:data.bpm,data,snapshots:[],backups:[]};return id}
function openProject(id){const e=LIB[id];if(!e)return false;stop();
  try{loadProject(e.data)}catch(err){toast('That cassette could not be opened ('+err.message+'). It was left untouched.');return false}
  curId=id;writeLib();undoStack.length=0;redoStack.length=0;$('#projTitle').value=e.title;setSaveState(storageOk?'saved':'err');renderAll();return true}
function migrateV1Library(){try{const old=JSON.parse(read('soundcraft.library.v1')||'null');if(!old)return 0;let n=0;
  Object.values(old).forEach(e=>{if(!e||!e.data||Object.values(LIB).some(x=>x.fromV1===e.id))return;try{const d=migrate(e.data);const id=newEntry(e.title||'Imported cassette',d);LIB[id].fromV1=e.id;LIB[id].color=e.color||LIB[id].color;n++}catch(err){errLog.push('v1 migrate: '+err.message)}});return n}catch(e){return 0}}
/* ───────── snapshots ───────── */
function takeSnapshot(name,desc){saveNow();const e=LIB[curId];e.snapshots=e.snapshots||[];e.snapshots.unshift({id:uid('n'),name:String(name||'Snapshot').slice(0,60),desc:String(desc||'').slice(0,300),date:Date.now(),data:snapshot()});e.snapshots=e.snapshots.slice(0,30);return writeLib()}
function restoreSnapshot(snapId){const e=LIB[curId],sn=(e.snapshots||[]).find(x=>x.id===snapId)||(e.backups||[]).find(x=>'b'+x.date===snapId);if(!sn)return false;
  takeSnapshot('Before restoring "'+(sn.name||new Date(sn.date).toLocaleString())+'"','Automatic safety snapshot');pushUndo();loadProject(sn.data);saveNow();renderAll();return true}
function compareSnapshot(data){const a=snapshot(),b=data,lines=[];if(a.bpm!==b.bpm)lines.push('Tempo '+b.bpm+' → '+a.bpm+' BPM now');
  const ids=new Set(a.tracks.map(t=>t.id)),bid=new Set(b.tracks.map(t=>t.id));b.tracks.filter(t=>!ids.has(t.id)).forEach(t=>lines.push('Track "'+t.name+'" exists only in the snapshot'));a.tracks.filter(t=>!bid.has(t.id)).forEach(t=>lines.push('Track "'+t.name+'" was added since'));
  a.tracks.forEach(t=>{const o=b.tracks.find(x=>x.id===t.id);if(!o)return;const nn=x=>x.patterns.reduce((s,p)=>s+p.notes.length,0);if(nn(t)!==nn(o))lines.push(t.name+': '+nn(o)+' → '+nn(t)+' notes');if(t.clips.length!==o.clips.length)lines.push(t.name+': '+o.clips.length+' → '+t.clips.length+' clips');
    if(t.engine!==o.engine)lines.push(t.name+': instrument changed');if(JSON.stringify(t.params)!==JSON.stringify(o.params))lines.push(t.name+': sound settings differ');if(t.fx.length!==o.fx.length)lines.push(t.name+': '+o.fx.length+' → '+t.fx.length+' effects');if(Math.abs(t.vol-o.vol)>0.01)lines.push(t.name+': volume changed')});
  return lines.length?lines:['No differences in tempo, tracks, notes, clips, sounds or effects.']}
/* ───────── undo (whole-project snapshots) ───────── */
const undoStack=[],redoStack=[];
function pushUndo(){undoStack.push(JSON.stringify(snapshot()));if(undoStack.length>60)undoStack.shift();redoStack.length=0}
function undoRedo(from,to,label){const s=from.pop();if(!s){toast('Nothing to '+label+'.');return}to.push(JSON.stringify(snapshot()));const sel=S.sel,view=S.view;loadProject(JSON.parse(s));S.sel=Math.min(sel,S.tracks.length-1);markDirty();renderAll()}
const undo=()=>undoRedo(undoStack,redoStack,'undo'),redo=()=>undoRedo(redoStack,undoStack,'redo');
/* ───────── building from presets ───────── */
function addTrackFrom(src,o={}){pushUndo();const tr=newTrack(src,o);S.tracks.push(tr);if(ctx)applyMix();markDirty();return tr}
function loadEnsemble(ens){pushUndo();const group=uid('g'),made=[],miss=[];
  ens.members.forEach((m,i)=>{let tr;if(m.track){tr=newTrack(m.track,{name:m.track.name});tr.fx=sanitizeFxList(m.track.fx)}else{const pre=presetById(m.preset);if(!pre){miss.push(m.preset);return}tr=newTrack(pre)}
    tr.name=(m.name||tr.name);tr.vol=m.vol!=null?m.vol:tr.vol;tr.pan=m.pan||0;tr.input={group,lo:m.lo||0,hi:m.hi==null?127:m.hi,vlo:m.vlo||0,vhi:m.vhi==null?127:m.vhi,ch:m.ch||0,tr:m.tr||0,ens:ens.name};
    (m.fx||[]).forEach(([type,params])=>{if(FX[type])tr.fx.push(newFx(type,params))});S.tracks.push(tr);made.push(tr)});
  if(ctx)applyMix();markDirty();return {made,miss,group}}
function exportProjectFile(withSamples){saveNow();const e=LIB[curId],data=snapshot(),env=envelope('project',e.title,data,{type:'project'});
  const ids=new Set();data.tracks.forEach(t=>(t.zones||[]).forEach(z=>z.sample&&ids.add(z.sample)));
  const finish=samples=>{if(samples)env.samples=samples;env.dependencies={samples:[...ids].map(id=>({id,name:(SAMPLE_META.get(id)||{}).name||id,embedded:!!(samples&&samples[id])}))};return JSON.stringify(env)};
  if(!withSamples||!ids.size)return Promise.resolve(finish(null));
  return Promise.all([...ids].map(id=>getSample(id).then(r=>[id,r]).catch(()=>[id,null]))).then(rs=>{const out={};rs.forEach(([id,r])=>{if(r)out[id]={name:r.name,b64:b64(r.bytes)}});return finish(out)})}
function b64(buf){const u=new Uint8Array(buf);let s='';for(let i=0;i<u.length;i+=0x8000)s+=String.fromCharCode.apply(null,u.subarray(i,i+0x8000));return btoa(s)}
function unb64(s){const b=atob(s),u=new Uint8Array(b.length);for(let i=0;i<b.length;i++)u[i]=b.charCodeAt(i);return u.buffer}
async function importEmbeddedSamples(samples){let n=0;for(const id of Object.keys(samples||{})){if(!/^smp_\w+$/.test(id))continue;const e=samples[id];if(!e||typeof e.b64!=='string')continue;
  try{const bytes=unb64(e.b64);const buf=await decoderCtx().decodeAudioData(bytes.slice(0));await putSample({id,name:String(e.name||id).slice(0,120),type:'audio',size:bytes.byteLength,added:Date.now(),duration:buf.duration,bytes});SAMPLE_BUF.set(id,buf);SAMPLE_MISSING.delete(id);n++}catch(err){errLog.push('embedded sample '+id+': '+err.message)}}return n}
function showMissing(){if(!missingReport.length)return;const m=$('#missingModal');if(!m)return;$('#missingList').innerHTML=missingReport.map(x=>'<li>'+esc(x)+'</li>').join('');m.hidden=false}
