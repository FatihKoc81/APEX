import dotenv from "dotenv";
dotenv.config();

export type ProviderName = "openai" | "gemini" | "grok" | "claude";
export type ProviderReply = { provider: ProviderName; text: string };

const endpoints: Record<ProviderName,string> = {
 openai: "https://api.openai.com/v1/chat/completions",
 gemini: "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
 grok: "https://api.x.ai/v1/chat/completions",
 claude: "https://api.anthropic.com/v1/messages"
};
const keys: Record<ProviderName,string|undefined> = {
 openai: process.env.OPENAI_API_KEY, gemini: process.env.GEMINI_API_KEY,
 grok: process.env.XAI_API_KEY, claude: process.env.ANTHROPIC_API_KEY
};

export function providerStatus() {
 return (Object.keys(keys) as ProviderName[]).map(provider => ({
   provider, configured: Boolean(keys[provider])
 }));
}

// Provider adapters intentionally fail clearly when credentials are absent.
// Implement each vendor's current request/response contract here; keep secrets server-side.
export async function askProvider(provider:ProviderName, system:string, prompt:string):Promise<ProviderReply> {
 const key = keys[provider];
 if (!key) throw new Error(`${provider} API anahtarı yapılandırılmamış`);
 // Claude uses its Messages API; other three expose OpenAI-compatible chat endpoints.
 if (provider === "claude") {
   const r = await fetch(endpoints.claude,{method:"POST",headers:{
     "x-api-key":key,"anthropic-version":"2023-06-01","content-type":"application/json"
   },body:JSON.stringify({model:"claude-3-7-sonnet-latest",max_tokens:2500,system,messages:[{role:"user",content:prompt}]})});
   const j:any = await r.json(); if(!r.ok) throw new Error(j.error?.message || `Claude HTTP ${r.status}`);
   return {provider,text:(j.content||[]).map((x:any)=>x.text||"").join("\\n")};
 }
 const model = provider==="openai" ? "gpt-4.1-mini" : provider==="gemini" ? "gemini-2.5-flash" : "grok-3-mini";
 const r = await fetch(endpoints[provider],{method:"POST",headers:{
   "authorization":`Bearer ${key}`,"content-type":"application/json"
 },body:JSON.stringify({model,temperature:0.2,messages:[{role:"system",content:system},{role:"user",content:prompt}]})});
 const j:any=await r.json(); if(!r.ok) throw new Error(j.error?.message || `${provider} HTTP ${r.status}`);
 return {provider,text:j.choices?.[0]?.message?.content || ""};
}
