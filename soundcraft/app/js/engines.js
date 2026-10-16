'use strict';
/* Sound engines. An engine is a real synthesis method with a parameter registry. Presets are saved
   configurations of an engine and are never counted as separate instruments.
   on(tr, note, velocity, time, output, P, M):
     P is the parameter set for this note (base values plus note-start modulation),
     M is the voice modulation context: M.bind(key, audioParam, unitsPerFullRange) registers a live
     destination, M.prev is the previous held note for mono/legato, M.legato says whether to glide
     without retriggering. */
const ENG={};
const KIND={synth:'Synthesized',phys:'Physical model',hybrid:'Hybrid',sampler:'Sampler'};
const ENV_GROUP=(pre,label,a,d,s,r)=>[P(pre+'A',label+' attack',label,0.001,8,a,{log:true,fmt:FMT.s}),P(pre+'D',label+' decay',label,0.005,8,d,{log:true,fmt:FMT.s}),P(pre+'S',label+' sustain',label,0,1,s,{fmt:FMT.pct}),P(pre+'R',label+' release',label,0.005,10,r,{log:true,fmt:FMT.s})];
const OUT_GROUP=(lv=0.8)=>[P('level','Level','Output',0,1,lv,{fmt:FMT.pct,live:1}),P('pan','Pan','Output',-1,1,0,{fmt:FMT.pan,live:1})];
/* Shared output stage: level and pan as real per-voice AudioParams. */
function outStage(P,M,out){const lvl=G(P.level),pan=ctx.createStereoPanner();pan.pan.value=P.pan;lvl.connect(pan).connect(out);M.bind('level',lvl.gain,1);M.bind('pan',pan.pan,2);return lvl}
function adsr(p,t,P,pre,peak){env(p,t,Math.max(0.002,P[pre+'A']),peak,Math.max(0.004,P[pre+'D']/3),P[pre+'S'])}
function glideOsc(o,ratio,f,t,P,M){if(M.prev!=null&&P.glide>0.001){o.frequency.setValueAtTime(mtof(M.prev)*ratio,t);o.frequency.exponentialRampToValueAtTime(f*ratio,t+P.glide)}}
function pitchEnv(o,t,P){if(Math.abs(P.penv)>0.01){o.detune.setValueAtTime(o._d+P.penv*100,t);o.detune.setTargetAtTime(o._d,t,Math.max(0.002,P.pdec/3))}}
const LEGATO_ENV=(p,t,P,pre,peak)=>{p.cancelScheduledValues(t);p.setValueAtTime(peak*P[pre+'S'],t)};

/* ───────── Analog / subtractive ───────── */
ENG.va={name:'Analog Synth',kind:KIND.synth,family:'synths',
  desc:'Two-oscillator virtual analog: unison, sub, noise, FM and ring modulation, a 12/24 dB multimode filter with drive and key tracking, filter, amp and pitch envelopes, and mono/legato glide.',
  params:[C('wave1','Osc 1 wave','Oscillators',WAVES,'sawtooth'),C('wave2','Osc 2 wave','Oscillators',WAVES,'square'),P('semi2','Osc 2 pitch','Oscillators',-24,24,0,{int:true,fmt:FMT.st}),P('detune2','Osc 2 detune','Oscillators',0,50,7,{fmt:FMT.ct}),
    P('mix2','Osc 2 level','Oscillators',0,1,0.5,{fmt:FMT.pct}),P('unison','Unison voices','Oscillators',1,7,1,{int:true,fmt:FMT.int}),P('spread','Unison detune','Oscillators',0,60,12,{fmt:FMT.ct}),P('sub','Sub level','Oscillators',0,1,0,{fmt:FMT.pct}),
    P('noise','Noise level','Oscillators',0,1,0,{fmt:FMT.pct}),P('fm','Osc 2 → 1 FM','Oscillators',0,1,0,{fmt:FMT.pct,live:1}),P('ring','Ring mod','Oscillators',0,1,0,{fmt:FMT.pct,live:1}),
    P('tune','Pitch','Oscillators',-24,24,0,{int:true,fmt:FMT.st,live:1}),P('fine','Fine tune','Oscillators',-100,100,0,{fmt:FMT.ct,live:1}),
    C('ftype','Filter type','Filter',[['lowpass','Low-pass'],['highpass','High-pass'],['bandpass','Band-pass'],['notch','Notch']],'lowpass'),C('slope','Slope','Filter',[[12,'12 dB'],[24,'24 dB']],12),
    P('cutoff','Cutoff','Filter',30,18000,2400,{log:true,fmt:FMT.hz,live:1}),P('reso','Resonance','Filter',0.1,24,1,{log:true,fmt:FMT.dec,live:1}),P('fdrive','Filter drive','Filter',0,1,0,{fmt:FMT.pct}),
    P('keytrack','Key tracking','Filter',0,1,0.3,{fmt:FMT.pct}),P('velFilter','Velocity → cutoff','Filter',0,1,0.3,{fmt:FMT.pct}),
    P('fenv','Env amount','Filter envelope',-1,1,0.3,{fmt:FMT.pct}),...ENV_GROUP('f','Filter',0.005,0.4,0.3,0.4),
    ...ENV_GROUP('a','Amp',0.005,0.3,0.8,0.3),P('velAmp','Velocity → level','Amp',0,1,0.7,{fmt:FMT.pct}),
    P('penv','Pitch env','Pitch envelope',-24,24,0,{fmt:FMT.st}),P('pdec','Pitch decay','Pitch envelope',0.003,2,0.08,{log:true,fmt:FMT.s}),
    C('mode','Voice mode','Voice',[['poly','Poly'],['mono','Mono'],['legato','Legato']],'poly'),P('glide','Glide','Voice',0,1,0,{fmt:FMT.s}),...OUT_GROUP()],
  on(tr,n,v,t,out,P,M){
    const f=mtof(n),oscs=[],ext=[],mix1=G(1),mix2=G(1),pre=G(1),amp=G(0),uni=Math.round(P.unison),norm=0.42/Math.sqrt(uni),base=P.tune*100+P.fine;
    for(let i=0;i<uni;i++){const d=uni>1?(i/(uni-1)*2-1)*P.spread:0;
      const o=O(P.wave1,f,t,tr,base+d);glideOsc(o,1,f,t,P,M);pitchEnv(o,t,P);const g=G(norm*(1-P.mix2*0.5));o.connect(g).connect(mix1);oscs.push(o);M.bind('tune',o.detune,4800);M.bind('fine',o.detune,200);
      if(P.mix2>0.01||P.fm>0.01||P.ring>0.01){const r2=Math.pow(2,P.semi2/12),o2=O(P.wave2,f*r2,t,tr,base+d+P.detune2);glideOsc(o2,r2,f,t,P,M);pitchEnv(o2,t,P);oscs.push(o2);M.bind('tune',o2.detune,4800);M.bind('fine',o2.detune,200);
        const g2=G(norm*P.mix2);o2.connect(g2).connect(mix2);
        const fg=G(f*4*P.fm);o2.connect(fg).connect(o.frequency);M.bind('fm',fg.gain,f*4);
        const rg=G(P.ring*norm);const vca=G(0);o.connect(vca);o2.connect(rg).connect(vca.gain);vca.connect(mix1);M.bind('ring',rg.gain,norm)}}
    if(P.sub>0.01){const s=O('sine',f/2,t,tr,base);glideOsc(s,0.5,f,t,P,M);const g=G(P.sub*0.45);s.connect(g).connect(mix1);oscs.push(s);M.bind('tune',s.detune,4800)}
    if(P.noise>0.01){const ns=N(t),g=G(P.noise*0.3);ns.connect(g).connect(mix1);ext.push(ns)}
    mix1.connect(pre);mix2.connect(pre);
    const f1=F(P.ftype,20,P.reso),f2=P.slope===24?F(P.ftype,20,Math.max(0.5,P.reso*0.7)):null;
    (P.fdrive>0.02?pre.connect(shaper(P.fdrive*0.8)):pre).connect(f1);let tail=f1;if(f2){f1.connect(f2);tail=f2}
    tail.connect(amp);amp.connect(outStage(P,M,out));
    const cut=clamp(P.cutoff*Math.pow(2,(n-60)/12*P.keytrack+P.velFilter*(v-0.7)*2.5),20,20000),pk=clamp(cut*Math.pow(2,P.fenv*6),20,20000),su=clamp(cut*Math.pow(2,P.fenv*6*P.fS),20,20000);
    [f1,f2].forEach(fl=>{if(!fl)return;const q=fl.frequency;if(M.legato){q.setValueAtTime(su,t)}else{q.setValueAtTime(cut,t);q.linearRampToValueAtTime(pk,t+Math.max(0.003,P.fA));q.setTargetAtTime(su,t+Math.max(0.003,P.fA),Math.max(0.005,P.fD/3))}
      M.bind('cutoff',fl.detune,Math.log2(18000/30)*1200);M.bind('reso',fl.Q,24)});
    const peak=0.5*(1-P.velAmp+P.velAmp*v);if(M.legato)LEGATO_ENV(amp.gain,t,P,'a',peak);else adsr(amp.gain,t,P,'a',peak);
    const h=voice(amp,oscs,Math.max(0.01,P.aR),ext),off=h.off;
    h.off=t2=>{t2=Math.max(t2,ctx.currentTime);[f1,f2].forEach(fl=>{if(fl){hold(fl.frequency,t2);fl.frequency.setTargetAtTime(cut,t2,Math.max(0.005,P.fR/3))}});off(t2)};return h}};

/* ───────── Wavetable ───────── */
const WT_TABLES={
  basic:{name:'Basic shapes',frames:[k=>k===1?1:0,k=>1/k,k=>k%2?1/k:0,k=>k%2?1/(k*k)*(((k-1)/2)%2?-1:1):0]},
  vocal:{name:'Vocal',frames:[[3,7],[2,9],[1.5,14],[2.5,5]].map(([a,b])=>k=>Math.exp(-Math.pow((k-a)/1.2,2))+0.7*Math.exp(-Math.pow((k-b)/2,2))+0.05/k)},
  digital:{name:'Digital',frames:[k=>1/k,k=>k%3===0?0.9/Math.sqrt(k):0.1/k,k=>k<=16&&(k&(k-1))===0?1/Math.sqrt(k):0,k=>Math.abs(Math.sin(k*1.7))/Math.sqrt(k)]},
  growl:{name:'Growl',frames:[k=>1/k,k=>(1/k)*(1+Math.sin(k*0.9)),k=>(1/Math.sqrt(k))*(k%2?1:0.4),k=>k>4?1/Math.sqrt(k):0.2]},
  glass:{name:'Glass',frames:[k=>[1,4,9,16].includes(k)?1/Math.sqrt(k):0,k=>[1,3,7,13].includes(k)?1/Math.sqrt(k):0,k=>[1,2,5,11,23].includes(k)?1/Math.sqrt(k):0,k=>[1,6,15,28].includes(k)?1/Math.sqrt(k):0]}
};
function wtWave(table,i){return cache('wt'+table+i,()=>{const n=48,re=new Float32Array(n),im=new Float32Array(n),fn=WT_TABLES[table].frames[i];for(let k=1;k<n;k++)im[k]=fn(k)||0;return ctx.createPeriodicWave(re,im)})}
function wtCurve(i){return cache('wtc'+i,()=>{const n=257,c=new Float32Array(n);for(let j=0;j<n;j++){const x=clamp((j/(n-1))*2-1,0,1)*3;c[j]=Math.max(0,1-Math.abs(x-i))}return c})}
ENG.wt={name:'Wavetable Synth',kind:KIND.synth,family:'synths',
  desc:'Four-frame wavetables morphed by a position signal. Position has its own envelope and is a live modulation destination, so LFOs and step modulators can scan the table.',
  params:[C('table','Wavetable','Oscillator',Object.keys(WT_TABLES).map(k=>[k,WT_TABLES[k].name]),'basic'),P('pos','Position','Oscillator',0,1,0.3,{fmt:FMT.pct,live:1}),P('posEnv','Position env','Oscillator',-1,1,0.3,{fmt:FMT.pct}),P('posDec','Position decay','Oscillator',0.01,8,0.8,{log:true,fmt:FMT.s}),
    P('unison','Unison voices','Oscillator',1,5,2,{int:true,fmt:FMT.int}),P('spread','Unison detune','Oscillator',0,60,14,{fmt:FMT.ct}),P('tune','Pitch','Oscillator',-24,24,0,{int:true,fmt:FMT.st,live:1}),P('fine','Fine tune','Oscillator',-100,100,0,{fmt:FMT.ct,live:1}),
    P('cutoff','Cutoff','Filter',30,18000,6000,{log:true,fmt:FMT.hz,live:1}),P('reso','Resonance','Filter',0.1,20,0.8,{log:true,fmt:FMT.dec,live:1}),P('fenv','Env amount','Filter',-1,1,0.2,{fmt:FMT.pct}),P('keytrack','Key tracking','Filter',0,1,0.4,{fmt:FMT.pct}),
    ...ENV_GROUP('a','Amp',0.01,0.5,0.7,0.5),C('mode','Voice mode','Voice',[['poly','Poly'],['mono','Mono'],['legato','Legato']],'poly'),P('glide','Glide','Voice',0,1,0,{fmt:FMT.s}),...OUT_GROUP()],
  on(tr,n,v,t,out,P,M){
    const f=mtof(n),oscs=[],ext=[],sum=G(1),amp=G(0),uni=Math.round(P.unison),norm=0.5/Math.sqrt(uni),base=P.tune*100+P.fine;
    const pos=ctx.createConstantSource();pos.offset.value=P.pos;pos.start(t);ext.push(pos);M.bind('pos',pos.offset,1);
    if(Math.abs(P.posEnv)>0.01){const p1=clamp(P.pos+P.posEnv,0,1);pos.offset.setValueAtTime(p1,t);pos.offset.setTargetAtTime(P.pos,t,P.posDec/3)}
    for(let fr=0;fr<4;fr++){const sh=ctx.createWaveShaper();sh.curve=wtCurve(fr);const g=G(0);pos.connect(sh).connect(g.gain);g.connect(sum);
      for(let i=0;i<uni;i++){const d=uni>1?(i/(uni-1)*2-1)*P.spread:0,o=O(wtWave(P.table,fr),f,t,tr,base+d);glideOsc(o,1,f,t,P,M);const og=G(norm);o.connect(og).connect(g);oscs.push(o);M.bind('tune',o.detune,4800);M.bind('fine',o.detune,200)}}
    const fl=F('lowpass',20,P.reso);sum.connect(fl).connect(amp);amp.connect(outStage(P,M,out));
    const cut=clamp(P.cutoff*Math.pow(2,(n-60)/12*P.keytrack),20,20000);fl.frequency.setValueAtTime(clamp(cut*Math.pow(2,P.fenv*5),20,20000),t);fl.frequency.setTargetAtTime(cut,t,0.25);
    M.bind('cutoff',fl.detune,Math.log2(18000/30)*1200);M.bind('reso',fl.Q,20);
    if(M.legato)LEGATO_ENV(amp.gain,t,P,'a',0.5*(0.4+v*0.6));else adsr(amp.gain,t,P,'a',0.5*(0.4+v*0.6));
    return voice(amp,oscs,Math.max(0.01,P.aR),ext)}};

/* ───────── FM ───────── */
ENG.fm={name:'FM Synth',kind:KIND.synth,family:'keys',
  desc:'Carrier with a main modulator and a second "tine" modulator, each with its own decay. Covers electric pianos, mallets, bells and FM basses.',
  params:[P('ratio','Mod ratio','Operators',0.5,14,1,{step:0.5,fmt:v=>v.toFixed(1)+':1'}),P('index','Mod index','Operators',0,12,2,{fmt:FMT.dec,live:1}),P('indexSus','Index sustain','Operators',0,1,0.25,{fmt:FMT.pct}),P('indexDecay','Index decay','Operators',0.02,6,1.6,{log:true,fmt:FMT.s}),
    C('tineRatio','Tine ratio','Operators',[[1,'1'],[2,'2'],[3.5,'3.5'],[7,'7'],[14,'14']],14),P('tine','Tine level','Operators',0,1,0.45,{fmt:FMT.pct}),P('tineDecay','Tine decay','Operators',0.005,0.5,0.06,{log:true,fmt:FMT.s}),
    P('attack','Attack','Amp',0.001,2,0.003,{log:true,fmt:FMT.s}),P('decay','Decay','Amp',0.1,12,2.8,{log:true,fmt:FMT.s}),P('sustain','Sustain','Amp',0,1,0,{fmt:FMT.pct}),P('release','Release','Amp',0.02,4,0.35,{log:true,fmt:FMT.s}),
    P('velBright','Velocity → bark','Amp',0,1,0.6,{fmt:FMT.pct}),P('tremRate','Tremolo rate','Tremolo',0.5,10,4.2,{fmt:v=>v.toFixed(1)+'Hz'}),P('trem','Stereo tremolo','Tremolo',0,1,0.35,{fmt:FMT.pct}),
    P('tune','Pitch','Operators',-24,24,0,{int:true,fmt:FMT.st,live:1}),...OUT_GROUP()],
  on(tr,n,v,t,out,P,M){
    const f=mtof(n),amp=G(0),d=P.tune*100,car=O('sine',f,t,tr,d),m1=O('sine',f*P.ratio,t,tr,d),g1=G(0),m2=O('sine',f*P.tineRatio,t,tr,d),g2=G(0),ext=[];
    m1.connect(g1).connect(car.frequency);m2.connect(g2).connect(car.frequency);[car,m1,m2].forEach(o=>M.bind('tune',o.detune,4800));
    const vb=1-P.velBright+P.velBright*v*1.3,idx=f*P.index*vb;g1.gain.setValueAtTime(idx,t);g1.gain.setTargetAtTime(idx*P.indexSus,t,P.indexDecay/3);M.bind('index',g1.gain,f*12);
    g2.gain.setValueAtTime(f*P.tine*4*vb,t);g2.gain.setTargetAtTime(0,t,P.tineDecay/3);
    const tp=ctx.createStereoPanner();if(P.trem>0.01){const l=O('sine',P.tremRate,t,Z),lg=G(P.trem*0.9);l.connect(lg).connect(tp.pan);ext.push(l)}
    car.connect(amp).connect(tp).connect(outStage(P,M,out));
    const pk=0.42*(0.3+v*0.7),dec=P.decay*Math.pow(2,-(n-60)/24);amp.gain.setValueAtTime(0,t);amp.gain.linearRampToValueAtTime(pk,t+P.attack);amp.gain.setTargetAtTime(pk*P.sustain,t+P.attack,dec/3);
    return voice(amp,[car,m1,m2],P.release,ext)}};

/* ───────── Tonewheel organ ───────── */
const DRAWBARS=[[0.5,"16'"],[1.5,"5⅓'"],[1,"8'"],[2,"4'"],[3,"2⅔'"],[4,"2'"],[5,"1⅗'"],[6,"1⅓'"],[8,"1'"]];
ENG.organ={name:'Tonewheel Organ',kind:KIND.synth,family:'keys',
  desc:'Nine drawbars on a real 3 dB-per-step scale with tonewheel foldback, single-trigger percussion (it takes over the 1\' bar, like the original), key click, the vibrato/chorus scanner, a swell pedal, a tube preamp and a two-rotor speaker whose horn and drum speed up and slow down at their own rates. Mod wheel above half switches the rotor to fast.',
  params:[...DRAWBARS.map(([r,l],i)=>P('db'+i,l,'Drawbars',0,8,[8,8,8,0,0,0,0,0,0][i],{int:true,fmt:FMT.int})),C('perc','Percussion','Voice',[['off','Off'],['2nd','2nd'],['3rd','3rd']],'off'),P('percDecay','Perc decay','Voice',0.05,2,0.4,{log:true,fmt:FMT.s}),
    C('percVol','Perc volume','Voice',[['normal','Normal'],['soft','Soft']],'normal'),P('click','Key click','Voice',0,1,0.3,{fmt:FMT.pct}),P('leak','Tonewheel leakage','Voice',0,1,0.15,{fmt:FMT.pct}),
    C('vib','Vibrato / chorus','Scanner',[['off','Off'],['V1','V1'],['V2','V2'],['V3','V3'],['C1','C1'],['C2','C2'],['C3','C3']],'off'),
    P('swell','Swell pedal','Amp',0,1,0.85,{fmt:FMT.pct,live:1}),P('drive','Tube drive','Amp',0,1,0.2,{fmt:FMT.pct}),P('tone','Amp tone','Amp',0,1,0.6,{fmt:FMT.pct}),
    C('leslie','Rotary','Rotary',[['off','Stopped'],['slow','Slow (chorale)'],['fast','Fast (tremolo)']],'slow'),P('hornBal','Horn / drum balance','Rotary',0,1,0.55,{fmt:FMT.pct}),...OUT_GROUP(0.7)],
  setup(tr,out){
    const input=G(1),scan=ctx.createDelay(0.01),scanLfo=ctx.createOscillator(),scanG=G(0),dry=G(1),wet=G(0),swell=G(1),pre=G(1),drv=ctx.createWaveShaper(),toneF=F('lowpass',6000,0.5),split=G(1),lo=F('lowpass',800,0.7),hi=F('highpass',800,0.7),sum=G(1),hiG=G(1),loG=G(1);
    scan.delayTime.value=0.0012;scanLfo.frequency.value=6.9;scanLfo.start();scanLfo.connect(scanG).connect(scan.delayTime);input.connect(scan).connect(wet).connect(swell);input.connect(dry).connect(swell);
    swell.connect(pre).connect(drv).connect(toneF).connect(split);split.connect(lo).connect(loG);split.connect(hi).connect(hiG);
    const rotor=(src,depthMs,amDepth)=>{const d=ctx.createDelay(0.02),am=G(1),pan=ctx.createStereoPanner(),l=ctx.createOscillator();l.frequency.value=0.8;l.start();const dg=G(0),ag=G(0),pg=G(0);
      d.delayTime.value=0.005;l.connect(dg).connect(d.delayTime);l.connect(ag).connect(am.gain);l.connect(pg).connect(pan.pan);src.connect(d).connect(am).connect(pan).connect(sum);return {l,dg,ag,pg,depthMs,amDepth}};
    const horn=rotor(hiG,0.0006,0.35),drum=rotor(loG,0.0003,0.15);sum.connect(out);let lastMode=null;
    const st={input,update(P){const t=ctx.currentTime;drv.curve=curve('soft',P.drive*0.8);at(pre.gain,1+P.drive*1.5);at(toneF.frequency,1800+P.tone*9000);
      const sw=P.swell;at(swell.gain,0.15+0.85*sw*sw);const vd={off:0,V1:0.00025,V2:0.0005,V3:0.0009,C1:0.00025,C2:0.0005,C3:0.0009}[P.vib]||0,ch=P.vib&&P.vib[0]==='C';
      at(scanG.gain,vd);at(wet.gain,vd?(ch?0.5:1):0);at(dry.gain,vd?(ch?0.5:0):1);at(hiG.gain,0.4+P.hornBal*1.2);at(loG.gain,1.6-P.hornBal*1.2);
      const mode=tr._mod!=null&&tr._mod>0.5?'fast':P.leslie,on=mode!=='off',up=lastMode!=null&&mode==='fast'&&lastMode!=='fast';lastMode=mode;
      // the light horn reaches speed in about a second; the heavy drum takes several
      [[horn,mode==='fast'?6.8:mode==='slow'?0.8:0,up?0.9:1.6],[drum,mode==='fast'?5.9:mode==='slow'?0.65:0,up?4:5.5]].forEach(([r,hz,ramp])=>{r.l.frequency.setTargetAtTime(Math.max(0.0001,hz),t,ramp/3);r.dg.gain.setTargetAtTime(on?r.depthMs:0,t,0.3);r.ag.gain.setTargetAtTime(on?r.amDepth:0,t,0.3);r.pg.gain.setTargetAtTime(on?0.7:0,t,0.3)})},
      dispose(){[horn,drum].forEach(r=>{try{r.l.stop()}catch(e){}});try{scanLfo.stop();sum.disconnect()}catch(e){}}};
    const at=(p,v)=>p.setTargetAtTime(v,ctx.currentTime,0.03);st.update(tr.params);return st},
  on(tr,n,v,t,out,P,M){const f=mtof(n),amp=G(0),sum=G(0.085),oscs=[],wave=cache('tonewheel',()=>ctx.createPeriodicWave(new Float32Array([0,0,0,0]),new Float32Array([0,1,0.02,0.03])));
    const fold=x=>{while(x>5900)x/=2;while(x<32.7)x*=2;return x},dbl=lv=>lv<0.5?0:Math.pow(10,-(8-lv)*3/20);
    // single-trigger percussion: only when no other key is down, and it borrows the 1' bar
    const others=tr._rt?[...tr._rt.live].filter(h=>!h.released).length:0,perc=P.perc!=='off'&&others===0;
    DRAWBARS.forEach(([r],i)=>{let lv=dbl(P['db'+i]);if(P.perc!=='off'&&i===8)lv=0;if(perc&&P.percVol==='normal')lv*=0.7;if(!lv)return;const o=O(wave,fold(f*r),t,tr),g=G(lv);o.connect(g).connect(sum);oscs.push(o)});
    if(P.leak>0.01){const o=O('sine',fold(f*3.01),t,tr),g=G(P.leak*0.02);o.connect(g).connect(sum);oscs.push(o)}
    if(perc){const r=P.perc==='3rd'?3:2,o=O('sine',fold(f*r),t,tr),g=G(0),pk=P.percVol==='soft'?0.35:0.7;g.gain.setValueAtTime(pk,t);g.gain.setTargetAtTime(0,t,P.percDecay/3);o.connect(g).connect(sum);oscs.push(o)}
    const clickAt=(tt,lvl)=>{if(P.click<0.01)return;const c=N(tt,0.006+Math.random()*0.004),g=G(P.click*lvl*(0.7+Math.random()*0.6));c.connect(F('bandpass',1800+Math.random()*2400,0.7)).connect(g).connect(amp)};
    clickAt(t,0.3);sum.connect(amp).connect(outStage(P,M,out));amp.gain.setValueAtTime(0,t);amp.gain.linearRampToValueAtTime(1,t+0.004);
    const vo=voice(amp,oscs,0.02);const off=vo.off;vo.off=t2=>{vo.released=true;clickAt(Math.max(t2,ctx.currentTime),0.12);off.call(vo,t2)};return vo}};

/* ───────── Piano (additive model with stretched partials) ───────── */
ENG.piano={name:'Modeled Piano',kind:KIND.synth,family:'keys',
  desc:'Additive piano model: up to twelve partials with string stiffness (stretched tuning), unison-string beating, two-stage decay that depends on pitch, a hammer noise burst, felt damping and body resonance. It is synthesized, not sampled.',
  params:[P('hardness','Hammer hardness','Hammer',0,1,0.5,{fmt:FMT.pct}),P('bright','Brightness','Hammer',0,1,0.55,{fmt:FMT.pct}),P('felt','Felt','Hammer',0,1,0,{fmt:FMT.pct}),
    P('stretch','String stiffness','Strings',0,1,0.4,{fmt:FMT.pct}),P('detune','Unison detune','Strings',0,6,1.2,{fmt:FMT.ct}),P('decay','Decay','Strings',0.3,2.5,1,{fmt:FMT.x}),
    P('body','Body resonance','Body',0,1,0.35,{fmt:FMT.pct}),P('release','Release','Body',0.05,1.5,0.25,{log:true,fmt:FMT.s}),...OUT_GROUP(0.85)],
  on(tr,n,v,t,out,P,M){
    const f=mtof(n),amp=G(1),pre=G(1),oscs=[],B_=P.stretch*0.0004*Math.pow(2,(n-48)/18),br=clamp(0.25+P.bright*0.8*(0.4+v*0.8)-P.felt*0.5,0.05,1.2),K=n>84?5:n>72?8:12,baseT=P.decay*clamp(9*Math.pow(2,-(n-36)/16),0.6,14);
    for(let k=1;k<=K;k++){const fk=f*k*Math.sqrt(1+B_*k*k);if(fk>16000)break;const a=Math.pow(k,-(1.6-br))*(Math.abs(Math.sin(Math.PI*k/7.3))+0.15),T=baseT/(1+0.45*(k-1)*(1+P.felt)),pk=a*0.16*(0.3+v*0.7),g=G(0);
      g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(pk,t+0.002+P.felt*0.006);g.gain.setTargetAtTime(pk*0.35,t+0.003,T*0.08);g.gain.setTargetAtTime(0,t+0.003+T*0.25,T/3);
      const o=O('sine',fk,t,tr);o.connect(g);oscs.push(o);if(k<=3&&P.detune>0.05){const o2=O('sine',fk,t,tr,P.detune*(k===2?-1:1));o2.connect(g);oscs.push(o2)}g.connect(pre)}
    const hn=N(t,0.05),hg=G(0);hg.gain.setValueAtTime((0.04+P.hardness*0.22)*v,t);hg.gain.setTargetAtTime(0,t,0.006+P.felt*0.01);hn.connect(F('bandpass',clamp(f*3,200,8000),0.7)).connect(hg).connect(pre);
    const lp=F('lowpass',clamp(f*(3+(1-P.felt)*18)*(0.5+v),300,18000),0.5),b1=F('peaking',220,1.2),b2=F('peaking',2200,1.5);b1.gain.value=P.body*6;b2.gain.value=P.body*3;
    pre.connect(lp).connect(b1).connect(b2).connect(amp).connect(outStage(P,M,out));return voice(amp,oscs,P.release)}};

/* ───────── Brass ───────── */
ENG.brass={name:'Brass Model',kind:KIND.synth,family:'brass',
  desc:'Synthesized brass: buzzing lip pitch rise, brightness that follows loudness, a bell formant, breath noise, delayed vibrato, a mute, stab articulation and a section of up to six detuned, staggered players.',
  params:[P('bright','Brightness','Tone',0,1,0.6,{fmt:FMT.pct,live:1}),P('growl','Growl','Tone',0,1,0.25,{fmt:FMT.pct}),P('formant','Bell formant','Tone',300,3000,1200,{log:true,fmt:FMT.hz}),P('breath','Breath','Tone',0,1,0.2,{fmt:FMT.pct}),
    P('mute','Mute','Tone',0,1,0,{fmt:FMT.pct}),P('attack','Attack','Articulation',0.005,2,0.04,{log:true,fmt:FMT.s}),P('stab','Stab','Articulation',0,1,0,{fmt:FMT.pct}),P('release','Release','Articulation',0.02,2,0.18,{log:true,fmt:FMT.s}),
    P('vibrato','Vibrato','Expression',0,1,0.3,{fmt:FMT.pct}),P('vibRate','Vibrato rate','Expression',3,8,5.2,{fmt:v=>v.toFixed(1)+'Hz'}),P('section','Players','Section',1,6,1,{int:true,fmt:FMT.int}),P('spread','Section detune','Section',0,30,8,{fmt:FMT.ct}),...OUT_GROUP()],
  on(tr,n,v,t,out,P,M){
    const f=mtof(n),np=Math.round(P.section),amp=G(0),mix=G(0.5/Math.sqrt(np)),oscs=[],ext=[],lp=F('lowpass',f*2,0.7+P.growl*2);
    for(let i=0;i<np;i++){const dl=i?Math.random()*0.03:0,det=np>1?(i/(np-1)*2-1)*P.spread:0,o=O('sawtooth',f,t+dl,tr,det);o.frequency.setValueAtTime(f*Math.pow(2,-(20+P.growl*80)/1200),t+dl);o.frequency.setTargetAtTime(f,t+dl,0.025+P.attack*0.05);
      const pn=ctx.createStereoPanner();pn.pan.value=np>1?(i/(np-1)*2-1)*0.6:0;o.connect(pn).connect(mix);oscs.push(o)}
    const l=O('sine',P.vibRate,t,Z),vg=G(0);vg.gain.setValueAtTime(0,t);vg.gain.linearRampToValueAtTime(P.vibrato*25,t+0.35+P.attack);l.connect(vg);oscs.forEach(o=>vg.connect(o.detune));ext.push(l);
    const lv=0.35+v*0.9,c0=clamp(f*1.2,40,20000),cp=clamp(f*(2+P.bright*14*lv),40,20000),cs=clamp(f*(1.6+P.bright*8*lv),40,20000);
    lp.frequency.setValueAtTime(c0,t);lp.frequency.linearRampToValueAtTime(cp,t+P.attack+0.03);lp.frequency.setTargetAtTime(cs,t+P.attack+0.03,0.15);M.bind('bright',lp.detune,3600);
    let chain=(P.growl>0.05?mix.connect(shaper(P.growl*0.6)):mix).connect(lp);
    if(P.mute>0.01){const dry=G(1-P.mute),wet=G(P.mute),hp=F('highpass',700,0.7),pkf=F('peaking',1800,4),j=G(1);pkf.gain.value=12;chain.connect(dry).connect(j);chain.connect(hp).connect(pkf).connect(wet).connect(j);chain=j}
    const form=F('peaking',P.formant,1.2);form.gain.value=6;chain.connect(form).connect(amp);
    if(P.breath>0.01){const ns=N(t),bg=G(P.breath*0.08);ns.connect(F('bandpass',P.formant*1.3,1.5)).connect(bg).connect(amp);ext.push(ns)}
    amp.connect(outStage(P,M,out));env(amp.gain,t,P.attack+0.01,0.55*(0.35+v*0.65),0.2,0.85);if(P.stab>0.01)amp.gain.setTargetAtTime(0,t+P.attack+0.02,0.04+(1-P.stab)*0.4);
    return voice(amp,oscs,P.release,ext)}};

/* ───────── Bowed strings ───────── */
ENG.strings={name:'Bowed Strings',kind:KIND.synth,family:'strings',
  desc:'Synthesized bowed strings: a section of detuned saw voices with independent vibrato, a three-mode body resonance scaled by register, bow noise and sustain, short and pizzicato articulations.',
  params:[C('artic','Articulation','Articulation',[['sustain','Sustain'],['short','Short'],['pizz','Pizzicato']],'sustain'),P('attack','Attack','Articulation',0.005,3,0.25,{log:true,fmt:FMT.s}),P('release','Release','Articulation',0.02,4,0.5,{log:true,fmt:FMT.s}),
    P('bright','Brightness','Tone',0,1,0.5,{fmt:FMT.pct,live:1}),P('bow','Bow noise','Tone',0,1,0.25,{fmt:FMT.pct}),P('body','Body','Tone',0,1,0.5,{fmt:FMT.pct}),P('bodyScale','Body size','Tone',0.4,2,1,{fmt:FMT.x}),
    P('vibrato','Vibrato','Expression',0,1,0.35,{fmt:FMT.pct}),P('vibRate','Vibrato rate','Expression',3,8,5.5,{fmt:v=>v.toFixed(1)+'Hz'}),P('section','Players','Section',1,8,1,{int:true,fmt:FMT.int}),P('spread','Section detune','Section',0,30,10,{fmt:FMT.ct}),...OUT_GROUP()],
  on(tr,n,v,t,out,P,M){
    const f=mtof(n),np=Math.round(P.section),amp=G(0),mix=G(0.45/Math.sqrt(np)),oscs=[],ext=[];
    for(let i=0;i<np;i++){const det=np>1?(i/(np-1)*2-1)*P.spread:0,o=O('sawtooth',f,t,tr,det),l=O('sine',P.vibRate*(0.9+Math.random()*0.2),t,Z),vg=G(0);vg.gain.setValueAtTime(0,t);vg.gain.linearRampToValueAtTime(P.vibrato*22,t+0.4);l.connect(vg).connect(o.detune);ext.push(l);
      const pn=ctx.createStereoPanner();pn.pan.value=np>1?(i/(np-1)*2-1)*0.7:0;o.connect(pn).connect(mix);oscs.push(o)}
    const lp=F('lowpass',clamp(f*(2+P.bright*10)*(0.5+v*0.7),200,18000),0.6);M.bind('bright',lp.detune,3600);mix.connect(lp);let node=lp;
    [[280,3],[460,3.5],[1100,2.5]].forEach(([hz,q])=>{const b=F('peaking',hz*P.bodyScale,q);b.gain.value=P.body*9;node.connect(b);node=b});node.connect(amp);
    if(P.bow>0.01){const ns=N(t),bg=G(P.bow*0.05);ns.connect(F('bandpass',2600*P.bodyScale,0.8)).connect(bg).connect(amp);ext.push(ns)}
    amp.connect(outStage(P,M,out));const pk=0.5*(0.35+v*0.65);
    if(P.artic==='pizz'){amp.gain.setValueAtTime(0,t);amp.gain.linearRampToValueAtTime(pk,t+0.002);amp.gain.setTargetAtTime(0,t+0.002,n<48?0.5:0.25);lp.frequency.setValueAtTime(clamp(f*8,200,18000),t);lp.frequency.setTargetAtTime(clamp(f*1.5,100,18000),t,0.1)}
    else if(P.artic==='short'){amp.gain.setValueAtTime(0,t);amp.gain.linearRampToValueAtTime(pk,t+0.015);amp.gain.setTargetAtTime(0,t+0.03,0.08)}
    else env(amp.gain,t,P.attack,pk,0.3,0.9);
    return voice(amp,oscs,P.artic==='sustain'?P.release:0.08,ext)}};

/* ───────── Woodwinds ───────── */
ENG.wind={name:'Woodwind Model',kind:KIND.synth,family:'woodwinds',
  desc:'Breath-driven woodwind synthesis with four source types (flute, clarinet, oboe, bassoon), breath noise, attack chiff, formant, delayed vibrato and a small section.',
  params:[C('tone','Instrument','Source',[['flute','Flute'],['clarinet','Clarinet'],['oboe','Oboe'],['bassoon','Bassoon']],'flute'),P('breath','Breath','Source',0,1,0.3,{fmt:FMT.pct}),P('chiff','Chiff','Source',0,1,0.3,{fmt:FMT.pct}),
    P('bright','Brightness','Tone',0,1,0.5,{fmt:FMT.pct,live:1}),P('formant','Formant','Tone',300,3500,1100,{log:true,fmt:FMT.hz}),P('attack','Attack','Articulation',0.005,1.5,0.06,{log:true,fmt:FMT.s}),P('release','Release','Articulation',0.02,2,0.15,{log:true,fmt:FMT.s}),
    P('vibrato','Vibrato','Expression',0,1,0.3,{fmt:FMT.pct}),P('vibRate','Vibrato rate','Expression',3,7,5,{fmt:v=>v.toFixed(1)+'Hz'}),P('section','Players','Section',1,4,1,{int:true,fmt:FMT.int}),...OUT_GROUP()],
  on(tr,n,v,t,out,P,M){
    const f=mtof(n),np=Math.round(P.section),amp=G(0),mix=G(0.6/Math.sqrt(np)),oscs=[],ext=[],tone=F('lowpass',f*(3+P.bright*10),0.7);M.bind('bright',tone.detune,3600);
    for(let i=0;i<np;i++){const det=np>1?(i-(np-1)/2)*7:0,src=G(1);
      if(P.tone==='flute'){[[1,1],[2,0.25],[3,0.08]].forEach(([r,a])=>{const o=O('sine',f*r,t,tr,det),g=G(a);o.connect(g).connect(src);oscs.push(o)})}
      else{const w=P.tone==='clarinet'?'square':pulse(P.tone==='oboe'?0.12:0.3),o=O(w,f,t,tr,det);o.connect(src);oscs.push(o)}
      src.connect(mix)}
    const l=O('sine',P.vibRate,t,Z),vg=G(0);vg.gain.setValueAtTime(0,t+0.2);vg.gain.linearRampToValueAtTime(P.vibrato*20,t+0.6);l.connect(vg);oscs.forEach(o=>vg.connect(o.detune));ext.push(l);
    const form=F('peaking',P.formant,P.tone==='flute'?0.8:2);form.gain.value=P.tone==='flute'?2:9;mix.connect(form).connect(tone).connect(amp);
    if(P.breath>0.01){const ns=N(t),bg=G(P.breath*0.06);ns.connect(F('bandpass',clamp(f*2,300,9000),1)).connect(bg).connect(amp);ext.push(ns)}
    if(P.chiff>0.01){const c=N(t,0.08),cg=G(0);cg.gain.setValueAtTime(P.chiff*0.25*v,t);cg.gain.setTargetAtTime(0,t,0.02);c.connect(F('bandpass',clamp(f*4,500,12000),2)).connect(cg).connect(amp)}
    amp.connect(outStage(P,M,out));env(amp.gain,t,P.attack,0.5*(0.4+v*0.6),0.15,0.9);return voice(amp,oscs,P.release,ext)}};

/* ───────── Plucked strings (Karplus-Strong in an AudioWorklet) ───────── */
ENG.pluck={name:'Plucked String',kind:KIND.phys,family:'bass',
  desc:'Karplus-Strong physical model with fractional-delay tuning: pick position, brightness, sustain time in seconds, string damping, palm mute, body resonance, drive and tone. Pitch bend is not available on this engine.',
  params:[P('bright','Pick brightness','String',0.05,1,0.7,{fmt:FMT.pct}),P('pick','Pick position','String',0,0.5,0.15,{fmt:FMT.pct}),P('sustain','Sustain','String',0.1,15,3,{log:true,fmt:FMT.s}),P('damp','String damping','String',0.05,0.5,0.25,{fmt:FMT.pct}),
    P('mute','Palm mute','String',0,1,0,{fmt:FMT.pct}),P('offDamp','Release damping','String',0.02,1.5,0.15,{log:true,fmt:FMT.s}),P('body','Body','Body',0,1,0.3,{fmt:FMT.pct}),P('drive','Drive','Body',0,1,0,{fmt:FMT.pct}),
    P('tone','Tone','Body',300,16000,8000,{log:true,fmt:FMT.hz,live:1}),...OUT_GROUP()],
  on(tr,n,v,t,out,P,M){
    const f=mtof(n),amp=G(Math.pow(v,1.2)*0.6),tone=F('lowpass',P.tone*(1-P.mute*0.75),0.6);M.bind('tone',tone.detune,Math.log2(16000/300)*1200);
    let node=amp;if(P.drive>0.02){const s=shaper(P.drive*0.8);node.connect(s);node=s}node.connect(tone);node=tone;
    if(P.body>0.02){[[110,1.5],[230,2],[480,2.5]].forEach(([hz,q])=>{const b=F('peaking',hz,q);b.gain.value=P.body*8;node.connect(b);node=b})}
    node.connect(outStage(P,M,out));
    if(!workletOK()){const o=O('triangle',f,t,tr),g=G(0);g.gain.setValueAtTime(0.8,t);g.gain.setTargetAtTime(0,t,Math.min(2,P.sustain/6));o.connect(g).connect(amp);return voice(g,[o],P.offDamp)}
    const T60=P.sustain*(1-P.mute*0.92),g=Math.pow(10,-3/(T60*f)),gOff=Math.pow(10,-3/(P.offDamp*f));
    const node2=new AudioWorkletNode(ctx,'sc-ks',{numberOfInputs:0,numberOfOutputs:1,outputChannelCount:[1],processorOptions:{freq:f,bright:P.bright*(0.4+v*0.6),pick:P.pick,damp:P.damp,g,gOff,start:t,maxLife:T60*2+2}});
    node2.connect(amp);node2.port.onmessage=()=>{try{node2.disconnect();amp.disconnect()}catch(e){}};
    return {oscs:[],off(t2){t2=Math.max(t2,ctx.currentTime);node2.parameters.get('gate').setValueAtTime(0,t2)}}}};

/* ───────── The original Soundcraft engines ───────── */
const VOWELS=[[300,870,2240],[570,840,2410],[730,1090,2440],[530,1840,2480],[270,2290,3010]],VOWEL_NAMES=['oo','oh','ah','eh','ee'];
function vowelAt(x){x=clamp(x,0,1)*4;const i=Math.min(3,Math.floor(x)),f=x-i;return VOWELS[i].map((v,k)=>v+(VOWELS[i+1][k]-v)*f)}
ENG.nebula={name:'Nebula Pad',kind:KIND.synth,family:'synths',
  desc:'Five detuned saws and a sub drifting through a breathing filter.',
  params:[P('bright','Bright','Tone',0,1,0.45,{fmt:FMT.pct}),P('drift','Drift','Tone',0,1,0.5,{fmt:FMT.pct}),P('bloom','Bloom','Tone',0,1,0.55,{fmt:FMT.pct}),...OUT_GROUP()],
  on(tr,n,v,t,out,P,M){const f=mtof(n),amp=G(0),lp=F('lowpass',300+P.bright*P.bright*7000,1.2),lfo=O('sine',0.08+P.drift*0.5,t,Z),lg=G(200+P.drift*1800*(0.3+P.bright));lfo.connect(lg).connect(lp.frequency);
    const mix=G(0.16),oscs=[],sp=4+P.drift*30;for(let i=0;i<5;i++){const o=O('sawtooth',f,t,tr,(i-2)*sp+(Math.random()-0.5)*4);o.connect(mix);oscs.push(o)}
    const sub=O('triangle',f/2,t,tr),sg=G(0.25);sub.connect(sg).connect(lp);oscs.push(sub);mix.connect(lp);lp.connect(amp).connect(outStage(P,M,out));
    env(amp.gain,t,0.02+P.bloom*P.bloom*2.2,0.5*v,1.5,0.7);return voice(amp,oscs,1+P.bloom*2.5,[lfo])}};
ENG.acid={name:'Acid Line',kind:KIND.synth,family:'bass',mono:true,
  desc:'Squelchy resonant mono bass. Overlap notes and it slides between them.',
  params:[P('cutoff','Cutoff','Filter',0,1,0.25,{fmt:FMT.pct}),P('reso','Resonance','Filter',0,1,0.7,{fmt:FMT.pct}),P('envAmt','Envelope','Filter',0,1,0.55,{fmt:FMT.pct}),...OUT_GROUP(0.75)],
  setup(tr,out){const t0=ctx.currentTime,m={held:[]};m.o=O('sawtooth',110,t0,Z);m.o2=O('square',110,t0,Z);const og=G(0.5),o2g=G(0.16);m.lp=F('lowpass',400,10);m.amp=G(0);m.lvl=G(tr.params.level);m.pan=ctx.createStereoPanner();
    m.o.connect(og).connect(m.lp);m.o2.connect(o2g).connect(m.lp);m.lp.connect(shaper(0.3)).connect(m.amp).connect(m.lvl).connect(m.pan).connect(out);
    return {m,update(P){m.lvl.gain.setTargetAtTime(P.level,ctx.currentTime,0.02);m.pan.pan.setTargetAtTime(P.pan,ctx.currentTime,0.02)},dispose(){try{m.o.stop();m.o2.stop();m.pan.disconnect()}catch(e){}}}},
  on(tr,n,v,t,out,P,M){const m=tr._rt.eng.m,f=mtof(n),base=80+P.cutoff*P.cutoff*2200,amt=(300+P.envAmt*6000)*(0.5+v),legato=m.held.length>0;
    m.lp.Q.setValueAtTime(3+P.reso*24,t);M.bind('level',m.lvl.gain,1);M.bind('pan',m.pan.pan,2);
    [m.o,m.o2].forEach(o=>{o.frequency.cancelScheduledValues(t);if(legato)o.frequency.setTargetAtTime(f,t,0.035);else o.frequency.setValueAtTime(f,t)});
    if(!legato)env(m.amp.gain,t,0.004,0.32*(0.6+v*0.5),0.2,0.75);
    const fp=m.lp.frequency;fp.cancelScheduledValues(t);fp.setValueAtTime(base+amt*(legato?0.35:1),t);fp.setTargetAtTime(base,t,0.06+P.envAmt*0.25);m.held.push(n);
    return {oscs:[m.o,m.o2],off(t2){t2=Math.max(t2,ctx.currentTime);const i=m.held.lastIndexOf(n);if(i>=0)m.held.splice(i,1);
      if(!m.held.length){hold(m.amp.gain,t2);m.amp.gain.setTargetAtTime(0,t2,0.02)}else{const f2=mtof(m.held[m.held.length-1]);[m.o,m.o2].forEach(o=>{o.frequency.cancelScheduledValues(t2);o.frequency.setTargetAtTime(f2,t2,0.035)})}}}}};
const GROWL_DIV=['1/2','1/4','1/4t','1/8','1/8t','1/16','1/16t','1/32'];
ENG.growl={name:'Growl Bass',kind:KIND.synth,family:'bass',
  desc:'Detuned saws over a sine sub, wobbled by a filter LFO locked to the tempo.',
  params:[C('rate','Wobble rate','Wobble',GROWL_DIV.map(d=>[d,d.toUpperCase().replace('T',' T')]),'1/8'),P('depth','Depth','Wobble',0,1,0.6,{fmt:FMT.pct}),P('drive','Drive','Tone',0,1,0.45,{fmt:FMT.pct}),...OUT_GROUP(0.75)],
  on(tr,n,v,t,out,P,M){const f=mtof(n),amp=G(0),lp=F('lowpass',180+P.depth*900,9),post=G(0.6),o1=O('sawtooth',f,t,tr,-9),o2=O('sawtooth',f,t,tr,9),o3=O('square',f/2,t,tr),sub=O('sine',f/2,t,tr);
    const mix=G(0.22);o1.connect(mix);o2.connect(mix);mix.connect(lp);const g3=G(0.15);o3.connect(g3).connect(lp);
    const lfo=O('sine',divHz(P.rate),t,Z),lg=G(300+P.depth*3200);lfo.connect(lg).connect(lp.frequency);
    lp.connect(shaper(0.15+P.drive*0.85)).connect(post).connect(amp);const sg=G(0.35);sub.connect(sg).connect(amp);amp.connect(outStage(P,M,out));env(amp.gain,t,0.006,0.5*v,0,1);
    return voice(amp,[o1,o2,o3,sub],0.09,[lfo])}};
ENG.vox={name:'Vox Choir',kind:KIND.synth,family:'synths',
  desc:'Three singers through a three-formant vowel filter with delayed vibrato.',
  params:[P('vowel','Vowel','Voice',0,1,0.5,{fmt:v=>VOWEL_NAMES[Math.round(v*4)]}),P('breath','Breath','Voice',0,1,0.25,{fmt:FMT.pct}),P('ensemble','Ensemble','Voice',0,1,0.5,{fmt:FMT.pct}),...OUT_GROUP(0.6)],
  on(tr,n,v,t,out,P,M){const f=mtof(n),amp=G(0),src=G(0.3),oscs=[],sp=3+P.ensemble*22;[-1,0,1].forEach(k=>{const o=O('sawtooth',f,t,tr,k*sp);o.connect(src);oscs.push(o)});
    const vib=O('sine',5.3,t,Z),vg=G(0);vib.connect(vg);oscs.forEach(o=>vg.connect(o.detune));vg.gain.setValueAtTime(0,t);vg.gain.linearRampToValueAtTime(18,t+0.7);
    const noise=N(t),ng=G(P.breath*0.5);noise.connect(F('highpass',2000)).connect(ng);const gains=[1,0.55,0.3];
    vowelAt(P.vowel).forEach((ff,i)=>{const b=F('bandpass',ff,i?14:9),g=G(gains[i]*3.4);src.connect(b);ng.connect(b);b.connect(g).connect(amp)});
    amp.connect(outStage(P,M,out));env(amp.gain,t,0.14,0.55*v,0.8,0.85);return voice(amp,oscs,0.45,[vib,noise])}};
ENG.chip={name:'8-Bit',kind:KIND.synth,family:'synths',
  desc:'Console-style pulse waves with a pitch blip on every note and delayed vibrato.',
  params:[C('duty','Duty','Pulse',[[0.125,'12.5%'],[0.25,'25%'],[0.5,'50%']],0.25),P('blip','Blip','Pulse',0,1,0.35,{fmt:FMT.pct}),P('vibrato','Vibrato','Pulse',0,1,0.3,{fmt:FMT.pct}),...OUT_GROUP(0.7)],
  on(tr,n,v,t,out,P,M){const f=mtof(n),amp=G(0),o=O(pulse(P.duty),f,t,tr);if(P.blip>0.02){o.frequency.setValueAtTime(f*(1+P.blip*3),t);o.frequency.exponentialRampToValueAtTime(f,t+0.015+P.blip*0.06)}
    const vib=O('sine',6,t,Z),vg=G(0);vib.connect(vg).connect(o.detune);vg.gain.setValueAtTime(0,t+0.22);vg.gain.linearRampToValueAtTime(P.vibrato*45,t+0.45);
    o.connect(amp).connect(outStage(P,M,out));amp.gain.setValueAtTime(0.2*(0.5+v*0.5),t);amp.gain.setValueAtTime(0.15*(0.5+v*0.5),t+0.08);return voice(amp,[o],0.04,[vib])}};
ENG.prism={name:'Prism Bells',kind:KIND.synth,family:'keys',
  desc:'Additive bells. The fundamental and its octave carry the pitch; quieter inharmonic partials give the metallic colour without pulling it out of tune. Shimmer detunes pairs symmetrically, so the average pitch stays exact, and sparkles only use in-tune octaves and fifths.',
  params:[P('shimmer','Shimmer','Bell',0,1,0.4,{fmt:FMT.pct}),P('decay','Decay','Bell',0,1,0.5,{fmt:FMT.pct}),P('sparkle','Sparkle','Bell',0,1,0.3,{fmt:FMT.pct}),...OUT_GROUP()],
  on(tr,n,v,t,out,P,M){const f=mtof(n),amp=G(1),R=[1,2,3,2.76,4.07,5.4,8.93],A_=[1,0.5,0.22,0.12,0.09,0.07,0.04],oscs=[],dec=0.8+P.decay*5;amp.connect(outStage(P,M,out));
    R.forEach((r,i)=>{if(f*r>16000)return;const g=G(0),pk=A_[i]*0.24*v;g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(pk,t+0.002);g.gain.setTargetAtTime(0,t+0.002,dec/(1+i*0.9)/3);
      const d=P.shimmer*2.2*(i+1);if(d>0.05){[-d,d].forEach(x=>{const o=O('sine',f*r,t,tr,x),h=G(0.5);o.connect(h).connect(g);oscs.push(o)})}else{const o=O('sine',f*r,t,tr);o.connect(g);oscs.push(o)}g.connect(amp)});
    if(P.sparkle>0.02){const SP=[2,3,4,6,8];for(let j=0;j<1+Math.floor(P.sparkle*4);j++){const tt=t+0.04+Math.random()*0.5*P.sparkle,o=O('sine',Math.min(15000,f*SP[Math.floor(Math.random()*SP.length)]),tt,Z),g=G(0);g.gain.setValueAtTime(0,tt);g.gain.linearRampToValueAtTime(0.04*v,tt+0.002);g.gain.setTargetAtTime(0,tt+0.002,0.08);o.connect(g).connect(amp);o.stop(tt+0.6)}}
    return voice(amp,oscs,0.4+dec*0.5)}};

/* ───────── Celesta (struck steel bars over wooden resonators) ───────── */
const hash01=x=>{const s=Math.sin(x*12.9898)*43758.5453;return s-Math.floor(s)};
ENG.celesta={name:'Celesta',kind:KIND.phys,family:'keys',
  desc:'Modal model of a celesta: felt hammers strike steel bars (free-bar partials 1, 2.76, 5.40) above tuned wooden resonators that reinforce the fundamental. Low notes are round and woody, high notes short and glassy; harder playing brings out the upper partials and the hammer. Dampers stop notes on release unless the sustain pedal is down.',
  params:[P('hardness','Hammer hardness','Bar',0,1,0.4,{fmt:FMT.pct}),P('resonator','Resonator','Bar',0,1,0.6,{fmt:FMT.pct}),P('decay','Ring time','Bar',0.3,3,1,{log:true,fmt:v=>v.toFixed(2)+'×'}),
    P('damp','Damper release','Bar',0.05,2,0.25,{log:true,fmt:FMT.s}),...OUT_GROUP(0.8)],
  on(tr,n,v,t,out,P,M){const f=mtof(n),amp=G(1),dst=outStage(P,M,out),oscs=[],reg=clamp((n-48)/48,0,1),hard=clamp(P.hardness*0.6+v*0.6,0,1);amp.connect(dst);
    const T1=P.decay*(2.6-reg*1.9),parts=[[1,1,T1],[2.756,0.06+hard*0.12,T1*0.18],[5.404,0.02+hard*0.08,T1*0.06]];
    parts.forEach(([r,a,T],i)=>{if(f*r>17000)return;const o=O('sine',f*r,t,tr),g=G(0),pk=a*0.32*(0.35+v*0.65),att=0.002+(1-hard)*0.004;g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(pk,t+att);g.gain.setTargetAtTime(0,t+att,T/4);o.connect(g).connect(amp);oscs.push(o)});
    if(P.resonator>0.02){const o=O('sine',f,t,tr),g=G(0),pk=0.12*P.resonator*(1-reg*0.6)*(0.4+v*0.6);g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(pk,t+0.02);g.gain.setTargetAtTime(0,t+0.02,T1*0.35);o.connect(F('lowpass',f*1.5,0.7)).connect(g).connect(amp);oscs.push(o)}
    const th=N(t,0.012),tg=G(0.05*hard*(0.8+hash01(n+(tr._hits=(tr._hits||0)+1))*0.4));th.connect(F('bandpass',f*2+600,1.2)).connect(tg).connect(amp);
    return voice(amp,oscs,P.damp)}};
/* ───────── Music box (plucked steel comb) ───────── */
ENG.musicbox={name:'Music Box',kind:KIND.phys,family:'keys',
  desc:'Modal model of a music-box comb: pins pluck clamped steel tines (cantilever partials 1, 6.27, 17.55) for a bright mechanical attack that rings on, with a small wooden case resonance. Each tine has its own fixed few-cent tuning error and pluck, like a real comb, and striking a tine that is still ringing gives the short damper buzz of the mechanism.',
  params:[P('pin','Pin brightness','Comb',0,1,0.6,{fmt:FMT.pct}),P('ring','Ring time','Comb',0.3,3,1,{log:true,fmt:v=>v.toFixed(2)+'×'}),P('case','Wooden case','Comb',0,1,0.5,{fmt:FMT.pct}),
    P('mech','Mechanism noise','Comb',0,1,0.2,{fmt:FMT.pct}),P('tuning','Comb tuning spread','Comb',0,1,0.4,{fmt:v=>'±'+(v*4).toFixed(1)+' c'}),...OUT_GROUP(0.8)],
  on(tr,n,v,t,out,P,M){const amp=G(1),dst=outStage(P,M,out),oscs=[],reg=clamp((n-60)/36,0,1);amp.connect(dst);
    const dev=(hash01(n*1.37)-0.5)*8*P.tuning,f=mtof(n)*Math.pow(2,dev/1200),hit=(tr._hits=(tr._hits||0)+1),var_=0.85+hash01(n+hit*0.71)*0.3;
    const ringing=tr._rt&&[...tr._rt.live].some(h=>h.n===n);
    if(ringing){const bz=N(t,0.035),bg=G(0.06*v);bz.connect(F('bandpass',f,6)).connect(bg).connect(amp)}
    const T1=P.ring*(4.5-reg*3.2);[[1,1,T1],[6.267,0.25+P.pin*0.4,T1*0.08],[17.55,0.08+P.pin*0.25,T1*0.025]].forEach(([r,a,T])=>{if(f*r>18000)return;const o=O('sine',f*r,t,tr,dev),g=G(0),pk=a*0.3*(0.45+v*0.55)*var_;g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(pk,t+0.0008);g.gain.setTargetAtTime(0,t+0.0008,T/4);o.connect(g).connect(amp);oscs.push(o)});
    const pk=N(t,0.004),pg=G(0.08*(0.4+P.pin)*v);pk.connect(F('highpass',4000,0.7)).connect(pg).connect(amp);
    if(P.case>0.02){const c=N(t,0.03),cg=G(0.05*P.case*v);c.connect(F('bandpass',420,2.5)).connect(cg).connect(amp)}
    if(P.mech>0.02){const tk=N(t+0.004+hash01(hit)*0.01,0.003),kg=G(0.03*P.mech);tk.connect(F('bandpass',2500,3)).connect(kg).connect(amp)}
    const vo=voice(amp,oscs,2.5+T1*0.3);return vo}};
