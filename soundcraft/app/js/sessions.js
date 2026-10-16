'use strict';
/* Sessions: continue where you left off.
   - History: every project keeps its undo/redo history on this device (IndexedDB), so it survives restarts.
     The History panel lists recent sessions and every change in the current one, and jumps to any point.
   - .soundcraft files: one file holds a whole session (project, recordings and imported samples, snapshots,
     undo history and where you were working). Double-clicking one opens Soundcraft at that session. */
const SESSION_EXT='.soundcraft';
/* ───────── per-project history on this device ───────── */
let histDbP=null;
function histDb(){if(histDbP)return histDbP;histDbP=new Promise((res,rej)=>{if(!window.indexedDB){rej(new Error('IndexedDB is not available'));return}
  const r=indexedDB.open('soundcraft-history',1);r.onupgradeneeded=()=>r.result.createObjectStore('h',{keyPath:'id'});r.onsuccess=()=>res(r.result);r.onerror=()=>rej(r.error)});return histDbP}
async function histReq(mode,fn){const db=await histDb();return new Promise((res,rej)=>{const tx=db.transaction('h',mode),r=fn(tx.objectStore('h'));tx.oncomplete=()=>res(r&&r.result);tx.onerror=()=>rej(tx.error)})}
let histTimer=0,histLoadingFor=null;
function historyChanged(){clearTimeout(histTimer);histTimer=setTimeout(persistHistory,1200);if(!$('#histModal').hidden)renderHistory()}
function persistHistory(){clearTimeout(histTimer);if(!curId||histLoadingFor===curId)return Promise.resolve();const id=curId,u=undoStack.slice(),r=redoStack.slice();
  return histReq('readwrite',st=>st.put({id,undo:u,redo:r,saved:Date.now()})).catch(e=>errLog.push('history: '+e.message))}
function loadHistory(id){histLoadingFor=id;return histReq('readonly',st=>st.get(id)).then(rec=>{if(curId!==id)return;
    if(rec&&!undoStack.length&&!redoStack.length){undoStack.push(...(rec.undo||[]).filter(validEntry));redoStack.push(...(rec.redo||[]).filter(validEntry))}}).catch(e=>errLog.push('history: '+e.message))
  .finally(()=>{if(histLoadingFor===id)histLoadingFor=null;if(!$('#histModal').hidden)renderHistory()})}
const validEntry=e=>e&&typeof e.s==='string'&&isFinite(e.t);
function dropHistory(id){return histReq('readwrite',st=>st.delete(id)).catch(()=>{})}
/* ───────── where you were working ───────── */
function uiState(){return {view:S.view,sel:S.sel,cursor:S.cursor,dock:PREF.dock,zoom:S.zoom}}
function restoreUi(u){if(!u)return;if(isFinite(u.sel))S.sel=clamp(Math.round(u.sel),0,S.tracks.length-1);if(isFinite(u.cursor))S.cursor=Math.max(0,+u.cursor);if(isFinite(u.zoom))S.zoom=clamp(+u.zoom,10,160);
  if(u.dock==='roll'||u.dock==='inst')PREF.dock=u.dock;if(VIEWS.includes(u.view))setView(u.view);S.octave=(selTrack()&&selTrack().oct)||4;showDock()}
function setWindowTitle(){const e=LIB[curId];document.title=(e?e.title:'Soundcraft')+(e&&e.filePath?' — '+e.filePath.split(/[\\/]/).pop():'')+' — Soundcraft'}
/* ───────── session files ───────── */
async function buildSessionFile(){saveNow();const e=LIB[curId],data=snapshot();e.uid=e.uid||uid('s');
  const ids=new Set();data.tracks.forEach(t=>{(t.zones||[]).forEach(z=>z.sample&&ids.add(z.sample));(t.clips||[]).forEach(c=>c.rec&&ids.add(c.rec))});
  const samples={},deps=[];for(const id of ids){let rec=null;try{rec=await getSample(id)}catch(err){}if(rec){samples[id]={name:rec.name,b64:b64(rec.bytes)};deps.push({id,name:rec.name,embedded:true})}else deps.push({id,name:(SAMPLE_META.get(id)||{}).name||id,embedded:false})}
  // keep history inside the file, newest first, up to about 20 MB of it
  const keep=(list,max)=>{const out=[];let size=0;for(let i=list.length-1;i>=0&&out.length<max;i--){size+=list[i].s.length;if(size>20e6)break;out.unshift(list[i])}return out};
  const env=envelope('project',e.title,data,{type:'project',samples,dependencies:{samples:deps},
    session:{uid:e.uid,saved:Date.now(),created:e.created,color:e.color,ui:uiState(),snapshots:e.snapshots||[],history:{undo:keep(undoStack,60),redo:keep(redoStack,30)}}});
  return JSON.stringify(env)}
async function saveSession(as){const e=LIB[curId];if(!e)return false;let text;try{text=await buildSessionFile()}catch(err){toast('Could not build the session file: '+err.message);return false}
  const name=fileSafe(e.title)+SESSION_EXT;
  if(window.native&&window.native.saveSession){const r=await window.native.saveSession(as?null:e.filePath||null,name,text);if(!r||r.canceled)return false;if(r.error){toast('Could not save the session file: '+r.error+'. Your work is still autosaved in Soundcraft.');return false}
    e.filePath=r.path;e.fileSaved=Date.now();writeLib();addRecentFile(r.path,e.title);setWindowTitle();setSaveState('saved');toast('Saved '+r.path.split(/[\\/]/).pop()+'.');return true}
  const ok=await saveBlob(new Blob([text],{type:'application/x-soundcraft'}),name);if(ok){e.fileSaved=Date.now();writeLib();toast('Saved '+name+'.')}return !!ok}
/* Ctrl+S: the project always autosaves on this device; when it belongs to a session file, the file is written too. */
function quickSave(){const ok=saveNow();const e=LIB[curId];if(e&&e.filePath&&window.native){saveSession(false);return}toast(ok?'Saved on this device. Use Save session as… (Ctrl+Shift+S) to write a .soundcraft file.':'Could not save: storage is blocked or full.')}
function addRecentFile(p,title){PREF.recentFiles=[{path:p,title,t:Date.now()}].concat((PREF.recentFiles||[]).filter(f=>f.path!==p)).slice(0,12);savePrefs()}
async function openSessionDialog(){
  if(window.native&&window.native.openSessionDialog){const r=await window.native.openSessionDialog();if(!r||r.canceled)return;handleOpened(r);return}
  $('#sessFile').click()}
function handleOpened(r){if(r.error){toast('Could not open '+(r.path||'that file')+': '+r.error+'.');return}let j;try{j=JSON.parse(r.text)}catch(err){toast('That file is not a Soundcraft session (it could not be read as one). Nothing was changed.');return}
  openSessionData(j,r.path||null,r.name).catch(err=>toast('That session was not opened: '+err.message+'. Nothing was changed.'))}
async function openSessionData(j,path,fname){
  let data,title=(fname||(path||'').split(/[\\/]/).pop()||'Session').replace(/(\.soundcraft)?(\.json)?$/i,''),ses=null;
  if(j&&j.format===FORMAT){if(j.formatVersion>FORMAT_VERSION)throw new Error('it was made with a newer Soundcraft (format '+j.formatVersion+'); update Soundcraft to open it');if(j.type!=='project')throw new Error('it is a '+(CONTENT_TYPES[j.type]||j.type)+', not a session. Import it in the Sounds workspace');data=j.data;title=j.name||title;ses=j.session||null}
  else if(j&&j.data&&j.data.tracks){data=j.data;title=j.title||title}else data=j;
  migrate(JSON.parse(JSON.stringify(data)));
  saveNow();await persistHistory();
  const uidS=ses&&typeof ses.uid==='string'?ses.uid:null;
  let ent=Object.values(LIB).find(e=>(path&&e.filePath===path)||(uidS&&e.uid===uidS));
  if(ent&&ent.edited>(ses&&ses.saved||0)+2000&&ent.filePath===path&&path){openProject(ent.id);toast('Opened "'+ent.title+'". Your autosaved version here is newer than the file, so it was kept; press Ctrl+S to update the file.');closeHistory();return}
  if(j&&j.samples){const n=await importEmbeddedSamples(j.samples);if(n)errLog.push('session: restored '+n+' embedded sample(s)')}
  let id;if(ent){id=ent.id;ent.data=data;ent.title=String(title).slice(0,60);ent.edited=Date.now()}else{id=newEntry(String(title).slice(0,60),data);ent=LIB[id]}
  if(uidS)ent.uid=uidS;if(path)ent.filePath=path;ent.fileSaved=ses&&ses.saved||Date.now();if(ses){if(Array.isArray(ses.snapshots))ent.snapshots=ses.snapshots.filter(x=>x&&x.data&&x.id).slice(0,30);if(ses.color)ent.color=String(ses.color).slice(0,20);if(ses.ui)ent.ui=ses.ui}
  writeLib();await dropHistory(id);
  openProject(id);
  if(ses&&ses.history){undoStack.length=0;redoStack.length=0;undoStack.push(...(ses.history.undo||[]).filter(validEntry));redoStack.push(...(ses.history.redo||[]).filter(validEntry));histLoadingFor=null;persistHistory()}
  if(path)addRecentFile(path,ent.title);closeHistory();
  toast('Opened session "'+ent.title+'"'+(ses&&ses.history&&ses.history.undo.length?' with '+ses.history.undo.length+' undo steps':'')+'.')}
$('#sessFile').onchange=async e=>{const f=e.target.files[0];e.target.value='';if(!f)return;handleOpened({name:f.name,text:await f.text()})};
/* ───────── History panel ───────── */
const ago=t=>{const s=(Date.now()-t)/1000;return s<60?'just now':s<3600?Math.round(s/60)+' min ago':s<86400?Math.round(s/3600)+' h ago':s<86400*7?Math.round(s/86400)+' days ago':fmtDate(t)};
function openHistory(){saveNow();renderHistory();openModal('histModal')}
function closeHistory(){const m=$('#histModal');if(m)m.hidden=true}
function renderHistory(){const list=Object.values(LIB).sort((a,b)=>b.edited-a.edited).slice(0,12);
  $('#histSessions').innerHTML=list.map(e=>{const d=e.data||{},tr=(d.tracks||[]).length,cur=e.id===curId;return '<li class="'+(cur?'cur':'')+'" style="--tc:'+e.color+'"><i class="tape-dot"></i><div class="grow"><b>'+esc(e.title)+'</b><div class="hint">'+(cur?'Open now · ':'')+'last worked on '+ago(e.edited)+' · '+tr+' tracks · '+(d.bpm||'?')+' BPM'+(e.filePath?' · '+esc(e.filePath.split(/[\\/]/).pop()):'')+'</div></div>'+(cur?'':'<button class="btn xs primary" data-h="cont" data-id="'+e.id+'">Continue</button>')+'</li>'}).join('');
  const rf=(PREF.recentFiles||[]).filter(f=>!Object.values(LIB).some(e=>e.filePath===f.path));
  $('#histFiles').hidden=!window.native||!rf.length;$('#histFileList').innerHTML=rf.map(f=>'<li><div class="grow"><b>'+esc(f.title||f.path.split(/[\\/]/).pop())+'</b><div class="hint">'+esc(f.path)+'</div></div><button class="btn xs" data-h="file" data-p="'+esc(f.path)+'">Open</button></li>').join('');
  const steps=[];const cur=snapshot();let after=cur;
  for(let i=undoStack.length-1;i>=0&&steps.length<80;i--){const e=undoStack[i];let before;try{before=JSON.parse(e.s)}catch(x){continue}steps.push({i,t:e.t,lines:diffProjects(after,before),kind:'done'});after=before}
  const fut=[];let prev=cur;for(let i=redoStack.length-1;i>=0&&fut.length<40;i--){const e=redoStack[i];let st;try{st=JSON.parse(e.s)}catch(x){continue}fut.push({i,t:e.t,lines:diffProjects(st,prev),kind:'undone'});prev=st}
  const row=s=>'<li class="'+s.kind+'"><span class="ht">'+new Date(s.t).toLocaleTimeString(undefined,{hour:'numeric',minute:'2-digit'})+'<small>'+(new Date(s.t).toDateString()===new Date().toDateString()?'':fmtDate(s.t))+'</small></span><div class="grow">'+s.lines.slice(0,3).map(esc).join('<br>')+(s.lines.length>3?'<br><span class="hint">+'+(s.lines.length-3)+' more</span>':'')+'</div>'+
    (s.kind==='done'?'<button class="btn xs" data-h="back" data-i="'+s.i+'" title="Return to the state before this change. Redo brings it back.">Go back to before this</button>':'<button class="btn xs" data-h="fwd" data-i="'+s.i+'">Redo to here</button>')+'</li>';
  $('#histSteps').innerHTML=(fut.length?fut.slice().reverse().map(row).join('')+'<li class="now-mark"><span>You are here</span></li>':'<li class="now-mark"><span>Now</span></li>')+steps.map(row).join('')||'';
  $('#histEmpty').hidden=!!(steps.length||fut.length);$('#histTitle2').textContent='Changes in "'+(LIB[curId]?LIB[curId].title:'')+'"'}
$('#histModal').addEventListener('click',e=>{const b=e.target.closest('[data-h]');if(!b)return;const a=b.dataset.h;
  if(a==='cont'){saveNow();persistHistory().then(()=>{if(openProject(b.dataset.id)){toast('Continuing "'+LIB[curId].title+'".');renderHistory()}})}
  else if(a==='file'){window.native.readSession(b.dataset.p).then(handleOpened)}
  else if(a==='back'){const i=+b.dataset.i;let n=undoStack.length-i;while(n-->0&&undoRedo(undoStack,redoStack,'undo',true));renderAll();renderHistory()}
  else if(a==='fwd'){const i=+b.dataset.i;let n=redoStack.length-i;while(n-->0&&undoRedo(redoStack,undoStack,'redo',true));renderAll();renderHistory()}});
$('#btnHistory').onclick=openHistory;$('#histOpen').onclick=openSessionDialog;$('#histSave').onclick=()=>saveSession(true);
$('#histClear').onclick=e=>{const b=e.currentTarget;if(!b.classList.contains('confirm')){b.classList.add('confirm','danger');b.textContent='Clear the undo history of this project?';return}b.classList.remove('confirm','danger');b.textContent='Clear history';undoStack.length=0;redoStack.length=0;persistHistory();renderHistory();toast('History cleared. The project itself is unchanged.')};
/* ───────── desktop hooks: double-clicked files, File menu ───────── */
function sessionBoot(){
  if(window.native&&window.native.rendererReady){window.native.onOpenSession(handleOpened);
    window.native.onMenu(c=>({open:openSessionDialog,save:quickSave,saveAs:()=>saveSession(true),history:openHistory,settings:openSettings}[c]||(()=>{}))());
    window.native.rendererReady().then(list=>{(list||[]).forEach(handleOpened);if(!(list||[]).length&&PREF.startup==='pick')openHistory()}).catch(e=>errLog.push('pending open: '+e.message))}
  else if(PREF.startup==='pick')setTimeout(openHistory,300);
  loadHistory(curId);setWindowTitle()}
