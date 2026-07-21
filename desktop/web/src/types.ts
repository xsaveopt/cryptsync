export type NodeKind = "file" | "dir" | "symlink";

export interface FileNode {
  path: string;
  display: string;
  type: NodeKind;
  size: number;
}

export interface Snapshot {
  id: string;
  short_id: string;
  time: string;
  paths: string[];
  hostname: string;
}
