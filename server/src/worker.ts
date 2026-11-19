import { randomUUID } from "node:crypto";
import { db, event } from "./db.js";
import { askProvider, askOpenAIWithTools, providerStatus, type ProviderName } from "./providers.js";

const MAX_STEPS = Number(process.env.MAX_STEPS_PER_TASK || 50);
const MAX_MINUTES = Number(process.env.MAX_TASK_MINUTES || 180);
const sleep = (ms:number) => new Promise(r => setTimeout(r, ms));

type Checkpoint = { notes: string; transcript: string[] };
function readCheckpoint(raw:string|null):Checkpoint {
  if (!raw) return {notes:"",transcript:[]};
  try { const x=JSON.parse(raw); return {notes:String(x.notes||""),transcript:Array.isArray(x.transcript)?x.transcript:[]}; }
  catch { return {notes:raw,transcript:[]}; }
}
function saveCheckpoint(id:string,step:number,cp:Checkpoint) {
  db.prepare("UPDATE tasks SET step=?,result=?,updated_at=? WHERE id=?")
    .run(step,JSON.stringify(cp),new Date().toISOString(),id);
}
export function createTask(instruction:string) {
  const id=randomUUID(), now=new Date().toISOString();
  db.prepare("INSERT INTO tasks(id,instruction,status,created_at,updated_at) VALUES(?,?,?,?,?)")
    .run(id,instruction,"queued",now,now);
  event(id,"queued","ok","Görev kuyruğa alındı; otomatik devam motoru başlatıldı");
  return id;
}
export function getTask(id:string) {
  const task=db.prepare("SELECT * FROM tasks WHERE id=?").get(id) as any;
  if(!task) return null;
  const cp=readCheckpoint(task.result);
  return {...task,result:cp.transcript.join("\n\n"),workingNotes:cp.notes,
    events:db.prepare("SELECT * FROM events WHERE task_id=? ORDER BY id").all(id)};
}
export function setTaskControl(id:string, action:"pause"|"resume"|"cancel") {
  const row=db.prepare("SELECT id,status FROM tasks WHERE id=?").get(id) as any;
  if(!row) return false;
  if(action==="cancel") db.prepare("UPDATE tasks SET status='cancelled',paused=0,updated_at=? WHERE id=?").run(new Date().toISOString(),id);
  else db.prepare("UPDATE tasks SET paused=?,status=?,updated_at=? WHERE id=?")
    .run(action==="pause"?1:0,action==="pause"?"paused":"queued",new Date().toISOString(),id);
  event(id,action,"ok",`Görev ${action==="pause"?"duraklatıldı":action==="resume"?"devam kuyruğuna alındı":"iptal edildi"}`);
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
      const cp=readCheckpoint(task.result);
      db.prepare("UPDATE tasks SET status='running',updated_at=? WHERE id=?").run(new Date().toISOString(),task.id);
      event(task.id,"orchestrator","running","GPT otomatik devam döngüsünü devraldı; Gemini denetçi olarak eşlik ediyor");
      try {
        const configured=providerStatus().filter(x=>x.configured).map(x=>x.provider as ProviderName);
        const missing=(["openai","gemini"] as ProviderName[]).filter(p=>!configured.includes(p));
        if(missing.length) throw new Error(`Eksik API anahtarı: ${missing.join(", ")}`);
        let terminal=false;
        while(!terminal && step<MAX_STEPS) {
          let state=db.prepare("SELECT status,paused FROM tasks WHERE id=?").get(task.id) as any;
          while(state?.paused && state.status==="paused") { await sleep(1000); state=db.prepare("SELECT status,paused FROM tasks WHERE id=?").get(task.id) as any; }
          if(!state || ["cancelled","completed","blocked","failed"].includes(state.status)) { terminal=true; break; }
          if((Date.now()-started)/60000>MAX_MINUTES) throw new Error("Güvenli çalışma süresi sınırına ulaşıldı; kayıt korundu.");
          step++;
          event(task.id,"iteration","running",`GPT'ye devam talimatı gönderiliyor • tur ${step}/${MAX_STEPS}`);
          const prompt=`KULLANICININ DEĞİŞMEZ ANA EMRİ:\n${task.instruction}\n\nÖNCEKİ ÇALIŞMA NOTLARI:\n${cp.notes||"(İlk tur)"}\n\nÖNCEKİ TUR KAYITLARININ SON BÖLÜMÜ:\n${cp.transcript.slice(-6).join("\n\n").slice(-18000)||"(Yok)"}\n\nŞİMDİ DEVAM ET. Önceki cevabınla yetinme. Kalan işi somut biçimde ilerlet, kendi çıktını kontrol et, eksikleri düzelt. Bu otomatik döngüde kullanıcıdan "devam et" bekleme; görev tamamlanmadıysa bir sonraki turda devam edilmek üzere açık ve uygulanabilir not bırak. Yapmadığın dış işlemi yapılmış gibi söyleme. Harici araç/erişim gerektiren işte bunu açıkça belirt.\n\nYanıtının sonunda tam olarak bir durum etiketi ver:\nTASK_STATUS: CONTINUE (iş sürüyorsa)\nTASK_STATUS: COMPLETE (bütün istekler gerçekten bitip kontrol edildiyse)\nTASK_STATUS: BLOCKED (izin/erişim/insan kararı olmadan ilerlenemiyorsa).`;
          const gptText=await askOpenAIWithTools("Sen ChatGPT rolündeki ana yürütücüsün. Kullanıcı emrini gerçekten yerine getir. GitHub deposunda dosya okuma/yazma ve Actions iş akışı başlatma araçların var. Gerekiyorsa bu araçları çağır; yalnızca plan yazıp durma. Sadece yapılandırılmış APEX deposunda ve izin verilen proje dosyalarında işlem yap. Her değişiklikten sonra sonucu kontrol et. Yapmadığın işlemi yapılmış gibi söyleme.",prompt);
          const gpt={provider:"openai" as const,text:gptText};
          cp.transcript.push(`TUR ${step} • GPT\n${gpt.text}`);
          event(task.id,"gpt","ok",`GPT tur ${step} yanıtını verdi`,"openai");
          const gem=await askProvider("gemini","Sen bağımsız kalite denetçisisin. GPT'nin yaptığı işi denetle; eksiklerini ve doğrulama gereksinimlerini belirt. Kullanıcı emrinin tamamlanıp tamamlanmadığını değerlendir.",`ANA EMİR:\n${task.instruction}\n\nGPT TUR ${step} ÇIKTISI:\n${gpt.text}\n\nÖNCEKİ NOTLAR:\n${cp.notes}\n\nKısa denetim yaz: somut eksikler, doğrulama ve devam için sonraki adım. Gerçekten bitmediyse tamamlandı deme.`);
          cp.transcript.push(`TUR ${step} • GEMINI DENETİMİ\n${gem.text}`);
          event(task.id,"gemini","ok",`Gemini tur ${step} denetimini tamamladı`,"gemini");
          // Optional gateways are independent reviewer lanes. They run concurrently and
          // never block the core GPT/Gemini workflow when a gateway is offline.
          const gatewayProviders=configured.filter(p=>p==="omniroute" || p==="freellmapi" || p==="9router");
          const gatewaySettled=await Promise.allSettled(gatewayProviders.map(provider =>
            askProvider(provider,"Sen bağımsız uzman denetçi ajanısın. Ana emri ve mevcut çıktıyı incele; hataları, eksikleri ve doğrulanabilir sonraki adımları bildir. Emin olmadığın şeyi gerçekmiş gibi sunma.",
              `ANA EMİR:\n${task.instruction}\n\nGPT ÇIKTISI:\n${gpt.text}\n\nGEMINI DENETİMİ:\n${gem.text}\n\nÖNCEKİ NOTLAR:\n${cp.notes}\n\nBu tur için bağımsız teknik/stratejik denetim yap.`)
          ));
          const gatewayAudits:string[]=[];
          gatewaySettled.forEach((result,index)=>{
            const provider=gatewayProviders[index];
            if(result.status==="fulfilled") {
              gatewayAudits.push(`[${provider}] ${result.value.text}`);
              cp.transcript.push(`TUR ${step} • ${provider.toUpperCase()} AJAN DENETİMİ\n${result.value.text}`);
              event(task.id,"agent_review","ok",`${provider} bağımsız ajan denetimini tamamladı`,provider);
            } else {
              const reason=result.reason instanceof Error?result.reason.message:String(result.reason);
              gatewayAudits.push(`[${provider}] Bu tur erişilemedi: ${reason}`);
              event(task.id,"agent_review","warning",`${provider} erişilemedi; ana görev devam ediyor: ${reason}`,provider);
            }
          });
          const gatewayAuditText=gatewayAudits.join("\\n\\n") || "(Ek ağ geçidi yapılandırılmadı)";
          const review=await askProvider("openai","Sen son karar veren görev yöneticisisin. Gemini denetimini dikkate al. Erken tamamlandı deme. Görev tamamlanmadıysa CONTINUE seç.",`KULLANICI EMRİ:\n${task.instruction}\n\nGPT ÇIKTISI:\n${gpt.text}\n\nGEMINI DENETİMİ:\n${gem.text}\n\nEK BAĞIMSIZ AJAN DENETİMLERİ:\n${gatewayAuditText}\n\nÖnceki çalışma notları:\n${cp.notes}\n\nBir sonraki tur için kısa çalışma notlarını güncelle. Tüm isteklerin gerçekten tamamlanıp kontrol edildiğine dair kanıt yoksa TASK_STATUS: CONTINUE yaz. Dış erişim/izin olmadan ilerlemek mümkün değilse TASK_STATUS: BLOCKED yaz. Yalnızca tüm iş tamam ve doğrulanmışsa TASK_STATUS: COMPLETE yaz. Son satırda durum etiketini yaz.`);
          cp.notes=review.text;
          cp.transcript.push(`TUR ${step} • GPT DEVAM KARARI\n${review.text}`);
          saveCheckpoint(task.id,step,cp);
          if(/TASK_STATUS:\s*BLOCKED/i.test(review.text)) {
            db.prepare("UPDATE tasks SET status='blocked',error=?,updated_at=? WHERE id=?").run("İnsan onayı veya harici erişim gerekiyor. Ayrıntı görev kaydında.",new Date().toISOString(),task.id);
            event(task.id,"blocked","warning","Görev erişim/izin bekliyor"); terminal=true;
          } else if(/TASK_STATUS:\s*COMPLETE/i.test(review.text)) {
            db.prepare("UPDATE tasks SET status='completed',error=NULL,updated_at=? WHERE id=?").run(new Date().toISOString(),task.id);
            event(task.id,"completion","ok","GPT ve Gemini kontrolünden sonra görev tamamlandı olarak işaretlendi"); terminal=true;
          } else {
            event(task.id,"continue","running","GPT'ye otomatik devam turu için yeni talimat gönderilecek");
            await sleep(1200);
          }
        }
        if(!terminal) {
          db.prepare("UPDATE tasks SET status='paused',error=?,updated_at=? WHERE id=?").run(`Otomatik güvenlik sınırı (${MAX_STEPS} tur) doldu. Kayıt korundu; devam ettirilebilir.`,new Date().toISOString(),task.id);
          event(task.id,"checkpoint_limit","warning","Tur sınırında güvenli duraklatma; çalışma notları saklandı");
        }
      } catch(e:any) {
        db.prepare("UPDATE tasks SET status='queued',error=?,updated_at=? WHERE id=?").run(e.message||String(e),new Date().toISOString(),task.id);
        event(task.id,"retry","warning",`Geçici hata; görev kaldığı yerden yeniden denenecek: ${e.message||String(e)}`);
        await sleep(5000);
      }
    }
  } finally { busy=false; }
}
