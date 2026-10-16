'use strict';
/* Recorded instruments: multisampled brass, strings, woodwinds and mallets from VSCO 2 Community Edition
   (CC0 1.0, public domain), prepared by tools/build-samples.py (trimmed, tuned to A4 = 440 Hz per zone,
   real dynamic layers, crossfade-looped sustains). Two engines:
   - orch:    one instrument with velocity or mod-wheel dynamics, round robins, legato transitions and
              articulations (sustain, short, pizzicato, mute) where the recordings exist.
   - section: several different instruments playing together. Each note goes to the player whose range
              and current load suit it (chords are spread across the section by register, a lone note is
              doubled in unison by two different instruments), and every player has its own seat, entry
              timing and round-robin cycle. Nothing is detuned to fake an ensemble. */
KIND.rec='Recorded';
const ORCH={buf:new Map(),loading:new Map(),failed:new Set()};
const LIB_IDS=()=>Object.keys(typeof SAMPLE_LIB==='object'?SAMPLE_LIB:{});
const ART_NAMES={sus:'Sustain',stac:'Short (spiccato / staccato)',pizz:'Pizzicato',mute:'Muted',pluck:'Plucked',hit:'Struck'};
async function assetBytes(f){if(window.native&&window.native.readAsset)return await window.native.readAsset(f);const r=await fetch(f);if(!r.ok)throw new Error('HTTP '+r.status);return await r.arrayBuffer()}
function orchArt(iid,art){const L=SAMPLE_LIB[iid];if(!L)return null;return L.arts[art]||L.arts.sus||Object.values(L.arts)[0]}
function orchLoad(iid,art){const L=SAMPLE_LIB[iid],a=orchArt(iid,art);if(!a)return Promise.resolve(false);const key=iid+':'+(L.arts[art]?art:'*');
  if(ORCH.loading.has(key))return ORCH.loading.get(key);
  const p=Promise.all(a.zones.map(async z=>{if(ORCH.buf.has(z.f)||ORCH.failed.has(z.f))return;try{const b=await assetBytes(z.f);ORCH.buf.set(z.f,await decoderCtx().decodeAudioData(b))}catch(e){ORCH.failed.add(z.f);errLog.push('sample '+z.f+': '+e.message)}}))
    .then(()=>{const miss=a.zones.filter(z=>ORCH.failed.has(z.f)).length;if(miss)errLog.push(L.name+': '+miss+' of '+a.zones.length+' recordings could not be loaded');return !miss});
  ORCH.loading.set(key,p);return p}
function orchReady(iid,art){const a=orchArt(iid,art);return !!a&&a.zones.every(z=>ORCH.buf.has(z.f))}
/* Every recorded instrument a track (or a whole project) needs, loaded before playing or rendering. */
function trackOrchNeeds(tr){const P=tr.params;if(tr.engine==='orch')return [[P.inst,P.art]];if(tr.engine==='section')return (BANDS[P.band]||[]).map(p=>[p.inst,partArt(p,P.art)]);return []}
function ensureTrackSamples(tr){return Promise.all(trackOrchNeeds(tr).map(([i,a])=>orchLoad(i,a)))}
function ensureProjectOrch(){return Promise.all(S.tracks.map(ensureTrackSamples))}
/* nearest recorded root (a root above wins ties: pitching down sounds more natural), then round robin */
function orchZone(a,n,layer,rr){let best=null,bd=1e9;for(const z of a.zones){const d=Math.abs(n-z.root)+Math.abs(z.v-layer)*4+(z.root<n?0.01:0);if(d<bd){bd=d;best=z}}
  if(!best)return null;const vs=a.zones.filter(z=>z.root===best.root&&z.v===best.v);return vs[rr%vs.length]}
/* Play one recorded note. Returns {amp, srcs, layers} or null when the recordings are not loaded yet. */
function orchNote(tr,iid,art,n,v,t,dst,P,o={}){
  const a=orchArt(iid,art);if(!a)return null;const sus=a.kind==='sus',dynMW=sus&&P.dyn==='mw';
  const lvl=dynMW?(tr._mw!=null?tr._mw:0.6):v,x=lvl*(a.layers-1),l0=Math.max(0,Math.floor(x)),l1=Math.min(a.layers-1,l0+1),fr=x-l0;
  const key=iid+art+n;tr._rrc=tr._rrc||{};const rr=tr._rrc[key]=(tr._rrc[key]||0)+1+(o.rrOffset||0);
  const amp=G(0),srcs=[],gains=[];const start=t+(o.delay||0);
  [[l0,Math.cos(fr*Math.PI/2)],[l1,Math.sin(fr*Math.PI/2)]].forEach(([l,w],i)=>{if(i===1&&l1===l0)return;const z=orchZone(a,n,l,rr);if(!z)return;const b=ORCH.buf.get(z.f);if(!b)return;
    const s=ctx.createBufferSource();s.buffer=b;s._d=(n-z.root)*100+z.tune+(P.tune||0)*100+(o.detune||0);s.detune.value=s._d+((tr._bend)||0)*100;
    if(sus&&z.ls&&P.loop!==false){s.loop=true;s.loopStart=z.ls;s.loopEnd=z.le}
    const g=G(l1===l0?1:w);s.connect(g).connect(amp);const off=Math.min(o.offset||0,b.duration*0.5);s.start(start,off);if(!s.loop)s.stop(start+b.duration-off+0.05);srcs.push(s);gains.push([g,l])});
  if(!srcs.length){orchLoad(iid,art);return null}
  // a gentle loudness slope inside each recorded layer, so velocity is continuous rather than stepped
  const peak=(o.level==null?1:o.level)*(dynMW?1:0.55+0.45*v),att=Math.max(0.003,P.attack||0)+(o.fade||0);
  amp.gain.setValueAtTime(0,start);amp.gain.linearRampToValueAtTime(peak,start+att);
  let node=amp;if(P.tone&&Math.abs(P.tone)>0.02){const sh=F('highshelf',2800,0.7);sh.gain.value=P.tone*10;amp.connect(sh);node=sh}
  node.connect(dst);
  const h={amp,srcs,setDyn(m){if(!dynMW||gains.length<2)return;const xx=m*(a.layers-1),f2=clamp(xx-l0,0,1);gains[0][0].gain.setTargetAtTime(Math.cos(f2*Math.PI/2),ctx.currentTime,0.05);gains[1][0].gain.setTargetAtTime(Math.sin(f2*Math.PI/2),ctx.currentTime,0.05)}};
  return h}
function orchRelease(P,a){return a&&a.kind==='sus'?Math.max(0.05,P.release):Math.max(0.15,P.release*1.5)}
const ORCH_COMMON=()=>[C('dyn','Dynamics from','Expression',[['vel','Velocity'],['mw','Mod wheel (crossfades recorded dynamics)']],'vel'),
  P('attack','Extra attack','Expression',0,3,0,{fmt:FMT.s}),P('release','Release','Expression',0.05,4,0.35,{log:true,fmt:FMT.s}),
  P('tone','Tone','Expression',-1,1,0,{fmt:v=>(v>0?'+':'')+Math.round(v*10)+' dB air'}),P('tune','Pitch','Expression',-12,12,0,{int:true,fmt:FMT.st}),...OUT_GROUP(0.85)];
ENG.orch={name:'Recorded Instrument',kind:KIND.rec,family:'strings',
  desc:'Real multisampled recordings (VSCO 2 Community Edition, CC0): several dynamic layers, round robins, tuned zones and looped sustains. Choose the articulation; with "Mod wheel" dynamics, sustained notes crossfade between the recorded soft and loud takes as you move the wheel.',
  params:[C('inst','Instrument','Source',LIB_IDS().map(k=>[k,SAMPLE_LIB[k].name]),'violin',{open:true}),C('art','Articulation','Source',Object.entries(ART_NAMES),'sus'),
    C('mode','Playing','Source',[['poly','Polyphonic'],['legato','Legato (mono, connected notes)'],['slide','Slide (mono, glides between notes)']],'poly'),P('glide','Slide time','Source',0.02,0.6,0.12,{log:true,fmt:FMT.s}),...ORCH_COMMON()],
  on(tr,n,v,t,out,P,M){const a=orchArt(P.inst,P.art);if(!a){M.missing='instrument '+P.inst;return null}
    const dst=outStage(P,M,out),con=M.prev!=null&&P.mode!=='poly'&&a.kind==='sus';
    // legato: start the new recording past its attack and crossfade, so phrases connect like one breath or bow
    const h=orchNote(tr,P.inst,P.art,con&&P.mode==='slide'?M.prev:n,v,t,dst,P,con?{offset:0.09,fade:0.06}:{});if(!h)return null;
    if(con&&P.mode==='slide'){const span=(n-M.prev)*100;h.srcs.forEach(s=>{s._d+=span;s.detune.setValueAtTime(s.detune.value,t);s.detune.linearRampToValueAtTime(s._d+((tr._bend)||0)*100,t+P.glide)})}
    const vo=voice(h.amp,h.srcs,orchRelease(P,a));vo.setMod=m=>h.setDyn(m==null?0.6:m);return vo}};

/* ───────── sections ───────── */
const BANDS={
  brass:[{id:'tpt',name:'Trumpets',inst:'trumpet',lo:54,hi:84,sweet:70,pan:-0.08,delay:0,level:0.9},{id:'hn',name:'Horns',inst:'horn',lo:40,hi:77,sweet:60,pan:-0.5,delay:0.016,level:1},
    {id:'tbn',name:'Trombones',inst:'trombone',lo:34,hi:72,sweet:53,pan:0.32,delay:0.009,level:0.95},{id:'tba',name:'Tuba',inst:'tuba',lo:26,hi:55,sweet:40,pan:0.55,delay:0.022,level:1}],
  strings:[{id:'vln',name:'Violins',inst:'violins',lo:55,hi:103,sweet:76,pan:-0.5,delay:0.004,level:0.9},{id:'vla',name:'Violas',inst:'violas',lo:48,hi:88,sweet:63,pan:0.12,delay:0.012,level:1},
    {id:'vc',name:'Cellos',inst:'cellos',lo:36,hi:76,sweet:50,pan:0.42,delay:0.008,level:1},{id:'cb',name:'Basses',inst:'bass',lo:28,hi:58,sweet:38,pan:0.62,delay:0.02,level:0.95}],
  winds:[{id:'fl',name:'Flute',inst:'flute',lo:60,hi:96,sweet:79,pan:-0.2,delay:0.006,level:0.9},{id:'ob',name:'Oboe',inst:'oboe',lo:58,hi:91,sweet:72,pan:0.18,delay:0.012,level:0.85},
    {id:'cl',name:'Clarinet',inst:'clarinet',lo:50,hi:91,sweet:64,pan:-0.38,delay:0,level:0.9},{id:'bn',name:'Bassoon',inst:'bassoon',lo:34,hi:75,sweet:50,pan:0.38,delay:0.015,level:0.95}]};
function partArt(p,art){const L=typeof SAMPLE_LIB==='object'&&SAMPLE_LIB[p.inst];if(!L)return art;if(L.arts[art])return art;if(art==='mute')return 'sus';if(art==='pizz'&&L.arts.stac)return 'stac';return L.arts.sus?'sus':Object.keys(L.arts)[0]}
/* Pick the players for a note: in range, nearest their sweet spot, avoiding piling a chord onto one part. */
function sectionPick(tr,band,n){const load=tr._secLoad||(tr._secLoad={});
  const sc=band.map(p=>{const inR=n>=p.lo&&n<=p.hi;return {p,s:Math.abs(n-p.sweet)+(inR?0:30+Math.min(Math.abs(n-p.lo),Math.abs(n-p.hi))*2)+(load[p.id]||0)*7}}).sort((a,b)=>a.s-b.s);return sc}
ENG.section={name:'Recorded Section',kind:KIND.rec,family:'brass',
  desc:'A real ensemble of different recorded instruments. Chords are voiced across the section by register (top notes to the high instruments, the bass note to the low ones); a single note is played in unison by two different instruments. Each player has its own seat, entry timing and round robin.',
  params:[C('band','Section','Source',[['brass','Brass (trumpet, horn, trombone, tuba)'],['strings','Strings (violins, violas, cellos, basses)'],['winds','Woodwinds (flute, oboe, clarinet, bassoon)']],'brass'),
    C('art','Articulation','Source',Object.entries(ART_NAMES),'sus'),C('unison','Single notes','Source',[['double','Doubled by two instruments'],['single','One instrument']],'double'),
    P('loose','Player timing','Ensemble',0,1,0.5,{fmt:FMT.pct}),P('width','Seating width','Ensemble',0,1,0.7,{fmt:FMT.pct}),...ORCH_COMMON()],
  on(tr,n,v,t,out,P,M){const band=BANDS[P.band]||BANDS.brass,dst=outStage(P,M,out),rk=sectionPick(tr,band,n);
    // a note arriving within 40 ms of another is part of a chord: drop the unison doubles of the earlier notes
    const now=t,recent=(tr._secRecent||[]).filter(x=>Math.abs(x.t-now)<0.04);tr._secRecent=recent;
    const chord=recent.length>0||[...(tr._rt?tr._rt.live:[])].some(h=>h.sec);recent.forEach(x=>x.dbl&&x.dbl.forEach(d=>{hold(d.amp.gain,Math.max(now,ctx.currentTime));d.amp.gain.setTargetAtTime(0,Math.max(now,ctx.currentTime),0.008);d.srcs.forEach(s=>{try{s.stop(Math.max(now,ctx.currentTime)+0.08)}catch(e){}})}));
    const n2=P.unison==='double'&&!chord?2:1,parts=rk.slice(0,n2).map(x=>x.p),hs=[],load=tr._secLoad;
    parts.forEach((p,i)=>{const pan=ctx.createStereoPanner();pan.pan.value=p.pan*P.width;pan.connect(dst);
      const jit=((n*7919+(tr._rrc&&tr._rrc[p.inst+n]||0)*104729)%97)/97-0.5,delay=P.loose*(p.delay+jit*0.008),art=partArt(p,P.art);
      const h=orchNote(tr,p.inst,art,n,v,t,pan,P,{delay:Math.max(0,delay),level:p.level*(i?0.8:1)*(n2>1?0.78:1),rrOffset:i});if(h){h.part=p;h.art=art;hs.push(h);load[p.id]=(load[p.id]||0)+1}});
    if(!hs.length)return null;
    tr._secRecent.push({t:now,dbl:hs.slice(1)});
    const a=orchArt(hs[0].part.inst,hs[0].art);
    const rel=orchRelease(P,a);let done=false;
    const vo={oscs:hs.flatMap(h=>h.srcs),sec:true,off(t2){if(done)return;done=true;t2=Math.max(t2,ctx.currentTime);hs.forEach(h=>{load[h.part.id]=Math.max(0,(load[h.part.id]||1)-1);hold(h.amp.gain,t2);h.amp.gain.setTargetAtTime(0,t2,Math.max(0.002,rel/5));h.srcs.forEach(s=>{try{s.stop(t2+rel+0.05)}catch(e){}})})},
      setMod(m){hs.forEach(h=>h.setDyn(m==null?0.6:m))}};
    return vo}};
