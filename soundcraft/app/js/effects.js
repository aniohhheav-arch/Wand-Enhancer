'use strict';
/* Effect rack. Each effect describes its parameters with the shared registry and builds real audio
   nodes. `aps` maps live-modulatable parameters to the AudioParam that carries them and how many
   AudioParam units one full-range sweep (normalized 0..1) represents. */
const FX_LIVE=new Set();
function mixPair(input,wetNode,output,mix){const dry=G(1-mix),wet=G(mix);input.connect(dry).connect(output);wetNode.connect(wet).connect(output);return {dry,wet,set(m){const t=ctx.currentTime;dry.gain.setTargetAtTime(1-m,t,0.02);wet.gain.setTargetAtTime(m,t,0.02)}}}
const at=(ap,v)=>ap.setTargetAtTime(v,ctx.currentTime,0.015);
const centsSpan=(p)=>Math.log2(p.max/p.min)*1200;
function lfoNode(shape,rate){const o=ctx.createOscillator();o.type=shape;o.frequency.value=rate;o.start();return o}
function lfoRate(P_){return P_.sync&&P_.sync!=='free'?divHz(P_.sync):P_.rate}
const FX={
  eq:{name:'Parametric EQ',cat:'EQ & filter',desc:'High-pass, low shelf, a parametric mid band, high shelf and low-pass.',
    params:[P('hpf','Low cut','Cut',20,2000,20,{log:true,fmt:FMT.hz,live:1}),P('lowF','Low freq','Low',30,1000,120,{log:true,fmt:FMT.hz}),P('lowG','Low gain','Low',-18,18,0,{fmt:FMT.db,live:1}),
      P('midF','Mid freq','Mid',100,12000,1000,{log:true,fmt:FMT.hz,live:1}),P('midG','Mid gain','Mid',-18,18,0,{fmt:FMT.db,live:1}),P('midQ','Mid Q','Mid',0.2,12,1,{log:true,fmt:FMT.dec,live:1}),
      P('highF','High freq','High',1000,18000,8000,{log:true,fmt:FMT.hz}),P('highG','High gain','High',-18,18,0,{fmt:FMT.db,live:1}),P('lpf','High cut','Cut',1000,20000,20000,{log:true,fmt:FMT.hz,live:1})],
    build(p){const hp=F('highpass',p.hpf,0.7),lo=F('lowshelf',p.lowF),mid=F('peaking',p.midF,p.midQ),hi=F('highshelf',p.highF),lp=F('lowpass',p.lpf,0.7);hp.connect(lo).connect(mid).connect(hi).connect(lp);
      const d=FX.eq.params,pp=k=>d.find(x=>x.k===k);
      return {input:hp,output:lp,aps:{hpf:{ap:hp.detune,perNorm:centsSpan(pp('hpf'))},lowG:{ap:lo.gain,perNorm:36},midF:{ap:mid.detune,perNorm:centsSpan(pp('midF'))},midG:{ap:mid.gain,perNorm:36},midQ:{ap:mid.Q,perNorm:11.8},highG:{ap:hi.gain,perNorm:36},lpf:{ap:lp.detune,perNorm:centsSpan(pp('lpf'))}},
        response:[hp,lo,mid,hi,lp],
        set(p){at(hp.frequency,p.hpf);at(lo.frequency,p.lowF);at(lo.gain,p.lowG);at(mid.frequency,p.midF);at(mid.gain,p.midG);at(mid.Q,p.midQ);at(hi.frequency,p.highF);at(hi.gain,p.highG);at(lp.frequency,p.lpf)}}}},
  comp:{name:'Compressor',cat:'Dynamics',desc:'Feed-forward compressor with knee, attack, release and make-up gain. Shows gain reduction.',
    params:[P('threshold','Threshold','Comp',-60,0,-18,{fmt:FMT.db,live:1}),P('ratio','Ratio','Comp',1,20,4,{fmt:v=>v.toFixed(1)+':1',live:1}),P('knee','Knee','Comp',0,40,6,{fmt:FMT.db}),
      P('attack','Attack','Time',0.0005,0.2,0.01,{log:true,fmt:FMT.s}),P('release','Release','Time',0.01,1,0.2,{log:true,fmt:FMT.s}),P('makeup','Make-up','Out',0,24,3,{fmt:FMT.db})],
    build(p){const c=ctx.createDynamicsCompressor(),out=G(1);c.connect(out);
      return {input:c,output:out,meter:()=>c.reduction,aps:{threshold:{ap:c.threshold,perNorm:60},ratio:{ap:c.ratio,perNorm:19}},
        set(p){at(c.threshold,p.threshold);at(c.ratio,p.ratio);at(c.knee,p.knee);at(c.attack,p.attack);at(c.release,p.release);at(out.gain,dbToGain(p.makeup))}}}},
  limiter:{name:'Limiter',cat:'Dynamics',desc:'Fast high-ratio compressor for catching peaks. It is not a true-peak limiter.',
    params:[P('drive','Input gain','Limit',0,24,0,{fmt:FMT.db}),P('ceiling','Ceiling','Limit',-12,0,-1,{fmt:FMT.db}),P('release','Release','Limit',0.01,0.5,0.06,{log:true,fmt:FMT.s})],
    build(p){const inG=G(1),c=ctx.createDynamicsCompressor();c.knee.value=0;c.ratio.value=20;c.attack.value=0.001;inG.connect(c);
      return {input:inG,output:c,meter:()=>c.reduction,aps:{},set(p){at(inG.gain,dbToGain(p.drive));at(c.threshold,p.ceiling);at(c.release,p.release)}}}},
  gate:{name:'Gate',cat:'Dynamics',desc:'Noise gate with threshold, range, attack and release.',worklet:'sc-gate',
    params:[P('threshold','Threshold','Gate',-80,0,-40,{fmt:FMT.db,live:1}),P('range','Range','Gate',-80,0,-60,{fmt:FMT.db,live:1}),P('attack','Attack','Gate',0.1,50,2,{log:true,fmt:FMT.ms}),P('release','Release','Gate',5,1000,120,{log:true,fmt:FMT.ms})],
    build(p){return workletFx('sc-gate',p,{threshold:80,range:80},(n,p)=>{['threshold','range','attack','release'].forEach(k=>at(n.parameters.get(k),p[k]))})}},
  drive:{name:'Saturation',cat:'Distortion',desc:'Soft saturation, hard clipping or wave folding with tone and mix.',
    params:[C('mode','Mode','Drive',[['soft','Soft'],['hard','Hard clip'],['fold','Fold']],'soft'),P('amount','Amount','Drive',0,1,0.35,{fmt:FMT.pct}),P('tone','Tone','Drive',500,18000,9000,{log:true,fmt:FMT.hz,live:1}),
      P('mix','Mix','Out',0,1,1,{fmt:FMT.pct,live:1}),P('out','Output','Out',-24,6,-3,{fmt:FMT.db})],
    build(p){const input=G(1),output=G(1),sh=ctx.createWaveShaper(),tone=F('lowpass',p.tone,0.7),o=G(1);sh.oversample='4x';input.connect(sh).connect(tone).connect(o);const mp=mixPair(input,o,output,p.mix);
      return {input,output,aps:{tone:{ap:tone.detune,perNorm:centsSpan(FX.drive.params[2])},mix:{ap:mp.wet.gain,perNorm:1}},
        set(p){sh.curve=curve(p.mode,p.amount);at(tone.frequency,p.tone);mp.set(p.mix);at(o.gain,dbToGain(p.out))}}}},
  reverb:{name:'Reverb',cat:'Space',desc:'Algorithmic convolution reverb. Size and damping rebuild the room.',
    params:[P('size','Size','Room',0.2,10,2.6,{log:true,fmt:FMT.s}),P('damp','Damping','Room',0,1,0.5,{fmt:FMT.pct}),P('predelay','Pre-delay','Room',0,200,15,{fmt:FMT.ms,live:1}),
      P('lowcut','Low cut','Tone',20,1000,120,{log:true,fmt:FMT.hz,live:1}),P('mix','Mix','Out',0,1,0.3,{fmt:FMT.pct,live:1})],
    xy:['size','mix'],toy:'Reverb room',
    build(p){const input=G(1),output=G(1),pre=ctx.createDelay(0.25),hp=F('highpass',p.lowcut,0.7),conv=ctx.createConvolver();input.connect(pre).connect(hp).connect(conv);const mp=mixPair(input,conv,output,p.mix);let key='',tmr=0;
      const c=ctx;const rebuild=(s,d)=>{const k=s.toFixed(2)+d.toFixed(2);if(k===key)return;key=k;conv.buffer=makeIR(s,d,c)};rebuild(p.size,p.damp);
      return {input,output,aps:{predelay:{ap:pre.delayTime,perNorm:0.2},lowcut:{ap:hp.detune,perNorm:centsSpan(FX.reverb.params[3])},mix:{ap:mp.wet.gain,perNorm:1}},
        set(p){at(pre.delayTime,p.predelay/1000);at(hp.frequency,p.lowcut);mp.set(p.mix);if(OFF)rebuild(p.size,p.damp);else{clearTimeout(tmr);tmr=setTimeout(()=>rebuild(p.size,p.damp),120)}}}}},
  delay:{name:'Delay',cat:'Space',desc:'Tempo-synced or free delay with feedback tone and optional ping-pong.',
    params:[C('sync','Sync','Time',SYNC_DIVS.slice(),'1/8d'),P('time','Free time','Time',1,2000,350,{log:true,fmt:FMT.ms,live:1}),P('feedback','Feedback','Echo',0,0.95,0.38,{fmt:FMT.pct,live:1}),
      P('tone','Tone','Echo',500,18000,4000,{log:true,fmt:FMT.hz,live:1}),B('ping','Ping-pong','Echo',false),P('mix','Mix','Out',0,1,0.3,{fmt:FMT.pct,live:1})],
    xy:['feedback','mix'],toy:'Delay tunnel',
    build(p){const input=G(1),output=G(1),dl=ctx.createDelay(4),dr=ctx.createDelay(4),fb=G(p.feedback),tone=F('lowpass',p.tone,0.5),mL=ctx.createChannelMerger(2),wet=G(1),split=ctx.createChannelSplitter(2);
      // ping-pong: L -> R -> L cross feedback; straight: both lines in parallel
      input.connect(split);const sum=G(0.5);split.connect(sum,0);split.connect(sum,1);sum.connect(dl);dl.connect(tone).connect(fb);
      const cross=G(0),direct=G(1);fb.connect(direct).connect(dl);fb.connect(cross).connect(dr);dr.connect(dl);
      dl.connect(mL,0,0);const rTap=G(1);dl.connect(rTap);rTap.connect(mL,0,1);dr.connect(mL,0,1);mL.connect(wet);const mp=mixPair(input,wet,output,p.mix);
      let last=p;const apply=p=>{last=p;const tm=p.sync==='free'?p.time/1000:divSec(p.sync);at(dl.delayTime,Math.min(3.9,tm));at(dr.delayTime,Math.min(3.9,tm));at(fb.gain,p.feedback);at(tone.frequency,p.tone);mp.set(p.mix);
        at(cross.gain,p.ping?1:0);at(direct.gain,p.ping?0:1);at(rTap.gain,p.ping?0:1)};
      const inst={input,output,tempo(){apply(last)},aps:{time:{ap:dl.delayTime,perNorm:1.999},feedback:{ap:fb.gain,perNorm:0.95},tone:{ap:tone.detune,perNorm:centsSpan(FX.delay.params[3])},mix:{ap:mp.wet.gain,perNorm:1}},set:apply};FX_LIVE.add(inst);return inst}},
  chorus:{name:'Chorus',cat:'Modulation',desc:'Two modulated delay lines in stereo quadrature.',
    params:[P('rate','Rate','LFO',0.05,8,0.8,{log:true,fmt:v=>v.toFixed(2)+'Hz',live:1}),P('depth','Depth','LFO',0,10,3,{fmt:FMT.ms,live:1}),P('delay','Delay','Line',5,30,14,{fmt:FMT.ms}),P('mix','Mix','Out',0,1,0.45,{fmt:FMT.pct,live:1})],
    build(p){const input=G(1),output=G(1),m=ctx.createChannelMerger(2),w=G(1),l=lfoNode('sine',p.rate),l2=lfoNode('sine',p.rate),dg=G(p.depth/1000),dg2=G(-p.depth/1000),A=ctx.createDelay(0.1),B_=ctx.createDelay(0.1);
      l.connect(dg).connect(A.delayTime);l2.connect(dg2).connect(B_.delayTime);input.connect(A);input.connect(B_);A.connect(m,0,0);B_.connect(m,0,1);m.connect(w);const mp=mixPair(input,w,output,p.mix);
      return {input,output,dispose(){l.stop();l2.stop()},aps:{rate:{ap:l.frequency,perNorm:8},depth:{ap:dg.gain,perNorm:0.01},mix:{ap:mp.wet.gain,perNorm:1}},
        set(p){at(l.frequency,p.rate);at(l2.frequency,p.rate*1.07);at(dg.gain,p.depth/1000);at(dg2.gain,-p.depth/1000);at(A.delayTime,p.delay/1000);at(B_.delayTime,p.delay/1000);mp.set(p.mix)}}}},
  flanger:{name:'Flanger',cat:'Modulation',desc:'Short modulated delay with positive or negative feedback.',
    params:[P('rate','Rate','LFO',0.02,5,0.25,{log:true,fmt:v=>v.toFixed(2)+'Hz',live:1}),P('depth','Depth','LFO',0,5,1.8,{fmt:FMT.ms,live:1}),P('base','Base delay','Line',0.5,10,2.5,{fmt:FMT.ms}),P('feedback','Feedback','Line',-0.95,0.95,0.6,{fmt:FMT.pct,live:1}),P('mix','Mix','Out',0,1,0.5,{fmt:FMT.pct,live:1})],
    build(p){const input=G(1),output=G(1),d=ctx.createDelay(0.05),fb=G(p.feedback),l=lfoNode('triangle',p.rate),lg=G(p.depth/1000);l.connect(lg).connect(d.delayTime);input.connect(d);d.connect(fb).connect(d);const mp=mixPair(input,d,output,p.mix);
      return {input,output,dispose(){l.stop()},aps:{rate:{ap:l.frequency,perNorm:5},depth:{ap:lg.gain,perNorm:0.005},feedback:{ap:fb.gain,perNorm:1.9},mix:{ap:mp.wet.gain,perNorm:1}},
        set(p){at(l.frequency,p.rate);at(lg.gain,p.depth/1000);at(d.delayTime,(p.base+p.depth)/1000);at(fb.gain,p.feedback);mp.set(p.mix)}}}},
  phaser:{name:'Phaser',cat:'Modulation',desc:'Chain of swept all-pass filters with feedback.',
    params:[P('rate','Rate','LFO',0.02,8,0.4,{log:true,fmt:v=>v.toFixed(2)+'Hz',live:1}),P('depth','Depth','LFO',0,1,0.6,{fmt:FMT.pct,live:1}),P('base','Centre','Filter',100,4000,700,{log:true,fmt:FMT.hz}),
      C('stages','Stages','Filter',[[2,'2'],[4,'4'],[6,'6'],[8,'8']],6),P('feedback','Feedback','Filter',0,0.9,0.4,{fmt:FMT.pct,live:1}),P('mix','Mix','Out',0,1,0.5,{fmt:FMT.pct,live:1})],
    build(p){const input=G(1),output=G(1),aps=[],l=lfoNode('sine',p.rate),lg=G(p.depth*2400),fb=G(p.feedback),head=G(1);l.connect(lg);
      for(let i=0;i<8;i++){const a=F('allpass',p.base,0.6);lg.connect(a.detune);aps.push(a)}let n=head;aps.forEach(a=>{n.connect(a);n=a});const tail=G(1);
      input.connect(head);let cur=null;const wire=st=>{try{aps.forEach(a=>a.disconnect())}catch(e){}aps.forEach(a=>lg.connect(a.detune));for(let i=0;i<st-1;i++)aps[i].connect(aps[i+1]);aps[st-1].connect(tail);aps[st-1].connect(fb);cur=st};
      fb.connect(head);const mp=mixPair(input,tail,output,p.mix);wire(p.stages);
      return {input,output,dispose(){l.stop()},aps:{rate:{ap:l.frequency,perNorm:8},depth:{ap:lg.gain,perNorm:2400},feedback:{ap:fb.gain,perNorm:0.9},mix:{ap:mp.wet.gain,perNorm:1}},
        set(p){at(l.frequency,p.rate);at(lg.gain,p.depth*2400);aps.forEach(a=>at(a.frequency,p.base));at(fb.gain,p.feedback);mp.set(p.mix);if(p.stages!==cur)wire(p.stages)}}}},
  tremolo:{name:'Tremolo',cat:'Modulation',desc:'Volume modulation, free or tempo-synced.',
    params:[C('sync','Sync','LFO',SYNC_DIVS.slice(),'1/8'),P('rate','Free rate','LFO',0.1,20,5,{log:true,fmt:v=>v.toFixed(2)+'Hz',live:1}),C('shape','Shape','LFO',[['sine','Sine'],['triangle','Triangle'],['square','Square']],'sine'),P('depth','Depth','LFO',0,1,0.5,{fmt:FMT.pct,live:1})],
    build(p){const input=G(1),g=G(1),l=lfoNode(p.shape,lfoRate(p)),lg=G(p.depth/2);l.connect(lg).connect(g.gain);input.connect(g);let last=p;
      const apply=p=>{last=p;l.type=p.shape;at(l.frequency,lfoRate(p));at(lg.gain,p.depth/2);at(g.gain,1-p.depth/2)};const inst={input,output:g,dispose(){l.stop()},tempo(){apply(last)},aps:{rate:{ap:l.frequency,perNorm:20},depth:{ap:lg.gain,perNorm:0.5}},set:apply};FX_LIVE.add(inst);return inst}},
  autopan:{name:'Auto-pan',cat:'Modulation',desc:'Moves the signal across the stereo field.',
    params:[C('sync','Sync','LFO',SYNC_DIVS.slice(),'1/4'),P('rate','Free rate','LFO',0.05,10,1,{log:true,fmt:v=>v.toFixed(2)+'Hz',live:1}),C('shape','Shape','LFO',[['sine','Sine'],['triangle','Triangle'],['square','Square']],'sine'),P('depth','Depth','LFO',0,1,0.7,{fmt:FMT.pct,live:1})],
    build(p){const pan=ctx.createStereoPanner(),l=lfoNode(p.shape,lfoRate(p)),lg=G(p.depth);l.connect(lg).connect(pan.pan);let last=p;
      const apply=p=>{last=p;l.type=p.shape;at(l.frequency,lfoRate(p));at(lg.gain,p.depth)};const inst={input:pan,output:pan,dispose(){l.stop()},tempo(){apply(last)},aps:{rate:{ap:l.frequency,perNorm:10},depth:{ap:lg.gain,perNorm:1}},set:apply};FX_LIVE.add(inst);return inst}},
  crusher:{name:'Bitcrusher',cat:'Distortion',desc:'Bit-depth and sample-rate reduction.',worklet:'sc-crush',
    params:[P('bits','Bits','Crush',1,16,8,{step:1,fmt:FMT.int,live:1}),P('down','Downsample','Crush',1,50,4,{step:1,fmt:v=>Math.round(v)+'×',live:1}),P('mix','Mix','Out',0,1,1,{fmt:FMT.pct,live:1})],
    build(p){return workletFx('sc-crush',p,{bits:15,down:49,mix:1},(n,p)=>{['bits','down','mix'].forEach(k=>at(n.parameters.get(k),p[k]))})}},
  filter:{name:'Filter',cat:'EQ & filter',desc:'Multimode filter with 12 or 24 dB slope and its own synced LFO.',
    params:[C('type','Type','Filter',[['lowpass','Low-pass'],['highpass','High-pass'],['bandpass','Band-pass'],['notch','Notch']],'lowpass'),P('cutoff','Cutoff','Filter',20,20000,2000,{log:true,fmt:FMT.hz,live:1}),
      P('q','Resonance','Filter',0.1,25,1,{log:true,fmt:FMT.dec,live:1}),C('slope','Slope','Filter',[[12,'12 dB'],[24,'24 dB']],12),C('sync','LFO sync','LFO',SYNC_DIVS.slice(),'1/4'),P('rate','LFO rate','LFO',0.05,20,1,{log:true,fmt:v=>v.toFixed(2)+'Hz'}),P('depth','LFO depth','LFO',0,4,0,{fmt:v=>v.toFixed(1)+' oct',live:1})],
    xy:['cutoff','q'],toy:'Filter garden',
    build(p){const input=G(1),f1=F(p.type,p.cutoff,p.q),f2=F(p.type,p.cutoff,p.q),out=G(1),l=lfoNode('sine',lfoRate(p)),lg=G(p.depth*1200),q2=G(1);l.connect(lg);lg.connect(f1.detune);lg.connect(f2.detune);
      input.connect(f1);let slope=0,last=p;const wire=s=>{try{f1.disconnect()}catch(e){}if(s===24){f1.connect(f2);f2.connect(out)}else f1.connect(out);slope=s};wire(p.slope);
      const apply=p=>{last=p;f1.type=f2.type=p.type;at(f1.frequency,p.cutoff);at(f2.frequency,p.cutoff);at(f1.Q,p.q);at(f2.Q,p.slope===24?Math.max(0.5,p.q*0.7):p.q);at(l.frequency,lfoRate(p));at(lg.gain,p.depth*1200);if(p.slope!==slope)wire(p.slope)};
      const inst={input,output:out,dispose(){l.stop()},tempo(){apply(last)},response:[f1],aps:{cutoff:{ap:f1.detune,perNorm:centsSpan(FX.filter.params[1])},q:{ap:f1.Q,perNorm:24.9},depth:{ap:lg.gain,perNorm:4800}},set:apply};
      // keep the second stage following the first for modulation
      const d2=G(1);inst.aps.cutoff.extra=[f2.detune];FX_LIVE.add(inst);return inst}},
  width:{name:'Stereo width',cat:'Utility',desc:'Mid/side width from mono (0%) to extra wide (200%).',
    params:[P('width','Width','Image',0,2,1.3,{fmt:FMT.pct}),P('out','Output','Image',-12,6,0,{fmt:FMT.db})],
    build(p){const input=G(1);input.channelCount=2;input.channelCountMode='explicit';input.channelInterpretation='speakers';const sp=ctx.createChannelSplitter(2),m=ctx.createChannelMerger(2),out=G(1);
      const ll=G(1),rr=G(1),lr=G(0),rl=G(0);input.connect(sp);sp.connect(ll,0);sp.connect(lr,0);sp.connect(rr,1);sp.connect(rl,1);ll.connect(m,0,0);rl.connect(m,0,0);rr.connect(m,0,1);lr.connect(m,0,1);m.connect(out);
      return {input,output:out,aps:{},set(p){const a=0.5+0.5*p.width,b=0.5-0.5*p.width;at(ll.gain,a);at(rr.gain,a);at(lr.gain,b);at(rl.gain,b);at(out.gain,dbToGain(p.out))}}}},
  transient:{name:'Transient shaper',cat:'Dynamics',desc:'Boosts or softens attacks and sustain independently of level.',worklet:'sc-transient',
    params:[P('attack','Attack','Shape',-1,1,0.3,{fmt:FMT.pct,live:1}),P('sustain','Sustain','Shape',-1,1,0,{fmt:FMT.pct,live:1}),P('out','Output','Out',-12,6,0,{fmt:FMT.db})],
    build(p){const inst=workletFx('sc-transient',p,{attack:2,sustain:2},(n,p)=>{at(n.parameters.get('attack'),p.attack);at(n.parameters.get('sustain'),p.sustain)});const o=G(1);inst.output.connect(o);const s0=inst.set;inst.output=o;inst.set=p=>{s0(p);at(o.gain,dbToGain(p.out))};return inst}},
  dream:{name:'Dream machine',cat:'Creative',desc:'Chorus into tempo delay into a long reverb. Amount and Motion drive several real parameters at once.',
    params:[P('amount','Amount','Dream',0,1,0.5,{fmt:FMT.pct}),P('motion','Motion','Dream',0,1,0.5,{fmt:FMT.pct}),P('tone','Tone','Dream',800,16000,5000,{log:true,fmt:FMT.hz}),P('mix','Mix','Out',0,1,0.5,{fmt:FMT.pct,live:1})],
    xy:['amount','motion'],toy:'Dream machine',
    build(p){const input=G(1),output=G(1),ch=FX.chorus.build(defaultsOf(FX.chorus.params)),dl=FX.delay.build(defaultsOf(FX.delay.params)),rv=FX.reverb.build(defaultsOf(FX.reverb.params)),tone=F('lowpass',p.tone,0.5);
      input.connect(ch.input);ch.output.connect(dl.input);dl.output.connect(rv.input);rv.output.connect(tone);const mp=mixPair(input,tone,output,p.mix);
      return {input,output,dispose(){ch.dispose();FX_LIVE.delete(dl)},aps:{mix:{ap:mp.wet.gain,perNorm:1}},
        set(p){const a=p.amount,m=p.motion;ch.set({rate:0.2+m*3,depth:1+m*7,delay:12,mix:0.2+m*0.6});dl.set({sync:'1/8d',time:350,feedback:0.2+a*0.6,tone:p.tone,ping:true,mix:0.15+a*0.5});
          rv.set({size:1.5+a*7,damp:0.6,predelay:20,lowcut:150,mix:0.3+a*0.6});at(tone.frequency,p.tone);mp.set(p.mix)}}}}
};
const FX_ORDER=['eq','filter','comp','limiter','gate','transient','drive','crusher','chorus','flanger','phaser','tremolo','autopan','width','delay','reverb','dream'];
function workletFx(name,p,spans,apply){
  const input=G(1),output=G(1);
  if(!workletOK()){input.connect(output);return {input,output,unavailable:true,aps:{},set(){}}}
  const n=new AudioWorkletNode(ctx,name,{numberOfInputs:1,numberOfOutputs:1,outputChannelCount:[2]});input.connect(n).connect(output);
  const aps={};Object.keys(spans).forEach(k=>aps[k]={ap:n.parameters.get(k),perNorm:spans[k]});
  return {input,output,aps,set(p){apply(n,p)},dispose(){try{n.disconnect()}catch(e){}}};
}
/* One effect slot: {id,type,on,params}. */
function newFx(type,params){const d=FX[type];return {id:uid('f'),type,on:true,params:Object.assign(defaultsOf(d.params),params||{})}}
function makeFx(f){const d=FX[f.type];const inst=d.build(f.params);inst.ctx=ctx;inst.def=d;inst.set(f.params);f._inst=inst;return inst}
function setFxParam(f,k,v){f.params[k]=v;if(f._inst&&f._inst.ctx===ctx)f._inst.set(f.params)}
function fxTempoChanged(){FX_LIVE.forEach(i=>{if(i.ctx===ctx&&i.tempo)i.tempo()})}
