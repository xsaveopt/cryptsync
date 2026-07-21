import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  buildTree,
  collapseTree,
  filePaths,
  formatSize,
  nodeState,
  sortedChildren,
} from "../src/tree.ts";
import type { FileNode } from "../src/types.ts";

function file(path: string, size: number, display = path): FileNode {
  return { path, display, type: "file", size };
}

describe("buildTree", () => {
  it("nests files by their display path and keeps the real path on leaves", () => {
    const root = buildTree([
      file("/storage/emulated/0/DCIM/photo.heic", 2048),
      file("/storage/emulated/0/doc.txt", 11),
    ]);

    const zero = root.children.get("storage")?.children.get("emulated")?.children.get("0");
    assert.ok(zero, "expected the /storage/emulated/0 chain");
    assert.deepEqual([...zero.children.keys()].sort(), ["DCIM", "doc.txt"]);

    const photo = zero.children.get("DCIM")?.children.get("photo.heic");
    assert.equal(photo?.type, "file");
    assert.equal(photo?.size, 2048);
    assert.equal(photo?.real, "/storage/emulated/0/DCIM/photo.heic");
  });

  it("builds structure from the display path while downloading uses the real path", () => {
    const root = buildTree([
      file(
        "/data/app/media_cache/storage/emulated/0/Pictures/x.heic",
        10,
        "/storage/emulated/0/Pictures/x.heic",
      ),
    ]);
    const pictures = sortedChildren(collapseTree(root))[0];
    assert.equal(pictures?.name, "storage/emulated/0/Pictures");
    const leaf = pictures?.children.get("x.heic");
    assert.equal(leaf?.real, "/data/app/media_cache/storage/emulated/0/Pictures/x.heic");
  });
});

describe("collapseTree", () => {
  it("collapses a single-child folder chain down to the file", () => {
    const root = collapseTree(buildTree([file("/storage/emulated/0/DCIM/Camera/photo.jpg", 386)]));
    const top = sortedChildren(root);
    assert.equal(top.length, 1);
    assert.equal(top[0]?.name, "storage/emulated/0/DCIM/Camera");
    const inner = sortedChildren(top[0]!);
    assert.equal(inner.length, 1);
    assert.equal(inner[0]?.name, "photo.jpg");
    assert.equal(inner[0]?.type, "file");
  });

  it("stops collapsing where a folder branches", () => {
    const root = collapseTree(buildTree([file("/a/b/c/one.txt", 1), file("/a/b/d/two.txt", 2)]));
    const top = sortedChildren(root);
    assert.equal(top.length, 1);
    assert.equal(top[0]?.name, "a/b");
    assert.deepEqual(
      sortedChildren(top[0]!).map((n) => n.name),
      ["c", "d"],
    );
  });
});

describe("filePaths and nodeState", () => {
  const root = collapseTree(
    buildTree([file("/a/one.txt", 1), file("/a/two.txt", 2), file("/b/three.txt", 3)]),
  );
  const folderA = sortedChildren(root).find((n) => n.name === "a")!;

  it("filePaths collects the real paths of files under a folder", () => {
    assert.deepEqual(filePaths(folderA).sort(), ["/a/one.txt", "/a/two.txt"]);
  });

  it("a folder is checked only when all its files are selected", () => {
    const selected = new Set(["/a/one.txt", "/a/two.txt"]);
    assert.equal(
      nodeState(folderA, (p) => selected.has(p)),
      "checked",
    );
  });

  it("a folder is indeterminate when only some files are selected", () => {
    const selected = new Set(["/a/one.txt"]);
    assert.equal(
      nodeState(folderA, (p) => selected.has(p)),
      "indeterminate",
    );
  });

  it("a folder is unchecked when none of its files are selected", () => {
    assert.equal(
      nodeState(folderA, () => false),
      "unchecked",
    );
  });
});

describe("sortedChildren", () => {
  it("lists directories before files, each alphabetically", () => {
    const root = buildTree([
      file("/b.txt", 1),
      file("/Alpha/x", 1),
      file("/a.txt", 1),
      file("/Beta/y", 1),
    ]);
    const names = sortedChildren(root).map((node) => node.name);
    assert.deepEqual(names, ["Alpha", "Beta", "a.txt", "b.txt"]);
  });
});

describe("formatSize", () => {
  it("scales bytes into human units", () => {
    assert.equal(formatSize(0), "0 B");
    assert.equal(formatSize(512), "512 B");
    assert.equal(formatSize(1024), "1.0 KB");
    assert.equal(formatSize(1536), "1.5 KB");
    assert.equal(formatSize(1024 * 1024), "1.0 MB");
    assert.equal(formatSize(5 * 1024 * 1024 * 1024), "5.0 GB");
  });
});
