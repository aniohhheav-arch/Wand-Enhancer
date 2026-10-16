'use strict';
/* Piano roll / drum grid for the selected track's current pattern. */
const SCALES={off:['Off',null],major:['Major',[0,2,4,5,7,9,11]],minor:['Minor',[0,2,3,5,7,8,10]],dorian:['Dorian',[0,2,3,5,7,9,10]],mixolydian:['Mixolydian',[0,2,4,5,7,9,10]],harmonic:['Harmonic minor',[0,2,3,5,7,8,11]],pentatonic:['Major pentatonic',[0,2,4,7,9]],minorpent:['Minor pentatonic',[0,3,5,7,10]],blues:['Blues',[0,3,5,6,7,10]]};
const scaleSet=()=>SCALES[S.scale.type]&&SCALES[S.scale.type][1];
const inScale=n=>{const s=scaleSet();return !s||s.includes(((n-S.scale.root)%12+12)%12)};
function snapScale(n){if(!$('#scaleSnap').checked||!scaleSet())return n;for(let d=0;d<7;d++){if(inScale(n-d))return n-d;if(inScale(n+d))return n+d}return n}
function chordFor(n,kind){if(kind==='single')return [n];if(kind==='power')return [n,n+7];if(kind==='octave')return [n,n+12];const s=scaleSet(),cnt=kind==='seventh'?4:3;
  if(!s)return kind==='seventh'?[n,n+4,n+7,n+11]:[n,n+4,n+7];const out=[n];let p=n;while(out.length<cnt){let steps=0;do{p++;if(inScale(p))steps++}while(steps<2);out.push(p)}return out}
$('#scaleRoot').innerHTML=NN.map((n,i)=>'<option value="'+i+'">'+n+'</option>').join('');$('#scaleType').innerHTML=Object.entries(SCALES).map(([k,v])=>'<option value="'+k+'">'+v[0]+'</option>').join('');
$('#scaleRoot').onchange=e=>{S.scale.root=+e.target.value;markDirty();renderRoll()};$('#scaleType').onchange=e=>{S.scale.type=e.target.value;markDirty();renderRoll()};
const R={keysW:56,rowH:14,colW:20,rows:[],idx:{},drag:null,sel:new Set()};
const isDrum=tr=>!!ENG[tr.engine].drum;
function rollRows(tr){if(isDrum(tr))return DRUM_NOTES.map((n,i)=>({n,label:padName(tr,i)})).reverse();const r=[];for(let n=108;n>=21;n--)r.push({n,label:n%12===0?noteName(n):''});return r}
const rowOf=(tr,p)=>R.idx[isDrum(tr)?DRUM_NOTES[padIndex(p)]:p];
const gridQ=()=>+$('#grid').value;
function renderPatternBar(){const tr=selTrack();if(!tr)return;const pat=curPattern(tr);$('#patSel').innerHTML=tr.patterns.map(p=>'<option value="'+p.id+'"'+(p.id===pat.id?' selected':'')+'>'+esc(p.name)+' · '+p.len+' bar'+(p.len>1?'s':'')+'</option>').join('');
  $('#patName').value=pat.name;$('#patLen').value=String(pat.len);$('#rollTitle').textContent=tr.name;$('#btnBreak').hidden=!isDrum(tr);$('#scaleRoot').value=String(S.scale.root);$('#scaleType').value=S.scale.type}
function renderRoll(scroll){
  const tr=selTrack();if(!tr||S.view!=='arrange')return;syncDock();if(isAudio(tr)||PREF.dock!=='roll')return;renderPatternBar();const pat=curPattern(tr),wrap=$('#rollWrap'),cv=$('#roll'),drum=isDrum(tr),rows=rollRows(tr),L=pat.len*16;
  R.rows=rows;R.rowH=drum?24:14;R.keysW=drum?96:56;R.colW=Math.max(drum?16:12,(wrap.clientWidth-R.keysW)/Math.min(L,64));R.idx={};rows.forEach((r,i)=>R.idx[r.n]=i);
  const W=Math.ceil(R.keysW+R.colW*L),H=rows.length*R.rowH,dpr=window.devicePixelRatio||1;cv.style.width=W+'px';cv.style.height=H+'px';cv.width=W*dpr;cv.height=H*dpr;$('#playhead').style.height=H+'px';
  const c=cv.getContext('2d');c.scale(dpr,dpr);const css=getComputedStyle(document.documentElement),col=k=>css.getPropertyValue(k).trim(),sc=scaleSet();
  rows.forEach((r,i)=>{c.fillStyle=drum?(i%2?col('--roll-a'):col('--roll-b')):(isBlack(r.n)?col('--roll-b'):col('--roll-a'));c.fillRect(R.keysW,i*R.rowH,W,R.rowH);
    if(!drum&&sc&&inScale(r.n)){c.fillStyle='rgba(143,202,162,.09)';c.fillRect(R.keysW,i*R.rowH,W,R.rowH)}if(!drum&&r.n%12===0){c.fillStyle=col('--grid-2');c.fillRect(R.keysW,(i+1)*R.rowH-1,W,1)}});
  for(let s=0;s<=L;s++){c.fillStyle=s%16===0?col('--grid-3'):s%4===0?col('--grid-2'):col('--grid');c.fillRect(Math.round(R.keysW+s*R.colW),0,1,H)}
  c.font='600 10px "IBM Plex Sans",sans-serif';
  rows.forEach((r,i)=>{const y=i*R.rowH;if(drum){c.fillStyle=col('--panel-2');c.fillRect(0,y,R.keysW-1,R.rowH-1);c.fillStyle=col('--fg');c.fillText(r.label,6,y+R.rowH/2+3.5)}
    else{c.fillStyle=isBlack(r.n)?col('--key-b'):col('--key-w');c.fillRect(0,y,R.keysW-1,R.rowH-1);if(sc&&inScale(r.n)){c.fillStyle='rgba(143,202,162,.55)';c.fillRect(R.keysW-5,y+1,3,R.rowH-3)}if(r.label){c.fillStyle='#6d6252';c.fillText(r.label,R.keysW-28,y+R.rowH-3)}}});
  for(let b=0;b<pat.len;b++){c.fillStyle=col('--muted');c.fillText(String(b+1),R.keysW+b*16*R.colW+4,11)}
  pat.notes.forEach(nt=>{const ri=rowOf(tr,nt.p);if(ri==null||nt.s>=L)return;const x=R.keysW+nt.s*R.colW+1,y=ri*R.rowH+1.5,w=Math.max(4,nt.l*R.colW-2),h=R.rowH-3,sel=R.sel.has(nt);
    c.globalAlpha=0.35+nt.v*0.65;c.fillStyle=tr.color;roundRect(c,x,y,w,h,3);c.fill();c.globalAlpha=1;if(sel){c.strokeStyle=col('--fg');c.lineWidth=2;c.stroke()}
    if(nt.pr!=null&&nt.pr<1){c.setLineDash([3,2]);c.strokeStyle=col('--rec');c.lineWidth=1;roundRect(c,x,y,w,h,3);c.stroke();c.setLineDash([])}
    if(!drum&&w>10){c.fillStyle='rgba(20,19,48,.5)';c.fillRect(x+w-4,y+2,2,h-4)}});
  if(R.drag&&R.drag.mode==='marquee'){const d=R.drag;c.strokeStyle=col('--accent');c.setLineDash([4,3]);c.strokeRect(Math.min(d.x0,d.x1),Math.min(d.y0,d.y1),Math.abs(d.x1-d.x0),Math.abs(d.y1-d.y0));c.setLineDash([])}
  if(scroll){const ps=pat.notes.map(n=>rowOf(tr,n.p)).filter(v=>v!=null);const mid=ps.length?(Math.min(...ps)+Math.max(...ps))/2:(R.idx[liveBase()]!=null?R.idx[liveBase()]:rows.length/2);wrap.scrollTop=Math.max(0,mid*R.rowH-wrap.clientHeight/2)}
  drawLane()}
function drawLane(){const cv=$('#lane'),tr=selTrack();if(!cv||!tr)return;const pat=curPattern(tr),W=Math.ceil(R.keysW+R.colW*pat.len*16),H=70,dpr=window.devicePixelRatio||1,mode=$('#laneMode').value;
  cv.style.width=W+'px';cv.width=W*dpr;cv.height=H*dpr;cv.style.transform='translateX('+(-$('#rollWrap').scrollLeft)+'px)';const c=cv.getContext('2d');c.scale(dpr,dpr);c.fillStyle='rgba(244,234,214,.55)';c.font='600 10px "IBM Plex Sans",sans-serif';c.fillText(mode==='v'?'Velocity':'Probability',6,14);
  pat.notes.forEach(nt=>{const val=mode==='v'?nt.v:(nt.pr==null?1:nt.pr),x=R.keysW+nt.s*R.colW+1,h=(H-8)*val;c.fillStyle=R.sel.has(nt)?'#f4ead6':tr.color;c.fillRect(x,H-4-h,Math.max(3,Math.min(8,R.colW-3)),h);c.fillRect(x,H-4-h,Math.max(3,R.colW*0.6),2)})}
$('#rollWrap').addEventListener('scroll',()=>{$('#lane').style.transform='translateX('+(-$('#rollWrap').scrollLeft)+'px)'});
function rollPoint(e){const b=$('#roll').getBoundingClientRect();return{x:e.clientX-b.left,y:e.clientY-b.top}}
function noteAt(tr,x,y){const pat=curPattern(tr),ri=Math.floor(y/R.rowH),col=(x-R.keysW)/R.colW;for(let i=pat.notes.length-1;i>=0;i--){const nt=pat.notes[i];if(rowOf(tr,nt.p)===ri&&col>=nt.s&&col<nt.s+Math.max(nt.l,0.5))return nt}return null}
$('#roll').addEventListener('pointerdown',e=>{
  if(e.button!==0)return;const tr=selTrack(),pat=curPattern(tr),{x,y}=rollPoint(e),ri=Math.floor(y/R.rowH),r=R.rows[ri];if(!r)return;
  if(x<R.keysW){audition(tr,r.n);return}
  const col=(x-R.keysW)/R.colW,hit=noteAt(tr,x,y);
  if(hit){if(e.shiftKey){R.sel.has(hit)?R.sel.delete(hit):R.sel.add(hit);renderRoll();return}if(!R.sel.has(hit)){R.sel=new Set([hit])}
    pushUndo();const right=R.keysW+(hit.s+hit.l)*R.colW,mode=!isDrum(tr)&&right-x<7&&hit.l*R.colW>10?'resize':'move';
    R.drag={mode,col0:col,row0:ri,orig:[...R.sel].map(n=>({n,s:n.s,p:n.p,l:n.l,ri:rowOf(tr,n.p)})),moved:false}}
  else if(e.shiftKey){R.drag={mode:'marquee',x0:x,y0:y,x1:x,y1:y};R.sel=new Set()}
  else{pushUndo();const q=gridQ(),s=Math.floor(col/q)*q;if(s>=pat.len*16){undoStack.pop();return}
    const base=isDrum(tr)?r.n:snapScale(r.n),ps=isDrum(tr)?[base]:chordFor(base,$('#chordTool').value),made=ps.map(p=>({p,s,l:isDrum(tr)?q:S.drawLen,v:S.vel}));
    pat.notes.push(...made);R.sel=new Set(made);R.drag={mode:'draw',made,s,moved:true};made.forEach(n=>audition(tr,n.p,0.2,S.vel))}
  try{$('#roll').setPointerCapture(e.pointerId)}catch(err){}renderRoll()});
$('#roll').addEventListener('pointermove',e=>{
  const tr=selTrack(),pat=curPattern(tr),{x,y}=rollPoint(e),d=R.drag,L=pat.len*16,q=gridQ(),col=(x-R.keysW)/R.colW;
  if(!d){const h=noteAt(tr,x,y);let cur='crosshair';if(x<R.keysW)cur='pointer';else if(h){cur=!isDrum(tr)&&R.keysW+(h.s+h.l)*R.colW-x<7?'ew-resize':'grab'}$('#roll').style.cursor=cur;return}
  if(d.mode==='marquee'){d.x1=x;d.y1=y;const c0=(Math.min(d.x0,x)-R.keysW)/R.colW,c1=(Math.max(d.x0,x)-R.keysW)/R.colW,r0=Math.floor(Math.min(d.y0,y)/R.rowH),r1=Math.floor(Math.max(d.y0,y)/R.rowH);
    R.sel=new Set(pat.notes.filter(n=>{const ri=rowOf(tr,n.p);return ri>=r0&&ri<=r1&&n.s+n.l>c0&&n.s<c1}));renderRoll();return}
  if(d.mode==='draw'){if(isDrum(tr))return;const l=clamp(Math.ceil((col-d.s)/q)*q,q,L-d.s);if(l!==d.made[0].l){d.made.forEach(n=>n.l=l);renderRoll()}return}
  if(d.mode==='resize'){const dl=Math.round((col-d.col0)/q)*q;d.orig.forEach(o=>{o.n.l=clamp(o.l+dl,0.25,L-o.s)});d.moved=d.moved||dl!==0;renderRoll();return}
  const ds=Math.round((col-d.col0)/q)*q,dr=Math.floor(y/R.rowH)-d.row0;if(!ds&&!dr&&!d.moved)return;d.moved=true;
  d.orig.forEach(o=>{o.n.s=clamp(o.s+ds,0,L-0.25);const ri=clamp(o.ri+dr,0,R.rows.length-1);const np=R.rows[ri].n;o.n.p=isDrum(tr)?np:snapScale(np)});
  if(dr&&d.orig.length===1)audition(tr,d.orig[0].n.p,0.15);renderRoll()});
function endRollDrag(){const d=R.drag;if(!d)return;R.drag=null;const tr=selTrack();
  if((d.mode==='move'||d.mode==='resize')&&!d.moved)undoStack.pop();else if(d.mode!=='marquee')markDirty();
  if(d.mode==='draw'&&!isDrum(tr))S.drawLen=d.made[0].l;if(d.mode==='resize'&&d.orig.length)S.drawLen=d.orig[0].n.l;renderRoll();drawTimeline()}
$('#roll').addEventListener('pointerup',endRollDrag);$('#roll').addEventListener('pointercancel',endRollDrag);
function deleteNotes(ns){if(!ns.length)return;pushUndo();const pat=curPattern(selTrack()),set=new Set(ns);pat.notes=pat.notes.filter(n=>!set.has(n));R.sel=new Set();markDirty();renderRoll();drawTimeline()}
$('#roll').addEventListener('dblclick',e=>{const {x,y}=rollPoint(e),h=noteAt(selTrack(),x,y);if(h)deleteNotes([h])});
$('#roll').addEventListener('contextmenu',e=>{e.preventDefault();const {x,y}=rollPoint(e),h=noteAt(selTrack(),x,y);if(h)deleteNotes(R.sel.has(h)?[...R.sel]:[h])});
/* lane editing */
let laneDrag=null;
function laneSet(e){const tr=selTrack(),pat=curPattern(tr),b=$('#lane').getBoundingClientRect(),x=e.clientX-b.left,val=clamp(1-(e.clientY-b.top-4)/62,0.01,1),col=Math.floor((x-R.keysW)/R.colW),mode=$('#laneMode').value;
  const hits=pat.notes.filter(n=>Math.floor(n.s)===col&&(!R.sel.size||R.sel.has(n)));const tgt=hits.length?hits:pat.notes.filter(n=>Math.floor(n.s)===col);
  tgt.forEach(n=>{if(mode==='v')n.v=+val.toFixed(2);else{n.pr=+val.toFixed(2);if(n.pr>=0.995)delete n.pr}});drawLane();renderRoll()}
$('#lane').addEventListener('pointerdown',e=>{pushUndo();laneDrag=true;$('#lane').setPointerCapture(e.pointerId);laneSet(e)});
$('#lane').addEventListener('pointermove',e=>{if(laneDrag)laneSet(e)});$('#lane').addEventListener('pointerup',()=>{if(laneDrag){laneDrag=null;markDirty()}});
$('#laneMode').onchange=drawLane;
/* toolbar */
$('#patSel').onchange=e=>{const tr=selTrack();tr.curPat=e.target.value;const {t,c}=selClipObj();if(c&&t===tr&&c.pat!==tr.curPat){pushUndo();c.pat=tr.curPat;toast('The selected clip now plays pattern '+curPattern(tr).name+'.');drawTimeline()}R.sel=new Set();markDirty();renderRoll(true)};
$('#patNew').onclick=()=>{pushUndo();const tr=selTrack(),p=newPattern(nextPatName(tr),curPattern(tr).len);tr.patterns.push(p);tr.curPat=p.id;const {t,c}=selClipObj();if(c&&t===tr)c.pat=p.id;markDirty();renderRoll(true);drawTimeline()};
$('#patDup').onclick=()=>{pushUndo();const tr=selTrack(),p=JSON.parse(JSON.stringify(curPattern(tr)));p.id=uid('p');p.name=nextPatName(tr);tr.patterns.push(p);tr.curPat=p.id;const {t,c}=selClipObj();if(c&&t===tr)c.pat=p.id;markDirty();renderRoll(true);drawTimeline()};
$('#patName').onchange=e=>{pushUndo();curPattern(selTrack()).name=e.target.value.trim().slice(0,30)||'A';markDirty();renderPatternBar();drawTimeline()};
$('#patLen').onchange=e=>{pushUndo();curPattern(selTrack()).len=+e.target.value;markDirty();renderRoll();drawTimeline()};
$('#btnClear').onclick=()=>{const p=curPattern(selTrack());if(!p.notes.length)return;pushUndo();p.notes=[];R.sel=new Set();markDirty();renderRoll();drawTimeline();toast('Cleared pattern '+p.name+'. Undo brings the notes back.')};
$('#btnQuantize').onclick=()=>{const pat=curPattern(selTrack()),q=S.quant||gridQ(),ns=R.sel.size?[...R.sel]:pat.notes;if(!ns.length)return;pushUndo();ns.forEach(n=>{n.s=Math.min(pat.len*16-q,Math.round(n.s/q)*q);n.l=Math.max(q,Math.round(n.l/q)*q)});markDirty();renderRoll();toast('Quantized '+ns.length+' notes to the grid.')};
$('#btnHumanize').onclick=()=>{const pat=curPattern(selTrack()),ns=R.sel.size?[...R.sel]:pat.notes;if(!ns.length)return;pushUndo();ns.forEach(n=>{n.s=clamp(+(n.s+(Math.random()-0.5)*0.3).toFixed(3),0,pat.len*16-0.1);n.v=clamp(+(n.v*(1+(Math.random()-0.5)*0.25)).toFixed(2),0.05,1)});markDirty();renderRoll();toast('Humanized '+ns.length+' notes. Undo restores them.')};
$('#btnBreak').onclick=()=>{pushUndo();const pat=curPattern(selTrack());pat.len=Math.max(pat.len,2);const N=i=>DRUM_NOTES[i],add=(i,s,v)=>pat.notes.push({p:N(i),s,l:1,v});
  [[0,2,10,11],[16,18,26]].forEach((ks,b)=>ks.forEach(s=>add(0,s,1)));[4,7,9,12,15,20,23,25,30].forEach(s=>add(2,s,0.9));[13,29].forEach(s=>add(4,s,0.45));for(let s=0;s<32;s+=2)add(5,s,s%4?0.45:0.75);markDirty();renderRoll();drawTimeline();toast('Inserted a two-bar break pattern on the kick, snare, ghost and hat pads.')};
$('#btnUndo').onclick=undo;$('#btnRedo').onclick=redo;$('#grid').onchange=()=>renderRoll();
function rollKeys(e){const tr=selTrack(),pat=curPattern(tr);if(!R.sel.size&&!(e.ctrlKey&&e.key.toLowerCase()==='a'))return false;const k=e.key;
  if(k==='Delete'||k==='Backspace'){deleteNotes([...R.sel]);return true}
  if((e.ctrlKey||e.metaKey)&&k.toLowerCase()==='a'){R.sel=new Set(pat.notes);renderRoll();return true}
  if((e.ctrlKey||e.metaKey)&&k.toLowerCase()==='d'){pushUndo();const ns=[...R.sel],s0=Math.min(...ns.map(n=>n.s)),s1=Math.max(...ns.map(n=>n.s+n.l)),span=Math.ceil((s1-s0)/4)*4||4;const c=ns.map(n=>({...n,s:n.s+span})).filter(n=>n.s<pat.len*16);pat.notes.push(...c);R.sel=new Set(c);markDirty();renderRoll();return true}
  const mv={ArrowLeft:[-1,0],ArrowRight:[1,0],ArrowUp:[0,1],ArrowDown:[0,-1]}[k];if(mv){pushUndo();const q=gridQ();R.sel.forEach(n=>{n.s=clamp(n.s+mv[0]*q,0,pat.len*16-0.25);if(mv[1]){if(isDrum(tr)){const i=clamp(padIndex(n.p)+mv[1],0,11);n.p=DRUM_NOTES[i]}else n.p=clamp(n.p+mv[1]*(e.shiftKey?12:1),0,127)}});markDirty();renderRoll();return true}
  return false}
function rollPlayhead(){const ph=$('#playhead'),tr=selTrack();if(!tr||!S.playing||PREF.dock!=='roll'){ph.hidden=true;return}const p=posAtNow();if(p==null)return;const c=clipAt(tr,Math.floor(p));
  if(!c||c.pat!==tr.curPat){ph.hidden=true;return}const pat=curPattern(tr),L=pat.len*16,ps=((p-c.start*16)%L+L)%L;ph.hidden=false;ph.style.transform='translateX('+(R.keysW+ps*R.colW)+'px)'}
