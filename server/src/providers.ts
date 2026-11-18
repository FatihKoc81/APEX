import dotenv from "dotenv";
import { executeGithubTool } from "./github.js";
dotenv.config();

export type ProviderName = "openrouter" | "gemini";
export type ProviderReply = { provider: ProviderName; text: string };
function firstConfigured(names: string[]) {
  for (const name of names) {
    const value = process.env[name]?.trim();
    if (value) return { key: value, envName: name };
  }
  return {};
}
const openrouterConfig = firstConfigured(["OPENROUTER_API_KEY"]);
const geminiConfig = firstConfigured(["GEMINI_API_KEY", "GOOGLE_API_KEY", "GOOGLE_GENERATIVE_AI_API_KEY"]);
const keys: Record<ProviderName, string | undefined> = {
  openrouter: openrouterConfig.key,
  gemini: geminiConfig.key
};
export function providerStatus() {
  return (Object.keys(keys) as ProviderName[]).map(provider => ({
    provider, configured: Boolean(keys[provider]),
    sourceEnv: provider === "openrouter" ? openrouterConfig.envName ?? null : geminiConfig.envName ?? null
  }));
}
const githubTools = [
 {type:"function",function:{name:"read_file",description:"Read a text file from the configured GitHub repository. Only approved project paths are allowed.",parameters:{type:"object",properties:{path:{type:"string"}},required:["path"],additionalProperties:false}}},
 {type:"function",function:{name:"write_file",description:"Create or update a text file in the configured GitHub repository. Provide the complete new file content. Changes are committed to the configured branch.",parameters:{type:"object",properties:{path:{type:"string"},content:{type:"string"},message:{type:"string"}},required:["path","content","message"],additionalProperties:false}}},
 {type:"function",function:{name:"dispatch_workflow",description:"Dispatch an existing GitHub Actions workflow file on the configured branch.",parameters:{type:"object",properties:{workflow:{type:"string"},inputs:{type:"object"}},required:["workflow"],additionalProperties:false}}}
] as const;

const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
const OPENROUTER_MODEL = process.env.OPENROUTER_MODEL || "openrouter/free";

export async function askOpenRouterWithTools(system: string, prompt: string): Promise<string> {
 const key = keys.openrouter;
 if (!key) throw new Error("OPENROUTER_API_KEY yapılandırılmamış");
 const messages: any[] = [{role:"system",content:system},{role:"user",content:prompt}];
 let finalText = "";
 for (let round=0; round<8; round++) {
   const response = await fetch(OPENROUTER_URL, {method:"POST",headers:{
     authorization:`Bearer ${key}`,"content-type":"application/json",
     "HTTP-Referer":process.env.APP_URL || "https://github.com/FatihKoc81/APEX",
     "X-Title":"APEX"
   },body:JSON.stringify({model:OPENROUTER_MODEL,temperature:0.1,messages,tools:githubTools,tool_choice:"auto"})});
   const data: any = await response.json();
   if (!response.ok) throw new Error(data.error?.message || `OpenRouter HTTP ${response.status}`);
   const message=data.choices?.[0]?.message;
   if (!message) throw new Error("OpenRouter boş yanıt verdi");
   messages.push(message);
   if (message.content) finalText += message.content;
   const calls=message.tool_calls || [];
   if (!calls.length) return finalText || "(OpenRouter metin yanıtı üretmedi)";
   for (const call of calls) {
     let result: string;
     try {
       const args=JSON.parse(call.function.arguments || "{}");
       result=await executeGithubTool(call.function.name,args);
     } catch(e: any) { result=JSON.stringify({success:false,error:e.message || String(e)}); }
     messages.push({role:"tool",tool_call_id:call.id,content:result});
   }
 }
 return finalText + "\nAraç çağrısı tur sınırına ulaşıldı; kalan işlem sonraki görev turunda sürdürülecek.";
}

export async function askProvider(provider: ProviderName, system: string, prompt: string): Promise<ProviderReply> {
 const key=keys[provider];
 if (!key) throw new Error(`${provider} API anahtarı yapılandırılmamış`);
 const url=provider==="openrouter" ? OPENROUTER_URL : "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
 const model=provider==="openrouter" ? OPENROUTER_MODEL : (process.env.GEMINI_MODEL || "gemini-2.5-flash");
 const headers: Record<string,string>={authorization:`Bearer ${key}`,"content-type":"application/json"};
 if (provider==="openrouter") {
   headers["HTTP-Referer"]=process.env.APP_URL || "https://github.com/FatihKoc81/APEX";
   headers["X-Title"]="APEX";
 }
 const response=await fetch(url,{method:"POST",headers,body:JSON.stringify({model,temperature:0.2,messages:[{role:"system",content:system},{role:"user",content:prompt}]})});
 const data: any=await response.json();
 if (!response.ok) throw new Error(data.error?.message || `${provider} HTTP ${response.status}`);
 return {provider,text:data.choices?.[0]?.message?.content || ""};
}
