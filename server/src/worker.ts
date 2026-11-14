import { randomUUID } from "node:crypto";
import { db, event } from "./db.js";
import { askProvider, providerStatus, type ProviderName } from "./providers.js";

const MAX_STEPS = Number(process.env.MAX_STEPS_PER_TASK || 24);
const MAX_MINUTES = Number(process.env.MAX_TASK_MINUTES || 90);
const sleep=(ms:number)=>new Promise(r=>setTimeout(r,ms));

export function createTask(instruction:string) {
 const id=randomUUID(), now=new Date().toISOString();
 db.prepare("INSERT INTO tasks(id,instruction,status,created_at,updated_at) VALUES(?,?,?,?,?)")
 .run(id,instruction,"queued",now,now);
 event(id,"queued","ok","Görev kuyruğa alındı");
 return id;
}
export function getTask(id:string) {
 const task=db.prepare("SELECT * FROM tasks WHERE id=?").get(id) as any;
 if(!task) return null;
 const events=db.prepare("SELECT * FROM events WHERE task_id=? ORDER BY id").all(id);
 return {...task,events};
}
export function setTaskControl(id:string, action:"pause"|"resume"|"cancel") {
 const row=db.prepare("SELECT id FROM tasks WHERE id=?").get(id); if(!row) return false;
 if(action==="cancel") db.prepare("UPDATE tasks SET status='cancelled',updated_at=? WHERE id=?").run(new Date().toISOString(),id);
 else db.prepare("UPDATE tasks SET paused=?,status=?,updated_at=? WHERE id=?")
 .run(action==="pause"?1:0,action==="pause"?"paused":"running",new Date().toISOString(),id);
 event(id,action,"ok",`Görev ${action==="pause"?"duraklatıldı":action==="resume"?"sürdürüldü":"iptal edildi"}`);
 return true;
}

let busy=false;
export async function workerLoop() {
 if(busy) return; busy=true;
 try {
  while(true) {
   const task=db.prepare("SELECT * FROM tasks WHERE status IN ('queued','running') AND paused=0 ORDER BY created_at LIMIT 1").get() as any;
   if(!task) { await sleep(1500); continue; }
   const started=Date.now();
   db.prepare("UPDATE tasks SET status='running',updated_at=? WHERE id=?").run(new Date().toISOString(),task.id);
   event(task.id,"orchestrator","running","Görev planlanıyor");
   try {
    const configured=providerStatus().filter(x=>x.configured).map(x=>x.provider as ProviderName);
    const required:ProviderName[]=["openai","gemini","grok","claude"];
    const missing=required.filter(p=>!configured.includes(p));
    if(missing.length>0) throw new Error(`Dört sağlayıcının tamamı zorunlu. Eksik API anahtarları: ${missing.join(", ")}`);
    // Independent first-pass analysis runs concurrently across all configured providers.
    const replies=await Promise.allSettled(configured.map(p=>askProvider(p,
      "You are one specialist in NEXORA, a coordinated multi-agent system. Be concise, factual, state assumptions and blockers.",
      `Kullanıcı görevi:\\n${task.instruction}\\n\\nGörevi tamamlamak için uygulanabilir plan, somut çıktı ve doğrulama ölçütleri üret.`)));
    const good=replies.filter((x):x is PromiseFulfilledResult<any>=>x.status==="fulfilled").map(x=>x.value);
    const bad=replies.filter(x=>x.status==="rejected");
    for(const b of bad) event(task.id,"provider","retryable_error",String((b as PromiseRejectedResult).reason));
    if(good.length===0) throw new Error("Tüm sağlayıcı çağrıları başarısız oldu.");
    event(task.id,"parallel_analysis","ok",`${good.length} sağlayıcı yanıt verdi`);
    const combined=good.map((r:any)=>`[${r.provider}]\\n${r.text}`).join("\\n\\n---\\n\\n");
    const synthesizer:ProviderName=good.some((r:any)=>r.provider==="openai")?"openai":good[0].provider;
    const final=await askProvider(synthesizer,
      "You are NEXORA's lead orchestrator. Synthesize the agents' work. Do not claim actions were performed unless evidenced. State remaining steps and blockers. Return a useful final result.",
      `Original task:\\n${task.instruction}\\n\\nAgent reports:\\n${combined}\\n\\nProduce the best consolidated result. If external execution is required but unavailable, clearly list it as a blocker.`);
    const elapsed=(Date.now()-started)/60000;
    if(elapsed>MAX_MINUTES) throw new Error("Görev zaman sınırını aştı; güvenli şekilde durduruldu.");
    db.prepare("UPDATE tasks SET status='completed',result=?,step=step+1,updated_at=? WHERE id=?")
      .run(final.text,new Date().toISOString(),task.id);
    event(task.id,"completion","ok","Sonuç birleştirildi ve kaydedildi",synthesizer);
   } catch(e:any) {
    db.prepare("UPDATE tasks SET status='failed',error=?,updated_at=? WHERE id=?")
      .run(e.message||String(e),new Date().toISOString(),task.id);
    event(task.id,"failed","error",e.message||String(e));
   }
  }
 } finally { busy=false; }
}
