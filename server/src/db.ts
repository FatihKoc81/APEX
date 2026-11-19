import Database from "better-sqlite3";
import dotenv from "dotenv";
dotenv.config();

export const db = new Database(process.env.DATABASE_PATH || "./nexora.sqlite");
db.pragma("journal_mode = WAL");
db.exec(`
CREATE TABLE IF NOT EXISTS tasks (
 id TEXT PRIMARY KEY, instruction TEXT NOT NULL, status TEXT NOT NULL,
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL, step INTEGER NOT NULL DEFAULT 0,
 result TEXT, error TEXT, paused INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS devices (
 id TEXT PRIMARY KEY, name TEXT NOT NULL, token_hash TEXT NOT NULL UNIQUE,
 created_at TEXT NOT NULL, revoked_at TEXT
);
CREATE TABLE IF NOT EXISTS events (
 id INTEGER PRIMARY KEY AUTOINCREMENT, task_id TEXT NOT NULL, at TEXT NOT NULL,
 stage TEXT NOT NULL, provider TEXT, status TEXT NOT NULL, detail TEXT
);
`);
export function event(taskId:string, stage:string, status:string, detail:string, provider?:string) {
 db.prepare("INSERT INTO events(task_id,at,stage,provider,status,detail) VALUES(?,?,?,?,?,?)")
   .run(taskId,new Date().toISOString(),stage,provider||null,status,detail);
}
