import {
  archive,
  authorizeDrive,
  type ConnectMode,
  connect,
  disconnect,
  fileUrl,
  getTree,
  session,
} from "./api.ts";
import {
  buildTree,
  collapseTree,
  filePaths,
  formatSize,
  nodeState,
  sortedChildren,
  type TreeNode,
} from "./tree.ts";

const ICON = {
  chevron: `<svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true"><path fill="currentColor" d="M6 3.5 10.5 8 6 12.5z"/></svg>`,
  folder: `<svg viewBox="0 0 16 16" width="15" height="15" aria-hidden="true"><path fill="currentColor" d="M1.5 4a1 1 0 0 1 1-1h3l1.2 1.3H13a1 1 0 0 1 1 1v6.2a1 1 0 0 1-1 1H2.5a1 1 0 0 1-1-1z"/></svg>`,
  file: `<svg viewBox="0 0 16 16" width="15" height="15" aria-hidden="true"><path fill="currentColor" d="M4 2h4.5L13 6v7.5a.5.5 0 0 1-.5.5h-8a.5.5 0 0 1-.5-.5v-11A.5.5 0 0 1 4 2Z"/></svg>`,
};

interface NodeControl {
  node: TreeNode;
  isDir: boolean;
  expand: () => void;
  collapse: () => void;
  toggle: () => void;
  isExpanded: () => boolean;
}

let mode: ConnectMode = "drive";
const snapshot = "latest";
const selectedFiles = new Set<string>();
const renderedChecks = new Map<TreeNode, HTMLInputElement>();
const controls = new Map<HTMLElement, NodeControl>();
let root: TreeNode | null = null;

function el<T extends HTMLElement>(id: string): T {
  const found = document.getElementById(id);
  if (!found) {
    throw new Error(`missing element #${id}`);
  }
  return found as T;
}

function init(): void {
  for (const tab of document.querySelectorAll<HTMLButtonElement>(".tab")) {
    tab.addEventListener("click", () => {
      mode = (tab.dataset.mode as ConnectMode) ?? "drive";
      for (const other of document.querySelectorAll<HTMLButtonElement>(".tab")) {
        other.classList.toggle("active", other === tab);
      }
      el("pane-drive").classList.toggle("hidden", mode !== "drive");
      el("pane-raw").classList.toggle("hidden", mode !== "raw");
    });
  }

  el("oauth-btn").addEventListener("click", () => void signInDrive());
  el("connect-btn").addEventListener("click", () => void doConnect());
  el("disconnect-btn").addEventListener("click", () => void doDisconnect());
  el("download-btn").addEventListener("click", () => void downloadSelected());
  el<HTMLInputElement>("select-all").addEventListener("change", () => {
    if (root) {
      setChecked(root, el<HTMLInputElement>("select-all").checked);
    }
  });
  el("tree").addEventListener("keydown", onKeydown);

  void restoreSession();
}

async function restoreSession(): Promise<void> {
  const connected = await session().catch(() => false);
  if (connected) {
    el("connect").classList.add("hidden");
    el("browser").classList.remove("hidden");
    await loadTree();
  }
}

async function signInDrive(): Promise<void> {
  const btn = el<HTMLButtonElement>("oauth-btn");
  const status = el("oauth-status");
  btn.disabled = true;
  btn.textContent = "Waiting for browser…";
  setStatus(status, "A browser window opened for Google sign-in. Complete it there.");
  try {
    const token = await authorizeDrive();
    el<HTMLTextAreaElement>("token").value = token;
    setStatus(status, "Signed in. Enter your password and connect.");
  } catch (err) {
    setStatus(status, message(err), true);
  } finally {
    btn.disabled = false;
    btn.textContent = "Sign in with Google Drive";
  }
}

async function doConnect(): Promise<void> {
  const btn = el<HTMLButtonElement>("connect-btn");
  const status = el("connect-status");
  btn.disabled = true;
  btn.textContent = "Connecting…";
  setStatus(status, "");

  try {
    await connect({
      mode,
      token: el<HTMLTextAreaElement>("token").value,
      body: el<HTMLTextAreaElement>("body").value,
      password: el<HTMLInputElement>("password").value,
      remote: el<HTMLInputElement>("remote").value,
      prefix: el<HTMLInputElement>("prefix").value,
    });
    el("connect").classList.add("hidden");
    el("browser").classList.remove("hidden");
    await loadTree();
  } catch (err) {
    setStatus(status, message(err), true);
  } finally {
    btn.disabled = false;
    btn.textContent = "Connect";
  }
}

async function doDisconnect(): Promise<void> {
  await disconnect();
  resetSelection();
  root = null;
  el("browser").classList.add("hidden");
  el("connect").classList.remove("hidden");
  el("tree").innerHTML = "";
}

async function loadTree(): Promise<void> {
  const tree = el("tree");
  const status = el("browser-status");
  resetSelection();
  tree.innerHTML = `<div class="empty">Loading…</div>`;
  setStatus(status, "");

  try {
    const nodes = await getTree(snapshot);
    if (nodes.length === 0) {
      tree.innerHTML = `<div class="empty">Nothing here yet.</div>`;
      return;
    }
    root = collapseTree(buildTree(nodes));
    tree.innerHTML = "";
    renderChildren(tree, root, 1);
    refreshSelection();
    const first = treeItems()[0];
    if (first) {
      first.tabIndex = 0;
    }
  } catch (err) {
    tree.innerHTML = "";
    setStatus(status, message(err), true);
  }
}

function renderChildren(container: HTMLElement, node: TreeNode, level: number): void {
  for (const child of sortedChildren(node)) {
    renderNode(container, child, level);
  }
}

function renderNode(container: HTMLElement, node: TreeNode, level: number): void {
  const isDir = node.type === "dir";
  const item = document.createElement("div");
  item.className = "treeitem";
  item.setAttribute("role", "treeitem");
  item.setAttribute("aria-level", String(level));
  item.tabIndex = -1;

  const row = document.createElement("div");
  row.className = `row ${isDir ? "dir" : "file"}`;

  const chevron = document.createElement("span");
  chevron.className = "chevron";
  if (isDir) {
    chevron.innerHTML = ICON.chevron;
  }
  row.append(chevron);

  const check = document.createElement("input");
  check.type = "checkbox";
  check.tabIndex = -1;
  check.addEventListener("change", () => setChecked(node, check.checked));
  row.append(check);
  renderedChecks.set(node, check);

  const icon = document.createElement("span");
  icon.className = "icon";
  icon.innerHTML = isDir ? ICON.folder : ICON.file;
  row.append(icon);

  const name = document.createElement("span");
  name.className = "name";
  name.textContent = node.name;
  name.title = node.path;
  row.append(name);

  const size = document.createElement("span");
  size.className = "size";
  size.textContent = isDir ? "" : formatSize(node.size);
  row.append(size);

  if (!isDir) {
    const link = document.createElement("a");
    link.className = "dl";
    link.textContent = "download";
    link.href = fileUrl(snapshot, node.real);
    link.setAttribute("download", node.name);
    link.tabIndex = -1;
    row.append(link);
  }

  item.append(row);
  container.append(item);
  applyState(node, check);

  row.addEventListener("click", (event) => {
    if ((event.target as HTMLElement).closest("input, a")) {
      return;
    }
    focusItem(item);
  });

  let group: HTMLElement | null = null;
  let expanded = false;
  const expand = (): void => {
    if (!isDir) {
      return;
    }
    expanded = true;
    item.setAttribute("aria-expanded", "true");
    item.classList.add("open");
    group ??= makeGroup(item);
    group.innerHTML = "";
    renderChildren(group, node, level + 1);
    refreshSelection();
  };
  const collapse = (): void => {
    expanded = false;
    item.setAttribute("aria-expanded", "false");
    item.classList.remove("open");
    group?.replaceChildren();
  };
  const toggle = (): void => (expanded ? collapse() : expand());

  if (isDir) {
    item.setAttribute("aria-expanded", "false");
    chevron.addEventListener("click", (event) => {
      event.stopPropagation();
      toggle();
    });
    name.addEventListener("click", toggle);
  }

  controls.set(item, { node, isDir, expand, collapse, toggle, isExpanded: () => expanded });

  if (isDir && node.children.size === 1) {
    expand();
  }
}

function makeGroup(item: HTMLElement): HTMLElement {
  const group = document.createElement("div");
  group.className = "group";
  group.setAttribute("role", "group");
  item.append(group);
  return group;
}

function setChecked(node: TreeNode, checked: boolean): void {
  for (const path of filePaths(node)) {
    if (checked) {
      selectedFiles.add(path);
    } else {
      selectedFiles.delete(path);
    }
  }
  refreshSelection();
}

function applyState(node: TreeNode, check: HTMLInputElement): void {
  const state = nodeState(node, (path) => selectedFiles.has(path));
  check.checked = state === "checked";
  check.indeterminate = state === "indeterminate";
  const item = check.closest<HTMLElement>('[role="treeitem"]');
  item?.setAttribute(
    "aria-checked",
    state === "checked" ? "true" : state === "indeterminate" ? "mixed" : "false",
  );
}

function refreshSelection(): void {
  for (const [node, check] of renderedChecks) {
    applyState(node, check);
  }
  if (root) {
    applyState(root, el<HTMLInputElement>("select-all"));
  }
  const btn = el<HTMLButtonElement>("download-btn");
  const count = selectedFiles.size;
  btn.disabled = count === 0;
  btn.textContent = count > 0 ? `Download ${count} selected (.zip)` : "Download selected (.zip)";
}

function resetSelection(): void {
  selectedFiles.clear();
  renderedChecks.clear();
  controls.clear();
  const selectAll = el<HTMLInputElement>("select-all");
  selectAll.checked = false;
  selectAll.indeterminate = false;
  refreshSelection();
}

function treeItems(): HTMLElement[] {
  return [...el("tree").querySelectorAll<HTMLElement>('[role="treeitem"]')];
}

function focusItem(item: HTMLElement): void {
  for (const other of treeItems()) {
    other.tabIndex = other === item ? 0 : -1;
  }
  item.focus();
}

function onKeydown(event: KeyboardEvent): void {
  const item = (event.target as HTMLElement).closest<HTMLElement>('[role="treeitem"]');
  if (!item) {
    return;
  }
  const control = controls.get(item);
  const list = treeItems();
  const index = list.indexOf(item);

  const focusAt = (target: number): void => {
    const next = list[target];
    if (next) {
      focusItem(next);
    }
  };

  switch (event.key) {
    case "ArrowDown":
      focusAt(index + 1);
      break;
    case "ArrowUp":
      focusAt(index - 1);
      break;
    case "Home":
      focusAt(0);
      break;
    case "End":
      focusAt(list.length - 1);
      break;
    case "ArrowRight":
      if (control?.isDir) {
        if (control.isExpanded()) {
          focusAt(index + 1);
        } else {
          control.expand();
        }
      }
      break;
    case "ArrowLeft":
      if (control?.isDir && control.isExpanded()) {
        control.collapse();
      } else {
        focusParent(item);
      }
      break;
    case " ":
      if (control) {
        const selected = nodeState(control.node, (path) => selectedFiles.has(path)) === "checked";
        setChecked(control.node, !selected);
      }
      break;
    case "Enter":
      if (control?.isDir) {
        control.toggle();
      } else {
        item.querySelector<HTMLAnchorElement>("a.dl")?.click();
      }
      break;
    default:
      return;
  }
  event.preventDefault();
}

function focusParent(item: HTMLElement): void {
  const parent = item.parentElement?.closest<HTMLElement>('[role="treeitem"]');
  if (parent) {
    focusItem(parent);
  }
}

async function downloadSelected(): Promise<void> {
  const btn = el<HTMLButtonElement>("download-btn");
  const status = el("browser-status");
  btn.disabled = true;
  btn.textContent = "Preparing…";
  setStatus(status, "");
  try {
    const blob = await archive(snapshot, [...selectedFiles]);
    triggerDownload(blob, "cryptsync-export.zip");
  } catch (err) {
    setStatus(status, message(err), true);
  } finally {
    refreshSelection();
  }
}

function triggerDownload(blob: Blob, name: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = name;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

function setStatus(element: HTMLElement, text: string, isError = false): void {
  element.textContent = text;
  element.classList.toggle("error", isError);
}

function message(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}

init();
