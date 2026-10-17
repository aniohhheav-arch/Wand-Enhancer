'use strict';
/* MIDI input: hot-plug detection, notes (with ensemble zones and channel filters), sustain, mod wheel,
   pitch bend, channel and polyphonic aftertouch, any CC as a modulation source, and MIDI learn for
   every registered parameter and macro. */
let midiAcc=null,lastMidi=0;const devHits={};
let learn=null; // {track id, dst} or {track id, macro id}
function setMidi(state,text){const c=$('#midiChip');if(!c)return;c.dataset.state=state;$('#midiText').textContent=text;renderMidiPop()}
async function initMIDI(){
  if(!navigator.requestMIDIAccess){setMidi('unsupported','MIDI not supported');return}
  setMidi('idle','Looking for MIDI…');
  try{midiAcc=await navigator.requestMIDIAccess({sysex:false});midiAcc.onstatechange=bindInputs;bindInputs()}catch(e){midiAcc=null;errLog.push('MIDI: '+e.name+' '+e.message);if(e.name==='NotAllowedError'||e.name==='SecurityError')setMidi('blocked','MIDI blocked here');else{midiErr=e.name+': '+e.message;setMidi('failed','MIDI unavailable')}}}
let midiErr='';
function midiInputs(){const l=[];if(midiAcc)midiAcc.inputs.forEach(i=>{if(i.state==='connected')l.push(i)});return l}
function bindInputs(){const l=[];midiAcc.inputs.forEach(i=>{i.onmidimessage=ev=>onMidi(ev,i);if(i.state==='connected')l.push(i)});if(!l.length)setMidi('none','No MIDI devices');else setMidi('ready',(l[0].name||'MIDI device')+(l.length>1?' +'+(l.length-1):''))}
function midiTargetsForControl(){const tr=selTrack();if(!tr)return [];return tr.input&&tr.input.group?S.tracks.filter(t=>t.input&&t.input.group===tr.input.group):[tr]}
/* Notes are handled the moment a message arrives. Controller floods (mod wheel, bend, mapped knobs) can
   send hundreds of messages a second, so their heavier work and all screen updates are coalesced into one
   pass per frame; that keeps the main thread free for the next note. */
const midiLater=new Map();let midiLaterQ=0;
function later(key,fn){midiLater.set(key,fn);if(midiLaterQ)return;midiLaterQ=1;const run=()=>{midiLaterQ=0;const l=[...midiLater.values()];midiLater.clear();l.forEach(f=>{try{f()}catch(e){errLog.push('midi: '+e.message)}})};
  if(document.hidden)setTimeout(run,16);else requestAnimationFrame(run)}
const midiLag={n:0,sum:0,max:0};
function velCurve(v){const x=v/127;return PREF.velCurve==='soft'?Math.pow(x,0.5):PREF.velCurve==='hard'?Math.pow(x,1.4):PREF.velCurve==='fixed'?0.8:Math.pow(x,0.8)}
function onMidi(ev,inp){
  const d=ev.data;if(!d||d.length<1)return;const st=d[0],type=st&0xf0,ch=(st&0x0f)+1,d1=d[1]||0,d2=d.length>2?d[2]:0;
  if(type<0x80||type===0xF0)return;if(S.midiCh&&ch!==S.midiCh)return;const now=performance.now();lastMidi=now;devHits[inp.id]=now;
  if((type===0x90||type===0x80)&&arcMidiNote(d1,d2/127,type===0x90&&d2>0))return;
  if(midiMapped(type,ch,d1,d2))return;
  if(type===0x90&&d2>0){if(ev.timeStamp>0&&ev.timeStamp<=now){const lag=now-ev.timeStamp;midiLag.n++;midiLag.sum+=lag;if(lag>midiLag.max)midiLag.max=lag}liveOn(d1,velCurve(d2),'midi',ch)}
  else if(type===0x80||(type===0x90&&d2===0))liveOff(d1,'midi');
  else if(type===0xB0)onCC(d1,d2/127);
  else if(type===0xE0){if(!ensureAudio())return;const b=(((d2<<7)|d1)-8192)/8192;midiTargetsForControl().forEach(t=>setBend(t,b*(t.bendRange||2)));later('ui:bend',()=>uiCtl('bend',b))}
  else if(type===0xD0){const v=d1/127;midiTargetsForControl().forEach(t=>{t._at=v;setLiveSource(t,'at',v)});later('ui:at',()=>uiCtl('at',v))}
  else if(type===0xA0){const v=d2/127;midiTargetsForControl().forEach(t=>{if(t._rt)t._rt.live.forEach(h=>{if(h.n===d1&&h.M&&h.M.pat)h.M.pat.offset.setTargetAtTime(v,ctx.currentTime,0.01)})})}
}
function onCC(cc,v){
  if(learn){S.ccMap[cc]=learn;const tr=S.tracks.find(t=>t.id===learn.track);toast('CC '+cc+' now controls '+(learn.macro?('macro "'+((tr&&tr.mod.macros.find(m=>m.id===learn.macro))||{}).name+'"'):(tr&&destDef(tr,learn.dst)||{label:learn.dst}).label)+'.');learn=null;renderLearnState();markDirty();return}
  midiTargetsForControl().forEach(t=>{t._cc=t._cc||{};t._cc[cc]=v;setLiveSource(t,'cc'+cc,v)});
  if(cc===64){setSustain(v>=0.5);return}
  if(cc===1){if(ensureAudio()){const ts=midiTargetsForControl();ts.forEach(t=>{t._mod=t._mw=v;setLiveSource(t,'mw',v)});later('mw',()=>ts.forEach(t=>refreshLive(t)))}later('ui:mw',()=>uiCtl('mw',v));return}
  if(cc===120||cc===123){if(ctx)panic();return}
  const map=S.ccMap[cc];if(!map)return;
  later('cc'+cc,()=>{const tr=S.tracks.find(t=>t.id===map.track);if(!tr)return;
    if(map.macro){const m=tr.mod.macros.find(x=>x.id===map.macro);if(m){applyMacro(tr,m,v);syncControl(tr,'macro:'+m.id)}return}
    const dd=destDef(tr,map.dst);if(dd){setParam(tr,map.dst,fromNorm(dd.p,v),{quiet:true});syncControl(tr,map.dst);markDirty()}})}
function startLearn(trId,what){learn=Object.assign({track:trId},what);renderLearnState();toast('Move a knob, fader or pedal on your MIDI controller to link it.')}
function renderMidiPop(){
  const chip=$('#midiChip');if(!chip)return;const st=chip.dataset.state,list=midiInputs(),inViewer=!!S.dl&&!window.native;
  $('#midiMsg').textContent=st==='ready'?'Notes, velocity, sustain, mod wheel, pitch bend, aftertouch and CCs go to the selected track (or all instruments of its ensemble). Devices appear as you plug them in. On Windows a MIDI device can only be used by one app at a time, so close other music apps or browser tabs that use it if notes do not arrive.'
    :st==='none'?'No controller found yet. Plug one in and it appears here automatically. If it is already plugged in, another app may be using it (Windows lets only one app open a MIDI device), or its driver may be missing. Until then, play with your computer keyboard.'
    :st==='blocked'?(inViewer?'This embedded view does not allow MIDI access. Use the Soundcraft desktop app, or open the app file in Chrome or Edge.':'MIDI access was refused. Allow MIDI for Soundcraft in your system or browser settings, then press Scan again.')
    :st==='failed'?'The system MIDI service could not start ('+midiErr+'). Check that your keyboard\'s driver is installed, unplug and replug it, then press Scan again.'
    :st==='unsupported'?'This browser has no Web MIDI support. Use the Soundcraft desktop app, or Chrome, Edge, Opera or Firefox.':'Asking the system for MIDI access…';
  const lat=$('#midiLat');if(lat){const o=ctx?((ctx.baseLatency||0)+(ctx.outputLatency||0))*1000:0;lat.hidden=!ctx||st!=='ready';
    lat.textContent=ctx?'Sound output latency ≈ '+o.toFixed(0)+' ms'+(midiLag.n?' · MIDI handling '+(midiLag.sum/midiLag.n).toFixed(1)+' ms average, '+midiLag.max.toFixed(0)+' ms worst':'')+(o>25?'. Higher than ideal: set Settings → Audio → Latency to Lowest, use wired headphones or speakers instead of Bluetooth, and close other audio apps.':''):''}
  $('#devList').innerHTML=list.map(i=>'<li><i class="led on" data-dev="'+esc(i.id)+'"></i>'+esc(i.name||'MIDI input')+'<small>'+esc(i.manufacturer||'')+'</small></li>').join('');
  const maps=Object.entries(S.ccMap).map(([cc,m])=>{const tr=S.tracks.find(t=>t.id===m.track);if(!tr)return '';const lbl=m.macro?'Macro '+((tr.mod.macros.find(x=>x.id===m.macro)||{}).name||'?'):(destDef(tr,m.dst)||{label:m.dst}).label;return '<li>CC '+cc+' → '+esc(tr.name)+' · '+esc(lbl)+'<button class="btn xs" data-unmap="'+cc+'">Remove</button></li>'}).join('');
  const pick=$('#mapPick');if(pick&&!pick.dataset.filled){pick.dataset.filled=1}if(pick)pick.innerHTML=['play','stop','rec','loop','metro','hold','arp','arpRate','arpMode','arpOct','master','swing','bpm','selvol','selpan','selrev','selmute','selsolo','selmacro:0','selmacro:1','selmacro:2','selmacro:3','trkNext','trkPrev','octUp','octDown','undo','marker','panic','ret:rev','ret:dly'].map(k=>'<option value="'+k+'">'+esc(actionDef(k).label)+'</option>').join('');
  const ml=$('#mapList');if(ml)ml.innerHTML=Object.entries(PREF.midiMap).map(([id,k])=>{const d=actionDef(k);return '<li>'+esc(id.startsWith('cc:')?'CC '+id.slice(3):'Pad '+noteName(+id.slice(5)))+' → '+esc(d?d.label:k+' (track gone)')+'<button class="btn xs" data-unmapa="'+esc(id)+'">Remove</button></li>'}).join('')||'<li class="hint">Nothing mapped yet.</li>';
  $('#ccMaps').innerHTML=maps||'<li class="hint">No CC links yet. Right-click any knob and choose “Learn MIDI CC”.</li>'}

/* ───────── controller mapping: any knob, fader, button or pad on a controller to app controls ─────────
   Mappings are global (stored with the preferences), because they describe your hardware, not a song.
   A CC drives continuous controls directly and toggles buttons when it crosses the middle; a note (a pad
   or key) presses buttons, sets toggles, or, for continuous controls, jumps to its velocity. Mapped notes
   do not play sound. Learn: right-click a control → "Learn MIDI", or the MIDI panel's Learn list. */
PREF.midiMap=PREF.midiMap||{};
let actLearn=null;const ccState={};
const trkOf=id=>S.tracks.find(t=>t.id===id);
function actionDef(key){const [a,b,c]=key.split(':');
  const T={master:['Master volume','cont'],swing:['Swing','cont'],bpm:['Tempo','cont'],play:['Play / stop','btn'],stop:['Stop','btn'],rec:['Record','btn'],loop:['Loop on/off','btn'],metro:['Metronome','btn'],
    hold:['Hold (latch notes)','btn'],arp:['Arpeggiator on/off (selected track)','btn'],arpRate:['Arp rate (selected track)','cont'],arpMode:['Arp mode (selected track)','cont'],arpOct:['Arp octaves (selected track)','cont'],
    trkNext:['Next track','btn'],trkPrev:['Previous track','btn'],octUp:['Octave up','btn'],octDown:['Octave down','btn'],panic:['All notes off','btn'],undo:['Undo','btn'],marker:['Add section marker','btn'],
    selvol:['Selected track volume','cont'],selpan:['Selected track pan','cont'],selrev:['Selected track reverb send','cont'],selmute:['Selected track mute','btn'],selsolo:['Selected track solo','btn'],
    selmacro:['Selected track macro '+(+b+1),'cont']}[a];
  if(T)return {label:T[0],kind:T[1]};
  if(a==='trk'){const t=trkOf(b);if(!t)return null;const L={vol:['volume','cont'],pan:['pan','cont'],rev:['reverb send','cont'],dly:['delay send','cont'],mute:['mute','btn'],solo:['solo','btn'],arm:['record arm','btn'],sel:['select','btn']}[c];return L&&{label:t.name+' '+L[0],kind:L[1]}}
  if(a==='ret')return {label:(b==='rev'?'Reverb':'Delay')+' return level',kind:'cont'};return null}
const ARP_RATES=['1/4','1/8','1/8t','1/16','1/16t','1/32'];
function runAction(key,v,press){const d=actionDef(key);if(!d)return;const [a,b,c]=key.split(':'),tr=selTrack();
  if(d.kind==='btn'&&!press)return;
  const mix=()=>{applyMix();markDirty();later('ui:mixer',()=>{if(S.view==='mix')renderMix();if(S.view==='arrange')renderArrangeHeads()})};
  switch(a){
    case 'master':S.master=v;applyMix();later('ui:master',()=>{$('#masterVol').value=S.master});markDirty();return;
    case 'swing':S.swing=v*0.5;later('ui:swing',()=>{$('#swing').value=S.swing});markDirty();return;
    case 'bpm':S.bpm=Math.round(60+v*140);later('ui:bpm',()=>{$('#bpm').value=S.bpm;fxTempoChanged()});markDirty();return;
    case 'play':S.playing?stop():play();return;case 'stop':stop();return;case 'rec':toggleRec();return;case 'loop':$('#btnLoop').click();return;case 'metro':$('#btnMetro').click();return;
    case 'hold':setLatch(!S.latch);return;
    case 'arp':if(tr&&!isAudio(tr)){tr.arp.on=!tr.arp.on;if(!tr.arp.on){tr._arpHeld=null;tr._arpClock=null}markDirty();later('ui:arp',()=>{renderTransport();renderArrangeHeads();if(S.view==='lab')renderLab()});toast('Arpeggiator '+(tr.arp.on?'on':'off')+' on '+tr.name+'.')}return;
    case 'arpRate':if(tr){tr.arp.rate=ARP_RATES[Math.min(ARP_RATES.length-1,Math.floor(v*ARP_RATES.length))];later('ui:arp',()=>{if(S.view==='lab')renderLab()});markDirty()}return;
    case 'arpMode':if(tr){tr.arp.mode=ARP_MODES[Math.min(ARP_MODES.length-1,Math.floor(v*ARP_MODES.length))][0];later('ui:arp',()=>{if(S.view==='lab')renderLab()});markDirty()}return;
    case 'arpOct':if(tr){tr.arp.oct=1+Math.min(3,Math.floor(v*4));markDirty()}return;
    case 'trkNext':later('sel',()=>selectTrack(Math.min(S.tracks.length-1,S.sel+1)));return;case 'trkPrev':later('sel',()=>selectTrack(Math.max(0,S.sel-1)));return;
    case 'octUp':S.octave=Math.min(8,S.octave+1);later('ui:kb',renderKeyboard);return;case 'octDown':S.octave=Math.max(0,S.octave-1);later('ui:kb',renderKeyboard);return;
    case 'panic':if(ctx)panic();return;case 'undo':later('undo',undo);return;case 'marker':$('#btnMarker').click();return;
    case 'selvol':if(tr){tr.vol=v;mix()}return;case 'selpan':if(tr){tr.pan=v*2-1;mix()}return;case 'selrev':if(tr){tr.rev=v;mix()}return;
    case 'selmute':if(tr){tr.mute=!tr.mute;mix()}return;case 'selsolo':if(tr){tr.solo=!tr.solo;mix()}return;
    case 'selmacro':{const m=tr&&tr.mod.macros[+b];if(m){applyMacro(tr,m,v);later('mac'+b,()=>syncControl(tr,'macro:'+m.id))}return}
    case 'ret':S.returns[b].vol=v;mix();return;
    case 'trk':{const t=trkOf(b);if(!t)return;if(c==='vol')t.vol=v;else if(c==='pan')t.pan=v*2-1;else if(c==='rev')t.rev=v;else if(c==='dly')t.dly=v;else if(c==='mute')t.mute=!t.mute;else if(c==='solo')t.solo=!t.solo;
      else if(c==='arm'){toggleArm(t);return}else if(c==='sel'){later('sel',()=>selectTrack(S.tracks.indexOf(t)));return}mix();return}}}
/* Called first for every message; returns true when the message drove a mapped control (so it does not play). */
function midiMapped(type,ch,d1,d2){
  const isNote=type===0x90||type===0x80,isCC=type===0xB0;if(!isNote&&!isCC)return false;
  const id=(isNote?'note:':'cc:')+d1,on=type===0x90&&d2>0;
  if(actLearn&&(isCC||on)){PREF.midiMap[id]=actLearn;savePrefs();const d=actionDef(actLearn);toast((isCC?'CC '+d1:'Pad / key '+noteName(d1))+' now controls '+(d?d.label:actLearn)+'.');actLearn=null;document.body.classList.remove('midi-learning');renderMidiPop();return true}
  const key=PREF.midiMap[id];if(!key)return false;const d=actionDef(key);if(!d)return true;
  if(isCC){const v=d2/127;if(d.kind==='cont')runAction(key,v,false);else{const was=ccState[id]>=0.5;ccState[id]=v;if(v>=0.5&&!was)runAction(key,1,true)}}
  else if(on){if(d.kind==='cont')runAction(key,d2/127,false);else runAction(key,1,true)}
  return true}
function startActionLearn(key){actLearn=key;learn=null;document.body.classList.add('midi-learning');const d=actionDef(key);toast('Move a knob or fader, or hit a pad, on your controller to control '+(d?d.label:key)+'. Esc cancels.')}
/* right-click any mappable control → Learn MIDI */
function learnKeyFor(el){if(!el||!el.closest)return null;const x=el.closest('[data-learn]');if(x)return x.dataset.learn;
  const strip=el.closest('.strip[data-strip]'),k=el.dataset&&(el.dataset.k||(el.dataset.ret?'ret':null));
  if(strip){const s=strip.dataset.strip;if(s==='master'&&el.classList.contains('fader'))return 'master';if(s.startsWith('ret:')&&el.classList.contains('fader'))return 'ret:'+s.slice(4);if(s.startsWith('track:')&&k&&['vol','pan','rev','dly'].includes(k))return 'trk:'+s.slice(6)+':'+k}
  const th=el.closest('.th[data-i]');if(th){const t=S.tracks[+th.dataset.i],a=el.dataset.act;if(t&&['vol','mute','solo','arm'].includes(a))return 'trk:'+t.id+':'+a;if(t&&!a)return 'trk:'+t.id+':sel'}return null}
document.addEventListener('contextmenu',e=>{if(e.defaultPrevented)return;const key=learnKeyFor(e.target);if(!key)return;e.preventDefault();const d=actionDef(key),mapped=Object.entries(PREF.midiMap).filter(([,k])=>k===key).map(([id])=>id);
  openMenu(e.clientX,e.clientY,[{label:'Learn MIDI for '+(d?d.label:key)+'…',act:()=>startActionLearn(key)}].concat(mapped.length?[{label:'Forget MIDI ('+mapped.join(', ')+')',act:()=>{mapped.forEach(id=>delete PREF.midiMap[id]);savePrefs();renderMidiPop()}}]:[]))});
window.addEventListener('keydown',e=>{if(e.key==='Escape'&&actLearn){actLearn=null;document.body.classList.remove('midi-learning')}},true);
/* Hold: notes keep sounding after you let go; the next chord (or turning Hold off) releases them. */
const latched=[],physDown=new Set();
function setLatch(on){S.latch=on;const b=$('#btnHold');if(b)b.setAttribute('aria-pressed',on);if(!on)releaseLatched();toast('Hold '+(on?'on: notes keep sounding until you play the next chord.':'off.'))}
function releaseLatched(){latched.splice(0).forEach(([n,src])=>liveOffNow(n,src))}
