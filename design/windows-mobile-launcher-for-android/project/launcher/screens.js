/* ============ app list + settings sheet ============ */
window.Screens = (function(){
  const $ = s=>document.querySelector(s);
  const colorVal = id => (window.TILE_COLORS.find(c=>c.id===id)||{v:'#2b78e4'}).v;
  let L;

  /* ---------- app list ---------- */
  function buildAppList(filter){
    const scroll=$('#appsScroll'); scroll.innerHTML='';
    const apps = window.APPS.filter(a=> !filter || a.name.toLowerCase().includes(filter.toLowerCase()));
    let curLetter='';
    apps.forEach(a=>{
      const L0=a.name[0].toLowerCase();
      if(L0!==curLetter){
        curLetter=L0;
        const h=document.createElement('div'); h.className='alpha-head'; h.textContent=L0; h.dataset.jump='1';
        scroll.appendChild(h);
      }
      const row=document.createElement('div'); row.className='app-row'; row.dataset.app=a.id;
      row.innerHTML = `<div class="app-tile" style="--tcol:${L.state.accent}">${window.icon(a.ic)}</div><div class="nm">${a.name}</div>`;
      scroll.appendChild(row);
    });
    if(!apps.length){ scroll.innerHTML='<div style="padding:30px 18px;color:var(--fg-dim)">no apps found</div>'; }
  }

  function buildJump(){
    const j=$('#jumpGrid'); j.innerHTML='';
    const have=new Set(window.APPS.map(a=>a.name[0].toLowerCase()));
    '#abcdefghijklmnopqrstuvwxyz'.split('').forEach(c=>{
      const t=document.createElement('div');
      const has = c!=='#' && have.has(c);
      t.className='jt '+(has?'has':'off'); t.textContent=c; if(has) t.dataset.go=c;
      j.appendChild(t);
    });
  }

  function jumpTo(letter){
    $('#jumpGrid').classList.remove('on');
    const heads=[...document.querySelectorAll('#appsScroll .alpha-head')];
    const h=heads.find(x=>x.textContent===letter);
    if(h){ const sc=$('#appsScroll'); sc.scrollTop = h.offsetTop - 6; }
  }

  /* ---------- settings sheet ---------- */
  function buildSettings(){
    const body=$('#setBody');
    const accents=['#2b78e4','#1452cc','#6b3fd4','#c4287e','#d6262b','#e5641e','#e2a200','#7cb518','#1f9e57','#0f9b9b','#1399c6','#5a6b7b','#9b6a8f','#3a4554'];
    body.innerHTML = `
      <div class="set-group">
        <div class="set-label">theme</div>
        <div class="seg" id="segTheme">
          <div data-theme="dark" class="${L.state.theme==='dark'?'on':''}">dark</div>
          <div data-theme="light" class="${L.state.theme==='light'?'on':''}">light</div>
        </div>
      </div>
      <div class="set-group">
        <div class="set-label">accent colour</div>
        <div class="swatches" id="accentSw">
          ${accents.map(c=>`<i data-acc="${c}" style="background:${c}" class="${L.state.accent===c?'sel':''}"></i>`).join('')}
        </div>
      </div>
      <div class="set-group">
        <div class="toggle-row">transparent tiles
          <span class="tg ${L.state.glass?'on':''}" data-tg="glass"></span></div>
        <div class="toggle-row">blur wallpaper
          <span class="tg ${L.state.blur?'on':''}" data-tg="blur"></span></div>
      </div>
      <div class="set-group">
        <div class="set-label">tile transparency</div>
        <div class="slider"><input type="range" id="transRange" min="0" max="100" value="${Math.round(L.state.transparency*100)}"></div>
      </div>
      <div class="set-group">
        <div class="set-label">wallpaper</div>
        <div class="wallrow" id="wallRow">
          <label class="w add" id="wallAdd">${window.icon('image')}photo
            <input type="file" accept="image/*" id="wallFile" hidden></label>
          ${window.WALLPAPERS.map(w=>`<div class="w ${L.state.wall===w.id&&!L.state.customWall?'sel':''}" data-wall="${w.id}" style="background:${w.css}"></div>`).join('')}
        </div>
      </div>
      <div class="set-group">
        <div class="set-label">layout</div>
        <div class="toggle-row" style="cursor:pointer" id="resetLayout">reset start layout
          <span style="color:var(--fg-dim)">↺</span></div>
      </div>`;
    bindSettings();
  }

  function bindSettings(){
    $('#segTheme').onclick=e=>{ const d=e.target.closest('[data-theme]'); if(!d)return;
      L.state.theme=d.dataset.theme; L.applyChrome(); buildSettings(); L.save(); };
    $('#accentSw').onclick=e=>{ const i=e.target.closest('[data-acc]'); if(!i)return;
      L.state.accent=i.dataset.acc; L.applyChrome(); L.render(); buildAppList($('#searchInput')?$('#searchInput').value:''); buildSettings(); L.save(); };
    $('#setBody').querySelectorAll('[data-tg]').forEach(tg=> tg.onclick=()=>{
      const k=tg.dataset.tg; L.state[k]=!L.state[k]; L.applyChrome(); tg.classList.toggle('on'); L.save(); });
    $('#transRange').oninput=e=>{ L.state.transparency=e.target.value/100; L.applyTransparency(); };
    $('#transRange').onchange=()=>L.save();
    $('#wallRow').onclick=e=>{ const w=e.target.closest('[data-wall]'); if(!w)return;
      L.state.wall=w.dataset.wall; L.state.customWall=null; L.applyChrome(); buildSettings(); L.save(); };
    const wf=$('#wallFile'); if(wf) wf.onchange=e=>{
      const f=e.target.files[0]; if(!f)return;
      const rd=new FileReader(); rd.onload=()=>{ L.state.customWall=rd.result; L.applyChrome(); buildSettings(); L.save(); };
      rd.readAsDataURL(f);
    };
    $('#resetLayout').onclick=()=>{ L.state.tiles=window.DEFAULT_TILES(); L.state.dock=window.DEFAULT_DOCK.slice(); L.render(); L.save(); L.toast('layout reset'); };
  }

  L_open_settings:{} // label placeholder (noop)

  return {
    init(_L){
      L=_L;
      buildAppList(''); buildJump(); buildSettings();

      // search
      const inp=$('#searchInput');
      inp.addEventListener('input',()=>buildAppList(inp.value));

      // app list interactions: tap launch, long-press pin
      const sc=$('#appsScroll');
      let lp=null;
      sc.addEventListener('pointerdown',e=>{
        const head=e.target.closest('.alpha-head');
        if(head){ $('#jumpGrid').classList.add('on'); return; }
        const row=e.target.closest('.app-row'); if(!row) return;
        lp={id:row.dataset.app,x:e.clientX,y:e.clientY,moved:false,
            t:setTimeout(()=>{ if(lp&&!lp.moved){ navigator.vibrate&&navigator.vibrate(8); L.pinApp(lp.id); lp=null; } },450)};
      });
      sc.addEventListener('pointermove',e=>{ if(lp && Math.hypot(e.clientX-lp.x,e.clientY-lp.y)>8){ lp.moved=true; clearTimeout(lp.t);} });
      sc.addEventListener('pointerup',e=>{ if(!lp)return; clearTimeout(lp.t);
        if(!lp.moved){ const row=e.target.closest('.app-row'); if(row) L.launch(row.dataset.app); } lp=null; });

      // jump grid
      $('#jumpGrid').addEventListener('click',e=>{
        const t=e.target.closest('.jt');
        if(!t){ $('#jumpGrid').classList.remove('on'); return; }
        if(t.dataset.go) jumpTo(t.dataset.go);
        else if(t.classList.contains('off')) $('#jumpGrid').classList.remove('on');
      });
    }
  };
})();
