import { randomUUID } from "node:crypto";
import { db } from "./db.js";

export type StudioMode = "hourly" | "custom";
export function studioConfig() {
  const checks: Array<[string, boolean]> = [
    ["OpenAI", Boolean(process.env.OPENAI_API_KEY)],
    ["Gemini", Boolean(process.env.GEMINI_API_KEY || process.env.GOOGLE_API_KEY)],
    ["Pexels", Boolean(process.env.PEXELS_API_KEY)],
    ["Creatomate", Boolean(process.env.CREATOMATE_API_KEY && process.env.CREATOMATE_TEMPLATE_ID)],
    ["ElevenLabs", Boolean(process.env.ELEVENLABS_API_KEY)],
    ["TikTok OAuth", Boolean(process.env.TIKTOK_CLIENT_KEY && process.env.TIKTOK_CLIENT_SECRET && process.env.TIKTOK_ACCESS_TOKEN)],
    ["YouTube OAuth", Boolean(process.env.YOUTUBE_CLIENT_ID && process.env.YOUTUBE_CLIENT_SECRET && process.env.YOUTUBE_REFRESH_TOKEN)]
  ];
  return {
    schedule: "HOURLY",
    timezone: process.env.STUDIO_TIMEZONE || "Europe/Istanbul",
    topics: (process.env.STUDIO_TOPICS || "Tarih, teknoloji, genel kültür, otomobil, ilginç bilgiler").split(",").map(x=>x.trim()),
    defaultDurationSeconds: 60,
    autoPublish: true,
    integrations: checks.map(([name,configured])=>({name,configured})),
    readyToRender: checks.slice(0,5).every(x=>x[1]),
    readyToPublish: checks.slice(5).every(x=>x[1])
  };
}
function addEvent(jobId:string,stage:string,status:string,detail:string) {
  db.prepare("INSERT INTO studio_events(job_id,at,stage,status,detail) VALUES(?,?,?,?,?)")
    .run(jobId,new Date().toISOString(),stage,status,detail);
}
export function createStudioJob(input:{mode?:StudioMode;topic?:string;durationSeconds?:number}) {
  const id=randomUUID(), now=new Date().toISOString();
  const mode=input.mode==="custom"?"custom":"hourly";
  const topic=(input.topic||"").trim() || "Tarih, teknoloji, genel kültür, otomobil ve ilginç bilgiler arasından güncel ve ilgi çekici bir konu seç";
  const duration=Math.max(15,Math.min(600,Number(input.durationSeconds)||60));
  db.prepare("INSERT INTO studio_jobs(id,mode,topic,duration_seconds,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?)")
    .run(id,mode,topic,duration,"queued",now,now);
  addEvent(id,"queued","ok",mode==="hourly"?"Saatlik video üretim görevi kuyruğa alındı":"Özel video talimatı kuyruğa alındı");
  return id;
}
export function getStudioJob(id:string) {
  const row=db.prepare("SELECT * FROM studio_jobs WHERE id=?").get(id) as any;
  if(!row)return null;
  const events=db.prepare("SELECT stage,status,detail,at FROM studio_events WHERE job_id=? ORDER BY id").all(id);
  return {...row,events};
}
export function listStudioJobs(limit=30) {
  return db.prepare("SELECT id,mode,topic,duration_seconds,status,title,video_url,publish_status,error,created_at,updated_at FROM studio_jobs ORDER BY created_at DESC LIMIT ?").all(Math.max(1,Math.min(100,limit)));
}
export function studioWebhookAuthorized(secret:string) {
  const expected=process.env.STUDIO_WEBHOOK_SECRET?.trim();
  return Boolean(expected && secret && expected.length===secret.length && Buffer.from(expected).equals(Buffer.from(secret)));
}
export function recordStudioRenderWebhook(body:any) {
  const id=String(body?.metadata?.jobId || body?.metadata?.job_id || "");
  if(!id)return false;
  const status=String(body?.status||"").toLowerCase();
  const url=String(body?.url||"");
  const ok=status==="succeeded" || status==="completed";
  db.prepare("UPDATE studio_jobs SET status=?,video_url=?,error=?,updated_at=? WHERE id=?")
    .run(ok?"rendered":"render_failed",ok?url:null,ok?null:String(body?.error_message||body?.error||"Render başarısız"),new Date().toISOString(),id);
  addEvent(id,"render",ok?"ok":"error",ok?"Video render tamamlandı":"Video render başarısız oldu");
  return true;
}
