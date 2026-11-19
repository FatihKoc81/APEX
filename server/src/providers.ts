import dotenv from "dotenv";
import { executeGithubTool } from "./github.js";
dotenv.config();

export type ProviderName = "openai" | "gemini" | "omniroute" | "freellmapi" | "9router";
export type ProviderReply = { provider: ProviderName; text: string };
function firstConfigured(names:string[]):{key?:string;envName?:string} {
  for (const name of names) {
    const value=process.env[name]?.trim();
    if (value) return {key:value,envName:name};
  }
  return {};
}
const openaiConfig=firstConfigured(["OPENAI_API_KEY","OPENAI_KEY"]);
const geminiConfig=firstConfigured(["GEMINI_API_KEY","GOOGLE_API_KEY","GOOGLE_GENERATIVE_AI_API_KEY"]);
const gatewayConfigs = {
  omniroute: { baseUrl: process.env.OMNIROUTE_BASE_URL?.trim(), key: process.env.OMNIROUTE_API_KEY?.trim(), model: process.env.OMNIROUTE_MODEL?.trim() || "auto" },
  freellmapi: { baseUrl: process.env.FREELLMAPI_BASE_URL?.trim(), key: process.env.FREELLMAPI_API_KEY?.trim(), model: process.env.FREELLMAPI_MODEL?.trim() || "auto" },
  "9router": { baseUrl: process.env.NINEROUTER_BASE_URL?.trim(), key: process.env.NINEROUTER_API_KEY?.trim(), model: process.env.NINEROUTER_MODEL?.trim() || "auto" }
};
const keys: Record<ProviderName,string|undefined> = {
  openai:openaiConfig.key, gemini:geminiConfig.key,
  omniroute:gatewayConfigs.omniroute.key,
  freellmapi:gatewayConfigs.freellmapi.key,
  "9router":gatewayConfigs["9router"].key
};
const sourceEnvs: Record<ProviderName,string> = {
  openai:"OPENAI_API_KEY", gemini:"GEMINI_API_KEY",
  omniroute:"OMNIROUTE_API_KEY", freellmapi:"FREELLMAPI_API_KEY", "9router":"NINEROUTER_API_KEY"
};
export function providerStatus() {
  return (Object.keys(keys) as ProviderName[]).map(provider => ({
    provider,
    configured:Boolean(keys[provider] && (provider==="openai" || provider==="gemini" || gatewayConfigs[provider as keyof typeof gatewayConfigs]?.baseUrl)),
    sourceEnv:provider==="openai" ? openaiConfig.envName ?? null : provider==="gemini" ? geminiConfig.envName ?? null : sourceEnvs[provider],
    model: provider in gatewayConfigs ? gatewayConfigs[provider as keyof typeof gatewayConfigs].model : null
  }));
}

export async function providerHealth() {
  const gateways: Array<"omniroute" | "freellmapi" | "9router"> = ["omniroute", "freellmapi", "9router"];
  return Promise.all(gateways.map(async provider => {
    const config = gatewayConfigs[provider];
    const configured = Boolean(config.baseUrl && config.key);
    if (!configured) {
      return { provider, configured: false, reachable: false, status: "missing_configuration", latencyMs: null, modelCount: null };
    }
    const started = Date.now();
    try {
      const base = config.baseUrl!.replace(/\/+$/, "");
      const response = await fetch(`${base}/models`, {
        method: "GET",
        headers: { authorization: `Bearer ${config.key}`, accept: "application/json" },
        signal: AbortSignal.timeout(10000)
      });
      const data: any = await response.json().catch(() => null);
      return {
        provider,
        configured: true,
        reachable: response.ok,
        status: response.ok ? "online" : `http_${response.status}`,
        statusCode: response.status,
        latencyMs: Date.now() - started,
        modelCount: Array.isArray(data?.data) ? data.data.length : null
      };
    } catch (error: any) {
      return {
        provider,
        configured: true,
        reachable: false,
        status: "connection_error",
        latencyMs: Date.now() - started,
        modelCount: null,
        error: String(error?.message || "Connection failed").slice(0, 180)
      };
    }
  }));
}

const githubTools = [
 {type:"function",function:{name:"read_file",description:"Read a text file from the configured GitHub repository. Only approved project paths are allowed.",parameters:{type:"object",properties:{path:{type:"string"}},required:["path"],additionalProperties:false}}},
 {type:"function",function:{name:"write_file",description:"Create or update a text file in the configured GitHub repository. Provide the complete new file content. Changes are committed to the configured branch.",parameters:{type:"object",properties:{path:{type:"string"},content:{type:"string"},message:{type:"string"}},required:["path","content","message"],additionalProperties:false}}},
 {type:"function",function:{name:"dispatch_workflow",description:"Dispatch an existing GitHub Actions workflow file on the configured branch.",parameters:{type:"object",properties:{workflow:{type:"string"},inputs:{type:"object"}},required:["workflow"],additionalProperties:false}}}
] as const;

export async function askOpenAIWithTools(system:string,prompt:string):Promise<string> {
 const key=keys.openai; if(!key) throw new Error("OpenAI API anahtarı yapılandırılmamış");
 const messages:any[]=[{role:"system",content:system},{role:"user",content:prompt}];
 let finalText="";
 for(let round=0;round<8;round++) {
   const response=await fetch("https://api.openai.com/v1/chat/completions",{method:"POST",headers:{authorization:`Bearer ${key}`,"content-type":"application/json"},body:JSON.stringify({model:"gpt-4.1-mini",temperature:0.1,messages,tools:githubTools,tool_choice:"auto"})});
   const data:any=await response.json(); if(!response.ok) throw new Error(data.error?.message||`OpenAI HTTP ${response.status}`);
   const message=data.choices?.[0]?.message; if(!message) throw new Error("OpenAI boş yanıt verdi");
   messages.push(message); if(message.content) finalText+=message.content;
   const calls=message.tool_calls||[]; if(!calls.length) return finalText||"(GPT metin yanıtı üretmedi)";
   for(const call of calls) {
     let result:string;
     try { const args=JSON.parse(call.function.arguments||"{}"); result=await executeGithubTool(call.function.name,args); }
     catch(e:any) { result=JSON.stringify({success:false,error:e.message||String(e)}); }
     messages.push({role:"tool",tool_call_id:call.id,content:result});
   }
 }
 return finalText+"\nAraç çağrısı tur sınırına ulaşıldı; kalan işlem bir sonraki görev turunda sürdürülecek.";
}
export async function askProvider(provider:ProviderName,system:string,prompt:string):Promise<ProviderReply> {
 const key=keys[provider]; if(!key) throw new Error(`${provider} API anahtarı yapılandırılmamış`);
 let url:string, model:string;
 if(provider==="openai") { url="https://api.openai.com/v1/chat/completions"; model="gpt-4.1-mini"; }
 else if(provider==="gemini") { url="https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"; model="gemini-2.5-flash"; }
 else {
   const config=gatewayConfigs[provider];
   if(!config.baseUrl) throw new Error(`${provider} BASE_URL yapılandırılmamış`);
   url=`${config.baseUrl.replace(/\/+$/,"")}/chat/completions`;
   model=config.model;
 }
 const response=await fetch(url,{method:"POST",headers:{authorization:`Bearer ${key}`,"content-type":"application/json"},body:JSON.stringify({model,temperature:0.2,messages:[{role:"system",content:system},{role:"user",content:prompt}]})});
 const data:any=await response.json(); if(!response.ok) throw new Error(data.error?.message||`${provider} HTTP ${response.status}`);
 return {provider,text:data.choices?.[0]?.message?.content||""};
}
