'use strict';
/* Arrangement: track headers, clip timeline with ruler, loop region and section markers; instrument dock. */
const TL={ROW:48,RULER:26,drag:null};
function barW(){return S.zoom}
/* Track heights: each track can have its own (drag the bottom edge of its header); otherwise the global height. */
const rowH=t=>clamp(Math.round((t&&t.h)||PREF.rowH||48),28,240),rowTop=i=>{let y=TL.RULER;for(let k=0;k<i;k++)y+=rowH(S.tracks[k]);return y};
function rowAt(y){let top=TL.RULER;for(let i=0;i<S.tracks.length;i++){const h=rowH(S.tracks[i]);if(y<top+h)return i;top+=h}return S.tracks.length}
function renderArrange(){if(S.view!=='arrange')return;applyArrSizes();renderArrangeHeads();drawTimeline()}
function renderArrangeHeads(){const box=$('#arrHeadsInner');if(!box)return;
  box.innerHTML=S.tracks.map((t,i)=>{const E=ENG[t.engine],pre=t.preset&&presetById(t.preset);return '<div class="th'+(i===S.sel?' sel':'')+(rowH(t)<40?' compact':'')+'" data-i="'+i+'" style="--c:'+t.color+';height:'+rowH(t)+'px">'+
    '<div class="th-top"><input class="th-name" value="'+esc(t.name)+'" aria-label="Track name" data-act="name"><button class="ms wide" data-act="menu" aria-label="Track options for '+esc(t.name)+'">⋯</button></div>'+
    '<div class="th-ctl"><button class="ms" data-act="mute" aria-pressed="'+t.mute+'" title="Mute">M</button><button class="ms" data-act="solo" aria-pressed="'+t.solo+'" title="Solo">S</button>'+(isAudio(t)?'<button class="ms arm" data-act="arm" aria-pressed="'+!!t.arm+'" title="Arm for recording">●</button>':'')+
    '<input type="range" min="0" max="1" step="0.01" value="'+t.vol+'" data-act="vol" aria-label="'+esc(t.name)+' volume"><span class="meter-h"><i></i></span></div>'+
    '<div class="th-inst" title="'+esc(E.name)+'">'+(isAudio(t)?esc('Mic · '+(t.arm?(micOpen()?MIC.label:'armed'):'not armed')+(t.monitor?' · monitoring':'')):esc(pre?pre.name:E.name))+(t.input&&t.input.group?' · ensemble':'')+(t.arp&&t.arp.on?' · arp':'')+'</div><i class="th-resize" data-i="'+i+'" title="Drag to resize this track; double-click to reset"></i></div>'}).join('');
  updateStudioTrack()}
$('#arrHeadsInner').addEventListener('click',e=>{const th=e.target.closest('.th');if(!th)return;const i=+th.dataset.i,t=S.tracks[i],a=e.target.dataset.act;
  if(a==='mute'||a==='solo'){pushUndo();t[a]=!t[a];e.target.setAttribute('aria-pressed',t[a]);applyMix();markDirty();return}
  if(a==='arm'){toggleArm(t);return}
  if(a==='menu'){const r=e.target.getBoundingClientRect();openMenu(r.left,r.bottom,(isAudio(t)?[{label:t.arm?'Disarm':'Arm for recording',act:()=>toggleArm(t)},{label:t.monitor?'Monitoring off':'Monitor input',act:()=>{t.monitor=!t.monitor;markDirty();updateMonitors();renderArrangeHeads()}}]:[
    {label:'Edit instrument in Lab',act:()=>{selectTrack(i);setView('lab')}},{label:'Change sound…',act:()=>{selectTrack(i);setView('sounds')}}]).concat([
    {label:'Duplicate track',act:()=>dupTrack(i)},{label:'Save as track preset',act:()=>saveTrackPreset(t)},
    {label:'Move up',act:()=>moveTrack(i,-1)},{label:'Move down',act:()=>moveTrack(i,1)},
    {label:'Next color',act:()=>{pushUndo();t.color=TRACK_COLORS[(TRACK_COLORS.indexOf(t.color)+1)%TRACK_COLORS.length];markDirty();renderArrange();renderKeyboard()}},'-',
    {label:'Delete track…',act:()=>confirmDeleteTrack(i)}]));return}
  if(a!=='name'&&a!=='vol')selectTrack(i)});
$('#arrHeadsInner').addEventListener('change',e=>{const th=e.target.closest('.th');if(!th)return;const t=S.tracks[+th.dataset.i];if(e.target.dataset.act==='name'){pushUndo();t.name=e.target.value.trim().slice(0,40)||t.name;t.autoName=false;markDirty();updateStudioTrack()}});
$('#arrHeadsInner').addEventListener('input',e=>{if(e.target.dataset.act!=='vol')return;const t=S.tracks[+e.target.closest('.th').dataset.i];t.vol=+e.target.value;applyMix();markDirty()});
function dupTrack(i){pushUndo();const src=S.tracks[i],c=JSON.parse(JSON.stringify(src,(k,v)=>k[0]==='_'?undefined:v));c.id=uid('t');c.name=src.name+' copy';
  const pm={};c.patterns.forEach(p=>{const n=uid('p');pm[p.id]=n;p.id=n});c.clips.forEach(x=>{x.id=uid('c');x.pat=pm[x.pat]});c.curPat=pm[c.curPat]||c.patterns[0].id;
  const fm={};c.fx.forEach(f=>{const n=uid('f');fm[f.id]=n;f.id=n});c.mod.routes.forEach(r=>{if(r.dst.startsWith('fx:')){const p=r.dst.split(':');if(fm[p[1]])r.dst='fx:'+fm[p[1]]+':'+p[2]}});
  S.tracks.splice(i+1,0,c);if(ctx)applyMix();markDirty();selectTrack(i+1);renderArrange();toast('Duplicated '+src.name+'.')}
function moveTrack(i,d){const j=i+d;if(j<0||j>=S.tracks.length)return;pushUndo();const t=S.tracks.splice(i,1)[0];S.tracks.splice(j,0,t);S.sel=j;markDirty();renderArrange()}
function confirmDeleteTrack(i){if(S.tracks.length<2){toast('A project needs at least one track.');return}const t=S.tracks[i];
  openMenu(innerWidth/2-100,innerHeight/3,[{label:'Delete "'+t.name+'" and its clips',act:()=>{pushUndo();disposeTrack(t);S.tracks.splice(i,1);S.sel=Math.min(S.sel,S.tracks.length-1);markDirty();renderAll();toast('Deleted '+t.name+'. Undo brings it back.')}},{label:'Keep the track',act:()=>{}}])}
function saveTrackPreset(t){const r=addUserItem('track',t.name,trackData(t),{desc:'Track preset from "'+$('#projTitle').value+'"'});toast(r.dup?'That track preset is already in your library ("'+r.dup.name+'").':r.item?'Saved track preset "'+t.name+'".':'Could not save the track preset.')}
$('#arrScroll').addEventListener('scroll',()=>{$('#arrHeadsInner').style.transform='translateY('+(-$('#arrScroll').scrollTop+TL.RULER)+'px)'});
/* ───────── timeline drawing ───────── */
function drawTimeline(){const cv=$('#tl'),wrap=$('#arrScroll');if(!cv||S.view!=='arrange')return;const bw=barW(),bars=Math.ceil(songEnd()+8),W=Math.max(wrap.clientWidth,bars*bw),H=rowTop(S.tracks.length)+20,dpr=window.devicePixelRatio||1;
  cv.style.width=W+'px';cv.style.height=H+'px';cv.width=W*dpr;cv.height=H*dpr;const c=cv.getContext('2d');c.scale(dpr,dpr);const css=getComputedStyle(document.documentElement),col=k=>css.getPropertyValue(k).trim();
  c.fillStyle=col('--panel-2');c.fillRect(0,0,W,TL.RULER);
  if(S.loop.on){c.fillStyle='rgba(255,180,84,.28)';c.fillRect(S.loop.start*bw,0,(S.loop.end-S.loop.start)*bw,TL.RULER);c.fillStyle='rgba(255,180,84,.06)';c.fillRect(S.loop.start*bw,TL.RULER,(S.loop.end-S.loop.start)*bw,H)}
  S.tracks.forEach((t,i)=>{const y=rowTop(i),RH=rowH(t);c.fillStyle=i===S.sel?col('--roll-a'):(i%2?col('--roll-b'):col('--panel'));c.fillRect(0,y,W,RH);c.fillStyle=col('--line');c.fillRect(0,y+RH-1,W,1)});
  for(let b=0;b<=bars;b++){const x=Math.round(b*bw);c.fillStyle=b%4===0?col('--grid-3'):col('--grid-2');c.fillRect(x,TL.RULER,1,H);if(bw>=44)for(let q=1;q<4;q++){c.fillStyle=col('--grid');c.fillRect(Math.round(x+q*bw/4),TL.RULER,1,H)}
    if(bw>=24||b%4===0){c.fillStyle=col('--muted');c.font='600 10px "IBM Plex Sans",sans-serif';c.fillText(String(b+1),x+3,TL.RULER-6)}}
  S.markers.forEach(m=>{const x=m.bar*bw;c.fillStyle=col('--pink');c.fillRect(x,0,2,TL.RULER);c.font='600 11px "IBM Plex Sans",sans-serif';const w=c.measureText(m.name).width+8;c.fillRect(x,1,w,13);c.fillStyle='#1c1a33';c.fillText(m.name,x+4,11)});
  c.fillStyle=col('--accent');c.fillRect(S.cursor*bw,0,2,TL.RULER);
  S.tracks.forEach((t,i)=>{const y=rowTop(i),ROW=rowH(t);t.clips.forEach(cl=>{const x=cl.start*bw+1,w=Math.max(4,cl.len*bw-2),pat=t.patterns.find(p=>p.id===cl.pat),sel=S.selClip&&S.selClip.clip===cl.id;
    c.globalAlpha=0.85;c.fillStyle=t.color;roundRect(c,x,y+3,w,ROW-7,5);c.fill();c.globalAlpha=1;if(sel){c.strokeStyle=col('--fg');c.lineWidth=2;c.stroke()}
    c.fillStyle='rgba(20,19,48,.85)';c.font='600 10.5px "IBM Plex Sans",sans-serif';c.save();c.beginPath();c.rect(x,y,w,ROW);c.clip();c.fillText(cl.rec?(cl.name||'Take'):pat?pat.name:'?',x+4,y+15);if(cl.rec)drawAudioClip(c,cl,x,y,w,ROW,bw);
    else if(pat&&pat.notes.length){const ps=pat.notes.map(n=>n.p),lo=Math.min(...ps),hi=Math.max(...ps)+1,L=pat.len;c.fillStyle='rgba(20,19,48,.55)';
      for(let rep=0;rep*L<cl.len;rep++)pat.notes.forEach(n=>{const nx=x+(rep*L+n.s/16)*bw;if(nx>x+w)return;c.fillRect(nx,y+ROW-8-(n.p-lo)/(hi-lo)*(ROW-26),Math.max(1,n.l/16*bw),2)});
      for(let rep=1;rep*L<cl.len;rep++){c.fillStyle='rgba(20,19,48,.35)';c.fillRect(x+rep*L*bw,y+4,1,ROW-9)}}c.restore()})});
  let ph=$('#tlHead');if(!ph){ph=document.createElement('div');ph.id='tlHead';ph.className='playhead';wrap.appendChild(ph)}ph.style.height=H+'px'}
function roundRect(c,x,y,w,h,r){c.beginPath();c.moveTo(x+r,y);c.arcTo(x+w,y,x+w,y+h,r);c.arcTo(x+w,y+h,x,y+h,r);c.arcTo(x,y+h,x,y,r);c.arcTo(x,y,x+w,y,r);c.closePath()}
function tlPoint(e){const b=$('#tl').getBoundingClientRect();return {x:e.clientX-b.left,y:e.clientY-b.top}}
const clipSnap=()=>+$('#clipSnap').value;
const snapBar=(b,q)=>Math.max(0,Math.round(b/q)*q);
function clipHit(x,y){const row=rowAt(y),t=S.tracks[row];if(!t||y<TL.RULER)return {row,t};const bar=x/barW();
  const c=t.clips.slice().reverse().find(cl=>bar>=cl.start&&bar<cl.start+cl.len);return {row,t,c,bar,edge:c&&(c.start+c.len)*barW()-x<8}}
$('#tl').addEventListener('pointerdown',e=>{if(e.button!==0)return;const {x,y}=tlPoint(e),bw=barW();
  if(y<TL.RULER){const m=S.markers.find(m=>x>=m.bar*bw&&x<=m.bar*bw+80&&y<15);if(m){editMarker(m,e.clientX,e.clientY);return}TL.drag={mode:'ruler',b0:Math.floor(x/bw),moved:false};$('#tl').setPointerCapture(e.pointerId);return}
  const h=clipHit(x,y);if(!h.t)return;selectTrack(h.row);
  if(h.c){S.selClip={tr:h.t.id,clip:h.c.id};if(h.c.pat)h.t.curPat=h.c.pat;else renderAudioDock();pushUndo();TL.drag={mode:h.edge?'resize':'move',t:h.t,c:h.c,x0:x,s0:h.c.start,l0:h.c.len,moved:false};renderRoll(true)}
  else if(isAudio(h.t)){S.selClip=null;S.cursor=Math.max(0,Math.floor(h.bar/clipSnap())*clipSnap());renderTransport();drawTimeline();renderAudioDock();return}
  else{pushUndo();const pat=curPattern(h.t),q=clipSnap(),cl={id:uid('c'),pat:pat.id,start:Math.floor(h.bar/q)*q,len:pat.len};h.t.clips.push(cl);S.selClip={tr:h.t.id,clip:cl.id};TL.drag={mode:'resize',t:h.t,c:cl,x0:x,s0:cl.start,l0:cl.len,moved:true,created:true};markDirty()}
  $('#tl').setPointerCapture(e.pointerId);drawTimeline()});
$('#tl').addEventListener('pointermove',e=>{const {x,y}=tlPoint(e),g=TL.drag,bw=barW();
  if(!g){const h=y>=TL.RULER?clipHit(x,y):{};$('#tl').style.cursor=y<TL.RULER?'pointer':h.c?(h.edge?'ew-resize':'grab'):'copy';return}
  const q=clipSnap();
  if(g.mode==='ruler'){const b=Math.floor(x/bw);if(b!==g.b0)g.moved=true;if(g.moved){S.loop={on:true,start:Math.max(0,Math.min(g.b0,b)),end:Math.max(g.b0,b)+1};renderTransport();drawTimeline()}return}
  const dx=(x-g.x0)/bw;if(Math.abs(x-g.x0)>3)g.moved=true;if(g.mode==='move')g.c.start=snapBar(g.s0+dx,q);else g.c.len=Math.max(q,snapBar(g.l0+dx,q));drawTimeline()});
$('#tl').addEventListener('pointerup',e=>{const g=TL.drag;TL.drag=null;if(!g)return;
  if(g.mode==='ruler'){if(!g.moved){S.cursor=g.b0;if(!S.playing)renderTransport()}markDirty();drawTimeline();return}
  if(!g.moved&&!g.created)undoStack.pop();else markDirty();drawTimeline()});
$('#tl').addEventListener('dblclick',e=>{const {x,y}=tlPoint(e),h=clipHit(x,y);if(h.c)splitClip(h.t,h.c,snapBar(h.bar,clipSnap()))});
$('#tl').addEventListener('contextmenu',e=>{e.preventDefault();const {x,y}=tlPoint(e),h=clipHit(x,y);if(!h.c)return;S.selClip={tr:h.t.id,clip:h.c.id};drawTimeline();
  openMenu(e.clientX,e.clientY,[{label:'Duplicate clip',act:dupClip},{label:'Split here',act:()=>splitClip(h.t,h.c,snapBar(h.bar,clipSnap()))},{label:'Make unique',act:makeUnique}].concat(h.c.rec?[{label:'Make a Sampler instrument from this take',act:()=>takeToSampler(h.t,h.c)}]:[]).concat(['-',{label:'Delete clip',act:delClip}]))});
function selClipObj(){if(!S.selClip)return {};const t=trById(S.selClip.tr);return {t,c:t&&t.clips.find(c=>c.id===S.selClip.clip)}}
function dupClip(){const {t,c}=selClipObj();if(!c){toast('Select a clip first.');return}pushUndo();const n=Object.assign({},c,{id:uid('c'),start:c.start+c.len});t.clips.push(n);S.selClip={tr:t.id,clip:n.id};markDirty();drawTimeline()}
function delClip(){const {t,c}=selClipObj();if(!c){toast('Select a clip first.');return}pushUndo();t.clips=t.clips.filter(x=>x!==c);S.selClip=null;markDirty();drawTimeline()}
function splitClip(t,c,at){if(at<=c.start||at>=c.start+c.len){toast('Split point must be inside the clip.');return}pushUndo();if(c.rec){splitAudioClip(t,c,at);markDirty();drawTimeline();return}const pat=t.patterns.find(p=>p.id===c.pat),off=(at-c.start)%pat.len;
  let np=pat;if(off){np=JSON.parse(JSON.stringify(pat));np.id=uid('p');np.name=pat.name+'′';np.notes=pat.notes.map(n=>({...n,s:((n.s-off*16)%(pat.len*16)+pat.len*16)%(pat.len*16)}));t.patterns.push(np)}
  t.clips.push({id:uid('c'),pat:np.id,start:at,len:c.start+c.len-at});c.len=at-c.start;markDirty();drawTimeline()}
function makeUnique(){const {t,c}=selClipObj();if(!c){toast('Select a clip first.');return}if(c.rec){toast('Audio clips have no pattern to copy.');return}pushUndo();const pat=t.patterns.find(p=>p.id===c.pat),np=JSON.parse(JSON.stringify(pat));np.id=uid('p');np.name=nextPatName(t);t.patterns.push(np);c.pat=np.id;t.curPat=np.id;markDirty();drawTimeline();renderRoll();toast('The clip now has its own pattern "'+np.name+'".')}
function nextPatName(t){const used=new Set(t.patterns.map(p=>p.name));for(let i=0;i<26;i++){const n=String.fromCharCode(65+i);if(!used.has(n))return n}return 'P'+(t.patterns.length+1)}
function editMarker(m,x,y){openMenu(x,y,[{label:'Rename "'+m.name+'"…',act:()=>{const inp=document.createElement('input');inp.type='text';inp.value=m.name;inp.className='ctxmenu';inp.style.cssText='left:'+x+'px;top:'+y+'px;min-width:160px;padding:6px';document.body.appendChild(inp);inp.focus();inp.select();
    const done=()=>{if(inp.value.trim()){pushUndo();m.name=inp.value.trim().slice(0,40);markDirty();drawTimeline()}inp.remove()};inp.onkeydown=e=>{if(e.key==='Enter')done();if(e.key==='Escape')inp.remove()};inp.onblur=done}},
  {label:'Loop this section',act:()=>{const nx=S.markers.filter(o=>o.bar>m.bar).sort((a,b)=>a.bar-b.bar)[0];S.loop={on:true,start:m.bar,end:nx?nx.bar:songEnd()};renderTransport();drawTimeline();markDirty()}},
  {label:'Move to cursor',act:()=>{pushUndo();m.bar=S.cursor;markDirty();drawTimeline()}},{label:'Delete marker',act:()=>{pushUndo();S.markers=S.markers.filter(o=>o!==m);markDirty();drawTimeline()}}])}
$('#btnMarker').onclick=()=>{pushUndo();const bar=S.playing?Math.floor(posAtNow()/16):S.cursor;S.markers.push({id:uid('k'),bar,name:'Section '+(S.markers.length+1)});markDirty();drawTimeline();toast('Marker added at bar '+(bar+1)+'. Click it in the ruler to rename.')};
$('#btnSplitClip').onclick=()=>{const {t,c}=selClipObj();if(!c){toast('Select a clip first.');return}splitClip(t,c,S.cursor)};
$('#btnDupClip').onclick=dupClip;$('#btnDelClip').onclick=delClip;$('#btnUniqueClip').onclick=makeUnique;
/* zoom: buttons, Ctrl+wheel (horizontal, around the mouse), Alt+wheel (all track heights) */
function setZoom(z,anchorX){const w=$('#arrScroll'),old=S.zoom,ax=anchorX==null?w.clientWidth/2:anchorX,bar=(w.scrollLeft+ax)/old;S.zoom=clamp(Math.round(z),4,400);drawTimeline();w.scrollLeft=bar*S.zoom-ax}
function setRowH(h){PREF.rowH=clamp(Math.round(h),28,240);S.tracks.forEach(t=>delete t.h);savePrefs();renderArrange()}
$('#zoomIn').onclick=()=>setZoom(S.zoom*1.3);$('#zoomOut').onclick=()=>setZoom(S.zoom/1.3);
$('#rowIn').onclick=()=>setRowH((PREF.rowH||48)*1.25);$('#rowOut').onclick=()=>setRowH((PREF.rowH||48)/1.25);
$('#arrScroll').addEventListener('wheel',e=>{if(e.ctrlKey||e.metaKey){e.preventDefault();const b=$('#arrScroll').getBoundingClientRect();setZoom(S.zoom*(e.deltaY<0?1.15:1/1.15),e.clientX-b.left)}else if(e.altKey){e.preventDefault();setRowH((PREF.rowH||48)*(e.deltaY<0?1.1:1/1.1))}},{passive:false});
$('#arrHeads').addEventListener('wheel',e=>{if(e.altKey||e.ctrlKey){e.preventDefault();setRowH((PREF.rowH||48)*(e.deltaY<0?1.1:1/1.1))}else{$('#arrScroll').scrollTop+=e.deltaY}},{passive:false});
/* per-track height: drag the bottom edge of a header; double-click it to reset */
$('#arrHeadsInner').addEventListener('pointerdown',e=>{const g=e.target.closest('.th-resize');if(!g)return;e.preventDefault();e.stopPropagation();const t=S.tracks[+g.dataset.i],y0=e.clientY,h0=rowH(t);g.setPointerCapture(e.pointerId);
  const mv=ev=>{t.h=clamp(h0+(ev.clientY-y0)/(PREF.scale||1),28,240);const th=g.closest('.th');th.style.height=rowH(t)+'px';drawTimeline()};
  const up=()=>{g.removeEventListener('pointermove',mv);g.removeEventListener('pointerup',up);markDirty();renderArrange()};g.addEventListener('pointermove',mv);g.addEventListener('pointerup',up)});
$('#arrHeadsInner').addEventListener('dblclick',e=>{const g=e.target.closest('.th-resize');if(!g)return;delete S.tracks[+g.dataset.i].h;markDirty();renderArrange()});
/* header width: drag the line between the track headers and the timeline */
(()=>{const d=$('#headSplit');let x0=null,w0=0;d.addEventListener('pointerdown',e=>{x0=e.clientX;w0=PREF.headW||210;d.setPointerCapture(e.pointerId)});
  d.addEventListener('pointermove',e=>{if(x0==null)return;PREF.headW=clamp(w0+(e.clientX-x0)/(PREF.scale||1),120,520);applyArrSizes()});d.addEventListener('pointerup',()=>{if(x0==null)return;x0=null;savePrefs();drawTimeline()});
  d.addEventListener('dblclick',()=>{PREF.headW=210;savePrefs();applyArrSizes();drawTimeline()})})();
function applyArrSizes(){const b=$('#arrBody');b.style.setProperty('--head-w',(PREF.headW||210)+'px');b.style.setProperty('--arr-h',PREF.dockMax?'calc(100vh - 230px)':PREF.splitH+'px');document.body.classList.toggle('dock-max',!!PREF.dockMax)}
$('#btnMaxTl').onclick=()=>{PREF.dockMax=!PREF.dockMax;savePrefs();applyArrSizes();$('#btnMaxTl').setAttribute('aria-pressed',!!PREF.dockMax);drawTimeline()};
$('#btnAddTrack').onclick=()=>{UI.addMode=true;setView('sounds');toast('Pick a sound and choose "Add as new track".')};
$('#btnDupTrack').onclick=()=>dupTrack(S.sel);$('#btnAddMic').onclick=addMicTrack;
/* splitter */
(()=>{const sp=$('#splitter');let y0=null,h0=0;sp.addEventListener('pointerdown',e=>{y0=e.clientY;h0=PREF.splitH;sp.setPointerCapture(e.pointerId)});
  sp.addEventListener('pointermove',e=>{if(y0==null)return;PREF.dockMax=false;PREF.splitH=clamp(h0+(e.clientY-y0)/(PREF.scale||1),100,Math.max(300,innerHeight/(PREF.scale||1)-200));applyArrSizes()});sp.addEventListener('pointerup',()=>{y0=null;savePrefs();drawTimeline()});
  sp.addEventListener('dblclick',()=>$('#btnMaxTl').click());sp.addEventListener('keydown',e=>{if(e.key==='ArrowUp'||e.key==='ArrowDown'){PREF.splitH=clamp(PREF.splitH+(e.key==='ArrowUp'?-20:20),120,900);$('#arrBody').style.setProperty('--arr-h',PREF.splitH+'px');savePrefs()}})})();
$$('.dock-tabs [data-dock]').forEach(b=>b.onclick=()=>{PREF.dock=b.dataset.dock;savePrefs();showDock()});
function showDock(){$$('.dock-tabs [data-dock]').forEach(b=>b.setAttribute('aria-selected',b.dataset.dock===PREF.dock));syncDock();if(isAudio(selTrack()))return;if(PREF.dock==='roll')renderRoll(true);else renderSimple()}
/* ───────── simple instrument view ───────── */
function simpleKeys(tr){const E=ENG[tr.engine];if(E.simple)return E.simple;const ps=E.params.filter(p=>!/^p\d+[tdlp]$/.test(p.k)&&!/^L\d/.test(p.k));const groups=[...new Set(ps.map(p=>p.group))];
  return ps.filter(p=>groups.slice(0,3).includes(p.group)||p.k==='level').slice(0,12).map(p=>p.k)}
function renderSimple(){const card=$('#instCard');if(!card||S.view!=='arrange')return;const tr=selTrack();if(!tr)return;if(isAudio(tr)){syncDock();return}const E=ENG[tr.engine],pre=tr.preset&&presetById(tr.preset);
  card.style.setProperty('--c',tr.color);$('#instTag').textContent=E.kind+' · '+E.name;$('#instName').textContent=pre?pre.name:E.name;$('#instDesc').textContent=(pre&&pre.desc?pre.desc+' ':'')+E.desc;
  $('#perfMacros').innerHTML=tr.mod.macros.length?tr.mod.macros.map(m=>ctlHTML(tr,'macro:'+m.id,{big:true,color:'var(--pink)'})).join(''):'<span class="hint">This sound has no macros yet. Create them in the Lab, or right-click any knob and choose “Add to a new macro”.</span>';
  $('#simpleKnobs').innerHTML=simpleKeys(tr).map(k=>ctlHTML(tr,'eng:'+k,{color:tr.color})).join('')+ctlHTML(tr,'trk:rev',{color:tr.color})+ctlHTML(tr,'trk:dly',{color:tr.color})}
function drawScope(){const cv=$('#scope');if(!cv||!cv.clientWidth)return;const dpr=window.devicePixelRatio||1,w=cv.clientWidth,h=cv.clientHeight;if(cv.width!==Math.round(w*dpr)){cv.width=Math.round(w*dpr);cv.height=Math.round(h*dpr)}
  const c=cv.getContext('2d');c.setTransform(dpr,0,0,dpr,0,0);c.clearRect(0,0,w,h);c.strokeStyle='rgba(172,164,207,.08)';for(let i=1;i<4;i++){c.beginPath();c.moveTo(0,h*i/4);c.lineTo(w,h*i/4);c.stroke()}
  const col=selTrack()?selTrack().color:'#ffb454';if(GR.analyser&&!OFF){const bars=64;c.fillStyle=col;c.globalAlpha=0.2;for(let i=0;i<bars;i++){const idx=Math.floor(Math.pow(i/bars,1.8)*400)+1,v=fbuf[idx]/255;c.fillRect(i*w/bars+1,h-v*h*0.9,w/bars-2,v*h*0.9)}c.globalAlpha=1;
    let z=0;for(let i=1;i<1024;i++){if(tbuf[i-1]<0&&tbuf[i]>=0){z=i;break}}c.beginPath();for(let i=0;i<1024;i++){const x=i/1023*w,y=h/2-tbuf[z+i]*h*0.42;i?c.lineTo(x,y):c.moveTo(x,y)}c.strokeStyle=col;c.lineWidth=2;c.stroke()}
  c.fillStyle='rgba(244,234,214,.6)';c.font='19px VT323,monospace';c.fillText('OUTPUT',10,20)}
