import type { FileNode, Snapshot } from "./types.ts";

export type ConnectMode = "drive" | "raw";

export interface ConnectRequest {
  mode: ConnectMode;
  token: string;
  body: string;
  remote: string;
  prefix: string;
  password: string;
}

interface ConnectResponse {
  snapshots: Snapshot[];
}

interface TreeResponse {
  nodes: FileNode[];
}

export async function session(): Promise<boolean> {
  const data = await getJSON<{ connected: boolean }>("/api/session");
  return data.connected;
}

export async function authorizeDrive(): Promise<string> {
  const data = await postJSON<{ token: string }>("/api/oauth/drive", {});
  return data.token;
}

export async function connect(request: ConnectRequest): Promise<Snapshot[]> {
  const data = await postJSON<ConnectResponse>("/api/connect", request);
  return data.snapshots ?? [];
}

export async function getTree(snapshot: string): Promise<FileNode[]> {
  const data = await getJSON<TreeResponse>(`/api/tree?snapshot=${encodeURIComponent(snapshot)}`);
  return data.nodes ?? [];
}

export function fileUrl(snapshot: string, path: string): string {
  return `/api/file?snapshot=${encodeURIComponent(snapshot)}&path=${encodeURIComponent(path)}`;
}

export async function archive(snapshot: string, paths: string[]): Promise<Blob> {
  const resp = await fetch("/api/archive", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ snapshot, paths }),
  });
  if (!resp.ok) {
    throw new Error(await errorText(resp));
  }
  return resp.blob();
}

export async function disconnect(): Promise<void> {
  await fetch("/api/disconnect", { method: "POST" });
}

export async function errorText(resp: Response): Promise<string> {
  try {
    const data = (await resp.json()) as { error?: string };
    return data.error ?? resp.statusText;
  } catch {
    return resp.statusText;
  }
}

async function postJSON<T>(url: string, body: unknown): Promise<T> {
  const resp = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!resp.ok) {
    throw new Error(await errorText(resp));
  }
  return (await resp.json()) as T;
}

async function getJSON<T>(url: string): Promise<T> {
  const resp = await fetch(url);
  if (!resp.ok) {
    throw new Error(await errorText(resp));
  }
  return (await resp.json()) as T;
}
