'use strict';
/* Shared UI: registry-bound controls, context menu, views, keybed, toasts. */
let toastT=0;function toast(m){const t=$('#toast');if(!t)return;t.textContent=m;t.classList.add('show');clearTimeout(toastT);toastT=setTimeout(()=>t.classList.remove('show'),3400)}
function trById(id){if(id&&id[0]==='@')return pseudoTrack(id);return S.tracks.find(t=>t.id===id)}
const realTrack=id=>S.tracks.find(t=>t.id===id);
/* ───────── controls bound to the parameter registry ───────── */
function ctlHTML(tr,dst,o={}){
  const d=destDef(tr,dst);if(!d)return '';const p=d.p,v=getParamValue(tr,dst),label=o.label||p.label;
  if(p.type==='choice'||p.type==='bool'){const ch=p.type==='bool'?[[true,'On'],[false,'Off']]:p.choices;
    return '<label class="kchoice"><span>'+esc(label)+'</span><select data-dst="'+esc(dst)+'" data-tr="'+tr.id+'">'+ch.map(c=>'<option value="'+esc(JSON.stringify(c[0]))+'"'+(c[0]===v?' selected':'')+'>'+esc(c[1])+'</option>').join('')+(ch.some(c=>c[0]===v)?'':'<option value="'+esc(JSON.stringify(v))+'" selected>Missing: '+esc(v)+'</option>')+'</select></label>'}
  const n=toNorm(p,v),rs=routesFor(tr,dst).filter(r=>r.on),mm=macroMapsFor(tr,dst);
  let ring='';if(rs.length){const lo=clamp(n+Math.min(0,...rs.map(r=>r.amt)),0,1),hi=clamp(n+Math.max(0,...rs.map(r=>r.amt)),0,1),col=rs[0].color||'var(--pink)';
    ring='<i class="modring" style="background:conic-gradient(from -135deg,transparent '+(lo*270)+'deg,'+col+' '+(lo*270)+'deg '+(hi*270)+'deg,transparent '+(hi*270)+'deg)"></i><i class="moddot"></i>'}
  const title=label+(rs.length?' · modulated by '+rs.map(r=>(srcInfo(tr,r.src)||{name:r.src}).name).join(', '):'')+(mm.length?' · controlled by macro '+mm.map(x=>x.m.name).join(', '):'')+(d.live==='audio'?' · live':'');
  return '<div class="kwrap'+(o.big?' big':'')+'"><div class="knob'+(UI.selDst===dst&&UI.selTr===tr.id?' sel':'')+'" role="slider" tabindex="0" data-dst="'+esc(dst)+'" data-tr="'+tr.id+'" aria-label="'+esc(label)+'" aria-valuemin="0" aria-valuemax="100" aria-valuenow="'+pct(n)+'" aria-valuetext="'+esc(fmtParam(p,v))+'" title="'+esc(title)+'" style="--v:'+n+(o.color?';--c2:'+o.color:'')+'">'+ring+'</div><span class="klabel">'+esc(label)+(mm.length?' ⚙':'')+'</span><span class="kval">'+esc(fmtParam(p,v))+'</span></div>'}
const UI={selDst:null,selTr:null,drag:null,histTimer:0};
function syncControl(tr,dst){const d=destDef(tr,dst);if(!d)return;const v=getParamValue(tr,dst),n=toNorm(d.p,v);
  $$('.knob[data-tr="'+tr.id+'"]').forEach(k=>{if(k.dataset.dst!==dst)return;k.style.setProperty('--v',n);k.setAttribute('aria-valuenow',pct(n));k.setAttribute('aria-valuetext',fmtParam(d.p,v));const kv=k.parentNode.querySelector('.kval');if(kv)kv.textContent=fmtParam(d.p,v)});
  $$('select[data-tr="'+tr.id+'"]').forEach(s=>{if(s.dataset.dst===dst)s.value=JSON.stringify(v)})}
function ctlChanged(tr,dst){syncControl(tr,dst);if(dst.startsWith('trk:')){renderArrangeHeads();if(S.view==='mix')renderStrips()}labHistoryMark(tr)}
document.addEventListener('pointerdown',e=>{const k=e.target.closest&&e.target.closest('.knob[data-dst]');if(!k||e.button!==0)return;const tr=trById(k.dataset.tr);if(!tr)return;
  if(learnArmed){startLearn(tr.id,{dst:k.dataset.dst});learnArmed=false;return}
  const d=destDef(tr,k.dataset.dst);pushUndo();UI.drag={tr,dst:k.dataset.dst,y:e.clientY,n:toNorm(d.p,getParamValue(tr,k.dataset.dst)),moved:false};k.setPointerCapture(e.pointerId);e.preventDefault();selectControl(tr,k.dataset.dst)});
document.addEventListener('pointermove',e=>{const g=UI.drag;if(!g)return;const d=destDef(g.tr,g.dst);const n=clamp(g.n+(g.y-e.clientY)/(e.shiftKey?600:170),0,1);g.moved=true;setParam(g.tr,g.dst,fromNorm(d.p,n));ctlChanged(g.tr,g.dst)});
document.addEventListener('pointerup',()=>{if(UI.drag){if(!UI.drag.moved)undoStack.pop();UI.drag=null}});
document.addEventListener('dblclick',e=>{const k=e.target.closest&&e.target.closest('.knob[data-dst]');if(!k)return;const tr=trById(k.dataset.tr),d=destDef(tr,k.dataset.dst);pushUndo();setParam(tr,k.dataset.dst,d.p.def);ctlChanged(tr,k.dataset.dst)});
document.addEventListener('wheel',e=>{const k=e.target.closest&&e.target.closest('.knob[data-dst]');if(!k)return;e.preventDefault();const tr=trById(k.dataset.tr),d=destDef(tr,k.dataset.dst);setParam(tr,k.dataset.dst,fromNorm(d.p,toNorm(d.p,getParamValue(tr,k.dataset.dst))-Math.sign(e.deltaY)*0.02));ctlChanged(tr,k.dataset.dst)},{passive:false});
document.addEventListener('keydown',e=>{const k=e.target.closest&&e.target.closest('.knob[data-dst]');if(!k)return;const dir={ArrowUp:1,ArrowRight:1,ArrowDown:-1,ArrowLeft:-1}[e.key];if(!dir)return;e.preventDefault();e.stopPropagation();
  const tr=trById(k.dataset.tr),d=destDef(tr,k.dataset.dst);setParam(tr,k.dataset.dst,fromNorm(d.p,toNorm(d.p,getParamValue(tr,k.dataset.dst))+dir*(e.shiftKey?0.1:0.02)));ctlChanged(tr,k.dataset.dst)},true);
document.addEventListener('change',e=>{const s=e.target.closest&&e.target.closest('select[data-dst]');if(!s)return;const tr=trById(s.dataset.tr);if(!tr)return;pushUndo();setParam(tr,s.dataset.dst,JSON.parse(s.value));ctlChanged(tr,s.dataset.dst);if(s.dataset.rerender)renderLab()});
/* drag a modulation source onto a knob */
document.addEventListener('dragover',e=>{const k=e.target.closest&&e.target.closest('.knob[data-dst]');if(k&&UI.dragSrc){e.preventDefault();$$('.knob.drop').forEach(x=>x!==k&&x.classList.remove('drop'));k.classList.add('drop')}});
document.addEventListener('dragleave',e=>{const k=e.target.closest&&e.target.closest('.knob.drop');if(k)k.classList.remove('drop')});
document.addEventListener('drop',e=>{const k=e.target.closest&&e.target.closest('.knob[data-dst]');$$('.knob.drop').forEach(x=>x.classList.remove('drop'));if(!k||!UI.dragSrc)return;e.preventDefault();addRoute(trById(k.dataset.tr),UI.dragSrc,k.dataset.dst);UI.dragSrc=null});
function addRoute(tr,src,dst,amt){if(!tr)return;const r=newRoute(src,dst,amt==null?0.4:amt),m=routeMode(tr,r);if(!m.mode){toast('Cannot route: '+m.why+'.');return null}
  const s=srcInfo(tr,src);if(s&&s.src&&s.src.type==='follow'&&s.src.track&&followCreatesCycle(s.src.track,tr.id)){toast('Cannot route: that follower would create a feedback loop between tracks.');return null}
  pushUndo();r.color=s&&s.src&&s.src.color||'';tr.mod.routes.push(r);if(tr._rt)buildTrackMod(tr);markDirty();toast((s?s.name:src)+' → '+destDef(tr,dst).label+(m.mode==='note'?' (applied when each note starts)':m.mode==='js'?' (read at every grain)':' (live)'));
  quest('modulate');renderLab();renderSimple();return r}
function selectControl(tr,dst){UI.selDst=dst;UI.selTr=tr.id;$$('.knob.sel').forEach(k=>k.classList.remove('sel'));$$('.knob[data-tr="'+tr.id+'"]').forEach(k=>{if(k.dataset.dst===dst)k.classList.add('sel')});renderAssign()}
function renderAssign(){const box=$('#assign');if(!box)return;const tr=trById(UI.selTr);if(!tr||!UI.selDst){return}const d=destDef(tr,UI.selDst);if(!d)return;const rs=routesFor(tr,UI.selDst),mm=macroMapsFor(tr,UI.selDst);
  box.innerHTML='<b style="color:var(--fg)">'+esc(d.label)+'</b><span>Base value '+esc(fmtParam(d.p,getParamValue(tr,UI.selDst)))+' · '+(d.live==='audio'?'<span class="badge live">live</span> modulated every sample':d.live==='js'?'<span class="badge live">live</span> read at every grain':'<span class="badge note">note start</span> modulated when each note starts')+'</span>'+
    (rs.length?rs.map(r=>'<span>↳ '+esc((srcInfo(tr,r.src)||{name:r.src}).name)+' '+(r.amt>0?'+':'')+Math.round(r.amt*100)+'%'+(r.on?'':' (off)')+'</span>').join(''):'<span>No modulation routes.</span>')+
    (mm.length?mm.map(x=>'<span>⚙ Macro "'+esc(x.m.name)+'" sets '+Math.round(x.mp.min*100)+'–'+Math.round(x.mp.max*100)+'%'+(x.mp.inv?' (reversed)':'')+'</span>').join(''):'')}
/* context menu */
let ctxMenu=null,learnArmed=false;
function closeMenu(){if(ctxMenu){ctxMenu.remove();ctxMenu=null}}
function openMenu(x,y,items){closeMenu();const m=document.createElement('div');m.className='ctxmenu';m.setAttribute('role','menu');
  items.forEach(it=>{if(it==='-'){m.appendChild(document.createElement('hr'));return}const b=document.createElement('button');b.textContent=it.label;b.setAttribute('role','menuitem');b.onclick=()=>{closeMenu();it.act()};m.appendChild(b)});
  document.body.appendChild(m);const r=m.getBoundingClientRect();m.style.left=Math.min(x,innerWidth-r.width-8)+'px';m.style.top=Math.min(y,innerHeight-r.height-8)+'px';ctxMenu=m;const f=m.querySelector('button');if(f)f.focus()}
document.addEventListener('pointerdown',e=>{if(ctxMenu&&!ctxMenu.contains(e.target))closeMenu()},true);
document.addEventListener('contextmenu',e=>{const k=e.target.closest&&e.target.closest('.knob[data-dst]');if(!k)return;e.preventDefault();const tr=trById(k.dataset.tr),dst=k.dataset.dst,d=destDef(tr,dst);selectControl(tr,dst);
  const items=[{label:'Reset to default',act:()=>{pushUndo();setParam(tr,dst,d.p.def);ctlChanged(tr,dst)}},{label:'Randomize this control',act:()=>{pushUndo();setParam(tr,dst,fromNorm(d.p,Math.random()));ctlChanged(tr,dst)}},{label:'Learn MIDI CC',act:()=>startLearn(tr.id,{dst})},'-'];
  allSources(tr).forEach(s=>{const m=routeMode(tr,{src:s.id,dst});if(m.mode)items.push({label:'Modulate with '+s.name,act:()=>addRoute(tr,s.id,dst)})});
  items.push('-');tr.mod.macros.forEach(m=>items.push({label:'Add to macro "'+m.name+'"',act:()=>addMacroMap(tr,m,dst)}));items.push({label:'Add to a new macro',act:()=>{pushUndo();const m=newMacro('Macro '+(tr.mod.macros.length+1));tr.mod.macros.push(m);addMacroMap(tr,m,dst)}});
  openMenu(e.clientX,e.clientY,items)});
function addMacroMap(tr,m,dst){const d=destDef(tr,dst);if(!d||d.kind==='v'){toast('Macros set base values; per-voice controls can only be modulated. Route the macro as a source instead.');return}
  pushUndo();const n=toNorm(d.p,getParamValue(tr,dst));m.maps.push({dst,min:clamp(n-0.25,0,1),max:clamp(n+0.25,0,1),inv:false,curve:'lin'});if(tr._rt)buildTrackMod(tr);markDirty();toast('"'+d.label+'" added to macro '+m.name+'.');renderLab();renderSimple()}
function renderLearnState(){renderMidiPop()}
function uiCtl(kind,v){const id={mw:'#modBar',bend:'#bendBar',at:'#atBar'}[kind];const b=$(id);if(!b)return;if(kind==='bend'){b.style.width=Math.abs(v)*50+'%';b.style.marginLeft=(v<0?50-Math.abs(v)*50:50)+'%'}else b.style.width=pct(v)+'%'}
/* ───────── views ───────── */
const VIEWS=['studio','arrange','mix','sounds','lab'];
function setView(v){if(!VIEWS.includes(v))v='studio';S.view=v;document.body.className=document.body.className.replace(/\bv-\w+/g,'').trim()+' v-'+v;
  $$('.tabs button').forEach(b=>b.setAttribute('aria-selected',b.dataset.view===v));if(v!=='studio'){walkKeys.clear()}else panicLive();
  PREF.view=v;savePrefs();requestAnimationFrame(()=>{if(v==='arrange'){renderArrange();renderRoll(true);renderSimple()}if(v==='mix')renderMix();if(v==='sounds')renderSounds();if(v==='lab')renderLab();renderKeyboard()});quest('view:'+v)}
document.addEventListener('click',e=>{const g=e.target.closest&&e.target.closest('[data-goto]');if(g)setView(g.dataset.goto);const t=e.target.closest&&e.target.closest('.tabs [data-view]');if(t)setView(t.dataset.view);
  const c=e.target.closest&&e.target.closest('[data-close]');if(c){const m=c.closest('.modal');if(m)m.hidden=true}});
function renderAll(){renderTransport();if(S.view==='arrange'){renderArrange();renderRoll(true);renderSimple()}if(S.view==='mix')renderMix();if(S.view==='sounds')renderSounds();if(S.view==='lab')renderLab();renderKeyboard();updateStudioTrack()}
function updateStudioTrack(){const tr=selTrack(),el=$('#studioTrack');if(el&&tr)el.textContent=tr.name+' · '+ENG[tr.engine].name}
function selectTrack(i){if(i<0||i>=S.tracks.length)return;setTimeout(renderTransport,0);panicLive();S.sel=i;const tr=selTrack();S.octave=tr.oct||4;renderArrangeHeads();renderRoll(true);renderSimple();renderKeyboard();updateStudioTrack();if(S.view==='lab')renderLab();if(S.view==='mix')renderMix();drawTimeline()}
function panicLive(){if(!ctx)return;held.forEach(e=>e.hs.forEach(h=>h.off(ctx.currentTime)));held.clear();sustained.splice(0).forEach(h=>h.off(ctx.currentTime));S.tracks.forEach(t=>{t._arpHeld=null;t._arpClock=null});keyCounts.clear();$$('.key.on').forEach(k=>k.classList.remove('on'))}
/* ───────── transport ───────── */
function renderTransport(){const bh=$('#btnHold'),ba=$('#btnArp'),st=selTrack();if(bh)bh.setAttribute('aria-pressed',!!S.latch);if(ba)ba.setAttribute('aria-pressed',!!(st&&st.arp&&st.arp.on));$('#btnPlay').setAttribute('aria-pressed',S.playing);$('#btnRec').setAttribute('aria-pressed',S.rec);$('#btnMetro').setAttribute('aria-pressed',S.metro);$('#btnLoop').setAttribute('aria-pressed',S.loop.on);
  $('#bpm').value=S.bpm;$('#quant').value=String(S.quant);$('#swing').value=S.swing;$('#masterVol').value=S.master;if(!S.playing){const b=S.loop.on?S.loop.start:S.cursor;$('#lcdPos').textContent=(Math.floor(b)+1)+'.'+(Math.floor(b%1*4)+1)+'.1';$('#playhead').hidden=true}}
function toggleRec(){S.rec=!S.rec;if(S.rec&&!S.playing)play();renderTransport();audioRecSync();const mics=S.tracks.filter(t=>isAudio(t)&&t.arm);if(S.rec)toast(mics.length?'Recording audio into '+mics.map(t=>t.name).join(', ')+(isAudio(selTrack())?'':' and notes into '+selTrack().name)+'.':'Recording into '+selTrack().name+'. Each note lands when you release it.')}
/* ───────── keybed ───────── */
const KEYMAP="awsedftgyhujkolp;'";const liveBase=()=>S.octave*12+12;
function renderKeyboard(){const el=$('#keyboard');if(!el||!el.clientWidth)return;const W=el.clientWidth,tr=selTrack(),drum=tr&&ENG[tr.engine].drum,base=liveBase();
  el.style.setProperty('--c',tr?tr.color:'var(--accent)');let whites=clamp(Math.floor(W/(W<560?34:40)),7,40),start=base;if(whites>=17)start-=12;if(whites>=30)start-=12;start=Math.max(0,start);
  const ww=W/whites;let html='',wi=0,n=start;while(wi<whites){const lab=n-base,kc=lab>=0&&lab<KEYMAP.length?KEYMAP[lab].toUpperCase():'',top=drum?(lab>=0&&lab<12?padName(tr,((n%12)+12)%12):''):(n%12===0?noteName(n):'');
    if(isBlack(n))html+='<div class="key b" data-n="'+n+'" style="left:'+(wi*ww-ww*0.31)+'px;width:'+(ww*0.62)+'px">'+(kc?'<span>'+kc+'</span>':'')+'</div>';
    else{html+='<div class="key w" data-n="'+n+'" style="left:'+(wi*ww)+'px;width:'+(ww+1)+'px">'+(top?'<span class="kl">'+esc(top)+'</span>':'')+(kc?'<span>'+kc+'</span>':'')+'</div>';wi++}n++}
  el.innerHTML=html;keyCounts.forEach((c,k)=>{const e=el.querySelector('[data-n="'+k+'"]');if(e)e.classList.add('on')});$('#octV').textContent='C'+S.octave;$('#velV').textContent=pct(S.vel);
  const ens=tr&&tr.input&&tr.input.group?S.tracks.filter(t=>t.input&&t.input.group===tr.input.group):[];$('#ensInfo').textContent=ens.length>1?'Playing ensemble "'+(tr.input.ens||'')+'" ('+ens.length+' instruments)':''}
function keyVisual(n,on){const c=(keyCounts.get(n)||0)+(on?1:-1);if(c<=0)keyCounts.delete(n);else keyCounts.set(n,c);const el=document.querySelector('.key[data-n="'+n+'"]');if(el)el.classList.toggle('on',c>0)}
function flashNote(tr,n,t,dur){if(tr!==selTrack()||S.view==='studio')return;const d=Math.max(0,(t-ctx.currentTime)*1000);setTimeout(()=>keyVisual(n,true),d);setTimeout(()=>keyVisual(n,false),d+Math.max(60,dur*1000-10))}
const ptrKeys=new Map();
function ptrVel(k,y){const b=k.getBoundingClientRect();return clamp(0.35+0.65*(y-b.top)/b.height,0.2,1)}
document.addEventListener('pointerdown',e=>{const k=e.target.closest&&e.target.closest('#keyboard .key');if(!k)return;e.preventDefault();const n=+k.dataset.n;ptrKeys.set(e.pointerId,n);liveOn(n,ptrVel(k,e.clientY),'p'+e.pointerId)});
window.addEventListener('pointermove',e=>{if(!ptrKeys.has(e.pointerId))return;const t=document.elementFromPoint(e.clientX,e.clientY),k=t&&t.closest&&t.closest('#keyboard .key'),old=ptrKeys.get(e.pointerId),n=k?+k.dataset.n:null;if(n===old)return;
  liveOff(old,'p'+e.pointerId);if(n!=null){ptrKeys.set(e.pointerId,n);liveOn(n,ptrVel(k,e.clientY),'p'+e.pointerId)}else ptrKeys.delete(e.pointerId)});
function ptrUp(e){if(!ptrKeys.has(e.pointerId))return;liveOff(ptrKeys.get(e.pointerId),'p'+e.pointerId);ptrKeys.delete(e.pointerId)}
window.addEventListener('pointerup',ptrUp);window.addEventListener('pointercancel',ptrUp);
/* audition a note on a track (preview) */
function audition(tr,n,len=0.25,v=0.8){if(!ensureAudio())return;const h=trigger(tr,n,v,ctx.currentTime);keyVisual(n,true);setTimeout(()=>{if(h)h.off(ctx.currentTime);keyVisual(n,false)},len*1000)}
/* quests: progression is not built yet; real app events still call this hook */
function quest(){}
async function saveBlob(blob,name){
  if(window.native){const buf=new Uint8Array(await blob.arrayBuffer());const r=await window.native.saveFile(name,buf);if(r&&r.path){toast('Saved to '+r.path);return r.path}if(r&&r.error)toast('Could not save: '+r.error);return null}
  if(S.dl){try{await S.dl.save({filename:name,data:blob});toast('Saved '+name+'.');return name}catch(e){if(e&&e.code!=='declined')toast('This view could not save '+name+'.');return null}}
  const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=name;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(a.href),8000);toast('Downloaded '+name+'.');return name}
const fileSafe=s=>(s||'soundcraft').replace(/[^\w\- ]+/g,'').trim().replace(/\s+/g,'-').toLowerCase()||'soundcraft';
