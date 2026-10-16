'use strict';
/* Parameter registry. Every engine, effect, track control and macro destination is described by one
   of these records, and every UI control, modulation route, macro mapping, preset and randomizer
   reads the same records. `live` marks parameters backed by a real AudioParam that can be
   modulated continuously; everything else is applied when a note starts. */
function P(k,label,group,min,max,def,o={}){return Object.assign({k,label,group,min,max,def,type:'num',log:false,int:false,step:0,fmt:FMT.dec},o)}
function C(k,label,group,choices,def,o={}){return Object.assign({k,label,group,type:'choice',choices,def,min:0,max:choices.length-1},o)}
function B(k,label,group,def,o={}){return Object.assign({k,label,group,type:'bool',def,min:0,max:1},o)}
function toNorm(p,v){
  if(p.type==='choice'){const i=p.choices.findIndex(c=>c[0]===v);return p.choices.length>1?Math.max(0,i)/(p.choices.length-1):0}
  if(p.type==='bool')return v?1:0;
  if(p.log)return clamp(Math.log(v/p.min)/Math.log(p.max/p.min),0,1);
  return clamp((v-p.min)/(p.max-p.min),0,1);
}
function fromNorm(p,n){
  n=clamp(n,0,1);
  if(p.type==='choice')return p.choices[Math.round(n*(p.choices.length-1))][0];
  if(p.type==='bool')return n>=0.5;
  let v=p.log?p.min*Math.pow(p.max/p.min,n):p.min+(p.max-p.min)*n;
  if(p.int)v=Math.round(v);else if(p.step)v=Math.round(v/p.step)*p.step;
  return clamp(v,p.min,p.max);
}
function fmtParam(p,v){if(p.type==='choice'){const c=p.choices.find(c=>c[0]===v);return c?c[1]:String(v)}if(p.type==='bool')return v?'On':'Off';return p.fmt(v)}
function defaultsOf(params){const o={};params.forEach(p=>o[p.k]=Array.isArray(p.def)?p.def.slice():p.def);return o}
/* Clamp an incoming value set against the registry; unknown keys are reported, never executed. */
function sanitizeParams(params,obj){
  const o={},bad=[];obj=obj||{};
  params.forEach(p=>{let v=obj[p.k];
    if(v===undefined){o[p.k]=p.def;return}
    if(p.type==='choice'){if(p.open&&typeof v==='string'&&v.length<120){/* references to other content are kept even if not installed, so they can be reported */}else if(!p.choices.some(c=>c[0]===v)){bad.push(p.k);v=p.def}}
    else if(p.type==='bool')v=!!v;
    else{v=+v;if(!isFinite(v)){bad.push(p.k);v=p.def}v=clamp(v,p.min,p.max);if(p.int)v=Math.round(v)}
    o[p.k]=v});
  const unknown=Object.keys(obj).filter(k=>!params.some(p=>p.k===k));
  return {o,bad,unknown};
}
const CURVES={lin:x=>x,exp:x=>x*x,log:x=>Math.sqrt(x),s:x=>x*x*(3-2*x)};
const CURVE_NAMES=[['lin','Linear'],['exp','Exponential'],['log','Logarithmic'],['s','S-curve']];
const shapeCurve=(c,x)=>{const f=CURVES[c]||CURVES.lin;return x<0?-f(-x):f(x)};
const SYNC_DIVS=[['free','Free (Hz)'],['4/1','4 bars'],['2/1','2 bars'],['1/1','1 bar'],['1/2','1/2'],['1/4','1/4'],['1/4t','1/4 T'],['1/8','1/8'],['1/8t','1/8 T'],['1/8d','1/8 D'],['1/16','1/16'],['1/16t','1/16 T'],['1/32','1/32']];
function divBeats(d){const m=/^(\d+)\/(\d+)([td]?)$/.exec(d);if(!m)return 1;let b=4*(+m[1])/(+m[2]);if(m[3]==='t')b*=2/3;if(m[3]==='d')b*=1.5;return b}
const divHz=d=>S.bpm/60/divBeats(d),divSec=d=>60/S.bpm*divBeats(d);
const WAVES=[['sawtooth','Saw'],['square','Square'],['triangle','Triangle'],['sine','Sine']];
/* Track-level parameters every track exposes (mixer + sends). */
const TRACK_PARAMS=[
  P('vol','Volume','Track',0,1,0.8,{fmt:FMT.pct,live:'trk'}),
  P('pan','Pan','Track',-1,1,0,{fmt:FMT.pan,live:'trk'}),
  P('rev','Reverb send','Track',0,1,0.2,{fmt:FMT.pct,live:'trk'}),
  P('dly','Delay send','Track',0,1,0.1,{fmt:FMT.pct,live:'trk'})
];
/* Per-voice destinations every instrument gets for free (created only when a route uses them). */
const VOICE_PARAMS=[
  P('level','Voice level','Voice',0,1,1,{fmt:FMT.pct,live:'voice'}),
  P('vpan','Voice pan','Voice',-1,1,0,{fmt:FMT.pan,live:'voice'}),
  P('vcut','Voice filter','Voice',40,20000,20000,{log:true,fmt:FMT.hz,live:'voice'}),
  P('vpitch','Voice pitch','Voice',-12,12,0,{fmt:FMT.st,live:'voice'})
];
