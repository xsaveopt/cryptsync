import assert from "node:assert/strict";
import { afterEach, before, describe, it, mock } from "node:test";
import { buildPage, FakeElement } from "./fake-dom.ts";
import type { FileNode } from "../src/types.ts";

type Handler = (
  url: string,
  method: string,
  body: string | undefined,
) => Response | Promise<Response>;

interface Call {
  url: string;
  method: string;
  body: string | undefined;
}

const base = "/storage/emulated/0/";

function file(display: string, size: number): FileNode {
  return { path: base + display, display, type: "file", size };
}

const nodes: FileNode[] = [
  file("DCIM/Camera/a.jpg", 2048),
  file("DCIM/Camera/b.jpg", 10),
  file("DCIM/c.png", 5),
  file("Music/Album/song.mp3", 3),
  file("notes.txt", 1),
];

function respond(body: unknown, status = 200): Response {
  return {
    ok: status < 400,
    status,
    statusText: "Bad Gateway",
    json: async () => body,
    blob: async () => new Blob(["zip"]),
  } as unknown as Response;
}

const defaultHandler: Handler = (url) => {
  if (url === "/api/session") {
    return respond({ connected: true });
  }
  if (url.startsWith("/api/tree")) {
    return respond({ nodes });
  }
  if (url === "/api/connect") {
    return respond({ snapshots: [] });
  }
  if (url === "/api/oauth/drive") {
    return respond({ token: `{"access_token":"x"}` });
  }
  return respond({ ok: true });
};

function deferred<T>(): { promise: Promise<T>; resolve: (value: T) => void } {
  let resolve: (value: T) => void = () => {};
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

const doc = buildPage();
Object.defineProperty(globalThis, "document", { value: doc, configurable: true });

const calls: Call[] = [];
let handler: Handler = defaultHandler;

mock.method(globalThis, "fetch", async (input: string | URL | Request, init?: RequestInit) => {
  const url = String(input);
  const method = init?.method ?? "GET";
  const body = typeof init?.body === "string" ? init.body : undefined;
  calls.push({ url, method, body });
  return handler(url, method, body);
});

async function settle(): Promise<void> {
  for (let i = 0; i < 10; i++) {
    await new Promise((resolve) => setImmediate(resolve));
  }
}

function byId(id: string): FakeElement {
  const found = doc.getElementById(id);
  if (!found) {
    throw new Error(`missing #${id}`);
  }
  return found;
}

function find(parent: FakeElement, selector: string): FakeElement {
  const found = parent.querySelector(selector);
  if (!found) {
    throw new Error(`missing ${selector}`);
  }
  return found;
}

function items(): FakeElement[] {
  return byId("tree").querySelectorAll('[role="treeitem"]');
}

function nameOf(item: FakeElement): string {
  return find(item, ".name").textContent;
}

function names(): string[] {
  return items().map(nameOf);
}

function item(name: string): FakeElement {
  const found = items().find((candidate) => nameOf(candidate) === name);
  if (!found) {
    throw new Error(`missing tree item ${name}`);
  }
  return found;
}

function checkbox(target: FakeElement): FakeElement {
  return find(target, "input");
}

function setCheck(target: FakeElement, checked: boolean): void {
  const check = checkbox(target);
  check.checked = checked;
  check.dispatch("change");
}

function key(target: FakeElement, name: string): boolean {
  return target.dispatch("keydown", { key: name }).defaultPrevented;
}

function hidden(id: string): boolean {
  return byId(id).classList.contains("hidden");
}

function downloadLabel(): string {
  return byId("download-btn").textContent;
}

function lastCall(url: string): Call {
  const found = calls.findLast((call) => call.url === url);
  if (!found) {
    throw new Error(`no call to ${url}`);
  }
  return found;
}

describe("app", () => {
  before(async () => {
    await import("../src/app.ts");
    await settle();
  });

  afterEach(() => {
    handler = defaultHandler;
  });

  it("restores an existing session and renders the collapsed tree", () => {
    assert.equal(calls[0]?.url, "/api/session");
    assert.equal(calls[1]?.url, "/api/tree?snapshot=latest");
    assert.equal(hidden("connect"), true);
    assert.equal(hidden("browser"), false);
    assert.deepEqual(names(), ["DCIM", "Music/Album", "song.mp3", "notes.txt"]);

    assert.equal(item("DCIM").getAttribute("aria-expanded"), "false");
    assert.equal(item("DCIM").getAttribute("aria-level"), "1");
    assert.equal(item("Music/Album").getAttribute("aria-expanded"), "true");
    assert.equal(item("song.mp3").getAttribute("aria-level"), "2");
    assert.deepEqual(
      items().map((node) => node.tabIndex),
      [0, -1, -1, -1],
    );

    const notes = item("notes.txt");
    const link = find(notes, "a.dl");
    assert.equal(link.href, "/api/file?snapshot=latest&path=%2Fstorage%2Femulated%2F0%2Fnotes.txt");
    assert.equal(link.getAttribute("download"), "notes.txt");
    assert.equal(find(notes, ".size").textContent, "1 B");
    assert.equal(find(notes, ".name").title, "/notes.txt");
    assert.equal(item("DCIM").querySelector("a.dl"), null);
    assert.equal(find(item("DCIM"), ".size").textContent, "");

    assert.equal(byId("download-btn").disabled, true);
    assert.equal(downloadLabel(), "Download selected (.zip)");
  });

  it("selecting a file updates the download button and parent state", () => {
    setCheck(item("song.mp3"), true);
    assert.equal(downloadLabel(), "Download 1 selected (.zip)");
    assert.equal(byId("download-btn").disabled, false);
    assert.equal(item("song.mp3").getAttribute("aria-checked"), "true");
    assert.equal(item("Music/Album").getAttribute("aria-checked"), "true");
    assert.equal(checkbox(item("Music/Album")).checked, true);
    assert.equal(byId("select-all").indeterminate, true);
    assert.equal(byId("select-all").checked, false);

    setCheck(item("song.mp3"), false);
    assert.equal(downloadLabel(), "Download selected (.zip)");
    assert.equal(byId("download-btn").disabled, true);
    assert.equal(item("Music/Album").getAttribute("aria-checked"), "false");
    assert.equal(byId("select-all").indeterminate, false);
  });

  it("select all toggles every file", () => {
    const selectAll = byId("select-all");
    selectAll.checked = true;
    selectAll.dispatch("change");
    assert.equal(downloadLabel(), "Download 5 selected (.zip)");
    for (const node of items()) {
      assert.equal(node.getAttribute("aria-checked"), "true");
    }
    assert.equal(checkbox(item("DCIM")).checked, true);

    selectAll.checked = false;
    selectAll.dispatch("change");
    assert.equal(downloadLabel(), "Download selected (.zip)");
    assert.equal(item("DCIM").getAttribute("aria-checked"), "false");
  });

  it("expands and collapses directories with the chevron and the name", () => {
    const dcim = item("DCIM");
    find(dcim, ".chevron").click();
    assert.equal(dcim.getAttribute("aria-expanded"), "true");
    assert.equal(dcim.classList.contains("open"), true);
    assert.deepEqual(names(), ["DCIM", "Camera", "c.png", "Music/Album", "song.mp3", "notes.txt"]);
    assert.notEqual(doc.activeElement, dcim);

    setCheck(item("Camera"), true);
    assert.equal(dcim.getAttribute("aria-checked"), "mixed");
    assert.equal(checkbox(dcim).indeterminate, true);
    assert.equal(downloadLabel(), "Download 2 selected (.zip)");
    setCheck(item("Camera"), false);
    assert.equal(dcim.getAttribute("aria-checked"), "false");

    find(dcim, ".name").click();
    assert.equal(dcim.getAttribute("aria-expanded"), "false");
    assert.equal(dcim.classList.contains("open"), false);
    assert.deepEqual(names(), ["DCIM", "Music/Album", "song.mp3", "notes.txt"]);
    assert.equal(doc.activeElement, dcim);
    assert.equal(dcim.tabIndex, 0);
  });

  it("row clicks move focus but checkbox and link clicks do not", () => {
    const notes = item("notes.txt");
    const row = notes.children[0];
    assert.ok(row);
    row.click();
    assert.equal(doc.activeElement, notes);
    assert.equal(notes.tabIndex, 0);
    assert.equal(item("DCIM").tabIndex, -1);

    checkbox(item("song.mp3")).click();
    find(item("song.mp3"), "a.dl").click();
    assert.equal(doc.activeElement, notes);
  });

  it("supports keyboard navigation of the tree", () => {
    const dcim = item("DCIM");
    assert.equal(key(dcim, "ArrowDown"), true);
    assert.equal(doc.activeElement, item("Music/Album"));
    key(item("Music/Album"), "End");
    assert.equal(doc.activeElement, item("notes.txt"));
    key(item("notes.txt"), "Home");
    assert.equal(doc.activeElement, dcim);
    key(dcim, "ArrowUp");
    assert.equal(doc.activeElement, dcim);

    key(dcim, "ArrowRight");
    assert.equal(dcim.getAttribute("aria-expanded"), "true");
    assert.equal(doc.activeElement, dcim);
    key(dcim, "ArrowRight");
    assert.equal(doc.activeElement, item("Camera"));

    key(item("Camera"), "ArrowLeft");
    assert.equal(doc.activeElement, dcim);
    key(dcim, "ArrowLeft");
    assert.equal(dcim.getAttribute("aria-expanded"), "false");
    key(dcim, "ArrowLeft");
    assert.equal(doc.activeElement, dcim);

    key(dcim, "Enter");
    assert.equal(dcim.getAttribute("aria-expanded"), "true");
    key(dcim, "Enter");
    assert.equal(dcim.getAttribute("aria-expanded"), "false");

    const notes = item("notes.txt");
    key(notes, " ");
    assert.equal(downloadLabel(), "Download 1 selected (.zip)");
    key(notes, " ");
    assert.equal(downloadLabel(), "Download selected (.zip)");
    key(dcim, " ");
    assert.equal(downloadLabel(), "Download 3 selected (.zip)");
    key(dcim, " ");
    assert.equal(downloadLabel(), "Download selected (.zip)");

    let downloads = 0;
    find(notes, "a.dl").addEventListener("click", () => {
      downloads++;
    });
    key(notes, "Enter");
    assert.equal(downloads, 1);

    assert.equal(key(notes, "x"), false);
    assert.equal(key(byId("tree"), "ArrowDown"), false);
  });

  it("downloads the selection as a zip archive", async () => {
    setCheck(item("notes.txt"), true);
    setCheck(item("song.mp3"), true);

    const appended: FakeElement[] = [];
    const append = doc.body.append.bind(doc.body);
    const appendMock = mock.method(doc.body, "append", (...added: FakeElement[]) => {
      appended.push(...added);
      append(...added);
    });
    const created = mock.method(URL, "createObjectURL", () => "blob:fake");
    const revoked = mock.method(URL, "revokeObjectURL", () => {});

    const pending = deferred<Response>();
    handler = (url, method, body) =>
      url === "/api/archive" ? pending.promise : defaultHandler(url, method, body);

    byId("download-btn").click();
    assert.equal(byId("download-btn").disabled, true);
    assert.equal(downloadLabel(), "Preparing…");
    pending.resolve(respond({}));
    await settle();

    const call = lastCall("/api/archive");
    assert.equal(call.method, "POST");
    assert.deepEqual(JSON.parse(call.body ?? ""), {
      snapshot: "latest",
      paths: [`${base}notes.txt`, `${base}Music/Album/song.mp3`],
    });
    assert.equal(created.mock.callCount(), 1);
    assert.deepEqual(revoked.mock.calls[0]?.arguments, ["blob:fake"]);
    assert.equal(appended.length, 1);
    const anchor = appended[0];
    assert.ok(anchor);
    assert.equal(anchor.tagName, "A");
    assert.equal(anchor.href, "blob:fake");
    assert.equal(anchor.download, "cryptsync-export.zip");
    assert.equal(anchor.parentElement, null);
    assert.equal(downloadLabel(), "Download 2 selected (.zip)");
    assert.equal(byId("download-btn").disabled, false);

    appendMock.mock.restore();
    created.mock.restore();
    revoked.mock.restore();
  });

  it("shows an error when the archive fails", async () => {
    handler = (url, method, body) =>
      url === "/api/archive"
        ? respond({ error: "archive broke" }, 502)
        : defaultHandler(url, method, body);
    byId("download-btn").click();
    await settle();
    assert.equal(byId("browser-status").textContent, "archive broke");
    assert.equal(byId("browser-status").classList.contains("error"), true);
    assert.equal(downloadLabel(), "Download 2 selected (.zip)");
    assert.equal(byId("download-btn").disabled, false);
  });

  it("disconnects and returns to the connect panel", async () => {
    byId("disconnect-btn").click();
    await settle();
    assert.equal(lastCall("/api/disconnect").method, "POST");
    assert.equal(hidden("browser"), true);
    assert.equal(hidden("connect"), false);
    assert.equal(byId("tree").children.length, 0);
    assert.equal(byId("download-btn").disabled, true);
    assert.equal(downloadLabel(), "Download selected (.zip)");
    assert.equal(byId("select-all").checked, false);
  });

  it("switches between the drive and raw panes", () => {
    const [drive, raw] = doc.querySelectorAll(".tab");
    assert.ok(drive);
    assert.ok(raw);
    raw.click();
    assert.equal(raw.classList.contains("active"), true);
    assert.equal(drive.classList.contains("active"), false);
    assert.equal(hidden("pane-raw"), false);
    assert.equal(hidden("pane-drive"), true);

    drive.click();
    assert.equal(drive.classList.contains("active"), true);
    assert.equal(hidden("pane-raw"), true);
    assert.equal(hidden("pane-drive"), false);

    raw.click();
  });

  it("reports a failed connect and restores the button", async () => {
    byId("body").value = "type = s3";
    byId("password").value = "pw";
    byId("remote").value = "r";
    byId("prefix").value = "p";

    const pending = deferred<Response>();
    handler = (url, method, body) =>
      url === "/api/connect" ? pending.promise : defaultHandler(url, method, body);
    byId("connect-btn").click();
    assert.equal(byId("connect-btn").disabled, true);
    assert.equal(byId("connect-btn").textContent, "Connecting…");
    pending.resolve(respond({ error: "bad password" }, 502));
    await settle();

    const call = lastCall("/api/connect");
    assert.equal(call.method, "POST");
    assert.deepEqual(JSON.parse(call.body ?? ""), {
      mode: "raw",
      token: "",
      body: "type = s3",
      password: "pw",
      remote: "r",
      prefix: "p",
    });
    assert.equal(byId("connect-status").textContent, "bad password");
    assert.equal(byId("connect-status").classList.contains("error"), true);
    assert.equal(byId("connect-btn").disabled, false);
    assert.equal(byId("connect-btn").textContent, "Connect");
    assert.equal(hidden("connect"), false);
  });

  it("connects and shows an empty repository", async () => {
    handler = (url, method, body) =>
      url.startsWith("/api/tree") ? respond({ nodes: [] }) : defaultHandler(url, method, body);
    byId("connect-btn").click();
    await settle();
    assert.equal(byId("connect-status").textContent, "");
    assert.equal(byId("connect-status").classList.contains("error"), false);
    assert.equal(hidden("connect"), true);
    assert.equal(hidden("browser"), false);
    assert.match(byId("tree").innerHTML, /Nothing here yet/);
    assert.equal(items().length, 0);
  });

  it("shows an error when the tree cannot be loaded", async () => {
    byId("disconnect-btn").click();
    await settle();
    handler = (url, method, body) =>
      url.startsWith("/api/tree")
        ? respond({ error: "tree broke" }, 502)
        : defaultHandler(url, method, body);
    byId("connect-btn").click();
    await settle();
    assert.equal(byId("browser-status").textContent, "tree broke");
    assert.equal(byId("browser-status").classList.contains("error"), true);
    assert.equal(byId("tree").innerHTML, "");
    assert.equal(items().length, 0);
  });

  it("signs in with Google Drive and fills the token", async () => {
    const pending = deferred<Response>();
    handler = (url, method, body) =>
      url === "/api/oauth/drive" ? pending.promise : defaultHandler(url, method, body);
    byId("oauth-btn").click();
    assert.equal(byId("oauth-btn").disabled, true);
    assert.equal(byId("oauth-btn").textContent, "Waiting for browser…");
    assert.match(byId("oauth-status").textContent, /browser window/);
    pending.resolve(respond({ token: `{"access_token":"new"}` }));
    await settle();

    assert.equal(lastCall("/api/oauth/drive").method, "POST");
    assert.equal(byId("token").value, `{"access_token":"new"}`);
    assert.equal(byId("oauth-status").textContent, "Signed in. Enter your password and connect.");
    assert.equal(byId("oauth-status").classList.contains("error"), false);
    assert.equal(byId("oauth-btn").disabled, false);
    assert.equal(byId("oauth-btn").textContent, "Sign in with Google Drive");
  });

  it("shows an error when Google sign-in fails", async () => {
    handler = (url, method, body) =>
      url === "/api/oauth/drive"
        ? respond({ error: "sign-in failed" }, 502)
        : defaultHandler(url, method, body);
    byId("oauth-btn").click();
    await settle();
    assert.equal(byId("oauth-status").textContent, "sign-in failed");
    assert.equal(byId("oauth-status").classList.contains("error"), true);
    assert.equal(byId("oauth-btn").disabled, false);
  });
});
