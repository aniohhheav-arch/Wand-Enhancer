'use strict';
/* Boot, shortcuts and the frame loop (visuals only; audio is scheduled on the audio clock). */
$('#btnPlay').onclick=()=>S.playing?stop():play();
$('#btnStop').onclick=stop;$('#btnRec').onclick=toggleRec;
$('#btnMetro').onclick=()=>{S.metro=!S.metro;renderTransport();markDirty()};
$('#btnLoop').onclick=()=>{S.loop.on=!S.loop.on;if(S.loop.end<=S.loop.start)S.loop.end=S.loop.start+4;renderTransport();drawTimeline();markDirty()};
$('#bpm').onchange=e=>{pushUndo();S.bpm=clamp(Math.round(+e.target.value)||112,40,240);e.target.value=S.bpm;fxTempoChanged();S.tracks.forEach(t=>{if(t._rt)buildTrackMod(t)});markDirty()};
$('#quant').onchange=e=>{S.quant=+e.target.value;markDirty()};$('#swing').oninput=e=>{S.swing=+e.target.value;markDirty()};
$('#masterVol').oninput=e=>{S.master=+e.target.value;applyMix();markDirty()};
$('#projTitle').addEventListener('change',e=>{const t=e.target.value.trim()||'Untitled cassette';e.target.value=t;if(LIB[curId]){LIB[curId].title=t;saveNow();setWindowTitle()}});
$('#midiChip').onclick=e=>{e.stopPropagation();const p=$('#midiPop');p.hidden=!p.hidden;$('#midiChip').setAttribute('aria-expanded',!p.hidden);renderMidiPop();if(!midiAcc&&!p.hidden&&$('#midiChip').dataset.state!=='unsupported')initMIDI()};
document.addEventListener('click',e=>{if(!e.target.closest('#midiPop,#midiChip')&&!$('#midiPop').hidden){$('#midiPop').hidden=true;$('#midiChip').setAttribute('aria-expanded','false')}});
$('#midiCh').innerHTML='<option value="0">All channels</option>'+Array.from({length:16},(_,i)=>'<option value="'+(i+1)+'">'+(i+1)+'</option>').join('');$('#midiCh').onchange=e=>S.midiCh=+e.target.value;
$('#btnRescan').onclick=()=>midiAcc?bindInputs():initMIDI();
$('#ccMaps').onclick=e=>{const b=e.target.closest('[data-unmap]');if(!b)return;delete S.ccMap[b.dataset.unmap];markDirty();renderMidiPop()};
$('#btnGetApp').onclick=async()=>{try{const r=await fetch('soundcraft-app.html');if(!r.ok)throw 0;saveBlob(await r.blob(),'soundcraft.html')}catch(e){toast('The app file is not available from this page.')}};
/* keyboard */
const kbHeld=new Map();
window.addEventListener('keydown',e=>{
  const inField=e.target.closest&&e.target.closest('input,select,textarea');const k=e.key.toLowerCase(),mod=e.ctrlKey||e.metaKey;
  if(e.key==='Escape'){closeMenu();$$('.modal').forEach(m=>m.hidden=true);if(tourI>=0){tourI=-1;tourStep()}learn=null;return}
  if(inField)return;
  if(mod&&k==='z'){e.preventDefault();e.shiftKey?redo():undo();return}if(mod&&k==='y'){e.preventDefault();redo();return}if(mod&&k==='s'){e.preventDefault();e.shiftKey?saveSession(true):quickSave();return}if(mod&&k==='o'){e.preventDefault();openSessionDialog();return}if(mod&&k==='h'){e.preventDefault();openHistory();return}if(mod&&(k===','||e.code==='Comma')){e.preventDefault();openSettings();return}
  if(/^f[1-5]$/.test(k)){e.preventDefault();setView(VIEWS[+k[1]-1]);return}
  if(!$$('.modal').every(m=>m.hidden))return;
  if(S.view==='arrange'&&PREF.dock==='roll'&&rollKeys(e)){e.preventDefault();return}
  if(S.view==='arrange'&&S.selClip&&!R.sel.size){if(mod&&k==='d'){e.preventDefault();dupClip();return}if(k==='delete'||k==='backspace'){e.preventDefault();delClip();return}}
  if(mod||e.altKey)return;
  if(k===' '){e.preventDefault();if(!e.repeat)S.playing?stop():play();return}
  if(S.view==='studio'){if(WALK[k]){e.preventDefault();walkKeys.add(WALK[k]);me.target=null;return}if((k==='e'||k==='enter')&&!e.repeat){e.preventDefault();useNearest();return}if(k==='r'&&!e.repeat)toggleRec();return}
  if(e.repeat){if(KEYMAP.includes(k))e.preventDefault();return}
  const i=KEYMAP.indexOf(k);if(i>=0){e.preventDefault();const n=liveBase()+i;kbHeld.set(e.code,n);liveOn(n,S.vel,'kb');return}
  if(k==='z'){S.octave=Math.max(0,S.octave-1);renderKeyboard()}else if(k==='x'){S.octave=Math.min(8,S.octave+1);renderKeyboard()}
  else if(k==='c'){S.vel=clamp(Math.round((S.vel-0.1)*10)/10,0.1,1);renderKeyboard()}else if(k==='v'){S.vel=clamp(Math.round((S.vel+0.1)*10)/10,0.1,1);renderKeyboard()}
  else if(k==='r')toggleRec();else if(/^[1-9]$/.test(k))selectTrack(+k-1)});
window.addEventListener('keyup',e=>{const k=e.key.toLowerCase();if(WALK[k])walkKeys.delete(WALK[k]);if(!kbHeld.has(e.code))return;liveOff(kbHeld.get(e.code),'kb');kbHeld.delete(e.code)});
window.addEventListener('blur',()=>{kbHeld.forEach(n=>liveOff(n,'kb'));kbHeld.clear();walkKeys.clear()});
['pointerdown','keydown'].forEach(ev=>window.addEventListener(ev,()=>{if(!ctx||ctx.state!=='running')ensureAudio()},{capture:true}));
/* frame loop */
let frameT=performance.now(),statT=0;
function frame(){requestAnimationFrame(frame);const now=performance.now(),dt=Math.min(0.05,(now-frameT)/1000);frameT=now;analyse(now);
  try{
  if(S.view==='studio')drawStudio(dt,now);
  if(S.view==='arrange'){if(PREF.dock==='inst')drawScope();const ml=micOpen()?micLevel():0;$$('#arrHeadsInner .meter-h i').forEach((el,i)=>{const t=S.tracks[i];if(t)el.style.width=clamp(Math.sqrt(Math.max(trackLevel(t),isAudio(t)&&t.arm?ml:0))*100,0,100)+'%'});const am=$('#auMeter');if(am&&!$('#dockAudio').hidden){am.style.width=clamp(Math.sqrt(ml)*100,0,100)+'%';am.classList.toggle('hot',ml>0.89)}rollPlayhead();
    const ph=$('#tlHead'),p=posAtNow();if(ph){if(p!=null){ph.hidden=false;ph.style.transform='translateX('+(p/16*barW())+'px)'}else ph.hidden=true}}
  if(S.view==='mix')drawMeters(now);if(S.view==='lab'||S.view==='arrange')animateModRings();
  const p=posAtNow();if(p!=null){$('#lcdPos').textContent=(Math.floor(p/16)+1)+'.'+(Math.floor(p%16/4)+1)+'.'+(Math.floor(p%4)+1);$('#beatDot').classList.toggle('beat',p%4<0.6)}else $('#beatDot').classList.remove('beat');
  $('#clipLed').classList.toggle('clip',now-A.clipAt<1500);$('#midiLed').classList.toggle('hit',now-lastMidi<90);$$('[data-dev]').forEach(l=>l.classList.toggle('hit',now-(devHits[l.dataset.dev]||0)<90));
  if(now-statT>500){statT=now;const v=liveVoices();$('#statVoices').textContent=v+' voice'+(v===1?'':'s');if(window.native&&window.native.cpu)window.native.cpu().then(c=>{$('#statCpu').textContent=c!=null?'CPU '+c.toFixed(0)+'%':''}).catch(()=>{});
    else if(performance.memory)$('#statCpu').textContent=Math.round(performance.memory.usedJSHeapSize/1048576)+' MB'}
  }catch(err){if(!frame.errd){frame.errd=1;errLog.push('frame: '+err.message);console.error(err)}}}
let rz=0;window.addEventListener('resize',()=>{clearTimeout(rz);rz=setTimeout(()=>{renderAll()},120)});
window.addEventListener('pagehide',()=>{saveNow();store(SESSION_KEY,'0')});document.addEventListener('visibilitychange',()=>{if(document.hidden)saveNow()});
/* boot */
(function boot(){
  loadUser();
  try{LIB=JSON.parse(read(LIB_KEY)||'{}')||{}}catch(e){LIB={};errLog.push('library unreadable: '+e.message)}
  const imported=migrateV1Library();curId=read(CUR_KEY);
  if(!Object.keys(LIB).length){curId=newEntry('Midnight Rain',demoProject());LIB[curId].color='#e8a0bb'}
  if(!LIB[curId])curId=Object.values(LIB).sort((a,b)=>b.edited-a.edited)[0].id;
  const crashed=read(SESSION_KEY)==='1';store(SESSION_KEY,'1');
  try{loadProject(LIB[curId].data)}catch(e){errLog.push('open: '+e.message);toast('The last cassette could not be read ('+e.message+'), so a new demo opened. The original is untouched in the library.');curId=newEntry('Midnight Rain',demoProject());loadProject(LIB[curId].data)}
  writeLib();$('#projTitle').value=LIB[curId].title;setSaveState(storageOk?'saved':'err');applyTheme();setView(PREF.view);restoreUi(LIB[curId].ui);showDock();renderAll();sessionBoot();requestAnimationFrame(frame);updateWake();initMIDI();
  if(imported)toast('Imported '+imported+' cassette(s) from the previous Soundcraft version. The originals are untouched.');
  else if(crashed)toast('Soundcraft did not close cleanly last time. Your work is autosaved up to the last change; older versions are in Snapshots → Automatic backups.');
  if(window.native){if(window.native.onQuit)window.native.onQuit(()=>{saveNow();store(SESSION_KEY,'0')})}
  if(window.claude&&window.claude.use&&!window.native){window.claude.use('downloads').then(d=>{S.dl=d||null;renderMidiPop()}).catch(()=>{})}
  if(!PREF.tourDone&&!navigator.webdriver)setTimeout(()=>{tourI=0;tourStep()},900);
})();
