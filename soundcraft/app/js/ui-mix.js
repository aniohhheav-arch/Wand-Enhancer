'use strict';
/* Mixer: strips, insert rack, effect editor with interactive toys, chains, metering, reference A/B. */
const FXCHAINS=[
  {id:'fc-lofi',name:'Lo-fi tape',desc:'Soft saturation, a dark filter, 12-bit crush and chorus.',fx:[['drive',{amount:0.3,tone:7000}],['filter',{type:'lowpass',cutoff:6000,q:0.8}],['crusher',{bits:12,down:2,mix:0.6}],['chorus',{mix:0.3}]]},
  {id:'fc-room',name:'Big room',desc:'Low cut and a large reverb.',fx:[['eq',{hpf:120}],['reverb',{size:5,mix:0.4}]]},
  {id:'fc-drums',name:'Punchy drums',desc:'Transient boost, compression and a low lift.',fx:[['transient',{attack:0.5}],['comp',{threshold:-20,ratio:4,attack:0.005}],['eq',{lowG:3,lowF:90}]]},
  {id:'fc-dream',name:'Dream space',desc:'The Dream machine at three quarters.',fx:[['dream',{amount:0.75,motion:0.6}]]},
  {id:'fc-glue',name:'Master glue',desc:'Gentle EQ, bus compression and a peak limiter. A starting point, not a finished master.',fx:[['eq',{hpf:30}],['comp',{threshold:-14,ratio:2,attack:0.02,makeup:2}],['limiter',{ceiling:-1}]]},
  {id:'fc-widepad',name:'Wide pad',desc:'Chorus, stereo widening and reverb.',fx:[['chorus',{mix:0.4}],['width',{width:1.6}],['reverb',{size:4,mix:0.35}]]},
  {id:'fc-wobble',name:'Synced wobble filter',desc:'A tempo-synced filter LFO for bass.',fx:[['filter',{cutoff:600,q:6,sync:'1/8',depth:2.5}],['drive',{amount:0.4}]]}
];
const MIX={sel:{kind:'track',id:null},fx:null,ref:null};
function pseudoTrack(kind){if(kind==='@master')return {id:'@master',name:'Master',fx:S.masterFx,mod:newModState(),params:{},engine:'va',_pseudo:'master'};
  if(kind==='@rev')return {id:'@rev',name:'Reverb return',fx:[S.returns.rev],mod:newModState(),params:{},engine:'va',_pseudo:'rev'};if(kind==='@dly')return {id:'@dly',name:'Delay return',fx:[S.returns.dly],mod:newModState(),params:{},engine:'va',_pseudo:'dly'};return null}
function stripTarget(){const s=MIX.sel;if(s.kind==='master')return pseudoTrack('@master');if(s.kind==='ret')return pseudoTrack('@'+s.key);return trById(s.id)||selTrack()}
function renderMix(){if(S.view!=='mix')return;if(!MIX.sel.id&&MIX.sel.kind==='track')MIX.sel.id=selTrack().id;renderStrips();renderFxEditor();renderChainSel()}
function slotsHTML(list,owner){return '<div class="slots">'+list.map((f,i)=>'<div class="slot'+(f.on?'':' off')+(MIX.fx===f.id?' cur':'')+'" data-fx="'+f.id+'" data-owner="'+owner+'"><button data-a="on" aria-label="'+(f.on?'Bypass':'Enable')+' '+FX[f.type].name+'" title="Bypass">'+(f.on?'●':'○')+'</button><span>'+esc(FX[f.type].name)+'</span><button data-a="up" aria-label="Move up">‹</button><button data-a="dn" aria-label="Move down">›</button><button data-a="rm" aria-label="Remove">✕</button></div>').join('')+
  '<select data-addfx="'+owner+'" aria-label="Add insert effect"><option value="">+ Insert…</option>'+FX_ORDER.map(k=>'<option value="'+k+'">'+FX[k].name+'</option>').join('')+'</select></div>'}
function renderStrips(){const box=$('#strips');if(!box)return;
  box.innerHTML=S.tracks.map(t=>'<div class="strip'+(MIX.sel.kind==='track'&&MIX.sel.id===t.id?' sel':'')+'" data-strip="track:'+t.id+'" style="--c:'+t.color+'"><h4 title="'+esc(t.name)+'">'+esc(t.name)+'</h4>'+slotsHTML(t.fx,t.id)+
    '<div class="row">Rev<input type="range" min="0" max="1" step="0.01" value="'+t.rev+'" data-k="rev" aria-label="'+esc(t.name)+' reverb send"></div><div class="row">Dly<input type="range" min="0" max="1" step="0.01" value="'+t.dly+'" data-k="dly" aria-label="'+esc(t.name)+' delay send"></div>'+
    '<div class="row">Pan<input type="range" min="-1" max="1" step="0.01" value="'+t.pan+'" data-k="pan" aria-label="'+esc(t.name)+' pan"></div>'+
    '<div class="fader-row"><input class="fader" type="range" min="0" max="1" step="0.01" value="'+t.vol+'" data-k="vol" aria-label="'+esc(t.name)+' volume"><canvas class="vmeter" data-meter="'+t.id+'"></canvas></div>'+
    '<div class="row"><button class="ms" data-k="mute" aria-pressed="'+t.mute+'">M</button><button class="ms" data-k="solo" aria-pressed="'+t.solo+'">S</button><span class="kval" data-db="'+t.id+'">−∞</span></div></div>').join('')+
  ['rev','dly'].map(k=>{const r=S.returns[k];return '<div class="strip'+(MIX.sel.kind==='ret'&&MIX.sel.key===k?' sel':'')+'" data-strip="ret:'+k+'" style="--c:var(--teal)"><h4>'+(k==='rev'?'Reverb return':'Delay return')+'</h4><div class="slots"><div class="slot'+(MIX.fx===r.id?' cur':'')+'" data-fx="'+r.id+'" data-owner="@'+k+'"><span>'+FX[r.type].name+'</span></div><span class="hint">Edit the return effect, then send to it from any track.</span></div>'+
    '<div class="fader-row"><input class="fader" type="range" min="0" max="1" step="0.01" value="'+r.vol+'" data-ret="'+k+'" aria-label="'+(k==='rev'?'Reverb':'Delay')+' return level"><canvas class="vmeter" data-meter="@'+k+'"></canvas></div><div class="row"><button class="ms" data-retmute="'+k+'" aria-pressed="'+r.mute+'">M</button></div></div>'}).join('')+
  '<div class="strip master'+(MIX.sel.kind==='master'?' sel':'')+'" data-strip="master" style="--c:var(--accent)"><h4>Master</h4>'+slotsHTML(S.masterFx,'@master')+'<div class="fader-row"><input class="fader" type="range" min="0" max="1" step="0.01" value="'+S.master+'" data-master="1" aria-label="Master volume"><canvas class="vmeter" data-meter="@master"></canvas></div><div class="row"><span class="hint">Safety limiter at −1 dB after master inserts</span></div></div>'}
function fxListOf(owner){if(owner==='@master')return S.masterFx;return (trById(owner)||{}).fx}
function rebuildOwner(owner){if(owner==='@master')rebuildMasterChain();else{const t=trById(owner);if(t)rebuildTrackChain(t)}}
$('#strips').addEventListener('click',e=>{const st=e.target.closest('[data-strip]');if(st){const [k,id]=st.dataset.strip.split(':');MIX.sel=k==='track'?{kind:'track',id}:k==='ret'?{kind:'ret',key:id}:{kind:'master'};if(k==='track'){const i=S.tracks.findIndex(t=>t.id===id);if(i>=0&&i!==S.sel){S.sel=i;S.octave=selTrack().oct||4;renderKeyboard()}}}
  const slot=e.target.closest('.slot');
  if(slot){const owner=slot.dataset.owner,a=e.target.dataset.a;MIX.fx=slot.dataset.fx;
    if(a&&owner!=='@rev'&&owner!=='@dly'){const list=fxListOf(owner),i=list.findIndex(f=>f.id===slot.dataset.fx);pushUndo();
      if(a==='on')list[i].on=!list[i].on;if(a==='rm'){const f=list.splice(i,1)[0];if(f._inst&&f._inst.dispose)f._inst.dispose();if(MIX.fx===f.id)MIX.fx=null}
      if(a==='up'&&i>0)list.splice(i-1,0,list.splice(i,1)[0]);if(a==='dn'&&i<list.length-1)list.splice(i+1,0,list.splice(i,1)[0]);rebuildOwner(owner);markDirty()}}
  const ms=e.target.dataset.k;if(ms==='mute'||ms==='solo'){const t=trById(e.target.closest('[data-strip]').dataset.strip.split(':')[1]);pushUndo();t[ms]=!t[ms];applyMix();markDirty()}
  if(e.target.dataset.retmute){const r=S.returns[e.target.dataset.retmute];pushUndo();r.mute=!r.mute;applyMix();markDirty()}
  renderStrips();renderFxEditor()});
$('#strips').addEventListener('input',e=>{const el=e.target;if(el.dataset.master){S.master=+el.value;applyMix();$('#masterVol').value=S.master;markDirty();return}
  if(el.dataset.ret){S.returns[el.dataset.ret].vol=+el.value;applyMix();markDirty();return}const k=el.dataset.k;if(!['vol','pan','rev','dly'].includes(k))return;
  const t=trById(el.closest('[data-strip]').dataset.strip.split(':')[1]);setParam(t,'trk:'+k,+el.value)});
$('#strips').addEventListener('change',e=>{const s=e.target.closest('select[data-addfx]');if(!s||!s.value)return;const owner=s.dataset.addfx,list=fxListOf(owner);pushUndo();const f=newFx(s.value);list.push(f);MIX.fx=f.id;rebuildOwner(owner);markDirty();renderStrips();renderFxEditor();quest('fx')});
function currentFx(){if(!MIX.fx)return null;for(const o of ['@master','@rev','@dly',...S.tracks.map(t=>t.id)]){const t=trById(o);const f=t&&t.fx.find(x=>x.id===MIX.fx);if(f)return {t,f}}return null}
function renderFxEditor(){const box=$('#fxEditor');if(!box)return;const cur=currentFx();
  if(!cur){box.innerHTML='<h2 style="margin:0;font-family:var(--f-display);font-size:16px;font-weight:500">Effect editor</h2><p class="hint" style="margin:0">Click an insert in any strip to edit it. Every control here changes the real effect, saves with the project and can be a modulation destination in the Lab.</p>';return}
  const {t,f}=cur,d=FX[f.type],inst=f._inst;
  box.innerHTML='<div class="row-actions"><h2 style="margin:0;font-family:var(--f-display);font-size:18px;font-weight:500">'+esc(d.name)+'</h2><span class="hint">on '+esc(t.name)+'</span><span class="spacer"></span>'+(t._pseudo==='rev'||t._pseudo==='dly'?'':'<button class="btn" id="fxBypass" aria-pressed="'+(!f.on)+'">Bypass</button>')+'</div><p class="hint" style="margin:0">'+esc(d.desc)+(inst&&inst.unavailable?' This effect needs AudioWorklet support, which this browser does not provide here, so it is passing audio through unchanged.':'')+'</p>'+
    (d.xy?'<div class="row-actions" style="align-items:flex-start"><div class="xy" id="xyPad" role="slider" tabindex="0" aria-label="'+esc(d.toy)+': horizontal '+esc(d.params.find(p=>p.k===d.xy[0]).label)+', vertical '+esc(d.params.find(p=>p.k===d.xy[1]).label)+'"><small style="left:8px;bottom:6px">'+esc(d.params.find(p=>p.k===d.xy[0]).label)+' →</small><small style="left:8px;top:6px">↑ '+esc(d.params.find(p=>p.k===d.xy[1]).label)+'</small><small style="right:8px;top:6px">'+esc(d.toy)+'</small><i id="xyDot"></i></div><div class="knobs">'+d.params.map(p=>ctlHTML(t,'fx:'+f.id+':'+p.k)).join('')+'</div></div>'
      :'<div class="knobs">'+d.params.map(p=>ctlHTML(t,'fx:'+f.id+':'+p.k)).join('')+'</div>')+(inst&&inst.meter?'<div class="hint">Gain reduction <b id="fxGR" style="font-family:var(--f-lcd);font-size:17px;color:var(--fg)">0.0 dB</b></div>':'')+
    (inst&&inst.response?'<canvas class="viz" id="fxResp"></canvas>':'');
  const bp=$('#fxBypass');if(bp)bp.onclick=()=>{pushUndo();f.on=!f.on;rebuildOwner(t.id);markDirty();renderStrips();renderFxEditor()};
  if(d.xy)placeXY(t,f);if(inst&&inst.response)drawResponse($('#fxResp'),inst.response)}
function placeXY(t,f){const d=FX[f.type],px=d.params.find(p=>p.k===d.xy[0]),py=d.params.find(p=>p.k===d.xy[1]),dot=$('#xyDot');if(!dot)return;dot.style.left=toNorm(px,f.params[px.k])*100+'%';dot.style.top=(1-toNorm(py,f.params[py.k]))*100+'%'}
document.addEventListener('pointerdown',e=>{const pad=e.target.closest&&e.target.closest('#xyPad');if(!pad)return;const cur=currentFx();if(!cur)return;pushUndo();pad.setPointerCapture(e.pointerId);
  const mv=ev=>{const b=pad.getBoundingClientRect(),x=clamp((ev.clientX-b.left)/b.width,0,1),y=clamp(1-(ev.clientY-b.top)/b.height,0,1),d=FX[cur.f.type];['x','y'].forEach((ax,i)=>{const p=d.params.find(q=>q.k===d.xy[i]),dst='fx:'+cur.f.id+':'+p.k;setParam(cur.t,dst,fromNorm(p,ax==='x'?x:y));syncControl(cur.t,dst)});placeXY(cur.t,cur.f)};
  mv(e);const up=()=>{pad.removeEventListener('pointermove',mv);pad.removeEventListener('pointerup',up);markDirty()};pad.addEventListener('pointermove',mv);pad.addEventListener('pointerup',up)});
function drawResponse(cv,filters){if(!cv)return;const dpr=window.devicePixelRatio||1,w=cv.clientWidth,h=cv.clientHeight;cv.width=w*dpr;cv.height=h*dpr;const c=cv.getContext('2d');c.scale(dpr,dpr);
  const n=200,fr=new Float32Array(n),mag=new Float32Array(n),ph=new Float32Array(n),tot=new Float32Array(n).fill(0);for(let i=0;i<n;i++)fr[i]=20*Math.pow(1000,i/(n-1));
  filters.forEach(f=>{f.getFrequencyResponse(fr,mag,ph);for(let i=0;i<n;i++)tot[i]+=gainToDb(mag[i])});c.strokeStyle='rgba(172,164,207,.2)';c.beginPath();c.moveTo(0,h/2);c.lineTo(w,h/2);c.stroke();
  c.strokeStyle='#ffb454';c.lineWidth=2;c.beginPath();for(let i=0;i<n;i++){const x=i/(n-1)*w,y=h/2-clamp(tot[i],-24,24)/24*h/2;i?c.lineTo(x,y):c.moveTo(x,y)}c.stroke();c.fillStyle='rgba(244,234,214,.5)';c.font='10px IBM Plex Sans,sans-serif';c.fillText('20 Hz',4,h-4);c.fillText('20 kHz',w-38,h-4)}
function renderChainSel(){$('#chainSel').innerHTML='<optgroup label="Factory">'+FXCHAINS.map(c=>'<option value="'+c.id+'">'+esc(c.name)+'</option>').join('')+'</optgroup>'+(USER.fxchains.length?'<optgroup label="Mine">'+USER.fxchains.map(c=>'<option value="'+c.id+'">'+esc(c.name)+'</option>').join('')+'</optgroup>':'')}
function chainById(id){const f=FXCHAINS.find(c=>c.id===id);if(f)return f.fx.map(([type,params])=>({type,params}));const u=USER.fxchains.find(c=>c.id===id);return u?u.data.fx:null}
function loadChainOnto(owner,fxList){const list=fxListOf(owner);if(!list){toast('Pick a track or the master strip first.');return}pushUndo();sanitizeFxList(fxList).forEach(f=>list.push(f));rebuildOwner(owner);markDirty();renderStrips();toast('Loaded '+fxList.length+' effects.')}
$('#chainLoad').onclick=()=>{const fx=chainById($('#chainSel').value);if(!fx)return;const s=MIX.sel;if(s.kind==='ret'){toast('Return strips have one fixed effect. Pick a track or the master.');return}loadChainOnto(s.kind==='master'?'@master':(s.id||selTrack().id),fx)};
$('#chainSave').onclick=()=>{const s=MIX.sel,owner=s.kind==='master'?'@master':(s.id||selTrack().id),list=fxListOf(owner);if(!list||!list.length){toast('That strip has no inserts to save.');return}
  const name=(s.kind==='master'?'Master':trById(owner).name)+' chain',r=addUserItem('fxchain',name,{fx:list.map(f=>({type:f.type,on:f.on,params:f.params}))});toast(r.dup?'That chain is already saved as "'+r.dup.name+'".':r.item?'Saved effect chain "'+name+'".':'Could not save the chain.');renderChainSel()};
/* meters (called from the frame loop) */
const meterHold={};
function drawMeters(now){$$('canvas.vmeter').forEach(cv=>{const id=cv.dataset.meter;let v=0;if(id==='@master')v=Math.max(A.peak[0],A.peak[1]);else if(id==='@rev'||id==='@dly'){const an=GR.retAn&&GR.retAn[id.slice(1)];if(an){an.getFloatTimeDomainData(pbuf);for(let i=0;i<pbuf.length;i++)v=Math.max(v,Math.abs(pbuf[i]))}}else{const t=realTrack(id);if(t)v=trackLevel(t)}
    const h=cv.clientHeight,w=cv.clientWidth;if(cv.width!==w){cv.width=w;cv.height=h}const c=cv.getContext('2d');c.clearRect(0,0,w,h);const db=gainToDb(v),y=h*(1-clamp((db+60)/60,0,1));
    const g=c.createLinearGradient(0,h,0,0);g.addColorStop(0,'#8fcaa2');g.addColorStop(0.75,'#ffb454');g.addColorStop(1,'#ef7a85');c.fillStyle=g;c.fillRect(0,y,w,h-y);
    if(v>=0.999)meterHold[id]=now;if(now-(meterHold[id]||0)<1500){c.fillStyle='#ef7a85';c.fillRect(0,0,w,4)}const lbl=$('[data-db="'+id+'"]');if(lbl)lbl.textContent=v>0.00001?db.toFixed(1):'−∞'});
  const fmtL=x=>isFinite(x)?x.toFixed(1)+' LUFS':'—';const pk=$('#mPeak');if(pk){pk.textContent=A.peakHold.map(x=>x>-100?x.toFixed(1):'−∞').join(' / ')+' dBFS';$('#mLufsM').textContent=fmtL(A.lufsM);$('#mLufsS').textContent=fmtL(A.lufsS);$('#mLufsI').textContent=fmtL(A.lufsI);$('#mClip').classList.toggle('clip',now-A.clipAt<1500)}
  const gr=$('#fxGR');if(gr){const cur=currentFx();if(cur&&cur.f._inst&&cur.f._inst.meter)gr.textContent=cur.f._inst.meter().toFixed(1)+' dB'}}
$('#lufsReset').onclick=()=>{resetIntegrated();toast('Integrated loudness reset.')};
/* reference track: plays on its own output, never into the project */
$('#refLoad').onclick=()=>$('#refFile').click();
$('#refFile').onchange=async e=>{const f=e.target.files[0];e.target.value='';if(!f)return;if(!ensureAudio())return;
  try{const buf=await ctx.decodeAudioData(await f.arrayBuffer());const oc=new OfflineAudioContext(2,buf.length,buf.sampleRate),s=oc.createBufferSource();s.buffer=buf;const hs=oc.createBiquadFilter();hs.type='highshelf';hs.frequency.value=1500;hs.gain.value=4;const hp=oc.createBiquadFilter();hp.type='highpass';hp.frequency.value=38;hp.Q.value=0.5;s.connect(hs).connect(hp).connect(oc.destination);s.start();
    const k=await oc.startRendering();let ms=0;for(let c=0;c<k.numberOfChannels;c++){const d=k.getChannelData(c);let q=0;for(let i=0;i<d.length;i++)q+=d[i]*d[i];ms+=q/d.length}
    MIX.ref={buf,name:f.name,lufs:-0.691+10*Math.log10(Math.max(1e-12,ms))};$('#refAB').disabled=false;$('#refInfo').textContent=f.name+' · '+MIX.ref.lufs.toFixed(1)+' LUFS (whole file, ungated). Press "Hear reference" to switch.'}
  catch(err){toast('Could not read that file as audio: '+err.message)}};
$('#refAB').onclick=()=>{const r=MIX.ref;if(!r||!ensureAudio())return;const on=$('#refAB').getAttribute('aria-pressed')!=='true';$('#refAB').setAttribute('aria-pressed',on);
  if(on){const g=G(1);if($('#refMatch').checked&&isFinite(A.lufsI))g.gain.value=dbToGain(clamp(A.lufsI-r.lufs,-24,12));const s=ctx.createBufferSource();s.buffer=r.buf;s.loop=true;s.connect(g).connect(ctx.destination);s.start();r.node=s;r.g=g;GR.master.gain.setTargetAtTime(0,ctx.currentTime,0.02);$('#refAB').textContent='Hear my mix'}
  else{if(r.node){r.node.stop();r.g.disconnect()}applyMix();$('#refAB').textContent='Hear reference'}};
