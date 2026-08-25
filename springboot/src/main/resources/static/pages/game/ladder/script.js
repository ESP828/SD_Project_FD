const state = { people: 3, restaurants: 1, total: 0, rungs: [], shares: [], animationId: null };
const $ = (selector) => document.querySelector(selector);
const won = (value) => `${Math.round(value).toLocaleString('ko-KR')}원`;

function renderAmounts() {
  const list = $('#amountList');
  const oldValues = [...list.querySelectorAll('input')].map((input) => input.value);
  list.innerHTML = '';
  for (let i = 0; i < state.restaurants; i++) {
    const row = document.createElement('div');
    row.className = 'amount-row';
    row.innerHTML = `<label for="amount${i}">식당 ${i + 1}</label><div class="amount-input-wrap"><input id="amount${i}" type="text" inputmode="numeric" placeholder="0" value="${oldValues[i] || ''}" aria-label="${i + 1}번째 식당 금액"><span>원</span></div><button class="remove-amount" type="button" aria-label="${i + 1}번째 식당 삭제" ${state.restaurants === 1 ? 'disabled' : ''}>×</button>`;
    row.querySelector('input').addEventListener('input', (event) => {
      const digits = event.target.value.replace(/\D/g, '').slice(0, 10);
      event.target.value = digits ? Number(digits).toLocaleString('ko-KR') : '';
      updateTotal();
    });
    row.querySelector('.remove-amount').addEventListener('click', () => {
      row.remove(); state.restaurants--; renumberAmounts(); updateTotal();
      $('#addRestaurant').disabled = false;
    });
    list.appendChild(row);
  }
  $('#addRestaurant').disabled = state.restaurants >= 5;
  updateTotal();
}

function renumberAmounts() {
  [...document.querySelectorAll('.amount-row')].forEach((row, i) => {
    row.querySelector('label').textContent = `식당 ${i + 1}`;
    row.querySelector('input').setAttribute('aria-label', `${i + 1}번째 식당 금액`);
  });
}

function updateTotal() {
  state.total = [...document.querySelectorAll('.amount-row input')].reduce((sum, input) => sum + Number(input.value.replaceAll(',', '')), 0);
  $('#totalAmount').textContent = won(state.total);
}

function setPeople(change) {
  state.people = Math.min(8, Math.max(2, state.people + change));
  $('#peopleCount').textContent = state.people;
  $('#decreasePeople').disabled = state.people === 2;
  $('#increasePeople').disabled = state.people === 8;
}

function createShares(total, count) {
  const weights = Array.from({ length: count }, () => .6 + Math.random() * .8);
  const weightSum = weights.reduce((a, b) => a + b, 0);
  const unit = total >= count * 100 ? 100 : 1;
  const shares = weights.map((weight) => Math.max(unit, Math.round((total * weight / weightSum) / unit) * unit));
  let diff = total - shares.reduce((a, b) => a + b, 0);
  let cursor = 0;
  while (diff !== 0) {
    const delta = Math.sign(diff) * Math.min(unit, Math.abs(diff));
    if (shares[cursor] + delta >= 0) { shares[cursor] += delta; diff -= delta; }
    cursor = (cursor + 1) % count;
  }
  return shares.sort(() => Math.random() - .5);
}

function generateRungs(count) {
  const rows = Math.max(9, count * 3);
  const rungs = [];
  for (let row = 0; row < rows; row++) {
    const used = new Set();
    for (let col = 0; col < count - 1; col++) {
      if (!used.has(col) && !used.has(col + 1) && Math.random() < .38) {
        rungs.push({ row, col }); used.add(col); used.add(col + 1);
      }
    }
  }
  return { rows, rungs };
}

function setupLadder() {
  const oldNames = [...document.querySelectorAll('#nameInputs input')].map((i) => i.value);
  $('#nameInputs').style.gridTemplateColumns = `repeat(${state.people},1fr)`;
  $('#prizeLabels').style.gridTemplateColumns = `repeat(${state.people},1fr)`;
  $('#nameInputs').innerHTML = Array.from({ length: state.people }, (_, i) => `<input type="text" maxlength="10" value="${oldNames[i] || `참여자 ${i + 1}`}" aria-label="${i + 1}번 참여자 이름">`).join('');
  state.shares = createShares(state.total, state.people);
  $('#prizeLabels').innerHTML = state.shares.map((share) => `<span>${won(share)}</span>`).join('');
  const generated = generateRungs(state.people);
  state.rungs = generated.rungs;
  state.rows = generated.rows;
  drawLadder();
}

function canvasMetrics() {
  const canvas = $('#ladderCanvas');
  const availableWidth = canvas.parentElement.getBoundingClientRect().width;
  const width = Math.max(520, Math.floor(availableWidth));
  const height = 410; const dpr = window.devicePixelRatio || 1;
  // canvas.width/height를 다시 대입하면 매번 캔버스 비트맵을 통째로 새로 만들어서
  // 비용이 큰데, 출발 애니메이션은 requestAnimationFrame으로 초당 60번씩 이 함수를
  // 부르면서 실제로는 크기가 안 바뀌는데도 매 프레임 이 작업을 반복하고 있었다.
  // 그 부담 때문에 애니메이션이 도는 2초 남짓 동안 스크롤이 버벅이거나 안 먹혔다.
  // 실제로 폭이 바뀐 경우에만 다시 만들도록 캐시한다.
  if (canvas.dataset.fitWidth !== String(width) || canvas.dataset.fitDpr !== String(dpr)) {
    canvas.width = width * dpr; canvas.height = height * dpr; canvas.style.width = `${width}px`; canvas.style.height = `${height}px`;
    canvas.dataset.fitWidth = String(width); canvas.dataset.fitDpr = String(dpr);
    canvas.getContext('2d').scale(dpr, dpr);
  }
  const ctx = canvas.getContext('2d');
  const marginX = 44, top = 24, bottom = 342;
  return { canvas, ctx, width, height, marginX, top, bottom, gap:(width - marginX * 2) / (state.people - 1), rowGap:(bottom - top) / (state.rows + 1) };
}

function drawLadder(paths = [], showMarkers = false) {
  const m = canvasMetrics();
  // canvas.width를 다시 대입하면 캔버스가 자동으로 지워지는데, 크기가 안 바뀌었을 땐
  // 그 대입 자체를 건너뛰도록 최적화해서(canvasMetrics 참고) 이 자동 지우기도 같이
  // 없어졌다. 그래서 다시하기 후 새 사다리를 그리면 이전 판의 색깔 경로가 안 지워지고
  // 그대로 남아있었다 — 매번 그리기 전에 명시적으로 지운다.
  m.ctx.clearRect(0, 0, m.canvas.width, m.canvas.height);
  m.ctx.lineCap = 'round'; m.ctx.lineWidth = 4; m.ctx.strokeStyle = '#dfd3c6';
  for (let i = 0; i < state.people; i++) { m.ctx.beginPath(); m.ctx.moveTo(m.marginX + i*m.gap,m.top); m.ctx.lineTo(m.marginX + i*m.gap,m.bottom); m.ctx.stroke(); }
  state.rungs.forEach(({row,col}) => { const y=m.top+(row+1)*m.rowGap; m.ctx.beginPath(); m.ctx.moveTo(m.marginX+col*m.gap,y); m.ctx.lineTo(m.marginX+(col+1)*m.gap,y); m.ctx.stroke(); });
  const colors=['#ff5d52','#3b9b78','#6d69e8','#ff9d3d','#2c8fbd','#d05aa2','#77a633','#8b6548'];
  paths.forEach((path, index) => {
    if (!path.length) return;
    m.ctx.strokeStyle=colors[index%colors.length]; m.ctx.lineWidth=5; m.ctx.beginPath();
    path.forEach((p,i)=>i?m.ctx.lineTo(p.x,p.y):m.ctx.moveTo(p.x,p.y)); m.ctx.stroke();
    if(showMarkers) {
      const point=path[path.length-1];
      m.ctx.fillStyle='#fff'; m.ctx.beginPath(); m.ctx.arc(point.x,point.y,7,0,Math.PI*2); m.ctx.fill();
      m.ctx.fillStyle=colors[index%colors.length]; m.ctx.beginPath(); m.ctx.arc(point.x,point.y,4.5,0,Math.PI*2); m.ctx.fill();
    }
  });
  // 금액을 별도 그리드가 아닌 각 도착선의 정확한 중심에 연결한다.
  m.ctx.textAlign='center'; m.ctx.textBaseline='middle'; m.ctx.font='800 12px Pretendard, sans-serif';
  state.shares.forEach((share,index)=>{
    const x=m.marginX+index*m.gap; const label=won(share); const labelY=m.bottom+42;
    m.ctx.strokeStyle='#e4d8cb'; m.ctx.lineWidth=2; m.ctx.beginPath(); m.ctx.moveTo(x,m.bottom); m.ctx.lineTo(x,m.bottom+15); m.ctx.stroke();
    const chipWidth=Math.min(m.gap-8,Math.max(54,m.ctx.measureText(label).width+16));
    m.ctx.fillStyle='#fff1e8'; m.ctx.beginPath(); m.ctx.roundRect(x-chipWidth/2,labelY-15,chipWidth,30,10); m.ctx.fill();
    m.ctx.fillStyle='#e65443'; m.ctx.fillText(label,x,labelY);
  });
  return m;
}

function getPartialPath(points, progress) {
  if(progress<=0) return [points[0]];
  if(progress>=1) return points;
  const lengths=[]; let totalLength=0;
  for(let i=1;i<points.length;i++) { const length=Math.hypot(points[i].x-points[i-1].x,points[i].y-points[i-1].y); lengths.push(length); totalLength+=length; }
  let remaining=totalLength*progress; const partial=[points[0]];
  for(let i=0;i<lengths.length;i++) {
    if(remaining>=lengths[i]) { partial.push(points[i+1]); remaining-=lengths[i]; continue; }
    const ratio=lengths[i] ? remaining/lengths[i] : 0;
    partial.push({x:points[i].x+(points[i+1].x-points[i].x)*ratio,y:points[i].y+(points[i+1].y-points[i].y)*ratio});
    break;
  }
  return partial;
}

function calculatePath(start, m) {
  let col=start; const points=[{x:m.marginX+col*m.gap,y:m.top}];
  for(let row=0;row<state.rows;row++) {
    const y=m.top+(row+1)*m.rowGap; points.push({x:m.marginX+col*m.gap,y});
    const right=state.rungs.some(r=>r.row===row&&r.col===col);
    const left=state.rungs.some(r=>r.row===row&&r.col===col-1);
    if(right){col++;points.push({x:m.marginX+col*m.gap,y});} else if(left){col--;points.push({x:m.marginX+col*m.gap,y});}
  }
  points.push({x:m.marginX+col*m.gap,y:m.bottom}); return {points,end:col};
}

function startGame() {
  const inputs=[...document.querySelectorAll('#nameInputs input')];
  if(inputs.some(input=>!input.value.trim())) { $('#ladderMessage').textContent='모든 참여자의 이름을 입력해 주세요.'; return; }
  $('#ladderMessage').textContent=''; $('#startGame').disabled=true; $('#shuffleLadder').disabled=true;
  const m=drawLadder(); const outcomes=inputs.map((input,i)=>({name:input.value.trim(),...calculatePath(i,m)}));
  const startedAt=performance.now(); const pathDuration=1900; const stagger=120;
  state.isAnimating=true;
  const animate=(now)=>{
    const elapsed=now-startedAt;
    const paths=outcomes.map((outcome,index)=>getPartialPath(outcome.points,Math.max(0,Math.min(1,(elapsed-index*stagger)/pathDuration))));
    drawLadder(paths,true);
    if(elapsed<pathDuration+(outcomes.length-1)*stagger) {
      state.animationId=requestAnimationFrame(animate);
    } else {
      state.isAnimating=false; drawLadder(outcomes.map(outcome=>outcome.points));
      showResults(outcomes); $('#startGame').disabled=false; $('#shuffleLadder').disabled=false;
    }
  };
  state.animationId=requestAnimationFrame(animate);
}

function shuffleLadder() {
  if(state.isAnimating) return;
  const generated=generateRungs(state.people);
  state.rungs=generated.rungs; state.rows=generated.rows;
  $('#resultSection').classList.add('hidden');
  $('#ladderMessage').textContent='새로운 사다리로 섞었어요!';
  const canvas=$('#ladderCanvas');
  canvas.classList.remove('ladder-shuffling');
  void canvas.offsetWidth;
  canvas.classList.add('ladder-shuffling');
  drawLadder();
  setTimeout(()=>{
    canvas.classList.remove('ladder-shuffling');
    if($('#ladderMessage').textContent==='새로운 사다리로 섞었어요!') $('#ladderMessage').textContent='';
  },700);
}

function showResults(outcomes) {
  $('#resultList').innerHTML=outcomes.map((item,i)=>`<div class="result-item" style="animation-delay:${i*.06}s"><span><span class="rank">${i+1}</span>${escapeHtml(item.name)}</span><strong>${won(state.shares[item.end])}</strong></div>`).join('');
  $('#resultTotal').textContent=won(state.total); $('#resultSection').classList.remove('hidden');
  $('#resultSection').scrollIntoView({behavior:'smooth',block:'start'});
}

function escapeHtml(text) { const div=document.createElement('div'); div.textContent=text; return div.innerHTML; }

$('#decreasePeople').addEventListener('click',()=>setPeople(-1));
$('#increasePeople').addEventListener('click',()=>setPeople(1));
$('#addRestaurant').addEventListener('click',()=>{if(state.restaurants<5){state.restaurants++;renderAmounts();}});
$('#makeLadder').addEventListener('click',()=>{
  updateTotal();
  if(state.total<=0){
    $('#setupMessage').textContent='한 곳 이상의 결제 금액을 입력해 주세요.';
    return;
  }
  $('#setupMessage').textContent='';
  $('#ladderSection').classList.remove('hidden');
  $('#resultSection').classList.add('hidden');
  // 숨겨진 요소는 실제 너비가 0이므로, 먼저 화면에 표시한 뒤 사다리를 그린다.
  requestAnimationFrame(()=>{
    setupLadder();
    $('#ladderSection').scrollIntoView({behavior:'smooth'});
  });
});
$('#startGame').addEventListener('click',startGame);
$('#shuffleLadder').addEventListener('click',shuffleLadder);
$('#restartGame').addEventListener('click',()=>{cancelAnimationFrame(state.animationId);state.isAnimating=false;$('#ladderSection').classList.add('hidden');$('#resultSection').classList.add('hidden');window.scrollTo({top:0,behavior:'smooth'});});
const ladderResizeObserver = new ResizeObserver(()=>{
  if(!$('#ladderSection').classList.contains('hidden') && state.rows && !state.isAnimating) drawLadder();
});
ladderResizeObserver.observe($('.ladder-wrap'));
setPeople(0); renderAmounts();
