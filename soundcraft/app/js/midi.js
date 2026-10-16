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
  try{midiAcc=await navigator.requestMIDIAccess({sysex:false});midiAcc.onstatechange=bindInputs;bindInputs()}catch(e){midiAcc=null;setMidi('blocked','MIDI blocked here')}}
function midiInputs(){const l=[];if(midiAcc)midiAcc.inputs.forEach(i=>{if(i.state==='connected')l.push(i)});return l}
function bindInputs(){const l=[];midiAcc.inputs.forEach(i=>{i.onmidimessage=ev=>onMidi(ev,i);if(i.state==='connected')l.push(i)});if(!l.length)setMidi('none','No MIDI devices');else setMidi('ready',(l[0].name||'MIDI device')+(l.length>1?' +'+(l.length-1):''))}
function midiTargetsForControl(){const tr=selTrack();if(!tr)return [];return tr.input&&tr.input.group?S.tracks.filter(t=>t.input&&t.input.group===tr.input.group):[tr]}
function onMidi(ev,inp){
  const d=ev.data;if(!d||d.length<1)return;const st=d[0],type=st&0xf0,ch=(st&0x0f)+1,d1=d[1]||0,d2=d.length>2?d[2]:0;
  if(type<0x80||type===0xF0)return;if(S.midiCh&&ch!==S.midiCh)return;lastMidi=performance.now();devHits[inp.id]=lastMidi;
  if(type===0x90&&d2>0)liveOn(d1,Math.pow(d2/127,0.8),'midi',ch);
  else if(type===0x80||(type===0x90&&d2===0))liveOff(d1,'midi');
  else if(type===0xB0)onCC(d1,d2/127);
  else if(type===0xE0){if(!ensureAudio())return;const b=(((d2<<7)|d1)-8192)/8192;midiTargetsForControl().forEach(t=>setBend(t,b*(t.bendRange||2)));uiCtl('bend',b)}
  else if(type===0xD0){const v=d1/127;midiTargetsForControl().forEach(t=>{t._at=v;setLiveSource(t,'at',v)});uiCtl('at',v)}
  else if(type===0xA0){const v=d2/127;midiTargetsForControl().forEach(t=>{if(t._rt)t._rt.live.forEach(h=>{if(h.n===d1&&h.M&&h.M.pat)h.M.pat.offset.setTargetAtTime(v,ctx.currentTime,0.01)})})}
}
function onCC(cc,v){
  if(learn){S.ccMap[cc]=learn;const tr=S.tracks.find(t=>t.id===learn.track);toast('CC '+cc+' now controls '+(learn.macro?('macro "'+((tr&&tr.mod.macros.find(m=>m.id===learn.macro))||{}).name+'"'):(tr&&destDef(tr,learn.dst)||{label:learn.dst}).label)+'.');learn=null;renderLearnState();markDirty();return}
  midiTargetsForControl().forEach(t=>{t._cc=t._cc||{};t._cc[cc]=v;setLiveSource(t,'cc'+cc,v)});
  if(cc===64){setSustain(v>=0.5);return}
  if(cc===1){if(ensureAudio())midiTargetsForControl().forEach(t=>setMod(t,v));uiCtl('mw',v);return}
  if(cc===120||cc===123){if(ctx)panic();return}
  const map=S.ccMap[cc];if(!map)return;const tr=S.tracks.find(t=>t.id===map.track);if(!tr)return;
  if(map.macro){const m=tr.mod.macros.find(x=>x.id===map.macro);if(m){applyMacro(tr,m,v);syncControl(tr,'macro:'+m.id)}return}
  const dd=destDef(tr,map.dst);if(dd){setParam(tr,map.dst,fromNorm(dd.p,v),{quiet:true});syncControl(tr,map.dst);markDirty()}}
function startLearn(trId,what){learn=Object.assign({track:trId},what);renderLearnState();toast('Move a knob, fader or pedal on your MIDI controller to link it.')}
function renderMidiPop(){
  const chip=$('#midiChip');if(!chip)return;const st=chip.dataset.state,list=midiInputs(),inViewer=!!S.dl&&!window.native;
  $('#midiMsg').textContent=st==='ready'?'Notes, velocity, sustain, mod wheel, pitch bend, aftertouch and CCs go to the selected track (or all instruments of its ensemble). Devices appear as you plug them in.'
    :st==='none'?'No controller found yet. Plug one in and it appears here automatically. Until then, play with your computer keyboard.'
    :st==='blocked'?(inViewer?'This embedded view does not allow MIDI access. Use the Soundcraft desktop app, or open the app file in Chrome or Edge.':'MIDI access was refused. Allow MIDI for Soundcraft in your system or browser settings, then press Scan again.')
    :st==='unsupported'?'This browser has no Web MIDI support. Use the Soundcraft desktop app, or Chrome, Edge, Opera or Firefox.':'Asking the system for MIDI access…';
  $('#devList').innerHTML=list.map(i=>'<li><i class="led on" data-dev="'+esc(i.id)+'"></i>'+esc(i.name||'MIDI input')+'<small>'+esc(i.manufacturer||'')+'</small></li>').join('');
  const maps=Object.entries(S.ccMap).map(([cc,m])=>{const tr=S.tracks.find(t=>t.id===m.track);if(!tr)return '';const lbl=m.macro?'Macro '+((tr.mod.macros.find(x=>x.id===m.macro)||{}).name||'?'):(destDef(tr,m.dst)||{label:m.dst}).label;return '<li>CC '+cc+' → '+esc(tr.name)+' · '+esc(lbl)+'<button class="btn xs" data-unmap="'+cc+'">Remove</button></li>'}).join('');
  $('#ccMaps').innerHTML=maps||'<li class="hint">No CC links yet. Right-click any knob and choose “Learn MIDI CC”.</li>'}
