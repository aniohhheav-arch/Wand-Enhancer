'use strict';
/* Song scheduler, arpeggiator, recording, offline rendering and MIDI files.
   Positions are global sixteenth-note steps (16 per bar, 4/4 only). */
const LOOK=0.12;let segStep=0,timer=0,nextT=0,step=0,stepLog=[],pendingOffs=[],stopAtStep=null;
const stepDur=()=>60/S.bpm/4;
const evCmp=(a,b)=>Math.abs(a.t-b.t)<1e-4?(a.on?1:0)-(b.on?1:0):a.t-b.t;
function clipAt(tr,k){let best=null;for(const c of tr.clips){if(k>=c.start*16&&k<(c.start+c.len)*16&&(!best||c.start>=best.start))best=c}return best}
const ARP_MODES=[['up','Up'],['down','Down'],['updown','Up & down'],['random','Random'],['chord','Chord']];
/* Stateless arpeggiator: what plays at step position tau depends only on which notes are held then. */
function arpNotesAt(notes,arp,tau){const held=notes.filter(n=>n.s<=tau+1e-6&&tau<n.s+n.l-1e-6);if(!held.length)return [];
  const r=divBeats(arp.rate)*4,start=Math.min(...held.map(n=>n.s)),idx=Math.round((tau-start)/r),vel=Math.max(...held.map(n=>n.v));
  const base=[...new Set(held.map(n=>n.p))].sort((a,b)=>a-b),seq=[];for(let o=0;o<arp.oct;o++)base.forEach(p=>seq.push(p+12*o));
  const dur=r*arp.gate;if(arp.mode==='chord')return seq.map(p=>({p,l:dur,v:vel}));
  let p;if(arp.mode==='down')p=seq[seq.length-1-idx%seq.length];else if(arp.mode==='updown'){const pp=seq.length>1?seq.concat(seq.slice(1,-1).reverse()):seq;p=pp[idx%pp.length]}
  else if(arp.mode==='random'){const x=Math.sin((Math.round(tau*1000)+idx)*12.9898)*43758.5453;p=seq[Math.floor((x-Math.floor(x))*seq.length)]}else p=seq[idx%seq.length];return [{p,l:dur,v:vel}]}
function stepEvents(k,T,sd,out,startStep,endStep){
  for(const tr of S.tracks){if(!audible(tr))continue;if(isAudio(tr)){audioClipEvents(tr,k,T,sd,out,startStep==null?k:startStep,endStep);continue}const c=clipAt(tr,k);if(!c)continue;const pat=tr.patterns.find(p=>p.id===c.pat);if(!pat)continue;
    const L=Math.round(pat.len*16),ps=((k-Math.round(c.start*16))%L+L)%L,sw=k%2===1?S.swing*sd*0.66:0;
    if(tr.arp&&tr.arp.on){const r=divBeats(tr.arp.rate)*4;for(let m=Math.ceil(ps/r-1e-9);m*r<ps+1-1e-9;m++){const tau=m*r;arpNotesAt(pat.notes,tr.arp,tau).forEach(a=>out.push({t:T+(tau-ps)*sd+(Math.abs(tau%2-1)<1e-6?sw:0),on:true,tr,nt:{p:a.p,v:a.v},dur:a.l*sd}))}continue}
    for(const nt of pat.notes){if(nt.s>=L||Math.floor(nt.s)!==ps)continue;if(nt.pr!=null&&nt.pr<1&&Math.random()>nt.pr)continue;out.push({t:T+(nt.s-ps)*sd+sw,on:true,tr,nt,dur:nt.l*sd})}}}
function runEvents(ev,horizon,onDeferred,visual){
  ev.sort(evCmp);
  while(ev.length){const e=ev.shift();
    if(e.click){clickSound(e.t,e.acc);continue}
    if(e.audio){playAudioClip(e);continue}
    if(e.on){e.tr._evDur=e.dur;const h=trigger(e.tr,e.nt.p,e.nt.v,e.t);e.tr._evDur=null;if(visual)flashNote(e.tr,e.nt.p,e.t,e.dur);if(!h)continue;const off={t:e.t+e.dur,h};
      if(off.t<horizon){let i=ev.findIndex(x=>evCmp(off,x)<0);if(i<0)i=ev.length;ev.splice(i,0,off)}else onDeferred(off)}
    else e.h.off(e.t)}}
function clickSound(t,acc){const o=O('square',acc?1760:1180,t,Z),g=G(0);g.gain.setValueAtTime(acc?0.22:0.12,t);g.gain.exponentialRampToValueAtTime(0.001,t+0.04);o.connect(F('bandpass',acc?1760:1180,4)).connect(g).connect(GR.bus);o.stop(t+0.06)}
function play(){if(rendering||!ensureAudio()||S.playing)return;S.playing=true;step=Math.round((S.loop.on?S.loop.start:S.cursor)*16);nextT=ctx.currentTime+0.08;stepLog=[];segStep=step;stopAtStep=null;resetIntegrated();
  S.tracks.forEach(t=>{if(t._rt)buildTrackMod(t,nextT)});timer=setInterval(tick,25);tick();renderTransport();audioRecSync()}
function stop(){if(!S.playing){if(ctx)panic();return}S.playing=false;clearInterval(timer);S.rec=false;flushOffs();stopAudioClips();audioRecSync();renderTransport()}
function flushOffs(){const now=ctx?ctx.currentTime:0;pendingOffs.forEach(o=>o.h.off(now));pendingOffs=[]}
function panic(){flushOffs();stopAudioClips();S.tracks.forEach(tr=>{if(tr._rt)[...tr._rt.live].forEach(h=>h.off(ctx.currentTime));tr._arpHeld=null});held.clear();sustained.length=0;keyCounts.clear();$$('.key.on').forEach(k=>k.classList.remove('on'))}
function tick(){
  const horizon=ctx.currentTime+LOOK,q=[],sd=stepDur();
  while(nextT<horizon){
    if(S.loop.on&&S.loop.end>S.loop.start&&step>=S.loop.end*16){step=Math.round(S.loop.start*16);segStep=step;audioRecWrap(nextT,step)}
    if(!S.loop.on&&step>=songEnd()*16){if(stopAtStep==null){stopAtStep=nextT;setTimeout(()=>{if(S.playing)stop()},(nextT-ctx.currentTime)*1000+50)}break}
    const k=step;stepLog.push([k,nextT,sd]);if(stepLog.length>128)stepLog.shift();
    if(S.metro&&k%4===0)q.push({t:nextT,click:true,acc:k%16===0});stepEvents(k,nextT,sd,q,segStep,S.loop.on&&S.loop.end>S.loop.start?S.loop.end*16:Infinity);nextT+=sd;step=k+1}
  const due=pendingOffs.filter(o=>o.t<horizon);pendingOffs=pendingOffs.filter(o=>o.t>=horizon);
  runEvents(q.concat(due),horizon,o=>pendingOffs.push(o),true);liveArpTick(horizon)}
function posAt(t){if(!S.playing||!stepLog.length)return null;for(let i=stepLog.length-1;i>=0;i--){const[k,T,sd]=stepLog[i];if(T<=t)return k+(t-T)/sd}return stepLog[0][0]}
const posAtNow=()=>ctx&&S.playing?posAt(ctx.currentTime):null;
/* ───────── live input, ensembles and live arpeggio ───────── */
const held=new Map(),sustained=[],keyCounts=new Map();
function liveTargets(n,v,ch,src){const tr=selTrack();if(!tr)return [];const vel=Math.round(v*127);
  const pool=tr.input&&tr.input.group?S.tracks.filter(t=>t.input&&t.input.group===tr.input.group):[tr];
  return pool.filter(t=>!isAudio(t)).filter(t=>!t.input||((t.input.ch||0)===0||!ch||t.input.ch===ch)&&n>=t.input.lo&&n<=t.input.hi&&vel>=t.input.vlo&&vel<=t.input.vhi).map(t=>[t,ENG[t.engine].drum&&src!=='midi'?DRUM_NOTES[((n%12)+12)%12]:n+((t.input&&t.input.tr)||0)])}
function liveOn(n,v,src,ch){
  if(rendering||!ensureAudio())return;const targets=liveTargets(n,v,ch,src);if(!targets.length)return;const key=src+':'+n;if(held.has(key))liveOff(n,src);
  const ent={n,v,hs:[],rec:S.rec&&S.playing?posAt(ctx.currentTime):null,targets};
  targets.forEach(([tr,nn])=>{if(tr.arp&&tr.arp.on){(tr._arpHeld=tr._arpHeld||new Map()).set(nn,v);if(!tr._arpClock)tr._arpClock={next:ctx.currentTime+0.01,i:0,start:ctx.currentTime};liveArpTick(ctx.currentTime+0.05)}
    else{const h=trigger(tr,nn,v,ctx.currentTime);if(h)ent.hs.push(h)}});
  held.set(key,ent);keyVisual(n,true);studioNote(v);quest('play')}
function liveOff(n,src){const key=src+':'+n,e=held.get(key);if(!e)return;held.delete(key);keyVisual(n,false);
  e.targets.forEach(([tr,nn])=>{if(tr._arpHeld){tr._arpHeld.delete(nn);if(!tr._arpHeld.size){tr._arpHeld=null;tr._arpClock=null}}});
  e.hs.forEach(h=>{if(S.sustain)sustained.push(h);else h.off(ctx.currentTime)});
  if(e.rec!=null&&S.rec&&S.playing){const end=posAt(ctx.currentTime);e.targets.forEach(([tr,nn])=>commitRec(tr,nn,e.v,e.rec,end))}}
function liveArpTick(horizon){S.tracks.forEach(tr=>{if(!tr._arpHeld||!tr._arpClock)return;const a=tr.arp,r=divBeats(a.rate)*4,sd=stepDur(),c=tr._arpClock;
  while(c.next<horizon){const notes=[...tr._arpHeld].map(([p,v])=>({p,s:0,l:1e9,v}));arpNotesAt(notes,a,c.i*r).forEach(x=>{const h=trigger(tr,x.p,x.v,c.next);if(h)h.off(c.next+x.l*sd)});c.i++;c.next+=r*sd}})}
function setSustain(on){S.sustain=on;const l=$('#susLed');if(l)l.classList.toggle('on',on);if(!on&&ctx)sustained.splice(0).forEach(h=>h.off(ctx.currentTime))}
function commitRec(tr,n,v,start,end){
  let c=clipAt(tr,Math.floor(start));if(!c){const pat=curPattern(tr),bar=Math.floor(start/16);c={id:uid('c'),pat:pat.id,start:bar,len:pat.len};
    const nextC=tr.clips.filter(x=>x.start>bar).sort((a,b)=>a.start-b.start)[0];if(nextC)c.len=Math.max(0.25,Math.min(c.len,nextC.start-bar));tr.clips.push(c)}
  const pat=tr.patterns.find(p=>p.id===c.pat),L=pat.len*16,q=S.quant;let s=((start-c.start*16)%L+L)%L,len=end-start;if(len<=0)len+=(S.loop.end-S.loop.start)*16;
  if(q){s=(Math.round(s/q)*q)%L;len=Math.max(q,Math.round(len/q)*q)}else len=Math.max(0.25,len);
  pat.notes.push({p:ENG[tr.engine].drum?DRUM_NOTES[padIndex(n)]:n,s:+s.toFixed(3),l:+len.toFixed(3),v:+v.toFixed(2)});markDirty();renderRoll();renderArrange();quest('record')}
/* ───────── offline rendering ───────── */
let renderOnly=null,renderNoMaster=false;
async function renderOffline(opts,onProgress){
  const OAC=window.OfflineAudioContext||window.webkitOfflineAudioContext;if(!OAC)throw new Error('offline rendering is not supported in this browser');
  const sr=opts.sampleRate||44100,sd=stepDur(),from=Math.round(opts.startBar*16),to=Math.round(opts.endBar*16),tail=opts.tail==null?2.5:opts.tail,dur=(to-from)*sd+tail,len=Math.ceil(sr*dur);
  const liveCtx=ctx,liveGR=Object.assign({},GR),stash=S.tracks.map(t=>({rt:t._rt,fx:t.fx.map(f=>f._inst)})),mstash=S.masterFx.map(f=>f._inst),rstash=[S.returns.rev._inst,S.returns.dly._inst];
  await ensureProjectOrch();
  rendering=true;OFF=true;renderOnly=opts.only||null;renderNoMaster=!!opts.noMaster;
  try{ctx=new OAC(2,len,sr);await loadWorklets(ctx);S.tracks.forEach(t=>{t._rt=null;t.fx.forEach(f=>f._inst=null)});S.masterFx.forEach(f=>f._inst=null);S.returns.rev._inst=S.returns.dly._inst=null;
    Object.keys(GR).forEach(k=>delete GR[k]);buildGraph();S.tracks.forEach(t=>buildTrackMod(t,0));
    const ev=[];for(let k=from;k<to;k++)stepEvents(k,(k-from)*sd,sd,ev,from,to);runEvents(ev,Infinity,()=>{},false);
    const c=ctx;for(let s=1;s<Math.floor(dur);s++)c.suspend(s).then(()=>{onProgress&&onProgress(s/dur);c.resume()}).catch(()=>{});
    return await c.startRendering();
  }finally{ctx=liveCtx;Object.keys(GR).forEach(k=>delete GR[k]);Object.assign(GR,liveGR);S.tracks.forEach((t,i)=>{t._rt=stash[i].rt;t.fx.forEach((f,j)=>f._inst=stash[i].fx[j])});
    S.masterFx.forEach((f,i)=>f._inst=mstash[i]);S.returns.rev._inst=rstash[0];S.returns.dly._inst=rstash[1];OFF=false;rendering=false;renderOnly=null;renderNoMaster=false}}
function encodeWav(b,bits=16){const ch=b.numberOfChannels,len=b.length,sr=b.sampleRate,bps=bits/8,fmt=bits===32?3:1,dv=new DataView(new ArrayBuffer(44+len*ch*bps));const w=(o,s)=>{for(let i=0;i<s.length;i++)dv.setUint8(o+i,s.charCodeAt(i))};
  w(0,'RIFF');dv.setUint32(4,36+len*ch*bps,true);w(8,'WAVE');w(12,'fmt ');dv.setUint32(16,16,true);dv.setUint16(20,fmt,true);dv.setUint16(22,ch,true);dv.setUint32(24,sr,true);dv.setUint32(28,sr*ch*bps,true);dv.setUint16(32,ch*bps,true);dv.setUint16(34,bits,true);w(36,'data');dv.setUint32(40,len*ch*bps,true);
  const data=[];for(let c=0;c<ch;c++)data.push(b.getChannelData(c));let o=44;
  for(let i=0;i<len;i++)for(let c=0;c<ch;c++){const s=data[c][i];if(bits===32){dv.setFloat32(o,s,true)}else{const x=clamp(s,-1,1);if(bits===24){const v=Math.round(x<0?x*0x800000:x*0x7fffff);dv.setUint8(o,v&255);dv.setUint8(o+1,(v>>8)&255);dv.setUint8(o+2,(v>>16)&255)}else dv.setInt16(o,x<0?x*0x8000:x*0x7fff,true)}o+=bps}
  return new Uint8Array(dv.buffer)}
const CRC=(()=>{const t=new Uint32Array(256);for(let n=0;n<256;n++){let c=n;for(let k=0;k<8;k++)c=c&1?0xEDB88320^(c>>>1):c>>>1;t[n]=c>>>0}return t})();
function crc32(u){let c=~0;for(let i=0;i<u.length;i++)c=CRC[(c^u[i])&255]^(c>>>8);return (~c)>>>0}
function zipFiles(files){const parts=[],cds=[];let off=0;
  files.forEach(f=>{const nm=new TextEncoder().encode(f.name),crc=crc32(f.data),sz=f.data.length,lh=new DataView(new ArrayBuffer(30)),cd=new DataView(new ArrayBuffer(46));
    lh.setUint32(0,0x04034b50,true);lh.setUint16(4,20,true);lh.setUint16(12,0x21,true);lh.setUint32(14,crc,true);lh.setUint32(18,sz,true);lh.setUint32(22,sz,true);lh.setUint16(26,nm.length,true);
    cd.setUint32(0,0x02014b50,true);cd.setUint16(4,20,true);cd.setUint16(6,20,true);cd.setUint16(14,0x21,true);cd.setUint32(16,crc,true);cd.setUint32(20,sz,true);cd.setUint32(24,sz,true);cd.setUint16(28,nm.length,true);cd.setUint32(42,off,true);
    parts.push(lh,nm,f.data);cds.push(cd,nm);off+=30+nm.length+sz});
  const cdSize=cds.reduce((a,x)=>a+(x.byteLength||x.length),0),eo=new DataView(new ArrayBuffer(22));eo.setUint32(0,0x06054b50,true);eo.setUint16(8,files.length,true);eo.setUint16(10,files.length,true);eo.setUint32(12,cdSize,true);eo.setUint32(16,off,true);
  return new Blob(parts.concat(cds,[eo]),{type:'application/zip'})}
/* ───────── MIDI files (Standard MIDI File, format 1, 480 PPQ) ───────── */
function vlq(n){const b=[n&127];while(n>>=7)b.unshift((n&127)|128);return b}
function songNotes(tr,from,to){const out=[];tr.clips.forEach(c=>{const pat=tr.patterns.find(p=>p.id===c.pat);if(!pat)return;const L=pat.len*16,cs=c.start*16,ce=(c.start+c.len)*16;
  for(let rep=0;cs+rep*L<ce;rep++)pat.notes.forEach(n=>{const s=cs+rep*L+n.s;if(s>=ce||s<from||s>=to)return;out.push({p:n.p,s:s-from,l:n.l,v:n.v})})});return out.sort((a,b)=>a.s-b.s)}
function exportMidi(from=0,to=songEnd()*16){const PPQ=480,tk=PPQ/4,tracks=[];
  const tempo=Math.round(60000000/S.bpm);tracks.push([0,0xFF,0x51,3,(tempo>>16)&255,(tempo>>8)&255,tempo&255,0,0xFF,0x58,4,4,2,24,8,0,0xFF,0x2F,0]);
  S.tracks.forEach((tr,i)=>{const ch=ENG[tr.engine].drum?9:i%16===9?10:i%16,ev=[];songNotes(tr,from,to).forEach(n=>{const s=Math.round(n.s*tk),e=Math.max(s+1,Math.round((n.s+n.l)*tk)),vel=clamp(Math.round(n.v*127),1,127),p=clamp(Math.round(n.p),0,127);ev.push([s,1,0x90|ch,p,vel],[e,0,0x80|ch,p,0])});
    ev.sort((a,b)=>a[0]-b[0]||a[1]-b[1]);const name=new TextEncoder().encode(tr.name.slice(0,60));let d=[0,0xFF,0x03,...vlq(name.length),...name],last=0;ev.forEach(x=>{d.push(...vlq(x[0]-last),x[2],x[3],x[4]);last=x[0]});d.push(0,0xFF,0x2F,0);tracks.push(d)});
  const hd=[0x4d,0x54,0x68,0x64,0,0,0,6,0,1,(tracks.length>>8)&255,tracks.length&255,(PPQ>>8)&255,PPQ&255],chunks=[hd];
  tracks.forEach(d=>chunks.push([0x4d,0x54,0x72,0x6b,(d.length>>>24)&255,(d.length>>16)&255,(d.length>>8)&255,d.length&255],d));return new Uint8Array(chunks.flat())}
function parseMidi(buf){const u=new Uint8Array(buf);let p=0;const rd=n=>{let v=0;for(let i=0;i<n;i++)v=(v<<8)|u[p++];return v>>>0},str=n=>{let s='';for(let i=0;i<n;i++)s+=String.fromCharCode(u[p++]);return s},vq=()=>{let v=0,b;do{b=u[p++];v=(v<<7)|(b&127)}while(b&128&&p<u.length);return v};
  if(str(4)!=='MThd')throw new Error('not a Standard MIDI File');const hl=rd(4),fmt=rd(2),nt=rd(2),div=rd(2);p=8+hl;if(div&0x8000)throw new Error('SMPTE-timed MIDI files are not supported');
  const out={ppq:div,tempo:null,tracks:[]};
  for(let t=0;t<nt&&p<u.length;t++){if(str(4)!=='MTrk')throw new Error('damaged track chunk');const ln=rd(4),end=p+ln;let tick=0,run=0,name='';const on={},notes=[];
    while(p<end){tick+=vq();let st=u[p];if(st&0x80){p++;run=st}else st=run;const type=st&0xF0,ch=st&15;
      if(st===0xFF){const mt=u[p++],l=vq();if(mt===0x51&&l===3&&out.tempo==null)out.tempo=60000000/((u[p]<<16)|(u[p+1]<<8)|u[p+2]);if(mt===0x03)name=new TextDecoder().decode(u.subarray(p,p+l));p+=l}
      else if(st===0xF0||st===0xF7){p+=vq()}
      else if(type===0x90||type===0x80){const n=u[p++],v=u[p++],k=ch+':'+n;if(type===0x90&&v>0)(on[k]=on[k]||[]).push([tick,v]);else{const s=on[k]&&on[k].shift();if(s)notes.push({ch,p:n,s:s[0],e:tick,v:s[1]/127})}}
      else if(type===0xC0||type===0xD0)p+=1;else p+=2}
    p=end;if(notes.length)out.tracks.push({name,notes})}
  return out}
function importMidiData(buf){const m=parseMidi(buf),tk=m.ppq/4;if(!m.tracks.length)throw new Error('the file contains no notes');pushUndo();if(m.tempo)S.bpm=clamp(Math.round(m.tempo),40,240);const made=[];
  m.tracks.slice(0,16).forEach((t,i)=>{const drum=t.notes.every(n=>n.ch===9),tr=newTrack(drum?'drums-acoustic':'keys-grand',{name:t.name||('MIDI '+(i+1))}),maxS=Math.max(...t.notes.map(n=>n.e))/tk,bars=clamp(Math.ceil(maxS/16),1,64);
    const pat=curPattern(tr);pat.len=bars;pat.notes=t.notes.map(n=>({p:n.p,s:+(n.s/tk).toFixed(3),l:Math.max(0.1,+((n.e-n.s)/tk).toFixed(3)),v:+n.v.toFixed(2)})).filter(n=>n.s<bars*16);tr.clips=[{id:uid('c'),pat:pat.id,start:0,len:bars}];S.tracks.push(tr);made.push(tr)});
  if(ctx)applyMix();markDirty();return made}
