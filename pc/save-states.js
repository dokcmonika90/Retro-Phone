(function(){
  function send(action,slot){
    const f=document.querySelector('#screen iframe');
    if(!f){alert('Start a game first.');return;}
    f.contentWindow.postMessage({rp:'state',action,slot},'*');
  }
  function add(){
    const a=document.querySelector('.actions');
    if(!a||document.getElementById('saveStates'))return;
    const w=document.createElement('div');w.id='saveStates';w.style='width:100%;display:flex;gap:8px;justify-content:center;flex-wrap:wrap;margin-top:6px';
    w.innerHTML='<select id="stateSlot" class="secondary"><option value="1">Slot 1</option><option value="2">Slot 2</option><option value="3">Slot 3</option><option value="4">Slot 4</option><option value="5">Slot 5</option></select><button id="saveState" class="secondary">💾 Save State</button><button id="loadState" class="secondary">📂 Load State</button>';
    a.parentNode.insertBefore(w,a.nextSibling);
    const slot=()=>Number(document.getElementById('stateSlot').value);
    document.getElementById('saveState').onclick=()=>send('save',slot());
    document.getElementById('loadState').onclick=()=>send('load',slot());
  }
  add();document.addEventListener('DOMContentLoaded',add);
  window.addEventListener('message',e=>{if(e.data?.rp==='stateResult'){const x=document.getElementById('romInfo');if(x)x.innerHTML='<span class="ok">'+e.data.message+'</span>'}});
})();
