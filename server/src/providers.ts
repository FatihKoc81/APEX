import dotenv from "dotenv";
import { executeGithubTool } from "./github.js";
dotenv.config();

export type ProviderName = "openai" | "openrouter" | "gemini" | "kimi" | "deepseek";
export type ProviderReply = { provider: ProviderName; text: string };
function firstConfigured(names: string[]) {
  for (const name of names) {
    const value = process.env[name]?.trim();
    if (value) return { key: value, envName: name };
  }
  return {};
}
const openaiConfig = firstConfigured(["OPENAI_API_KEY"]);
const openrouterConfig = firstConfigured(["OPENROUTER_API_KEY"]);
const kimiModel = process.env.KIMI_MODEL?.trim() || "";
const deepseekModel = process.env.DEEPSEEK_MODEL?.trim() || "";
const geminiConfig = firstConfigured(["GEMINI_API_KEY", "GOOGLE_API_KEY", "GOOGLE_GENERATIVE_AI_API_KEY"]);
const keys: Record<ProviderName, string | undefined> = {
  openai: openaiConfig.key, openrouter: openrouterConfig.key, gemini: geminiConfig.key,
  kimi: openrouterConfig.key && kimiModel.endsWith(":free") ? openrouterConfig.key : undefined,
  deepseek: openrouterConfig.key && deepseekModel.endsWith(":free") ? openrouterConfig.key : undefined
};
export function providerStatus() {
  return [
    ...(Object.keys(keys) as ProviderName[]).map(provider => ({
      provider, configured: Boolean(keys[provider]),
      sourceEnv: provider === "openai" ? openaiConfig.envName ?? null : provider === "gemini" ? geminiConfig.envName ?? null : openrouterConfig.envName ?? null,
      model: provider === "openai" ? (process.env.OPENAI_MODEL || "gpt-6-luna") : provider === "gemini" ? (process.env.GEMINI_MODEL || "gemini-2.5-flash") : provider === "openrouter" ? OPENROUTER_MODEL : provider === "kimi" ? (kimiModel || null) : (deepseekModel || null)
    })),
    { provider: "omniroute", configured: Boolean(omniRouteBase && omniRouteKey), sourceEnv: omniRouteBase && omniRouteKey ? "OMNIROUTE_BASE_URL + OMNIROUTE_API_KEY" : null, model: omniRouteModel }
  ];
}
const githubTools = [
 {type:"function",function:{name:"read_file",description:"Read a text file from the configured GitHub repository. Only approved project paths are allowed.",parameters:{type:"object",properties:{path:{type:"string"}},required:["path"],additionalProperties:false}}},
 {type:"function",function:{name:"write_file",description:"Create or update a text file in the configured GitHub repository. Provide the complete new file content. Changes are committed to the configured branch.",parameters:{type:"object",properties:{path:{type:"string"},content:{type:"string"},message:{type:"string"}},required:["path","content","message"],additionalProperties:false}}},
 {type:"function",function:{name:"dispatch_workflow",description:"Dispatch an existing GitHub Actions workflow file on the configured branch.",parameters:{type:"object",properties:{workflow:{type:"string"},inputs:{type:"object"}},required:["workflow"],additionalProperties:false}}}
] as const;

const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
const OPENROUTER_MODEL = process.env.OPENROUTER_MODEL || "openrouter/free";
const omniRouteBase = (process.env.OMNIROUTE_BASE_URL || "").trim().replace(/\/+$/, "");
const omniRouteKey = process.env.OMNIROUTE_API_KEY?.trim() || "";
const omniRouteModel = process.env.OMNIROUTE_MODEL?.trim() || "auto/coding";

export async function askOpenRouterWithTools(system: string, prompt: string): Promise<string> {
 const targets = [
   ...(omniRouteBase && omniRouteKey ? [{url:omniRouteBase + "/v1/chat/completions",key:omniRouteKey,model:omniRouteModel,name:"OmniRoute"}] : []),
   ...(keys.openrouter ? [{url:OPENROUTER_URL,key:keys.openrouter,model:OPENROUTER_MODEL,name:"OpenRouter"}] : [])
 ];
 if (!targets.length) throw new Error("OMNIROUTE bağlantısı veya OPENROUTER_API_KEY yapılandırılmamış");
 const messages: any[] = [{role:"system",content:system},{role:"user",content:prompt}];
 let finalText = "";
 let targetIndex = 0;
 for (let round=0; round<8; round++) {
   const target = targets[targetIndex];
   const headers: Record<string,string> = {authorization:"Bearer " + target.key,"content-type":"application/json"};
   if (target.name === "OpenRouter") {
     headers["HTTP-Referer"]=process.env.APP_URL || "https://github.com/FatihKoc81/APEX";
     headers["X-Title"]="APEX";
   }
   let response: Response;
   try {
     response = await fetch(target.url, {method:"POST",headers,body:JSON.stringify({model:target.model,temperature:0.1,messages,tools:githubTools,tool_choice:"auto"})});
   } catch (error) {
     if (targetIndex === 0 && targets.length > 1) { targetIndex++; round--; continue; }
     throw error;
   }
   const data: any = await response.json();
   if (!response.ok) {
     if (targetIndex === 0 && targets.length > 1) { targetIndex++; round--; continue; }
     throw new Error(data.error?.message || (target.name + " HTTP " + response.status));
   }
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
 const url=provider==="openai" ? "https://api.openai.com/v1/chat/completions" : provider==="gemini" ? "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions" : OPENROUTER_URL;
 const model=provider==="openai" ? (process.env.OPENAI_MODEL || "gpt-6-luna") : provider==="gemini" ? (process.env.GEMINI_MODEL || "gemini-2.5-flash") : provider==="kimi" ? kimiModel : provider==="deepseek" ? deepseekModel : OPENROUTER_MODEL;
 const headers: Record<string,string>={authorization:`Bearer ${key}`,"content-type":"application/json"};
 if (provider==="openrouter" || provider==="kimi" || provider==="deepseek") {
   headers["HTTP-Referer"]=process.env.APP_URL || "https://github.com/FatihKoc81/APEX";
   headers["X-Title"]="APEX";
 }
 const response=await fetch(url,{method:"POST",headers,body:JSON.stringify({model,temperature:0.2,messages:[{role:"system",content:system},{role:"user",content:prompt}]})});
 const data: any=await response.json();
 if (!response.ok) throw new Error(data.error?.message || `${provider} HTTP ${response.status}`);
 return {provider,text:data.choices?.[0]?.message?.content || ""};
}
