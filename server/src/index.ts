import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import { createTask,getTask,setTaskControl,workerLoop } from "./worker.js";
import { providerStatus } from "./providers.js";
import { enrollDevice,listDevices,requireAdmin,requireDevice,revokeDevice } from "./auth.js";
dotenv.config();
const app=express(); app.use(cors({origin:false})); app.use(express.json({limit:"1mb"}));
app.get("/health",(_req,res)=>res.json({ok:true,service:"APEX"}));
app.post("/auth/enroll",(req,res)=>{
 const invite=String(req.body?.inviteCode||""),name=String(req.body?.deviceName||"Android cihaz");
 if(!invite)return res.status(400).json({error:"Davet kodu gerekli"});
 const enrolled=enrollDevice(invite,name);
 if(!enrolled)return res.status(403).json({error:"Davet kodu geçersiz veya sunucuda tanımlı değil"});
 return res.status(201).json(enrolled);
});
app.use(requireDevice);
app.get("/providers",(_req,res)=>res.json(providerStatus()));
app.post("/tasks",(req,res)=>{
 const instruction=String(req.body?.instruction||"").trim();
 if(!instruction)return res.status(400).json({error:"instruction zorunlu"});
 const id=createTask(instruction); return res.status(202).json({id,status:"queued"});
});
app.get("/tasks/:id",(req,res)=>{const t=getTask(req.params.id);return t?res.json(t):res.status(404).json({error:"not found"});});
app.post("/tasks/:id/:action",(req,res)=>{
 const action=req.params.action;
 if(!["pause","resume","cancel"].includes(action))return res.status(400).json({error:"invalid action"});
 return setTaskControl(req.params.id,action as any)?res.json({ok:true}):res.status(404).json({error:"not found"});
});
app.get("/admin/devices",requireAdmin,(_req,res)=>res.json(listDevices()));
app.post("/admin/devices/:id/revoke",requireAdmin,(req,res)=>revokeDevice(req.params.id)?res.json({ok:true}):res.status(404).json({error:"device not found or already revoked"}));
const port=Number(process.env.PORT||8787); app.listen(port,()=>console.log(`APEX API listening on ${port}`));
workerLoop().catch(e=>console.error("worker stopped",e));
