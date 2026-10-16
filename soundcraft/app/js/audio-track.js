'use strict';
/* Mic tracks: record a microphone or audio interface into takes that play in the arrangement, the mixer and
   exports. A take is stored once on this device (IndexedDB, like imported samples); clips point into it with
   an offset, so splitting, moving and loop passes never copy audio. Recording over an earlier take replaces
   that part of the timeline, like tape. There is no time-stretch: changing the tempo moves clip starts but
   keeps the audio at its recorded speed. */
ENG.audio={name:'Mic / audio track',kind:'Audio',family:'audio',hidden:true,params:[],
  desc:'Records your microphone or audio interface. Takes are stored on this device and play back in the arrangement, mixer and exports.',
  on(){return null}};
const isAudio=tr=>!!tr&&tr.engine==='audio';
const secPerBar=()=>4*60/S.bpm;
function newAudioTrack(){const n=S.tracks.filter(isAudio).length+1,tr=newTrack({engine:'audio',params:{}},{name:'Mic '+n,color:'#79c3e0',rev:0.12,dly:0,vol:0.85});tr.arm=true;tr.monitor=!!PREF.monitorDefault;return tr}
function sanitizeAudioClip(c){return {id:c.id||uid('c'),rec:String(c.rec).slice(0,64),start:Math.max(0,+c.start||0),len:Math.max(1/64,+c.len||1),off:isFinite(+c.off)?+c.off:0,gain:clamp(c.gain==null?1:+c.gain,0,4),name:c.name?String(c.name).slice(0,40):undefined}}
function addMicTrack(){pushUndo();const tr=newAudioTrack();S.tracks.push(tr);if(ctx)applyMix();markDirty();selectTrack(S.tracks.length-1);renderArrange();
  openMic().then(ok=>{renderArrangeHeads();renderAudioDock();toast(ok?'Mic track ready and armed. Press Record (R) to record a take.':'Mic track added, but the input could not be opened: '+MIC.err)})}

/* ───────── playback ───────── */
const AUDIO_PLAY=new Set();
/* Called from stepEvents for every step: starts clips that begin in this step, or that are already running
   when playback starts (or the loop wraps) at startStep. endStep stops audio at the loop end or render end. */
function audioClipEvents(tr,k,T,sd,out,startStep,endStep){
  for(const c of tr.clips){if(!c.rec)continue;const cs=c.start*16,ce=(c.start+c.len)*16,lim=Math.min(ce,endStep==null?Infinity:endStep);let t,off,from;
    if(Math.floor(cs)===k&&cs>=startStep){t=T+(cs-k)*sd;off=c.off;from=cs}else if(k===startStep&&cs<k&&ce>k){t=T;off=c.off+(k-cs)*sd;from=k}else continue;
    const dur=(lim-from)*sd;if(dur>0.005)out.push({t,audio:true,tr,c,off,dur})}}
function playAudioClip(e){const b=SAMPLE_BUF.get(e.c.rec);if(!b||!ctx)return;buildTrack(e.tr);let t=e.t,off=e.off,dur=e.dur;
  if(off<0){t-=off;dur+=off;off=0}if(off>=b.duration||dur<=0.005)return;dur=Math.min(dur,b.duration-off);
  const s=ctx.createBufferSource(),g=G(0),v=e.c.gain==null?1:e.c.gain;s.buffer=b;
  g.gain.setValueAtTime(0,t);g.gain.linearRampToValueAtTime(v,t+0.004);g.gain.setValueAtTime(v,t+Math.max(0.004,dur-0.006));g.gain.linearRampToValueAtTime(0,t+dur);
  s.connect(g).connect(trackOut(e.tr));s.start(t,off);s.stop(t+dur+0.01);
  if(!OFF){const h={s,g};AUDIO_PLAY.add(h);s.onended=()=>{AUDIO_PLAY.delete(h);try{g.disconnect()}catch(x){}}}}
function stopAudioClips(){if(!ctx)return;const now=ctx.currentTime;AUDIO_PLAY.forEach(({s,g})=>{try{g.gain.cancelScheduledValues(now);g.gain.setTargetAtTime(0,now,0.004);s.stop(now+0.03)}catch(e){}});AUDIO_PLAY.clear()}

/* ───────── input ───────── */
const MIC={stream:null,src:null,an:null,ctx:null,label:'',err:'',inLat:0};
async function openMic(){
  if(MIC.stream&&MIC.ctx===ctx&&MIC.stream.active)return true;if(!ensureAudio()){MIC.err='the audio engine could not start';return false}closeMic();
  if(!navigator.mediaDevices||!navigator.mediaDevices.getUserMedia){MIC.err='this system does not allow audio input here';return false}
  const want=id=>({audio:Object.assign({echoCancellation:false,noiseSuppression:false,autoGainControl:false,channelCount:PREF.recCh===2?2:1},id?{deviceId:{exact:id}}:{}),video:false});
  try{try{MIC.stream=await navigator.mediaDevices.getUserMedia(want(PREF.inputId))}catch(e){if(PREF.inputId&&(e.name==='OverconstrainedError'||e.name==='NotFoundError')){toast('The chosen input is not connected, so the default input is used.');MIC.stream=await navigator.mediaDevices.getUserMedia(want(''))}else throw e}}
  catch(e){MIC.stream=null;errLog.push('mic: '+e.name+' '+e.message);
    MIC.err=e.name==='NotAllowedError'||e.name==='SecurityError'?'microphone access was refused. Allow it for Soundcraft in Windows Settings → Privacy & security → Microphone (or your browser\'s site settings), then arm the track again':
      e.name==='NotFoundError'?'no microphone or audio input was found. Plug one in and arm the track again':e.name==='NotReadableError'?'the input is in use by another app or its driver failed. Close other apps that use it and try again':e.name+': '+e.message;return false}
  MIC.ctx=ctx;MIC.src=ctx.createMediaStreamSource(MIC.stream);MIC.an=ctx.createAnalyser();MIC.an.fftSize=1024;MIC.src.connect(MIC.an);
  const tk=MIC.stream.getAudioTracks()[0],st=tk&&tk.getSettings?tk.getSettings():{};MIC.label=(tk&&tk.label)||'Microphone';MIC.inLat=+st.latency||0;MIC.ch=+st.channelCount||1;
  if(tk)tk.onended=()=>{toast('The audio input was disconnected.');closeMic();renderArrangeHeads();renderAudioDock()};MIC.err='';updateMonitors();return true}
function closeMic(){stopMonitors();if(MIC.stream)MIC.stream.getTracks().forEach(t=>t.stop());try{MIC.src&&MIC.src.disconnect()}catch(e){}MIC.stream=MIC.src=MIC.an=MIC.ctx=null}
function micOpen(){return !!(MIC.stream&&MIC.ctx===ctx&&MIC.stream.active)}
function micLevel(){if(!micOpen()||!MIC.an)return 0;MIC.an.getFloatTimeDomainData(mbuf);let m=0;for(let i=0;i<mbuf.length;i++){const v=Math.abs(mbuf[i]);if(v>m)m=v}return m}
function releaseMicIfIdle(){if(!S.tracks.some(t=>isAudio(t)&&t.arm)&&!REC)closeMic()}
/* Monitoring sends the live input through the track's effects and fader. It adds the system's input and
   output latency, so it is off by default; use headphones to avoid feedback. */
function updateMonitors(){S.tracks.forEach(tr=>{if(!isAudio(tr))return;const want=micOpen()&&tr.arm&&tr.monitor;
  if(tr._mon&&(!want||tr._monRt!==tr._rt)){try{tr._mon.disconnect()}catch(e){}tr._mon=null}
  if(want&&!tr._mon){buildTrack(tr);const g=G(1);MIC.src.connect(g);g.connect(trackOut(tr));tr._mon=g;tr._monRt=tr._rt}})}
function stopMonitors(){S.tracks.forEach(tr=>{if(tr._mon){try{tr._mon.disconnect()}catch(e){}tr._mon=null}})}
function toggleArm(tr){tr.arm=!tr.arm;markDirty();if(tr.arm)openMic().then(ok=>{if(!ok){tr.arm=false;toast('Could not open the input: '+MIC.err+'.')}updateMonitors();renderArrangeHeads();renderAudioDock()});else{updateMonitors();releaseMicIfIdle()}renderArrangeHeads();renderAudioDock()}

/* ───────── recording ───────── */
let REC=null;
function recordLatency(){return (ctx.baseLatency||0)+(ctx.outputLatency||0)+(MIC.inLat||0)+(+PREF.recOffset||0)/1000}
function audioRecSync(){const want=S.rec&&S.playing&&S.tracks.some(t=>isAudio(t)&&t.arm);if(want&&!REC)startAudioRec();else if(!want&&REC)finishAudioRec()}
async function startAudioRec(){REC={pending:true};
  const ok=await openMic();if(!ok){REC=null;toast('Not recording audio: '+MIC.err+'.');return}
  if(!workletOK()){REC=null;toast('Audio recording needs AudioWorklet, which this system does not provide.');return}
  if(!S.playing||!S.rec||!REC||!REC.pending){REC=null;return}
  const ch=PREF.recCh===2&&MIC.ch>=2?2:1,node=new AudioWorkletNode(ctx,'sc-rec',{numberOfInputs:1,numberOfOutputs:1,outputChannelCount:[1],channelCount:ch,channelCountMode:'explicit',processorOptions:{ch}}),sink=G(0);
  MIC.src.connect(node);node.connect(sink).connect(ctx.destination);
  let t0=ctx.currentTime+0.03;const first=stepLog.find(x=>x[1]<=t0)||stepLog[0];if(first&&t0<first[1])t0=first[1];
  const r={node,sink,ch,sr:ctx.sampleRate,chunks:[],startT:null,segs:[{t:t0,step:posAt(t0)}],tracks:S.tracks.filter(t=>isAudio(t)&&t.arm).map(t=>t.id),comp:recordLatency(),sd:stepDur(),done:null};
  node.port.onmessage=e=>{const m=e.data;if(m.start!=null)r.startT=m.start;if(m.data)r.chunks.push(m.data);if(m.done&&r.done)r.done()};
  node.port.postMessage({cmd:'start',at:t0});REC=r;renderTransport()}
/* The scheduler calls this when the loop wraps, so each pass becomes its own clip (the last pass ends on top). */
function audioRecWrap(t,step){if(REC&&REC.segs)REC.segs.push({t,step})}
async function finishAudioRec(){const r=REC;REC=null;if(!r)return;if(r.pending){return}
  const tEnd=ctx.currentTime;r.node.port.postMessage({cmd:'stop',at:tEnd});await new Promise(res=>{r.done=res;setTimeout(res,1500)});
  try{r.node.disconnect();r.sink.disconnect();MIC.src&&MIC.src.disconnect(r.node)}catch(e){}renderTransport();
  const len=r.chunks.reduce((a,c)=>a+c[0].length,0);if(r.startT==null||len<r.sr*0.05){releaseMicIfIdle();return}
  const buf=new AudioBuffer({length:len,numberOfChannels:r.ch,sampleRate:r.sr});let o=0;r.chunks.forEach(c=>{for(let k=0;k<r.ch;k++)buf.getChannelData(k).set(c[k],o);o+=c[0].length});
  const n=(PREF.takeN=(PREF.takeN||0)+1);savePrefs();const name='Take '+n+' · '+new Date().toLocaleString(undefined,{month:'short',day:'numeric',hour:'numeric',minute:'2-digit'});
  const bytes=encodeWav(buf,24),id='smp_'+await hashBytes(bytes.buffer);
  try{await putSample({id,name,type:'audio/wav',size:bytes.byteLength,added:Date.now(),duration:buf.duration,bytes:bytes.buffer,take:true})}catch(e){errLog.push('take store: '+e.message);toast('The take could not be stored on this device ('+e.message+'). It plays until you close Soundcraft; free up disk space and record again to keep it.')}
  SAMPLE_BUF.set(id,buf);SAMPLE_META.set(id,{name,duration:buf.duration});SAMPLE_MISSING.delete(id);
  pushUndo();const made=[];
  r.segs.forEach((g,i)=>{const ts=Math.max(g.t,r.startT),te=Math.min(i+1<r.segs.length?r.segs[i+1].t:tEnd,tEnd);if(te-ts<0.05)return;
    const start=(g.step+(ts-g.t)/r.sd)/16,c={rec:id,start,len:(te-ts)/r.sd/16,off:(ts-r.startT)+r.comp,gain:1,name:'Take '+n+(r.segs.length>1?' · pass '+(i+1):'')};
    r.tracks.forEach(tid=>{const tr=S.tracks.find(t=>t.id===tid);if(!tr)return;carveAudio(tr,c.start,c.start+c.len);const cl=Object.assign({id:uid('c')},c);tr.clips.push(cl);made.push(cl)})});
  markDirty();drawTimeline();renderAudioDock();releaseMicIfIdle();
  toast('Recorded '+name.split(' · ')[0]+' ('+fmtDur(buf.duration)+(r.segs.length>1?', '+r.segs.length+' loop passes':'')+'). Undo removes it.')}
/* Remove [a,b) bars of audio from a track's existing audio clips, splitting them where needed. */
function carveAudio(tr,a,b){const spb=secPerBar(),out=[];for(const c of tr.clips){if(!c.rec){out.push(c);continue}const cs=c.start,ce=c.start+c.len;
    if(ce<=a+1e-6||cs>=b-1e-6){out.push(c);continue}if(cs<a-1e-6)out.push(Object.assign({},c,{len:a-cs}));if(ce>b+1e-6)out.push(Object.assign({},c,{id:uid('c'),start:b,len:ce-b,off:c.off+(b-cs)*spb}))}
  tr.clips=out}
function splitAudioClip(t,c,at){const spb=secPerBar();t.clips.push(Object.assign({},c,{id:uid('c'),start:at,len:c.start+c.len-at,off:c.off+(at-c.start)*spb}));c.len=at-c.start}
const projectTakeIds=()=>{const s=new Set();S.tracks.forEach(t=>t.clips.forEach(c=>c.rec&&s.add(c.rec)));return s};
async function loadTakes(){const miss=[];for(const id of projectTakeIds()){const b=await loadSample(id);if(!b)miss.push((SAMPLE_META.get(id)||{}).name||id)}if(S.view==='arrange')drawTimeline();return miss}

/* ───────── drawing ───────── */
const PEAKS=new Map();
function peaksOf(id){if(PEAKS.has(id))return PEAKS.get(id);const b=SAMPLE_BUF.get(id);if(!b)return null;const per=Math.max(1,Math.floor(b.sampleRate/400)),n=Math.ceil(b.length/per),p=new Float32Array(n);
  for(let ch=0;ch<b.numberOfChannels;ch++){const d=b.getChannelData(ch);for(let i=0;i<n;i++){let m=p[i];for(let j=i*per,e=Math.min(b.length,j+per);j<e;j++){const v=d[j]<0?-d[j]:d[j];if(v>m)m=v}p[i]=m}}
  const r={p,rate:b.sampleRate/per,dur:b.duration};PEAKS.set(id,r);return r}
function drawAudioClip(c,cl,x,y,w,h,bw){const pk=peaksOf(cl.rec),spb=secPerBar(),mid=y+h/2+5;
  if(!pk){c.fillStyle='rgba(20,19,48,.7)';c.fillText(SAMPLE_MISSING.has(cl.rec)?'Missing take':'Loading…',x+4,y+28);return}
  c.fillStyle='rgba(20,19,48,.6)';const amp=(h-16)/2;
  for(let px=0;px<w;px+=1){const s0=cl.off+px/bw*spb,s1=cl.off+(px+1)/bw*spb;if(s1<0)continue;if(s0>pk.dur)break;let m=0;for(let i=Math.max(0,Math.floor(s0*pk.rate)),e=Math.min(pk.p.length,Math.ceil(s1*pk.rate));i<e;i++)if(pk.p[i]>m)m=pk.p[i];
    const hh=Math.max(0.5,Math.min(1,Math.sqrt(m))*amp);c.fillRect(x+px,mid-hh,1,hh*2)}}

/* ───────── dock panel ───────── */
async function renderAudioDock(){const box=$('#dockAudio'),tr=selTrack();if(!box||box.hidden||!isAudio(tr))return;
  let ins=[['','System default input']];try{(await navigator.mediaDevices.enumerateDevices()).filter(d=>d.kind==='audioinput'&&d.deviceId&&d.deviceId!=='default'&&d.deviceId!=='communications').forEach((d,i)=>ins.push([d.deviceId,d.label||'Input '+(i+1)]))}catch(e){}
  const opt=(arr,cur)=>arr.map(([v,l])=>'<option value="'+esc(v)+'"'+(String(cur)===String(v)?' selected':'')+'>'+esc(l)+'</option>').join('');
  const takes=tr.clips.filter(c=>c.rec).sort((a,b)=>a.start-b.start),sel=S.selClip&&S.selClip.tr===tr.id&&tr.clips.find(c=>c.id===S.selClip.clip);
  box.innerHTML='<div class="toolbar"><b style="font-family:var(--f-display);font-weight:500">'+esc(tr.name)+' · mic track</b><span class="sep"></span>'+
    '<button class="btn'+(tr.arm?' rec-on':'')+'" data-au="arm" aria-pressed="'+!!tr.arm+'">● '+(tr.arm?'Armed':'Arm')+'</button>'+
    '<button class="btn" data-au="mon" aria-pressed="'+!!tr.monitor+'" title="Hear the input through this track (use headphones)">Monitor '+(tr.monitor?'on':'off')+'</button>'+
    '<label class="tf">Input <select data-au="input">'+opt(ins,PREF.inputId)+'</select></label>'+
    '<label class="tf">Channels <select data-au="ch">'+opt([[1,'Mono'],[2,'Stereo']],PREF.recCh||1)+'</select></label>'+
    '<span class="in-meter" title="Input level"><i id="auMeter"></i></span><span class="hint" id="auState">'+(micOpen()?esc(MIC.label):MIC.err?'Input closed: '+esc(MIC.err):'Input closed. Arm the track to open it.')+'</span></div>'+
    '<div class="audio-dock-body"><div><h4>How to record</h4><p class="hint">Arm the track, press <b>Record</b> (R) and sing or play along. The take starts where the playhead is; with the loop on, every pass is recorded and the last one ends up on top (all passes stay in the take). Recording over an earlier take replaces that part, like tape. Undo removes a take.</p>'+
    '<p class="hint">Timing is corrected for the latency the system reports ('+(ctx?Math.round(recordLatency()*1000-(+PREF.recOffset||0))+' ms':'—')+'). If takes still sound early or late, adjust <b>Settings → Recording → Offset</b>.</p></div>'+
    '<div><h4>'+(sel&&sel.rec?'Selected clip':'Takes on this track')+'</h4>'+(sel&&sel.rec?'<label class="tf">Clip gain <input type="range" min="0" max="2" step="0.01" value="'+sel.gain+'" data-au="gain"> <span id="auGain">'+gainToDb(sel.gain).toFixed(1)+' dB</span></label><p class="hint">'+esc(sel.name||'Take')+' · starts at bar '+(sel.start+1).toFixed(2)+' · '+fmtDur(sel.len*secPerBar())+'</p>':'')+
    '<ul class="list">'+(takes.map(c=>'<li><span class="grow">'+esc(c.name||'Take')+' · bar '+(Math.floor(c.start)+1)+' · '+fmtDur(c.len*secPerBar())+(SAMPLE_MISSING.has(c.rec)?' · <b>missing</b>':'')+'</span><button class="btn xs" data-au="sel" data-c="'+c.id+'">Select</button><button class="btn xs danger" data-au="del" data-c="'+c.id+'">Delete</button></li>').join('')||'<li class="hint">No takes yet.</li>')+'</ul></div></div>'}
$('#dockAudio').addEventListener('click',e=>{const b=e.target.closest('[data-au]');if(!b)return;const tr=selTrack(),a=b.dataset.au;if(!isAudio(tr))return;
  if(a==='arm')toggleArm(tr);
  else if(a==='mon'){tr.monitor=!tr.monitor;markDirty();updateMonitors();renderAudioDock();if(tr.monitor&&!tr.arm)toast('Monitoring starts when the track is armed.');else if(tr.monitor)toast('Monitoring on. Use headphones to avoid feedback.')}
  else if(a==='sel'){S.selClip={tr:tr.id,clip:b.dataset.c};drawTimeline();renderAudioDock()}
  else if(a==='del'){pushUndo();tr.clips=tr.clips.filter(c=>c.id!==b.dataset.c);if(S.selClip&&S.selClip.clip===b.dataset.c)S.selClip=null;markDirty();drawTimeline();renderAudioDock()}});
$('#dockAudio').addEventListener('change',e=>{const a=e.target.dataset.au;
  if(a==='input'||a==='ch'){if(a==='input')PREF.inputId=e.target.value;else PREF.recCh=+e.target.value;savePrefs();if(micOpen()){closeMic();openMic().then(ok=>{if(!ok)toast('Could not open that input: '+MIC.err+'.');renderAudioDock()})}}
  if(a==='gain'){markDirty()}});
$('#dockAudio').addEventListener('input',e=>{if(e.target.dataset.au!=='gain')return;const tr=selTrack(),c=S.selClip&&tr.clips.find(x=>x.id===S.selClip.clip);if(!c)return;c.gain=+e.target.value;const l=$('#auGain');if(l)l.textContent=gainToDb(c.gain).toFixed(1)+' dB'});
$('#dockAudio').addEventListener('pointerdown',e=>{if(e.target.dataset.au==='gain')pushUndo()});
function syncDock(){const a=isAudio(selTrack()),au=$('#dockAudio');if(!au)return;const was=au.hidden;au.hidden=!a;$('#dockRoll').hidden=a||PREF.dock!=='roll';$('#dockInst').hidden=a||PREF.dock!=='inst';if(a&&(was||au.dataset.tr!==selTrack().id)){au.dataset.tr=selTrack().id;renderAudioDock()}}
