import { createHash, randomBytes, randomUUID, timingSafeEqual } from "node:crypto";
import type { Request, Response, NextFunction } from "express";
import { db } from "./db.js";
const hash=(value:string)=>createHash("sha256").update(value).digest("hex");
function safeEqual(a:string,b:string){const aa=Buffer.from(a),bb=Buffer.from(b);return aa.length===bb.length&&timingSafeEqual(aa,bb);}
export function enrollDevice(inviteCode:string,name:string){
 const expected=process.env.APEX_ENROLLMENT_CODE?.trim();
 if(!expected||!safeEqual(hash(inviteCode),hash(expected)))return null;
 const id=randomUUID(),token=randomBytes(32).toString("base64url"),now=new Date().toISOString();
 db.prepare("INSERT INTO devices(id,name,token_hash,created_at) VALUES(?,?,?,?)").run(id,name.slice(0,80)||"Android cihaz",hash(token),now);
 return {id,token};
}
export function requireDevice(req:Request,res:Response,next:NextFunction){
 const token=String(req.header("authorization")||"").replace(/^Bearer\s+/i,"").trim();
 if(!token)return res.status(401).json({error:"Cihaz yetkilendirmesi gerekli"});
 const device=db.prepare("SELECT id,name FROM devices WHERE token_hash=? AND revoked_at IS NULL").get(hash(token));
 if(!device)return res.status(401).json({error:"Cihaz yetkisi geçersiz veya iptal edilmiş"});
 (req as any).device=device; next();
}
export function revokeDevice(id:string){return db.prepare("UPDATE devices SET revoked_at=? WHERE id=? AND revoked_at IS NULL").run(new Date().toISOString(),id).changes>0;}
export function listDevices(){return db.prepare("SELECT id,name,created_at,revoked_at FROM devices ORDER BY created_at DESC").all();}
export function requireAdmin(req:Request,res:Response,next:NextFunction){
 const expected=process.env.APEX_ADMIN_TOKEN?.trim(),supplied=String(req.header("x-apex-admin")||"").trim();
 if(!expected||!supplied||!safeEqual(hash(supplied),hash(expected)))return res.status(403).json({error:"Yönetici yetkisi gerekli"});
 next();
}
