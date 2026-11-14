import dotenv from "dotenv";
dotenv.config();

const ownerRepo = (process.env.GITHUB_REPOSITORY || "FatihKoc81/APEX").trim();
const token = process.env.GITHUB_TOKEN;
const branch = process.env.GITHUB_BRANCH || "main";
const allowedRoots = ["android/", "server/src/", "server/package.json", "server/tsconfig.json", ".github/workflows/", "README.md", "docs/"];

function assertAllowed(path:string) {
  if (!path || path.startsWith("/") || path.split("/").includes("..") || !allowedRoots.some(root => path.startsWith(root)))
    throw new Error("Bu dosya yolu güvenlik izin listesinde değil.");
}
async function api(path:string, init:RequestInit={}) {
  if (!token) throw new Error("GitHub erişimi için Render ortamına GITHUB_TOKEN eklenmeli.");
  const response=await fetch(`https://api.github.com/repos/${ownerRepo}/${path}`,{
    ...init, headers:{authorization:`Bearer ${token}`,accept:"application/vnd.github+json","x-github-api-version":"2022-11-28","content-type":"application/json",...(init.headers||{})}
  });
  const data:any=await response.json().catch(()=>({}));
  if(!response.ok) throw new Error(data.message||`GitHub API HTTP ${response.status}`);
  return data;
}
export async function executeGithubTool(name:string,args:any):Promise<string> {
  if(name==="read_file") {
    const path=String(args.path||""); assertAllowed(path);
    const data=await api(`contents/${path.split("/").map(encodeURIComponent).join("/")}?ref=${encodeURIComponent(branch)}`);
    if(data.type!=="file") throw new Error("İstenen yol bir dosya değil.");
    const content=Buffer.from(data.content||"","base64").toString("utf8");
    return JSON.stringify({path,sha:data.sha,content:content.slice(0,50000)});
  }
  if(name==="write_file") {
    const path=String(args.path||""); assertAllowed(path);
    const content=String(args.content??"");
    if(content.length>90000) throw new Error("Dosya 90 KB sınırını aşıyor.");
    let sha:string|undefined;
    try { const old=await api(`contents/${path.split("/").map(encodeURIComponent).join("/")}?ref=${encodeURIComponent(branch)}`); sha=old.sha; } catch(e:any) {
      if(!String(e.message).includes("Not Found")) throw e;
    }
    const result=await api(`contents/${path.split("/").map(encodeURIComponent).join("/")}`,{method:"PUT",body:JSON.stringify({message:String(args.message||"APEX: AI-assisted update").slice(0,120),content:Buffer.from(content,"utf8").toString("base64"),branch,...(sha?{sha}:{})})});
    return JSON.stringify({success:true,path,commit:result.commit?.html_url,sha:result.content?.sha});
  }
  if(name==="dispatch_workflow") {
    const workflow=String(args.workflow||"");
    if(!/^[a-zA-Z0-9._-]+\.yml$/.test(workflow)) throw new Error("Workflow adı .yml dosyası olmalı.");
    const result=await api(`actions/workflows/${encodeURIComponent(workflow)}/dispatches`,{method:"POST",body:JSON.stringify({ref:branch,inputs:args.inputs||{}})});
    return JSON.stringify({success:true,message:"Workflow dispatch isteği gönderildi."});
  }
  throw new Error("Desteklenmeyen GitHub aracı.");
}
