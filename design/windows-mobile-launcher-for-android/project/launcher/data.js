/* ---- palette of tile colors (recolorable) ---- */
window.TILE_COLORS = [
  {id:'blue',   v:'#2b78e4'},
  {id:'cobalt', v:'#1452cc'},
  {id:'purple', v:'#6b3fd4'},
  {id:'magenta',v:'#c4287e'},
  {id:'red',    v:'#d6262b'},
  {id:'orange', v:'#e5641e'},
  {id:'amber',  v:'#e2a200'},
  {id:'lime',   v:'#7cb518'},
  {id:'green',  v:'#1f9e57'},
  {id:'teal',   v:'#0f9b9b'},
  {id:'cyan',   v:'#1399c6'},
  {id:'steel',  v:'#5a6b7b'},
  {id:'mauve',  v:'#9b6a8f'},
  {id:'slate',  v:'#3a4554'}
];

/* ---- wallpapers (mesh gradients read as photos for the transparency effect) ---- */
window.WALLPAPERS = [
  {id:'aurora', label:'aurora',  css:'radial-gradient(120% 90% at 15% 10%, #1c6e5a 0%, transparent 55%), radial-gradient(120% 100% at 85% 0%, #2a3b7a 0%, transparent 50%), radial-gradient(140% 120% at 70% 100%, #5b2a6e 0%, transparent 55%), #0c1320'},
  {id:'dusk',   label:'dusk',    css:'radial-gradient(120% 90% at 10% 100%, #b5341f 0%, transparent 55%), radial-gradient(120% 100% at 90% 90%, #d06a1e 0%, transparent 50%), radial-gradient(140% 120% at 60% 0%, #4a2360 0%, transparent 60%), #160d1a'},
  {id:'ocean',  label:'ocean',   css:'radial-gradient(120% 90% at 80% 15%, #1486c4 0%, transparent 55%), radial-gradient(120% 100% at 10% 85%, #0e5f8a 0%, transparent 55%), radial-gradient(140% 120% at 50% 50%, #146b9b 0%, transparent 70%), #06121d'},
  {id:'forest', label:'forest',  css:'radial-gradient(120% 90% at 20% 20%, #2f7d3a 0%, transparent 55%), radial-gradient(120% 100% at 90% 80%, #156b52 0%, transparent 55%), radial-gradient(120% 120% at 60% 50%, #3a5a1f 0%, transparent 70%), #0a140c'},
  {id:'rose',   label:'rose',    css:'radial-gradient(120% 90% at 15% 10%, #c4287e 0%, transparent 55%), radial-gradient(120% 100% at 90% 90%, #7a2c8a 0%, transparent 55%), radial-gradient(120% 120% at 60% 40%, #d0556a 0%, transparent 65%), #1a0d16'},
  {id:'mono',   label:'mono',    css:'radial-gradient(120% 120% at 30% 20%, #2a2a31 0%, transparent 70%), #131318'}
];

/* ---- contacts (for People tile + contacts) — colored avatars w/ initials ---- */
window.CONTACTS = [
  {n:'Aria Cole',     c:'#c4287e'}, {n:'Ben Ito',      c:'#1452cc'},
  {n:'Cara Voss',     c:'#1f9e57'}, {n:'Dev Rao',      c:'#e5641e'},
  {n:'Eli Frost',     c:'#6b3fd4'}, {n:'Fay Ng',       c:'#0f9b9b'},
  {n:'Gus Park',      c:'#d6262b'}, {n:'Halle Kim',    c:'#e2a200'},
  {n:'Ivo Marsh',     c:'#5a6b7b'}, {n:'Jin Abe',      c:'#7cb518'},
  {n:'Kira Lund',     c:'#9b6a8f'}, {n:'Liam Oba',     c:'#1399c6'}
];
window.initials = function(name){ return name.split(' ').map(w=>w[0]).join('').slice(0,2).toUpperCase(); };

/* ---- full app catalogue (A–Z) ---- */
window.APPS = [
  {id:'alarm',    name:'Alarms',        ic:'alarm',    col:'red'},
  {id:'auth',     name:'Authenticator', ic:'app',      col:'slate'},
  {id:'bank',     name:'Bank',          ic:'bank',     col:'green'},
  {id:'browser',  name:'Browser',       ic:'web',      col:'blue'},
  {id:'calc',     name:'Calculator',    ic:'calc',     col:'steel'},
  {id:'calendar', name:'Calendar',      ic:'calendar', col:'magenta', live:'calendar'},
  {id:'camera',   name:'Camera',        ic:'camera',   col:'slate'},
  {id:'cast',     name:'Cast',          ic:'cast',     col:'teal'},
  {id:'clock',    name:'Clock',         ic:'clock',    col:'cobalt',  live:'clock'},
  {id:'cloud',    name:'Cloud Drive',   ic:'cloud',    col:'cyan'},
  {id:'contacts', name:'Contacts',      ic:'contacts', col:'orange'},
  {id:'docs',     name:'Documents',     ic:'doc',      col:'cobalt'},
  {id:'files',    name:'Files',         ic:'files',    col:'amber'},
  {id:'fitness',  name:'Fitness',       ic:'fitness',  col:'lime'},
  {id:'games',    name:'Games',         ic:'game',     col:'purple'},
  {id:'health',   name:'Health',        ic:'health',   col:'red'},
  {id:'mail',     name:'Mail',          ic:'mail',     col:'purple',  live:'mail',     badge:7},
  {id:'maps',     name:'Maps',          ic:'maps',     col:'green'},
  {id:'messages', name:'Messages',      ic:'messages', col:'amber',   live:'messages', badge:3},
  {id:'mic',      name:'Recorder',      ic:'mic',      col:'magenta'},
  {id:'music',    name:'Music',         ic:'music',    col:'orange',  live:'music'},
  {id:'notes',    name:'Notes',         ic:'note',     col:'amber'},
  {id:'pay',      name:'Pay',           ic:'pay',      col:'green'},
  {id:'people',   name:'People',        ic:'people',   col:'teal',    live:'people'},
  {id:'phone',    name:'Phone',         ic:'phone',    col:'green'},
  {id:'photos',   name:'Photos',        ic:'photos',   col:'cyan',    live:'photos'},
  {id:'podcast',  name:'Podcasts',      ic:'podcast',  col:'mauve'},
  {id:'settings', name:'Settings',      ic:'settings', col:'slate'},
  {id:'store',    name:'Store',         ic:'store',    col:'cobalt'},
  {id:'tv',       name:'TV & Video',    ic:'video',    col:'red'},
  {id:'wallet',   name:'Wallet',        ic:'wallet',   col:'steel'},
  {id:'weather',  name:'Weather',       ic:'weather',  col:'cyan',    live:'weather'}
];
window.appById = function(id){ return window.APPS.find(a=>a.id===id); };

/* ---- default Start layout (ordered; dense flow packs them) ---- */
/* sizes: small (1x1) · medium (2x2) · wide (4x2) · large (4x4) */
window.DEFAULT_TILES = function(){
  return [
    {id:'t-clock',    app:'clock',    size:'wide',   color:'cobalt'},
    {id:'t-phone',    app:'phone',    size:'medium', color:'green'},
    {id:'t-camera',   app:'camera',   size:'medium', color:'slate'},
    {id:'t-people',   app:'people',   size:'medium', color:'teal'},
    {id:'t-weather',  app:'weather',  size:'medium', color:'cyan'},
    {id:'t-mail',     app:'mail',     size:'medium', color:'purple'},
    {id:'t-msg',      app:'messages', size:'medium', color:'amber'},
    {id:'t-cal',      app:'calendar', size:'wide',   color:'magenta'},
    {id:'t-photos',   app:'photos',   size:'large',  color:'cyan'},
    {id:'t-music',    app:'music',    size:'wide',   color:'orange'},
    {id:'g-social',   group:true, name:'social', size:'medium', color:'magenta',
       children:['contacts','mail','messages','people']},
    {id:'t-maps',     app:'maps',     size:'small',  color:'green'},
    {id:'t-store',    app:'store',    size:'small',  color:'cobalt'},
    {id:'t-settings', app:'settings', size:'small',  color:'slate'},
    {id:'t-browser',  app:'browser',  size:'small',  color:'blue'},
    {id:'t-notes',    app:'notes',    size:'small',  color:'amber'},
    {id:'t-fitness',  app:'fitness',  size:'small',  color:'lime'},
    {id:'t-bank',     app:'bank',     size:'medium', color:'green'},
    {id:'t-files',    app:'files',    size:'small',  color:'amber'},
    {id:'t-calc',     app:'calc',     size:'small',  color:'steel'}
  ];
};

/* ---- dock (D): fixed bottom row ---- */
window.DEFAULT_DOCK = ['phone','messages','camera','browser'];
