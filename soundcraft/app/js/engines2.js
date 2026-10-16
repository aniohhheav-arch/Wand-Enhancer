'use strict';
/* Drum machine, experimental engines and the Fusion Lab layer rack. */

/* ───────── drum voices ───────── */
function hitEnv(g,t,peak,dec){g.gain.setValueAtTime(peak,t);g.gain.exponentialRampToValueAtTime(0.001,t+Math.max(0.01,dec))}
function noiseHit(t,d,dec,peak,filters){const n=N(t,dec+0.1),g=G(0);let x=n;filters.forEach(f=>{x=x.connect(f)});x.connect(g).connect(d);hitEnv(g,t,peak,dec);return g}
function toneHit(t,d,type,f0,f1,sweep,dec,peak){const o=O(type,f0,t,Z),g=G(0);if(f1&&f1!==f0){o.frequency.setValueAtTime(f0,t);o.frequency.exponentialRampToValueAtTime(f1,t+sweep)}o.connect(g).connect(d);hitEnv(g,t,peak,dec);o.stop(t+dec+0.1);return g}
const CYM=[205.3,304.4,369.6,522.7,540,800];
function metal(t,d,decay,peak,r=1,bp=10000,hp=7000){const mix=G(0.16),g=G(0);CYM.forEach(f=>{const o=O('square',f*r,t,Z);o.connect(mix);o.stop(t+decay+0.08)});hitEnv(g,t,peak,decay);mix.connect(F('bandpass',bp,0.8)).connect(F('highpass',hp)).connect(g).connect(d);return g}
const tomFn=(f,ac)=>(t,d,q)=>{toneHit(t,d,'sine',f*1.7*q.r,f*q.r,0.08,(0.3+q.punch*0.4)*q.k,0.9);if(ac)noiseHit(t,d,0.08*q.k,0.15,[F('lowpass',f*6*q.r,0.7)])};
const congaFn=(f,slap)=>(t,d,q)=>{toneHit(t,d,'sine',f*1.5*q.r,f*q.r,0.015,0.22*q.k,0.8);noiseHit(t,d,slap?0.05:0.02,slap?0.5:0.15,[F('bandpass',slap?1800:1200,1)])};
const timpFn=f=>(t,d,q)=>{[[1,1],[1.5,0.5],[1.98,0.35],[2.44,0.2]].forEach(([m,a])=>toneHit(t,d,'sine',f*m*q.r,f*m*q.r*0.98,0.2,1.6*q.k/m,a*0.6));noiseHit(t,d,0.06,0.2,[F('lowpass',400,0.7)])};
const DV={
  kick808(t,d,q){const o=O('sine',50,t,Z),g=G(0);o.frequency.setValueAtTime((110+q.punch*190)*q.r,t);o.frequency.exponentialRampToValueAtTime(44*q.r,t+0.04+q.punch*0.1);g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(1,t+0.002);g.gain.exponentialRampToValueAtTime(0.001,t+(0.3+q.punch*0.55)*q.k);o.connect(g).connect(d);o.stop(t+1.4*q.k+0.1);noiseHit(t,d,0.012,0.25*(0.3+q.snap),[F('highpass',3000)])},
  kick909(t,d,q){const s=shaper(0.3);s.connect(d);toneHit(t,s,'sine',(140+q.punch*80)*q.r,52*q.r,0.035,0.45*q.k,1);noiseHit(t,d,0.01,0.3*(0.4+q.snap),[F('highpass',2000)])},
  kickAc(t,d,q){toneHit(t,d,'sine',130*q.r,58*q.r,0.03,0.32*q.k*(0.7+q.punch*0.6),1);noiseHit(t,d,0.006,0.3*(0.3+q.snap),[F('highpass',2500)]);noiseHit(t,d,0.05,0.4,[F('lowpass',180,0.7)])},
  kickBB(t,d,q){const s=shaper(0.5),lp=F('lowpass',1200,0.7);s.connect(lp).connect(d);toneHit(t,s,'sine',100*q.r,50*q.r,0.05,0.5*q.k*(0.7+q.punch*0.6),0.9)},
  kickSoft(t,d,q){toneHit(t,d,'sine',70*q.r,58*q.r,0.02,0.22*q.k,0.6)},
  snare808(t,d,q){toneHit(t,d,'triangle',240*q.r,170*q.r,0.05,0.12*q.k,0.7);noiseHit(t,d,(0.16+q.snap*0.14)*q.k,0.9,[F('bandpass',1500+q.snap*4000,0.7),F('highpass',800)])},
  snare909(t,d,q){toneHit(t,d,'triangle',180*q.r,170*q.r,0.03,0.1*q.k,0.5);toneHit(t,d,'triangle',330*q.r,320*q.r,0.03,0.08*q.k,0.3);noiseHit(t,d,(0.2+q.snap*0.1)*q.k,0.8,[F('highpass',1200),F('bandpass',5000,0.6)])},
  snareAc(t,d,q){toneHit(t,d,'triangle',185*q.r,180*q.r,0.02,0.1*q.k,0.6);toneHit(t,d,'triangle',330*q.r,330*q.r,0,0.07*q.k,0.3);noiseHit(t,d,(0.22+q.snap*0.1)*q.k,0.8,[F('highpass',1500)]);noiseHit(t,d,0.12*q.k,0.25,[F('bandpass',4000,2)])},
  snareBB(t,d,q){const s=shaper(0.4),lp=F('lowpass',5000,0.7);s.connect(lp).connect(d);toneHit(t,s,'triangle',200*q.r,190*q.r,0.03,0.09*q.k,0.6);noiseHit(t,s,0.22*q.k,0.8,[F('highpass',400)])},
  snareBreak(t,d,q){DV.snareAc(t,d,q);toneHit(t,d,'triangle',240*q.r,235*q.r,0.1,0.2*q.k,0.25);noiseHit(t,d,0.35*q.k,0.3,[F('bandpass',2500,0.8)])},
  ghost(t,d,q){const g=G(0.35);g.connect(d);DV.snareBreak(t,g,q)},
  snareOrch(t,d,q){noiseHit(t,d,0.14*q.k,0.8,[F('highpass',2000)]);toneHit(t,d,'triangle',250*q.r,240*q.r,0.02,0.05,0.4)},
  clap(t,d,q){const n=N(t,0.6),g=G(0),gp=g.gain;n.connect(F('bandpass',(1000+q.snap*1000)*q.r,1.3)).connect(g).connect(d);gp.setValueAtTime(0,t);[0,0.012,0.024].forEach(o=>{gp.setValueAtTime(0.9,t+o);gp.exponentialRampToValueAtTime(0.1,t+o+0.01)});gp.setValueAtTime(0.8,t+0.036);gp.exponentialRampToValueAtTime(0.001,t+(0.25+q.snap*0.15)*q.k)},
  rim(t,d,q){toneHit(t,d,'triangle',1700*q.r,1700*q.r,0,0.03*q.k,0.6);toneHit(t,d,'square',480*q.r,480*q.r,0,0.02,0.15)},
  sidestick(t,d,q){toneHit(t,d,'triangle',420*q.r,400*q.r,0.01,0.05*q.k,0.5);noiseHit(t,d,0.02,0.4,[F('highpass',3000)])},
  hatC(t,d,q,rt){if(rt.ohat){hold(rt.ohat.gain,t);rt.ohat.gain.setTargetAtTime(0,t,0.01);rt.ohat=null}metal(t,d,(0.04+q.snap*0.05)*q.k,0.8,q.r)},
  hatO(t,d,q,rt){rt.ohat=metal(t,d,(0.3+q.snap*0.35)*q.k,0.6,q.r)},
  hatAcC(t,d,q,rt){if(rt.ohat){hold(rt.ohat.gain,t);rt.ohat.gain.setTargetAtTime(0,t,0.01);rt.ohat=null}metal(t,d,0.05*q.k,0.4,q.r*1.4,11000,8000);noiseHit(t,d,0.04*q.k,0.35,[F('highpass',7000)])},
  hatAcO(t,d,q,rt){rt.ohat=metal(t,d,0.45*q.k,0.35,q.r*1.4,11000,8000);noiseHit(t,d,0.35*q.k,0.25,[F('highpass',7000)])},
  hatBB(t,d,q){noiseHit(t,d,0.045*q.k,0.5,[F('highpass',5000),F('lowpass',7000)])},
  hatBBo(t,d,q){noiseHit(t,d,0.25*q.k,0.35,[F('highpass',5000),F('lowpass',7500)])},
  ride(t,d,q){metal(t,d,1.4*q.k,0.3,q.r*1.2,9000,5000);const b=F('peaking',2500,3);b.gain.value=8;const g=G(1);b.connect(d);metal(t,b,0.6*q.k,0.12,q.r*2.1,6000,2000)},
  crash(t,d,q){metal(t,d,1.3*q.k,0.5,q.r);noiseHit(t,d,1.4*q.k,0.35,[F('highpass',5000)])},
  crashSoft(t,d,q){metal(t,d,2.4*q.k,0.25,q.r*0.9,8000,4000);noiseHit(t,d,2.2*q.k,0.18,[F('highpass',3000),F('lowpass',9000)])},
  cow(t,d,q){const bp=F('bandpass',2640,1.2),g=G(0);[587,845].forEach(f=>{const o=O('square',f*q.r,t,Z);o.connect(bp);o.stop(t+0.5)});hitEnv(g,t,0.5,0.3*q.k);bp.connect(g).connect(d)},
  zap(t,d,q){toneHit(t,d,'sine',3200*q.r,70*q.r,0.12+q.punch*0.1,0.25*q.k,0.5)},
  clave(t,d,q){toneHit(t,d,'sine',2500*q.r,2500*q.r,0,0.04*q.k,0.7)},
  wood(t,d,q){toneHit(t,d,'sine',800*q.r,780*q.r,0.01,0.06*q.k,0.6);toneHit(t,d,'sine',1250*q.r,1250*q.r,0,0.03*q.k,0.3)},
  shaker(t,d,q){const n=N(t,0.2),g=G(0),f=F('bandpass',6000*q.r,1.5);n.connect(f).connect(g).connect(d);g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(0.45,t+0.015);g.gain.exponentialRampToValueAtTime(0.001,t+0.08*q.k)},
  tamb(t,d,q){metal(t,d,0.25*q.k,0.35,q.r*1.6,9000,6000);noiseHit(t,d,0.2*q.k,0.25,[F('highpass',6000)])},
  congaLo:congaFn(180),congaMid:congaFn(250),congaHi:congaFn(330),congaSlap:congaFn(330,true),bongoLo:congaFn(400),bongoHi:congaFn(520),
  timpLo:timpFn(73),timpMid:timpFn(98),timpHi:timpFn(131),
  granCassa(t,d,q){toneHit(t,d,'sine',55*q.r,45*q.r,0.1,1.6*q.k,0.9);noiseHit(t,d,0.4*q.k,0.3,[F('lowpass',200,0.7)])},
  triangle(t,d,q){[[2500,0.4],[6100,0.2],[9300,0.12]].forEach(([f,a])=>toneHit(t,d,'sine',f*q.r,f*q.r,0,2.2*q.k,a))},
  tamtam(t,d,q){metal(t,d,3.5*q.k,0.4,q.r*0.35,1500,200);noiseHit(t,d,3*q.k,0.12,[F('lowpass',1200)])},
  glock(t,d,q){[[1,0.5],[2.76,0.15],[5.4,0.08]].forEach(([m,a])=>toneHit(t,d,'sine',1568*m*q.r,1568*m*q.r,0,1.2*q.k/m,a))},
  brushTap(t,d,q){noiseHit(t,d,0.07*q.k,0.5,[F('bandpass',2200*q.r,1)]);toneHit(t,d,'triangle',200*q.r,190*q.r,0.02,0.05,0.2)},
  brushSwish(t,d,q){const n=N(t,0.6),g=G(0);n.connect(F('bandpass',3000*q.r,0.8)).connect(g).connect(d);g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(0.35,t+0.08);g.gain.exponentialRampToValueAtTime(0.001,t+0.35*q.k)},
  tomL808:tomFn(90),tomM808:tomFn(130),tomH808:tomFn(190),tomAcL:tomFn(95,1),tomAcM:tomFn(140,1),tomAcH:tomFn(200,1)
};
const KITS={
  circuit:{name:'808 Circuit',pads:[['kick808','Kick'],['rim','Rim'],['snare808','Snare'],['clap','Clap'],['tomL808','Tom low'],['hatC','Hat closed'],['tomM808','Tom mid'],['hatO','Hat open'],['tomH808','Tom high'],['crash','Crash'],['cow','Cowbell'],['zap','Zap']]},
  electro:{name:'909 Electro',pads:[['kick909','Kick'],['rim','Rim'],['snare909','Snare'],['clap','Clap'],['tomL808','Tom low'],['hatC','Hat closed'],['tomM808','Tom mid'],['hatO','Hat open'],['tomH808','Tom high'],['crash','Crash'],['ride','Ride'],['zap','Zap']]},
  acoustic:{name:'Studio Acoustic',pads:[['kickAc','Kick'],['sidestick','Side stick'],['snareAc','Snare'],['ghost','Ghost snare'],['tomAcL','Floor tom'],['hatAcC','Hat closed'],['tomAcM','Rack tom'],['hatAcO','Hat open'],['tomAcH','High tom'],['crash','Crash'],['ride','Ride'],['tamb','Tambourine']]},
  boombap:{name:'Boom-bap Dust',pads:[['kickBB','Kick'],['rim','Rim'],['snareBB','Snare'],['clap','Clap'],['kickSoft','Kick soft'],['hatBB','Hat closed'],['tomL808','Tom'],['hatBBo','Hat open'],['shaker','Shaker'],['crashSoft','Crash'],['cow','Cowbell'],['tamb','Tambourine']]},
  brushes:{name:'Jazz Brushes',pads:[['kickSoft','Kick'],['sidestick','Side stick'],['brushTap','Brush tap'],['brushSwish','Brush swish'],['tomAcL','Floor tom'],['hatAcC','Hat closed'],['tomAcM','Rack tom'],['hatAcO','Hat open'],['tomAcH','High tom'],['crashSoft','Crash'],['ride','Ride'],['shaker','Shaker']]},
  break:{name:'Break Kit',pads:[['kickBB','Kick'],['rim','Rim'],['snareBreak','Snare'],['clap','Clap'],['ghost','Ghost snare'],['hatAcC','Hat closed'],['tomAcM','Tom'],['hatAcO','Hat open'],['tomAcH','High tom'],['crash','Crash'],['ride','Ride'],['tamb','Tambourine']]},
  hand:{name:'Hand Percussion',pads:[['congaLo','Conga low'],['congaMid','Conga mid'],['congaHi','Conga high'],['congaSlap','Conga slap'],['bongoLo','Bongo low'],['bongoHi','Bongo high'],['shaker','Shaker'],['tamb','Tambourine'],['clap','Clap'],['clave','Clave'],['wood','Woodblock'],['cow','Cowbell']]},
  orch:{name:'Orchestral Percussion',pads:[['timpLo','Timpani low'],['timpMid','Timpani mid'],['timpHi','Timpani high'],['granCassa','Bass drum'],['snareOrch','Snare'],['crash','Clash cymbals'],['crashSoft','Suspended cymbal'],['triangle','Triangle'],['tamtam','Tam-tam'],['wood','Woodblock'],['tamb','Tambourine'],['glock','Glockenspiel']]}
};
const DRUM_NOTES=[36,37,38,39,41,42,45,46,48,49,56,60];
const DRUM_BY_N=Object.fromEntries(DRUM_NOTES.map((n,i)=>[n,i]));
const padIndex=n=>DRUM_BY_N[n]!=null?DRUM_BY_N[n]:((n%12)+12)%12;
const padName=(tr,i)=>KITS[tr.params.kit].pads[i][1];
ENG.kit={name:'Drum Machine',kind:KIND.synth,family:'drums',drum:true,
  desc:'Synthesized drum machine with eight kits. Each of the twelve pads has its own tune, decay, level and pan; the whole kit has punch, snap, crush, tune and decay. Pad controllers map by General MIDI note.',
  params:[C('kit','Kit','Kit',Object.keys(KITS).map(k=>[k,KITS[k].name]),'circuit'),P('punch','Punch','Kit',0,1,0.5,{fmt:FMT.pct}),P('snap','Snap','Kit',0,1,0.5,{fmt:FMT.pct}),P('crush','Crush','Kit',0,1,0,{fmt:FMT.pct}),
    P('tune','Kit tune','Kit',-12,12,0,{fmt:FMT.st}),P('decay','Kit decay','Kit',0.3,2.5,1,{log:true,fmt:FMT.x}),
    ...DRUM_NOTES.flatMap((_,i)=>[P('p'+i+'t','Pad '+(i+1)+' tune','Pad '+(i+1),-12,12,0,{fmt:FMT.st}),P('p'+i+'d','Pad '+(i+1)+' decay','Pad '+(i+1),0.25,3,1,{log:true,fmt:FMT.x}),P('p'+i+'l','Pad '+(i+1)+' level','Pad '+(i+1),0,1.5,1,{fmt:FMT.pct}),P('p'+i+'p','Pad '+(i+1)+' pan','Pad '+(i+1),-1,1,0,{fmt:FMT.pan})]),
    ...OUT_GROUP(0.85)],
  on(tr,n,v,t,out,P,M){const i=padIndex(n),kit=KITS[P.kit]||KITS.circuit,g=G(v*0.9*P['p'+i+'l']);let dst=outStage(P,M,out);
    if(Math.abs(P['p'+i+'p'])>0.01){const pn=ctx.createStereoPanner();pn.pan.value=P['p'+i+'p'];pn.connect(dst);dst=pn}
    if(P.crush>0.03){g.connect(shaper(P.crush*0.9)).connect(G(1-P.crush*0.45)).connect(dst)}else g.connect(dst);
    DV[kit.pads[i][0]](t,g,{r:Math.pow(2,(P.tune+P['p'+i+'t'])/12),k:P.decay*P['p'+i+'d'],punch:P.punch,snap:P.snap},tr._rt);
    if(!OFF)setTimeout(()=>{try{g.disconnect()}catch(e){}},(t-ctx.currentTime+4*P.decay*P['p'+i+'d']+1)*1000);return {oscs:[],off(){}}}};

/* ───────── Granular ───────── */
const GRAIN_SRC={glass:'Glass',choir:'Choir',air:'Air',metal:'Metal'};
function grainBuffer(kind){return cache('grain'+kind,()=>{const sr=ctx.sampleRate,len=Math.floor(sr*2.5),b=ctx.createBuffer(1,len,sr),d=b.getChannelData(0),f0=261.63;
  if(kind==='air'){let y1=0,y2=0;for(let i=0;i<len;i++){const fc=600+500*Math.sin(i/sr*1.3),w=2*Math.PI*fc/sr,r=0.985,x=Math.random()*2-1,y=x*(1-r)+2*r*Math.cos(w)*y1-r*r*y2;y2=y1;y1=y;d[i]=y*3}}
  else{const P_=kind==='glass'?[[1,1],[2.76,0.5],[5.4,0.3],[8.93,0.15]]:kind==='metal'?[[1,1],[1.41,0.7],[2.33,0.5],[3.17,0.4],[4.6,0.25]]:Array.from({length:14},(_,k)=>[k+1,Math.exp(-Math.pow((k+1-3)/1.6,2))+0.6*Math.exp(-Math.pow((k+1-8)/2.4,2))+0.03]);
    for(let i=0;i<len;i++){const tt=i/sr,vib=kind==='choir'?Math.sin(tt*2*Math.PI*5)*0.004:0;let s=0;for(const[m,a]of P_)s+=a*Math.sin(2*Math.PI*f0*m*tt*(1+vib)+m);d[i]=s*0.3*(1+0.2*Math.sin(tt*2.1))}}
  let m=0;for(let i=0;i<len;i++)m=Math.max(m,Math.abs(d[i]));for(let i=0;i<len;i++)d[i]/=m||1;return b})}
const HANN=(()=>{const c=new Float32Array(32);for(let i=0;i<32;i++)c[i]=Math.sin(Math.PI*i/31)**2;return c})();
const GRAIN_VOICES=new Set();
ENG.granular={name:'Granular Cloud',kind:KIND.synth,family:'experimental',
  desc:'Granular synthesis over four generated source textures. Grain position, size, density, spray and pitch scatter are read at every grain, so live modulators move them continuously.',
  params:[C('src','Source','Grains',Object.entries(GRAIN_SRC),'glass'),P('position','Position','Grains',0,1,0.3,{fmt:FMT.pct,live:'js'}),P('scan','Scan speed','Grains',0,1,0.15,{fmt:FMT.pct,live:'js'}),P('size','Grain size','Grains',0.01,0.4,0.09,{log:true,fmt:FMT.s,live:'js'}),
    P('density','Density','Grains',2,80,24,{log:true,fmt:v=>Math.round(v)+'/s',live:'js'}),P('spray','Spray','Grains',0,1,0.2,{fmt:FMT.pct,live:'js'}),P('scatter','Pitch scatter','Grains',0,12,0.2,{fmt:FMT.st,live:'js'}),
    P('attack','Attack','Amp',0.005,6,0.6,{log:true,fmt:FMT.s}),P('release','Release','Amp',0.02,8,1.5,{log:true,fmt:FMT.s}),...OUT_GROUP(0.7)],
  on(tr,n,v,t,out,P,M){const buf=grainBuffer(P.src),amp=G(0),rate0=mtof(n)/261.63,dst=outStage(P,M,out);amp.connect(dst);env(amp.gain,t,P.attack,0.6*(0.4+v*0.6),0.5,1);
    const vc={t0:t,next:t,end:Infinity,grains:[],dead:false};
    const val=k=>M.js?M.js(k,P[k]):P[k];
    vc.pump=until=>{while(vc.next<until&&vc.next<vc.end){const tt=vc.next,size=val('size'),pos=(val('position')+val('scan')*(tt-t)*0.2)%1,sp=val('spray');
      const off=clamp(pos+(Math.random()-0.5)*sp*0.5,0,1)*(buf.duration-size*4),r=rate0*Math.pow(2,(Math.random()*2-1)*val('scatter')/12),s=ctx.createBufferSource(),g=G(0);
      s.buffer=buf;s.playbackRate.value=r;s.detune.value=(tr._bend||0)*100;g.gain.setValueCurveAtTime(HANN,tt,size);s.connect(g).connect(amp);s.start(tt,off,size*r+0.01);s.stop(tt+size+0.02);vc.grains.push({s,g,t:tt});
      vc.next+=1/val('density')*(0.75+Math.random()*0.5)}
      if(vc.grains.length>200)vc.grains.splice(0,vc.grains.length-200)};
    if(OFF)vc.pump(t+12);else{GRAIN_VOICES.add(vc);vc.pump(ctx.currentTime+0.15)}
    return {oscs:[],off(t2){t2=Math.max(t2,ctx.currentTime);vc.end=t2+P.release;hold(amp.gain,t2);amp.gain.setTargetAtTime(0,t2,P.release/5);
      if(OFF)vc.grains.forEach(gr=>{if(gr.t>vc.end)try{gr.g.disconnect()}catch(e){}});else setTimeout(()=>{GRAIN_VOICES.delete(vc);try{amp.disconnect()}catch(e){}},(vc.end-ctx.currentTime+0.5)*1000)}}}};
setInterval(()=>{if(!ctx||OFF)return;const until=ctx.currentTime+0.15;GRAIN_VOICES.forEach(vc=>vc.pump(until))},25);

/* ───────── Tape & vinyl ───────── */
function crackleBuf(){return cache('crackle',()=>{const sr=ctx.sampleRate,b=ctx.createBuffer(1,sr*3,sr),d=b.getChannelData(0);for(let i=0;i<d.length;i++)if(Math.random()<0.0009)d[i]=(Math.random()*2-1)*(0.3+Math.random()*0.7);return b})}
ENG.tape={name:'Tape & Vinyl',kind:KIND.synth,family:'experimental',
  desc:'A soft keys tone pushed through wow, flutter, saturation, age filtering, hiss and vinyl crackle, all generated in real time.',
  params:[P('tone','Tone','Source',0,1,0.45,{fmt:FMT.pct}),P('wow','Wow','Tape',0,1,0.35,{fmt:FMT.pct}),P('flutter','Flutter','Tape',0,1,0.3,{fmt:FMT.pct}),P('sat','Saturation','Tape',0,1,0.35,{fmt:FMT.pct}),
    P('age','Age','Tape',0,1,0.5,{fmt:FMT.pct}),P('hiss','Hiss','Vinyl',0,1,0.25,{fmt:FMT.pct}),P('crackle','Crackle','Vinyl',0,1,0.35,{fmt:FMT.pct}),...ENV_GROUP('a','Amp',0.005,1.2,0.3,0.6),...OUT_GROUP(0.75)],
  on(tr,n,v,t,out,P,M){const f=mtof(n),amp=G(0),src=G(0.4),o1=O('triangle',f,t,tr),o2=O('sawtooth',f,t,tr,4),g2=G(P.tone*0.35),w=O('sine',0.6,t,Z),wg=G(P.wow*25),fl=O('sine',7.5,t,Z),fg=G(P.flutter*8);
    o1.connect(src);o2.connect(g2).connect(src);w.connect(wg);fl.connect(fg);[o1,o2].forEach(o=>{wg.connect(o.detune);fg.connect(o.detune)});
    const lp=F('lowpass',12000-P.age*9500,0.6),hp=F('highpass',60+P.age*140,0.6);src.connect(shaper(0.2+P.sat*0.8)).connect(hp).connect(lp).connect(amp);
    const ext=[w,fl];if(P.hiss>0.01){const h=N(t),hg=G(P.hiss*0.025);h.connect(F('highpass',3000)).connect(hg).connect(amp);ext.push(h)}
    if(P.crackle>0.01){const c=ctx.createBufferSource();c.buffer=crackleBuf();c.loop=true;c.start(t,Math.random()*2);const cg=G(P.crackle*0.25);c.connect(F('bandpass',2500,0.5)).connect(cg).connect(amp);ext.push(c)}
    amp.connect(outStage(P,M,out));adsr(amp.gain,t,P,'a',0.5*(0.4+v*0.6));return voice(amp,[o1,o2],P.aR,ext)}};

/* ───────── Reverse swell ───────── */
ENG.reverse={name:'Reverse Swell',kind:KIND.synth,family:'experimental',
  desc:'Notes swell in like a reversed recording and cut off at the peak. The swell finishes even if the key is released early.',
  params:[C('src','Source','Swell',[['pad','Pad'],['bell','Bell'],['keys','Keys']],'pad'),P('length','Length','Swell',0.1,6,1.2,{log:true,fmt:FMT.s}),P('bright','Brightness','Swell',0,1,0.6,{fmt:FMT.pct}),P('sync','Snap to beat','Swell',0,1,0,{fmt:v=>v>0.5?'On':'Off'}),...OUT_GROUP(0.75)],
  on(tr,n,v,t,out,P,M){const f=mtof(n),len=P.sync>0.5?Math.max(0.1,Math.round(P.length/divSec('1/4'))*divSec('1/4')):P.length,amp=G(0.0001),lp=F('lowpass',f*2,0.8),oscs=[];
    const add=(type,m,a,d=0)=>{const o=O(type,f*m,t,tr,d),g=G(a);o.connect(g).connect(lp);oscs.push(o)};
    if(P.src==='pad'){add('sawtooth',1,0.25,-8);add('sawtooth',1,0.25,8);add('triangle',0.5,0.3)}else if(P.src==='bell'){[[1,0.5],[2.76,0.25],[5.4,0.15],[8.93,0.08]].forEach(([m,a])=>add('sine',m,a))}else{add('triangle',1,0.5);add('sine',2,0.2);add('sine',3,0.08)}
    lp.frequency.setValueAtTime(f*1.5,t);lp.frequency.exponentialRampToValueAtTime(clamp(f*(2+P.bright*20),100,18000),t+len);lp.connect(amp).connect(outStage(P,M,out));
    amp.gain.setValueAtTime(0.0001,t);amp.gain.exponentialRampToValueAtTime(0.5*(0.4+v*0.6),t+len);amp.gain.setValueAtTime(0.5*(0.4+v*0.6),t+len);amp.gain.linearRampToValueAtTime(0,t+len+0.02);
    oscs.forEach(o=>o.stop(t+len+0.05));oscs[0].onended=()=>{try{amp.disconnect()}catch(e){}};return {oscs,off(){}}}};

/* ───────── Cinematic impact ───────── */
ENG.impact={name:'Cinematic Impact',kind:KIND.synth,family:'experimental',
  desc:'One-shot hits built from a sub boom with pitch drop, a filtered noise blast and a low metallic layer. The key you play tunes the boom.',
  params:[P('boom','Boom','Layers',0,1,0.9,{fmt:FMT.pct}),P('noise','Noise blast','Layers',0,1,0.6,{fmt:FMT.pct}),P('metal','Metal','Layers',0,1,0.35,{fmt:FMT.pct}),P('drop','Pitch drop','Shape',0,24,12,{fmt:FMT.st}),P('decay','Decay','Shape',0.3,8,2.5,{log:true,fmt:FMT.s}),...OUT_GROUP(0.85)],
  on(tr,n,v,t,out,P,M){const f=mtof(n),d=G(v),dst=outStage(P,M,out);d.connect(dst);
    if(P.boom>0.01){const o=O('sine',f*Math.pow(2,P.drop/12),t,Z),g=G(0);o.frequency.exponentialRampToValueAtTime(f,t+P.decay*0.15);g.gain.setValueAtTime(P.boom,t);g.gain.exponentialRampToValueAtTime(0.001,t+P.decay);o.connect(shaper(0.25)).connect(g).connect(d);o.stop(t+P.decay+0.1)}
    if(P.noise>0.01){const ns=N(t,P.decay),lp=F('lowpass',9000,0.8),g=G(0);lp.frequency.setValueAtTime(9000,t);lp.frequency.exponentialRampToValueAtTime(150,t+P.decay*0.6);hitEnv(g,t,P.noise*0.7,P.decay*0.7);ns.connect(lp).connect(g).connect(d)}
    if(P.metal>0.01)metal(t,d,P.decay,P.metal*0.5,f/100,2500,300);
    if(!OFF)setTimeout(()=>{try{d.disconnect()}catch(e){}},(t-ctx.currentTime+P.decay+0.5)*1000);return {oscs:[],off(){}}}};

/* ───────── Glitch ───────── */
const GLITCH_DIV=['1/8','1/16','1/16t','1/32'];
ENG.glitch={name:'Glitch Stutter',kind:KIND.synth,family:'experimental',
  desc:'Tempo-locked gating with stepped random pitch jumps and crushing. The randomness is a fresh eight-step sequence for every note.',
  params:[C('wave','Source','Source',[['square','Square'],['sawtooth','Saw'],['fm','FM']],'square'),C('rate','Gate rate','Gate',GLITCH_DIV.map(d=>[d,d.toUpperCase().replace('T',' T')]),'1/16'),P('duty','Gate length','Gate',0.1,0.9,0.5,{fmt:FMT.pct}),
    P('chaos','Pitch chaos','Chaos',0,24,7,{fmt:FMT.st}),P('crush','Crush','Chaos',0,1,0.4,{fmt:FMT.pct}),P('cutoff','Cutoff','Chaos',200,18000,6000,{log:true,fmt:FMT.hz,live:1}),...OUT_GROUP(0.6)],
  on(tr,n,v,t,out,P,M){const f=mtof(n),oscs=[],ext=[],src=G(0.4);
    if(P.wave==='fm'){const c=O('sine',f,t,tr),m=O('sine',f*3.5,t,tr),mg=G(f*3);m.connect(mg).connect(c.frequency);c.connect(src);oscs.push(c,m)}else{const o=O(P.wave,f,t,tr);o.connect(src);oscs.push(o)}
    const hz=divHz(P.rate),gate=O(pulse(Math.round(P.duty*8)/8||0.125),hz,t,Z),gg=G(0.5),vca=G(0.5);gate.connect(gg).connect(vca.gain);ext.push(gate);
    if(P.chaos>0.1){const re=new Float32Array(32),im=new Float32Array(32),steps=Array.from({length:8},()=>Math.random()*2-1);
      for(let k=1;k<32;k++){let a=0,b=0;for(let s=0;s<8;s++){const p0=2*Math.PI*k*s/8,p1=2*Math.PI*k*(s+1)/8;a+=steps[s]*(Math.sin(p1)-Math.sin(p0))/(Math.PI*k);b+=steps[s]*(Math.cos(p0)-Math.cos(p1))/(Math.PI*k)}re[k]=a;im[k]=b}
      const sh=ctx.createOscillator();sh.setPeriodicWave(ctx.createPeriodicWave(re,im,{disableNormalization:true}));sh.frequency.value=hz/8;sh.start(t);const sg=G(P.chaos*100);sh.connect(sg);oscs.forEach(o=>sg.connect(o.detune));ext.push(sh)}
    const lp=F('lowpass',P.cutoff,1);M.bind('cutoff',lp.detune,Math.log2(18000/200)*1200);src.connect(vca);(P.crush>0.02?vca.connect(shaper(P.crush,'hard')):vca).connect(lp);
    const amp=G(0);lp.connect(amp).connect(outStage(P,M,out));env(amp.gain,t,0.003,0.6*(0.4+v*0.6),0,1);return voice(amp,oscs,0.03,ext)}};

/* ───────── Organic modal ───────── */
ENG.organic={name:'Organic Modal',kind:KIND.phys,family:'experimental',
  desc:'Modal synthesis: an exciter rings banks of tuned resonant filters. Wood behaves like a marimba bar, Bowl like a singing bowl, Drop like water and Breeze like wind through reeds.',
  params:[C('mode','Material','Body',[['wood','Wood'],['bowl','Bowl'],['drop','Drop'],['breeze','Breeze']],'wood'),P('decay','Decay','Body',0.05,6,0.8,{log:true,fmt:FMT.s}),P('bright','Brightness','Body',0,1,0.5,{fmt:FMT.pct}),
    P('motion','Motion','Body',0,1,0.3,{fmt:FMT.pct}),...OUT_GROUP(0.8)],
  on(tr,n,v,t,out,P,M){const f=mtof(n),amp=G(1),dst=outStage(P,M,out);amp.connect(dst);const ext=[],oscs=[];
    if(P.mode==='drop'){const o=O('sine',f*0.6,t,tr),g=G(0);o.frequency.exponentialRampToValueAtTime(f*1.6,t+0.04+P.motion*0.05);hitEnv(g,t,0.6*v,0.08+P.decay*0.1);o.connect(g).connect(amp);o.stop(t+0.6);return {oscs:[o],off(){}}}
    const modes=P.mode==='wood'?[[1,1],[3.93,0.4],[9.2,0.15],[15.8,0.06]]:P.mode==='bowl'?[[1,1],[2.71,0.6],[5.15,0.35],[8.2,0.15]]:[[1,1],[2,0.5],[3,0.3],[4,0.15]];
    const exc=G(1);if(P.mode==='breeze'){const ns=N(t),lg=G(0);ns.connect(lg).connect(exc);env(lg.gain,t,0.3,0.25*v,0.5,0.8);ext.push(ns);
      if(P.motion>0.01){const l=O('sine',0.3+P.motion,t,Z),lgg=G(P.motion*0.15);l.connect(lgg).connect(lg.gain);ext.push(l)}}
    else{const ns=N(t,0.01),eg=G(0);ns.connect(F('lowpass',1000+P.bright*12000,0.5)).connect(eg).connect(exc);hitEnv(eg,t,v,0.006);
      if(P.mode==='bowl'&&P.motion>0.01){const ns2=N(t),bg=G(P.motion*0.02);ns2.connect(bg).connect(exc);ext.push(ns2)}}
    modes.forEach(([m,a])=>{const fm=f*m;if(fm>18000)return;const q=clamp(Math.PI*fm*(P.mode==='breeze'?0.05+P.decay*0.02:P.decay/(1+m*0.15)),2,800),b=F('bandpass',fm,q),g=G(a*Math.sqrt(q)*0.35);exc.connect(b).connect(g).connect(amp)});
    if(P.mode==='breeze')return voice(amp,ext,0.8);
    if(!OFF)setTimeout(()=>{try{amp.disconnect()}catch(e){}},(t-ctx.currentTime+P.decay*2+1)*1000);return {oscs,off(){}}}};

/* ───────── Fusion Lab (layer rack) ───────── */
const LAYERS=4;
const layerParams=i=>[B('L'+i+'on','Layer '+(i+1)+' on','Layer '+(i+1),i<2),C('L'+i+'src','Layer '+(i+1)+' source','Layer '+(i+1),[['none','None']],'none'),
  P('L'+i+'level','Layer '+(i+1)+' level','Layer '+(i+1),0,1,0.7,{fmt:FMT.pct,live:1}),P('L'+i+'pan','Layer '+(i+1)+' pan','Layer '+(i+1),-1,1,0,{fmt:FMT.pan,live:1}),
  P('L'+i+'tune','Layer '+(i+1)+' transpose','Layer '+(i+1),-24,24,0,{int:true,fmt:FMT.st}),P('L'+i+'fine','Layer '+(i+1)+' fine','Layer '+(i+1),-50,50,0,{fmt:FMT.ct}),
  P('L'+i+'cut','Layer '+(i+1)+' filter','Layer '+(i+1),60,20000,20000,{log:true,fmt:FMT.hz,live:1}),P('L'+i+'atk','Layer '+(i+1)+' fade-in','Layer '+(i+1),0,4,0,{fmt:FMT.s}),
  P('L'+i+'drive','Layer '+(i+1)+' drive','Layer '+(i+1),0,1,0,{fmt:FMT.pct}),
  P('L'+i+'klo','Layer '+(i+1)+' lowest key','Layer '+(i+1),0,127,0,{int:true,fmt:noteName}),P('L'+i+'khi','Layer '+(i+1)+' highest key','Layer '+(i+1),0,127,127,{int:true,fmt:noteName}),
  P('L'+i+'vlo','Layer '+(i+1)+' lowest velocity','Layer '+(i+1),0,127,0,{int:true,fmt:FMT.int}),P('L'+i+'vhi','Layer '+(i+1)+' highest velocity','Layer '+(i+1),0,127,127,{int:true,fmt:FMT.int}),
  P('L'+i+'xf','Layer '+(i+1)+' velocity crossfade','Layer '+(i+1),0,64,0,{int:true,fmt:FMT.int}),B('L'+i+'solo','Layer '+(i+1)+' solo','Layer '+(i+1),false)];
ENG.fusion={name:'Fusion Lab',kind:KIND.hybrid,family:'fusion',
  desc:'Layer up to four sources from the library. Each layer has its own level, pan, filter, transpose, fade-in, drive, key range and velocity range with crossfades, so you can split, stack and velocity-switch sounds.',
  params:[...Array.from({length:LAYERS},(_,i)=>layerParams(i)).flat(),...OUT_GROUP(0.8)],
  on(tr,n,v,t,out,P,M){const hs=[],dst=outStage(P,M,out),vel=Math.round(v*127),anySolo=Array.from({length:LAYERS},(_,i)=>P['L'+i+'on']&&P['L'+i+'solo']).some(Boolean);
    for(let i=0;i<LAYERS;i++){const L=k=>P['L'+i+k];if(!L('on')||(anySolo&&!L('solo'))||L('src')==='none')continue;if(n<L('klo')||n>L('khi'))continue;
      const xf=L('xf');let w=1;if(vel<L('vlo')-xf||vel>L('vhi')+xf)continue;if(xf>0){if(vel<L('vlo'))w=1-(L('vlo')-vel)/xf;else if(vel>L('vhi'))w=1-(vel-L('vhi'))/xf}
      const pre=FACTORY_BY_ID[L('src')]||USER_PRESET_BY_ID()[L('src')];if(!pre){if(!tr._missingWarned){tr._missingWarned=1;toast('A Fusion layer uses a sound that is not installed: '+L('src')+'. That layer is silent.')}continue}
      const E=ENG[pre.engine];if(!E||E.setup||E.mono||pre.engine==='fusion')continue;
      const lin=G(1),lp=F('lowpass',L('cut'),0.7),lg=G(L('level')*w),pn=ctx.createStereoPanner(),fade=G(1);pn.pan.value=L('pan');
      let x=lin;if(L('drive')>0.02){const s=shaper(L('drive')*0.8);x.connect(s);x=s}x.connect(lp).connect(lg).connect(pn).connect(fade).connect(dst);
      if(L('atk')>0.005){fade.gain.setValueAtTime(0,t);fade.gain.linearRampToValueAtTime(1,t+L('atk'))}
      M.bind('L'+i+'level',lg.gain,1);M.bind('L'+i+'pan',pn.pan,2);M.bind('L'+i+'cut',lp.detune,Math.log2(20000/60)*1200);
      const LP=Object.assign(defaultsOf(E.params),pre.params,(tr.layerOver&&tr.layerOver[i])||{});
      const h=E.on(tr,n+L('tune'),v,t,lin,LP,{bind(){},prev:M.prev,legato:false});if(h){h.fine=L('fine');(h.oscs||[]).forEach(o=>{o._d+=L('fine');o.detune.value+=L('fine')});hs.push(h)}}
    return {oscs:hs.flatMap(h=>h.oscs||[]),off(t2){hs.forEach(h=>h.off(t2))}}}};
