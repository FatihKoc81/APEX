import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import { createTask,getTask,setTaskControl,workerLoop } from "./worker.js";
import { providerStatus } from "./providers.js";
dotenv.config();
const app=express(); app.use(cors()); app.use(express.json({limit:"1mb"}));
app.get("/health",(_req,res)=>res.json({ok:true,service:"NEXORA"}));
app.get("/providers",(_req,res)=>res.json(providerStatus()));
app.post("/tasks",(req,res)=>{
 const instruction=String(req.body?.instruction||"").trim();
 if(!instruction) return res.status(400).json({error:"instruction zorunlu"});
 const id=createTask(instruction); return res.status(202).json({id,status:"queued"});
});
app.get("/tasks/:id",(req,res)=>{const t=getTask(req.params.id); return t?res.json(t):res.status(404).json({error:"not found"});});
app.post("/tasks/:id/:action",(req,res)=>{
 const action=req.params.action;
 if(!["pause","resume","cancel"].includes(action)) return res.status(400).json({error:"invalid action"});
 return setTaskControl(req.params.id,action as any)?res.json({ok:true}):res.status(404).json({error:"not found"});
});
const port=Number(process.env.PORT||8787);
app.listen(port,()=>console.log(`NEXORA API listening on ${port}`));
workerLoop().catch(e=>console.error("worker stopped",e));
