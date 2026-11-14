import { randomUUID } from "node:crypto";
import { db, event } from "./db.js";
import { askProvider, providerStatus, type ProviderName } from "./providers.js";

const MAX_STEPS = Number(process.env.MAX_STEPS_PER_TASK || 20);
const MAX_MINUTES = Number(process.env.MAX_TASK_MINUTES || 90);
const sleep = (ms:number) => new Promise(r => setTimeout(r, ms));

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
  return {...task,events:db.prepare("SELECT * FROM events WHERE task_id=? ORDER BY id").all(id)};
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
      let step=Number(task.step||0);
      let workingNotes="";
      db.prepare("UPDATE tasks SET status='running',updated_at=? WHERE id=?").run(new Date().toISOString(),task.id);
      event(task.id,"orchestrator","running","GPT ve Gemini birlikte görevi devraldı");
      try {
        const configured=providerStatus().filter(x=>x.configured).map(x=>x.provider as ProviderName);
        const missing=(["openai","gemini"] as ProviderName[]).filter(p=>!configured.includes(p));
        if(missing.length) throw new Error(`Eksik API anahtarı: ${missing.join(", ")}`);

        let completed=false;
        while(!completed && step<MAX_STEPS) {
          const current=db.prepare("SELECT status,paused FROM tasks WHERE id=?").get(task.id) as any;
          if(!current || current.status==="cancelled") break;
          while(current?.paused) { await sleep(1200); }
          if((Date.now()-started)/60000 > MAX_MINUTES) throw new Error("Görev zaman sınırını aştı; güvenli durdurma yapıldı.");

          step++;
          event(task.id,"iteration","running",`Otomatik devam turu ${step}/${MAX_STEPS}`);
          const prompt=`Kullanıcının asıl emri: ${task.instruction}

Önceki turlardan kalan çalışma notları:
${workingNotes || "(Henüz yok)"}

Bu görevden vazgeçme ve kullanıcıdan "devam et" bekleme. Şimdi sıradaki somut işi yap/üret. Gerçekten yapamadığın harici işlemleri yapılmış gibi gösterme; gerekli erişim yoksa açık engel yaz. Henüz tamamlanmadıysa kalan işleri ve bir sonraki uygulanabilir adımı belirt.
Görev ancak istenen tüm çıktılar tamamlandığında ve kontrol edildiğinde tamamdır. Son satırda yalnızca şu işaretlerden birini kullan:
TASK_STATUS: CONTINUE
TASK_STATUS: COMPLETE
TASK_STATUS: BLOCKED`;
          const results=await Promise.all([
            askProvider("openai","Sen NEXORA'nın görev yürütücüsüsün. Asıl emri takip et, somut çıktı üret ve önceki işten devam et.",prompt),
            askProvider("gemini","Sen NEXORA'nın bağımsız uygulayıcısı ve denetçisisin. Eksikleri bul, çözüm üret ve görevin tamamlanmasını doğrula.",prompt)
          ]);
          const combined=results.map(r=>`[${r.provider.toUpperCase()}]\n${r.text}`).join("\n\n---\n\n");
          const review=await askProvider("openai",
            "Sen NEXORA görev yöneticisisin. GPT ve Gemini çıktılarını karşılaştır. Görev tamamlanmadıysa CONTINUE de. Harici erişim yoksa BLOCKED de. Tamamlandı iddiasını yalnızca somut çıktı ve doğrulama varsa kabul et.",
            `ASIL EMİR:\n${task.instruction}\n\nBU TURUN ÇIKTILARI:\n${combined}\n\nÖnceki notlar:\n${workingNotes}\n\nBir sonraki tur için kısa, kesin çalışma notları üret. En sonda TASK_STATUS: CONTINUE, TASK_STATUS: COMPLETE veya TASK_STATUS: BLOCKED yaz.`);
          workingNotes=review.text;
          db.prepare("UPDATE tasks SET step=?,result=?,updated_at=? WHERE id=?")
            .run(step,workingNotes,new Date().toISOString(),task.id);
          event(task.id,"checkpoint","ok",`Tur ${step} kaydedildi; otomatik devam kararı işlendi","openai");

          if(/TASK_STATUS:\s*BLOCKED/i.test(review.text)) {
            db.prepare("UPDATE tasks SET status='blocked',error=?,updated_at=? WHERE id=?")
              .run("Dış erişim veya kullanıcı onayı gerekiyor. Ayrıntı görev notlarında.",new Date().toISOString(),task.id);
            event(task.id,"blocked","warning","Görev harici erişim/izin bekliyor");
            completed=true;
          } else if(/TASK_STATUS:\s*COMPLETE/i.test(review.text)) {
            db.prepare("UPDATE tasks SET status='completed',result=?,updated_at=? WHERE id=?")
              .run(review.text,new Date().toISOString(),task.id);
            event(task.id,"completion","ok","İki sağlayıcıyla görev tamamlandı olarak değerlendirildi","openai");
            completed=true;
          } else {
            await sleep(700);
          }
        }
        if(!completed) {
          db.prepare("UPDATE tasks SET status='paused',error=?,updated_at=? WHERE id=?")
            .run("Güvenlik adım sınırına ulaşıldı. Devam ettirmek için görev yeniden başlatılmalı.",new Date().toISOString(),task.id);
          event(task.id,"step_limit","warning","Tur sınırına ulaşıldı; otomatik maliyet koruması devrede");
        }
      } catch(e:any) {
        db.prepare("UPDATE tasks SET status='failed',error=?,updated_at=? WHERE id=?")
          .run(e.message||String(e),new Date().toISOString(),task.id);
        event(task.id,"failed","error",e.message||String(e));
      }
    }
  } finally { busy=false; }
}
