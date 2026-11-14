import dotenv from "dotenv";
dotenv.config();

export type ProviderName = "openai" | "gemini";
export type ProviderReply = { provider: ProviderName; text: string };

const keys: Record<ProviderName,string|undefined> = {
  openai: process.env.OPENAI_API_KEY,
  gemini: process.env.GEMINI_API_KEY
};

export function providerStatus() {
  return (Object.keys(keys) as ProviderName[]).map(provider => ({
    provider, configured: Boolean(keys[provider])
  }));
}

export async function askProvider(provider:ProviderName, system:string, prompt:string):Promise<ProviderReply> {
  const key = keys[provider];
  if (!key) throw new Error(`${provider} API anahtarı yapılandırılmamış`);
  const url = provider === "openai"
    ? "https://api.openai.com/v1/chat/completions"
    : "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
  const model = provider === "openai" ? "gpt-4.1-mini" : "gemini-2.5-flash";
  const response = await fetch(url, {
    method: "POST",
    headers: { authorization: `Bearer ${key}`, "content-type": "application/json" },
    body: JSON.stringify({ model, temperature: 0.2, messages: [
      { role: "system", content: system }, { role: "user", content: prompt }
    ] })
  });
  const data:any = await response.json();
  if (!response.ok) throw new Error(data.error?.message || `${provider} HTTP ${response.status}`);
  return { provider, text: data.choices?.[0]?.message?.content || "" };
}
