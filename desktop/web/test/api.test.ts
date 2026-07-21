import assert from "node:assert/strict";
import { afterEach, describe, it, mock } from "node:test";
import { authorizeDrive, connect, errorText, fileUrl, getTree } from "../src/api.ts";
import type { ConnectRequest } from "../src/api.ts";

function jsonResponse(body: unknown, ok = true, status = 200): Response {
  return {
    ok,
    status,
    statusText: "Bad Gateway",
    json: async () => body,
  } as unknown as Response;
}

const request: ConnectRequest = {
  mode: "drive",
  token: "t",
  body: "",
  remote: "",
  prefix: "",
  password: "p",
};

describe("fileUrl", () => {
  it("encodes the snapshot and path", () => {
    assert.equal(
      fileUrl("abc123", "/DCIM/a b.heic"),
      "/api/file?snapshot=abc123&path=%2FDCIM%2Fa%20b.heic",
    );
  });
});

describe("connect", () => {
  afterEach(() => {
    mock.restoreAll();
  });

  it("returns the snapshots from the API", async () => {
    mock.method(globalThis, "fetch", async () =>
      jsonResponse({ snapshots: [{ short_id: "abc" }] }),
    );
    const snapshots = await connect(request);
    assert.equal(snapshots.length, 1);
    assert.equal(snapshots[0]?.short_id, "abc");
  });

  it("throws the server error message on failure", async () => {
    mock.method(globalThis, "fetch", async () =>
      jsonResponse({ error: "bad password" }, false, 502),
    );
    await assert.rejects(() => connect(request), /bad password/);
  });
});

describe("getTree", () => {
  afterEach(() => {
    mock.restoreAll();
  });

  it("returns the node list", async () => {
    mock.method(globalThis, "fetch", async () =>
      jsonResponse({ nodes: [{ path: "/a", type: "file", size: 1 }] }),
    );
    const nodes = await getTree("latest");
    assert.equal(nodes[0]?.path, "/a");
  });
});

describe("authorizeDrive", () => {
  afterEach(() => {
    mock.restoreAll();
  });

  it("returns the token from the API", async () => {
    mock.method(globalThis, "fetch", async () => jsonResponse({ token: `{"access_token":"x"}` }));
    assert.equal(await authorizeDrive(), `{"access_token":"x"}`);
  });
});

describe("errorText", () => {
  it("falls back to the status text when the body is not JSON", async () => {
    const resp = {
      statusText: "Bad Gateway",
      json: async () => {
        throw new Error("not json");
      },
    } as unknown as Response;
    assert.equal(await errorText(resp), "Bad Gateway");
  });
});
