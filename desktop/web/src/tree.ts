import type { FileNode, NodeKind } from "./types.ts";

export interface TreeNode {
  name: string;
  path: string;
  real: string;
  type: NodeKind;
  size: number;
  children: Map<string, TreeNode>;
}

export function buildTree(nodes: FileNode[]): TreeNode {
  const root: TreeNode = {
    name: "",
    path: "",
    real: "",
    type: "dir",
    size: 0,
    children: new Map(),
  };
  for (const node of nodes) {
    if (node.type === "dir") {
      continue;
    }
    const parts = node.display.split("/").filter(Boolean);
    let cur = root;
    let acc = "";
    for (const [i, part] of parts.entries()) {
      acc += `/${part}`;
      let child = cur.children.get(part);
      if (!child) {
        child = { name: part, path: acc, real: "", type: "dir", size: 0, children: new Map() };
        cur.children.set(part, child);
      }
      if (i === parts.length - 1) {
        child.type = node.type;
        child.size = node.size;
        child.real = node.path;
      }
      cur = child;
    }
  }
  return root;
}

export function collapseTree(root: TreeNode): TreeNode {
  for (const child of root.children.values()) {
    collapseNode(child);
  }
  return root;
}

function collapseNode(node: TreeNode): void {
  while (node.children.size === 1) {
    const child = node.children.values().next().value;
    if (!child || child.type !== "dir") {
      break;
    }
    node.name = `${node.name}/${child.name}`;
    node.path = child.path;
    node.children = child.children;
  }
  for (const child of node.children.values()) {
    collapseNode(child);
  }
}

export type CheckState = "checked" | "unchecked" | "indeterminate";

export function filePaths(node: TreeNode): string[] {
  if (node.type !== "dir") {
    return [node.real];
  }
  return [...node.children.values()].flatMap(filePaths);
}

export function nodeState(node: TreeNode, isSelected: (path: string) => boolean): CheckState {
  if (node.type !== "dir") {
    return isSelected(node.real) ? "checked" : "unchecked";
  }
  const states = [...node.children.values()].map((child) => nodeState(child, isSelected));
  if (states.every((state) => state === "unchecked")) {
    return "unchecked";
  }
  if (states.every((state) => state === "checked")) {
    return "checked";
  }
  return "indeterminate";
}

export function sortedChildren(node: TreeNode): TreeNode[] {
  return [...node.children.values()].sort((a, b) => {
    if ((a.type === "dir") !== (b.type === "dir")) {
      return a.type === "dir" ? -1 : 1;
    }
    return a.name.localeCompare(b.name);
  });
}

export function formatSize(bytes: number): string {
  if (!bytes) {
    return "0 B";
  }
  const units = ["B", "KB", "MB", "GB", "TB"];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
}
