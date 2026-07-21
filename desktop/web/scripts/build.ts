import { build } from "esbuild";
import { cpSync, mkdirSync, rmSync } from "node:fs";

rmSync("dist", { recursive: true, force: true });
mkdirSync("dist", { recursive: true });

await build({
  entryPoints: ["src/app.ts"],
  bundle: true,
  minify: true,
  format: "esm",
  target: ["es2020"],
  legalComments: "none",
  outfile: "dist/app.js",
});

await build({
  entryPoints: ["static/style.css"],
  minify: true,
  legalComments: "none",
  outfile: "dist/style.css",
});

cpSync("static/index.html", "dist/index.html");
