'use strict';
/* Soundcraft core: shared helpers, audio worklets, the master graph and per-track routing.
   Everything that touches an AudioContext reads the global `ctx`, so offline rendering can
   swap in an OfflineAudioContext and rebuild the same graph. Per-track runtime nodes live in
   `tr._rt` and global graph nodes in `GR`, both of which are excluded from saved projects. */
const APP_VERSION='0.4.0';
const $=(s,r=document)=>r.querySelector(s);
const $$=(s,r=document)=>[...r.querySelectorAll(s)];
const clamp=(v,a,b)=>Math.min(b,Math.max(a,v));
const mtof=n=>440*Math.pow(2,(n-69)/12);
const NN=['C','C♯','D','D♯','E','F','F♯','G','G♯','A','A♯','B'];
const noteName=n=>NN[((n%12)+12)%12]+(Math.floor(n/12)-1);
const isBlack=n=>[1,3,6,8,10].includes(((n%12)+12)%12);
let uidN=0;const uid=p=>(p||'x')+Date.now().toString(36)+(uidN++).toString(36)+Math.floor(Math.random()*1e5).toString(36);
const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const pct=v=>Math.round(v*100);
const dbToGain=db=>Math.pow(10,db/20),gainToDb=g=>20*Math.log10(Math.max(1e-9,g));
const FMT={
  pct:v=>Math.round(v*100)+'%',
  hz:v=>v>=1000?(v/1000).toFixed(v>=10000?0:1)+'k':Math.round(v)+'',
  s:v=>v<1?Math.round(v*1000)+'ms':v.toFixed(v<10?2:1)+'s',
  ms:v=>v.toFixed(v<10?1:0)+'ms',
  db:v=>(v>0?'+':'')+v.toFixed(1)+'dB',
  st:v=>(v>0?'+':'')+Math.round(v)+'st',
  ct:v=>Math.round(v)+'¢',
  x:v=>v.toFixed(2)+'×',
  int:v=>String(Math.round(v)),
  dec:v=>v.toFixed(2),
  pan:v=>{const p=Math.round(v*50);return p===0?'C':(p<0?'L':'R')+Math.abs(p)}
};
const errLog=[];
window.addEventListener('error',e=>errLog.push(new Date().toISOString()+' '+(e.message||e.type)));
window.addEventListener('unhandledrejection',e=>errLog.push(new Date().toISOString()+' '+(e.reason&&e.reason.message||e.reason)));

/* ───────── audio helpers ───────── */
let ctx=null,OFF=false,rendering=false;
const GR={};          // global graph nodes for the current context
const Z={_bend:0};
function G(v=0){const g=ctx.createGain();g.gain.value=v;return g}
function F(type,f,q=1){const b=ctx.createBiquadFilter();b.type=type;b.frequency.value=f;b.Q.value=q;return b}
function O(type,f,t,tr,d=0){const o=ctx.createOscillator();if(typeof type==='string')o.type=type;else o.setPeriodicWave(type);o.frequency.value=f;o._d=d;o.detune.value=d+((tr&&tr._bend)||0)*100;o.start(t);return o}
function N(t,dur){const s=ctx.createBufferSource();s.buffer=noiseBuf();s.loop=true;s.start(t,Math.random()*1.5);if(dur)s.stop(t+dur);return s}
function hold(p,t){if(p.cancelAndHoldAtTime)p.cancelAndHoldAtTime(t);else{p.cancelScheduledValues(t);p.setValueAtTime(p.value,t)}}
function env(p,t,a,peak,d,s){p.cancelScheduledValues(t);p.setValueAtTime(0.0001,t);p.linearRampToValueAtTime(peak,t+a);if(d)p.setTargetAtTime(peak*s,t+a,d)}
function voice(amp,oscs,rel,extra=[]){
  let done=false;const all=oscs.concat(extra);
  if(all[0])all[0].onended=()=>{try{amp.disconnect()}catch(e){}};
  return {oscs,off(t){if(done)return;done=true;t=Math.max(t,ctx.currentTime);hold(amp.gain,t);amp.gain.setTargetAtTime(0,t,Math.max(0.002,rel/5));all.forEach(o=>{try{o.stop(t+rel+0.05)}catch(e){}})}};
}
const perCtx=new WeakMap();
function cache(key,make){let m=perCtx.get(ctx);if(!m){m={};perCtx.set(ctx,m)}if(!(key in m))m[key]=make();return m[key]}
function noiseBuf(){return cache('noise',()=>{const sr=ctx.sampleRate,b=ctx.createBuffer(1,sr*2,sr),d=b.getChannelData(0);for(let i=0;i<d.length;i++)d[i]=Math.random()*2-1;return b})}
const curveCache={};
function curve(kind,k){k=Math.round(k*40)/40;const key=kind+k;if(curveCache[key])return curveCache[key];const n=2048,c=new Float32Array(n),a=1+k*30;
  for(let i=0;i<n;i++){const x=i/(n-1)*2-1;
    c[i]=kind==='hard'?clamp(x*(1+k*12),-1,1):kind==='fold'?Math.sin(x*(1+k*8)*Math.PI/2):Math.tanh(a*x)/Math.tanh(a)}
  return curveCache[key]=c}
function shaper(k,kind='soft'){const w=ctx.createWaveShaper();w.curve=curve(kind,k);w.oversample='2x';return w}
function pulse(d){return cache('pulse'+d,()=>{const n=48,re=new Float32Array(n),im=new Float32Array(n);for(let k=1;k<n;k++)re[k]=2/(k*Math.PI)*Math.sin(k*Math.PI*d);return ctx.createPeriodicWave(re,im)})}
function makeIR(sec,damp=0.5,c=ctx){const r=c.sampleRate,len=Math.max(64,Math.floor(r*sec)),b=c.createBuffer(2,len,r);
  for(let ch=0;ch<2;ch++){const d=b.getChannelData(ch);let lp=0;for(let i=0;i<len;i++){const x=i/len,k=clamp(0.1+damp*0.85*x+damp*0.1,0,0.97);lp=lp*k+(Math.random()*2-1)*(1-k);d[i]=(i<r*0.008?0:lp*(1+damp*2.5))*Math.pow(1-x,2.4)}}return b}

/* ───────── worklets (physical-model strings, gate, bitcrusher, transient shaper) ───────── */
const WORKLET_SRC=`
class KS extends AudioWorkletProcessor{
  static get parameterDescriptors(){return [{name:'gate',defaultValue:1,minValue:0,maxValue:1,automationRate:'k-rate'}]}
  constructor(o){super();const p=o.processorOptions,sr=sampleRate,D=sr/p.freq,s=p.damp;
    this.N=Math.max(2,Math.floor(D-s-0.0001));const fr=D-s-this.N;this.C=(1-fr)/(1+fr);this.ax=0;this.ay=0;
    this.b=new Float32Array(this.N);let lp=0;for(let k=0;k<this.N;k++){lp+=(Math.random()*2-1-lp)*p.bright;this.b[k]=lp}
    const pp=Math.floor(this.N*p.pick);if(pp>0){const t=this.b.slice();for(let k=0;k<this.N;k++)this.b[k]=t[k]-t[(k+pp)%this.N]*0.9}
    let m=0;for(let k=0;k<this.N;k++)m=Math.max(m,Math.abs(this.b[k]));if(m>0)for(let k=0;k<this.N;k++)this.b[k]/=m;
    this.s=s;this.g=p.g;this.gOff=p.gOff;this.start=Math.round(p.start*sr);this.i=0;this.quiet=0;this.maxLife=Math.round(sr*p.maxLife)}
  process(_,outs,par){const o=outs[0][0];if(!o)return true;const N=this.N,b=this.b,s=this.s,g=par.gate[0]<0.5?this.gOff:this.g;let e=0;
    for(let n=0;n<o.length;n++){if(currentFrame+n<this.start){o[n]=0;continue}
      const i=this.i,j=i+1<N?i+1:0,x=g*((1-s)*b[i]+s*b[j]),y=this.C*x+this.ax-this.C*this.ay;this.ax=x;this.ay=y;
      o[n]=b[i];b[i]=y;this.i=j;e+=y<0?-y:y}
    if(currentFrame>=this.start){this.quiet=e/o.length<2e-5?this.quiet+o.length:0;this.maxLife-=o.length;
      if(this.quiet>2048||this.maxLife<0){this.port.postMessage('done');return false}}
    return true}}
registerProcessor('sc-ks',KS);
class Gate extends AudioWorkletProcessor{
  static get parameterDescriptors(){return [{name:'threshold',defaultValue:-40},{name:'range',defaultValue:-60},{name:'attack',defaultValue:2},{name:'release',defaultValue:120}]}
  constructor(){super();this.env=0;this.g=1}
  process(ins,outs,p){const i=ins[0],o=outs[0];if(!i||!i.length)return true;const th=Math.pow(10,p.threshold[0]/20),fl=Math.pow(10,p.range[0]/20),
    ka=Math.exp(-1/(sampleRate*Math.max(0.1,p.attack[0])/1000)),kr=Math.exp(-1/(sampleRate*Math.max(1,p.release[0])/1000));
    for(let n=0;n<i[0].length;n++){let a=0;for(let c=0;c<i.length;c++){const v=Math.abs(i[c][n]);if(v>a)a=v}
      this.env=a>this.env?a:this.env*0.9995+a*0.0005;const tgt=this.env>=th?1:fl;this.g=tgt>this.g?tgt+(this.g-tgt)*ka:tgt+(this.g-tgt)*kr;
      for(let c=0;c<o.length;c++)o[c][n]=(i[c]||i[0])[n]*this.g}
    return true}}
registerProcessor('sc-gate',Gate);
class Crush extends AudioWorkletProcessor{
  static get parameterDescriptors(){return [{name:'bits',defaultValue:8},{name:'down',defaultValue:4},{name:'mix',defaultValue:1}]}
  constructor(){super();this.h=[0,0];this.c=0}
  process(ins,outs,p){const i=ins[0],o=outs[0];if(!i||!i.length)return true;const q=Math.pow(2,Math.round(p.bits[0])-1),d=Math.max(1,Math.round(p.down[0])),m=p.mix[0];
    for(let n=0;n<i[0].length;n++){if(this.c<=0){for(let c=0;c<o.length;c++)this.h[c]=Math.round((i[c]||i[0])[n]*q)/q;this.c=d}this.c--;
      for(let c=0;c<o.length;c++){const x=(i[c]||i[0])[n];o[c][n]=x*(1-m)+this.h[c]*m}}
    return true}}
registerProcessor('sc-crush',Crush);
class Transient extends AudioWorkletProcessor{
  static get parameterDescriptors(){return [{name:'attack',defaultValue:0},{name:'sustain',defaultValue:0}]}
  constructor(){super();this.f=0;this.s=0}
  process(ins,outs,p){const i=ins[0],o=outs[0];if(!i||!i.length)return true;const sr=sampleRate,fa=Math.exp(-1/(sr*0.001)),fr=Math.exp(-1/(sr*0.03)),sa=Math.exp(-1/(sr*0.03)),sr2=Math.exp(-1/(sr*0.25)),A=p.attack[0],S=p.sustain[0];
    for(let n=0;n<i[0].length;n++){let a=0;for(let c=0;c<i.length;c++){const v=Math.abs(i[c][n]);if(v>a)a=v}
      this.f=a>this.f?a+(this.f-a)*fa:a+(this.f-a)*fr;this.s=a>this.s?a+(this.s-a)*sa:a+(this.s-a)*sr2;
      const d=(this.f-this.s)/(this.s+1e-4);let g=1+(d>0?A*Math.min(d,3)*0.6:S*Math.min(-d,1)*0.9);g=Math.max(0,Math.min(4,g));
      for(let c=0;c<o.length;c++)o[c][n]=(i[c]||i[0])[n]*g}
    return true}}
registerProcessor('sc-transient',Transient);
/* Microphone recorder: captures from the first render quantum at or after 'at' (context time) and reports the
   exact start time, so takes line up with the song. Blocks without input are recorded as silence. */
class Rec extends AudioWorkletProcessor{
  constructor(o){super();this.ch=o.processorOptions.ch||1;this.at=Infinity;this.stopAt=Infinity;this.on=false;this.alloc();
    this.port.onmessage=e=>{const m=e.data;if(m.cmd==='start'){this.at=m.at;this.stopAt=Infinity}else if(m.cmd==='stop')this.stopAt=m.at}}
  alloc(){this.buf=[];for(let c=0;c<this.ch;c++)this.buf.push(new Float32Array(16384));this.n=0}
  flush(){if(!this.n)return;const d=this.buf.map(b=>b.slice(0,this.n));this.port.postMessage({data:d},d.map(x=>x.buffer));this.alloc()}
  process(ins){const t=currentTime,i=ins[0];
    if(!this.on&&t>=this.at-0.5/sampleRate&&t<this.stopAt){this.on=true;this.port.postMessage({start:t})}
    if(this.on&&t>=this.stopAt){this.on=false;this.flush();this.port.postMessage({done:true});return false}
    if(this.on){const L=128;for(let c=0;c<this.ch;c++){const src=i&&i.length?(i[c]||i[0]):null;if(src)this.buf[c].set(src,this.n);else this.buf[c].fill(0,this.n,this.n+L)}this.n+=L;if(this.n+L>this.buf[0].length)this.flush()}
    return true}}
registerProcessor('sc-rec',Rec);`;
const workletState=new WeakMap();
function loadWorklets(c){
  if(workletState.has(c))return workletState.get(c).p;
  const st={ok:false,p:null};workletState.set(c,st);
  if(!c.audioWorklet){st.p=Promise.resolve(false);return st.p}
  const url=URL.createObjectURL(new Blob([WORKLET_SRC],{type:'application/javascript'}));
  st.p=c.audioWorklet.addModule(url).then(()=>{st.ok=true;return true}).catch(e=>{errLog.push('worklet: '+e.message);return false});
  return st.p;
}
const workletOK=()=>!!(ctx&&workletState.get(ctx)&&workletState.get(ctx).ok);

/* ───────── master graph ───────── */
function buildGraph(){
  GR.bus=G(1);GR.masterIn=G(1);GR.master=G(S.master);
  GR.revIn=G(1);GR.revFx=makeFx(S.returns.rev);GR.revRet=G(S.returns.rev.vol);GR.revIn.connect(GR.revFx.input);GR.revFx.output.connect(GR.revRet).connect(GR.bus);
  GR.dlyIn=G(1);GR.dlyFx=makeFx(S.returns.dly);GR.dlyRet=G(S.returns.dly.vol);GR.dlyIn.connect(GR.dlyFx.input);GR.dlyFx.output.connect(GR.dlyRet).connect(GR.bus);
  GR.revAn=ctx.createAnalyser();GR.revAn.fftSize=256;GR.revRet.connect(GR.revAn);
  GR.retAn={rev:ctx.createAnalyser(),dly:ctx.createAnalyser()};GR.retAn.rev.fftSize=GR.retAn.dly.fftSize=512;GR.revRet.connect(GR.retAn.rev);GR.dlyRet.connect(GR.retAn.dly);
  GR.bus.connect(GR.masterIn);GR.masterFx=[];
  GR.preAn=ctx.createAnalyser();GR.preAn.fftSize=512;
  const lim=ctx.createDynamicsCompressor();lim.threshold.value=-1;lim.knee.value=0;lim.ratio.value=20;lim.attack.value=0.001;lim.release.value=0.08;GR.lim=lim;
  GR.master.connect(GR.preAn);GR.master.connect(lim);
  GR.analyser=ctx.createAnalyser();GR.analyser.fftSize=2048;GR.analyser.smoothingTimeConstant=0.8;lim.connect(GR.analyser);lim.connect(ctx.destination);
  if(!OFF){const sp=ctx.createChannelSplitter(2);lim.connect(sp);GR.pk=[ctx.createAnalyser(),ctx.createAnalyser()];GR.pk.forEach((a,i)=>{a.fftSize=2048;sp.connect(a,i)});
    const hs=F('highshelf',1500,0.7);hs.gain.value=4;const hp=F('highpass',38,0.5);lim.connect(hs).connect(hp);const sp2=ctx.createChannelSplitter(2);hp.connect(sp2);
    GR.kw=[ctx.createAnalyser(),ctx.createAnalyser()];GR.kw.forEach((a,i)=>{a.fftSize=2048;sp2.connect(a,i)})}
  rebuildMasterChain();
  S.tracks.forEach(buildTrack);applyMix();
}
function rebuildMasterChain(){
  if(!ctx)return;try{GR.masterIn.disconnect()}catch(e){}(GR.masterFx||[]).forEach(x=>{try{x.output.disconnect()}catch(e){}});
  GR.masterFx=S.masterFx.map(f=>{if(!f._inst||f._inst.ctx!==ctx)f._inst=makeFx(f);return f._inst});
  let node=GR.masterIn;S.masterFx.forEach(f=>{if(f.on&&!renderNoMaster){node.connect(f._inst.input);node=f._inst.output}});node.connect(GR.master);
}
function buildTrack(tr){
  if(!ctx||(tr._rt&&tr._rt.ctx===ctx))return;const E=ENG[tr.engine];
  const rt=tr._rt={ctx,live:new Set(),mod:null,in:G(1),pan:ctx.createStereoPanner(),vol:G(0),rev:G(0),dly:G(0),an:ctx.createAnalyser(),fx:[]};
  rt.an.fftSize=1024;
  if(E.setup)rt.eng=E.setup(tr,rt.in);if(tr.engine==='orch'||tr.engine==='section')ensureTrackSamples(tr);
  rt.pan.connect(rt.vol).connect(GR.bus);rt.vol.connect(rt.rev).connect(GR.revIn);rt.vol.connect(rt.dly).connect(GR.dlyIn);rt.vol.connect(rt.an);
  rebuildTrackChain(tr);
}
function rebuildTrackChain(tr){
  const rt=tr._rt;if(!rt)return;try{rt.in.disconnect()}catch(e){}rt.fx.forEach(x=>{try{x.output.disconnect()}catch(e){}});
  rt.fx=tr.fx.map(f=>{if(!f._inst||f._inst.ctx!==ctx)f._inst=makeFx(f);return f._inst});
  let node=rt.in;tr.fx.forEach(f=>{if(f.on){node.connect(f._inst.input);node=f._inst.output}});node.connect(rt.pan);
  buildTrackMod(tr);
}
function disposeTrack(tr){const rt=tr._rt;if(!rt)return;teardownTrackMod(tr);try{rt.live.forEach(h=>h.off(ctx.currentTime))}catch(e){}if(rt.eng&&rt.eng.dispose)rt.eng.dispose();
  try{rt.in.disconnect();rt.vol.disconnect();rt.pan.disconnect()}catch(e){}tr.fx.forEach(f=>{if(f._inst){f._inst.dispose&&f._inst.dispose();f._inst=null}});tr._rt=null}
const audible=tr=>{if(renderOnly)return renderOnly.has(tr.id);const solo=S.tracks.some(t=>t.solo);return !tr.mute&&(!solo||tr.solo)};
function applyMix(){if(!ctx)return;const now=ctx.currentTime;
  S.tracks.forEach(tr=>{buildTrack(tr);const rt=tr._rt;rt.vol.gain.setTargetAtTime(audible(tr)?tr.vol*tr.vol:0,now,0.015);rt.pan.pan.setTargetAtTime(tr.pan,now,0.015);rt.rev.gain.setTargetAtTime(tr.rev,now,0.015);rt.dly.gain.setTargetAtTime(tr.dly,now,0.015)});
  if(GR.revRet){GR.revRet.gain.setTargetAtTime(S.returns.rev.mute?0:S.returns.rev.vol,now,0.015);GR.dlyRet.gain.setTargetAtTime(S.returns.dly.mute?0:S.returns.dly.vol,now,0.015);GR.master.gain.setTargetAtTime(S.master,now,0.015)}}
function trackOut(tr){const rt=tr._rt;return rt.eng&&rt.eng.input?rt.eng.input:rt.in}
function trigger(tr,n,v,t){
  if(!ctx)return null;buildTrack(tr);const rt=tr._rt,E=ENG[tr.engine];
  const monoMode=!E.mono&&tr.params.mode&&tr.params.mode!=='poly';let prev=null;
  if(monoMode){const hs=[...rt.live];if(hs.length){prev=hs[hs.length-1].n;hs.forEach(h=>h.off(t))}}
  else if(!E.mono&&rt.live.size>=tr.poly){const oldest=rt.live.values().next().value;if(oldest)oldest.off(t)}
  const M=voiceCtx(tr,n,v,t),P=noteParams(tr,n,v,t);M.P=P;M.prev=prev;M.legato=prev!=null&&tr.params.mode==='legato';
  const out=voiceStrip(tr,M,trackOut(tr));
  let h;try{h=E.on(tr,n,v,t,out,P,M)||{off(){}}}catch(e){errLog.push('engine '+tr.engine+': '+e.message);h={off(){}}}
  h.n=n;attachVoice(tr,M,h);const off=h.off;h.off=t2=>{rt.live.delete(h);off.call(h,t2)};rt.live.add(h);return h}
function paramsOf(tr){return tr.params}
function setBend(tr,semi){tr._bend=semi;setLiveSource(tr,'bend',semi/(tr.bendRange||2));if(tr._rt)tr._rt.live.forEach(h=>(h.oscs||[]).forEach(o=>o.detune.setTargetAtTime(o._d+semi*100,ctx.currentTime,0.01)))}
function setMod(tr,m){tr._mod=m;tr._mw=m;setLiveSource(tr,'mw',m);refreshLive(tr)}
function refreshLive(tr){if(!tr._rt)return;const m=tr._mod!=null?tr._mod:null;tr._rt.live.forEach(h=>h.setMod&&h.setMod(m));if(tr._rt.eng&&tr._rt.eng.update)tr._rt.eng.update(tr.params)}
function liveVoices(){return S.tracks.reduce((a,t)=>a+(t._rt?t._rt.live.size:0),0)}

/* ───────── engine lifecycle ───────── */
let engineGen=0;
function createContext(){
  const AC=window.AudioContext||window.webkitAudioContext;if(!AC)return null;
  const opts={latencyHint:PREF.latency||'interactive'};if(PREF.sampleRate&&PREF.sampleRate!=='auto')opts.sampleRate=+PREF.sampleRate;
  try{return new AC(opts)}catch(e){errLog.push('AudioContext: '+e.message);return new AC()}
}
function ensureAudio(){
  if(OFF)return ctx;
  if(ctx){if(ctx.state==='suspended')ctx.resume();return ctx}
  ctx=createContext();if(!ctx){toast('This browser has no Web Audio support, so Soundcraft cannot make sound here.');return null}
  engineGen++;loadWorklets(ctx);buildGraph();GR.ambOut=G(0);GR.ambOut.connect(ctx.destination);startAmbience();
  if(PREF.sinkId&&ctx.setSinkId)ctx.setSinkId(PREF.sinkId).catch(e=>{errLog.push('setSinkId: '+e.message);toast('The chosen audio output is not available, so the default output is used.')});
  ctx.onstatechange=()=>{updateWake();if(ctx&&ctx.state==='interrupted')toast('Audio was interrupted by the system.')};
  if('onsinkchange' in ctx)ctx.onsinkchange=()=>toast('Audio output changed.');
  prewarmAudio();updateWake();return ctx;
}
/* Builds per-context tables that would otherwise be made on the first live note, a few per idle slice. */
function prewarmAudio(){const c=ctx;let i=0;const step=()=>{if(ctx!==c||OFF||i>=24)return;try{glitchWave()}catch(e){return}i++;setTimeout(step,40)};setTimeout(step,300)}
async function restartAudio(){
  const was=S.playing;stop();if(ctx){S.tracks.forEach(disposeTrack);S.masterFx.forEach(f=>f._inst=null);[S.returns.rev,S.returns.dly].forEach(f=>f._inst=null);try{await ctx.close()}catch(e){}}
  ctx=null;ensureAudio();if(was)play();toast('Audio engine restarted at '+ctx.sampleRate+' Hz.');
}
function updateWake(){const w=$('#wake');if(w)w.hidden=!!ctx&&ctx.state==='running'}

/* ───────── analysis shared by meters and the studio ───────── */
const fbuf=new Uint8Array(1024),tbuf=new Float32Array(2048),mbuf=new Float32Array(1024),pbuf=new Float32Array(512),kbuf=new Float32Array(2048);
const A={bass:0,mid:0,high:0,level:0,rev:0,kick:0,bassAvg:0,lastKick:0,clipAt:0,peak:[0,0],peakHold:[-120,-120],peakHoldAt:[0,0],msHist:[],lufsM:-Infinity,lufsS:-Infinity,lufsI:-Infinity,iSum:0,iN:0};
function analyse(now){
  if(!GR.analyser||OFF){A.bass*=0.9;A.mid*=0.9;A.high*=0.9;A.level*=0.9;A.rev*=0.9;return}
  GR.analyser.getByteFrequencyData(fbuf);const bin=ctx.sampleRate/2048;const band=(lo,hi)=>{let s=0,c=0;for(let i=Math.max(1,Math.round(lo/bin));i<=Math.min(1023,Math.round(hi/bin));i++){s+=fbuf[i];c++}return c?s/c/255:0};
  A.bass=band(25,150);A.mid=band(160,2000);A.high=band(2000,9000);
  GR.analyser.getFloatTimeDomainData(tbuf);let s=0;for(let i=0;i<tbuf.length;i++)s+=tbuf[i]*tbuf[i];A.level=Math.sqrt(s/tbuf.length);
  GR.revAn.getFloatTimeDomainData(pbuf.subarray(0,256));s=0;for(let i=0;i<256;i++)s+=pbuf[i]*pbuf[i];A.rev=Math.min(1,Math.sqrt(s/256)*6);
  GR.preAn.getFloatTimeDomainData(pbuf);for(let i=0;i<pbuf.length;i++)if(Math.abs(pbuf[i])>=0.999){A.clipAt=now;break}
  if(GR.pk){for(let c=0;c<2;c++){GR.pk[c].getFloatTimeDomainData(kbuf);let m=0;for(let i=0;i<kbuf.length;i++){const v=Math.abs(kbuf[i]);if(v>m)m=v}A.peak[c]=Math.max(m,A.peak[c]*0.92);const db=gainToDb(m);if(db>A.peakHold[c]||now-A.peakHoldAt[c]>1500){A.peakHold[c]=db;A.peakHoldAt[c]=now}}
    let ms=0;for(let c=0;c<2;c++){GR.kw[c].getFloatTimeDomainData(kbuf);let q=0;for(let i=0;i<kbuf.length;i++)q+=kbuf[i]*kbuf[i];ms+=q/kbuf.length}
    A.msHist.push([now,ms]);while(A.msHist.length&&now-A.msHist[0][0]>3000)A.msHist.shift();
    const win=w=>{let t=0,c=0;for(const[ts,v]of A.msHist)if(now-ts<=w){t+=v;c++}return c?t/c:0};
    const toL=m=>m>1e-12?-0.691+10*Math.log10(m):-Infinity;A.lufsM=toL(win(400));A.lufsS=toL(win(3000));
    if(S.playing&&A.lufsM>-70){A.iSum+=win(400);A.iN++;A.lufsI=toL(A.iSum/A.iN)}}
  const thr=0.05+(1-PREF.sens)*0.2;A.kick=0;if(A.bass-A.bassAvg>thr&&now-A.lastKick>140&&A.bass>0.35){A.kick=1;A.lastKick=now}A.bassAvg=A.bassAvg*0.9+A.bass*0.1;
}
function resetIntegrated(){A.iSum=0;A.iN=0;A.lufsI=-Infinity}
function trackLevel(tr){const rt=tr._rt;if(!rt||OFF)return 0;rt.an.getFloatTimeDomainData(mbuf);let m=0;for(let i=0;i<mbuf.length;i++){const v=Math.abs(mbuf[i]);if(v>m)m=v}return m}

/* ───────── ambience: its own output, never the master bus ───────── */
let ambNodes=[];
function startAmbience(){
  if(!ctx||OFF||!GR.ambOut)return;ambNodes.forEach(n=>{try{n.stop()}catch(e){}});ambNodes=[];
  GR.ambOut.gain.setTargetAtTime(PREF.amb?PREF.ambVol*0.5:0,ctx.currentTime,0.3);if(!PREF.amb)return;
  const w=PREF.weather,src=N(ctx.currentTime);ambNodes.push(src);
  if(w==='rain'||w==='storm'){const g=G(w==='storm'?0.5:0.35);src.connect(F('highpass',400)).connect(F('lowpass',w==='storm'?2600:1800)).connect(g).connect(GR.ambOut)}
  else if(w==='snow'||w==='night'){const bp=F('bandpass',380,0.7),g=G(0.25),l=O('sine',0.07,ctx.currentTime,Z),lg=G(220);l.connect(lg).connect(bp.frequency);ambNodes.push(l);src.connect(bp).connect(g).connect(GR.ambOut)}
  else{const g=G(0.08);src.connect(F('bandpass',4200,2)).connect(g).connect(GR.ambOut)}
}
function purr(){if(!ensureAudio()||!GR.ambOut)return;const t=ctx.currentTime,s=N(t,1.6),am=O('sine',24,t,Z),ag=G(0),out=G(0),lp=F('lowpass',220,1);am.connect(ag.gain);s.connect(lp).connect(ag).connect(out).connect(ctx.destination);
  out.gain.setValueAtTime(0,t);out.gain.linearRampToValueAtTime(0.5,t+0.25);out.gain.setTargetAtTime(0,t+1.1,0.15);am.stop(t+1.7)}
