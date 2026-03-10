import { defineConfig } from "rollup";
import { nodeResolve } from "@rollup/plugin-node-resolve";
import typescript from "@rollup/plugin-typescript";
// import multi from "@rollup/plugin-multi-entry";
import terser from "@rollup/plugin-terser";
import css from "rollup-plugin-import-css";

export default defineConfig({
    input: "src/main/typescript/script.ts",
    output: {
        dir: "build",
        format: "es",
        sourcemap: "inline",
    },
    plugins: [
        typescript(),
        // multi({
        //     entryFileName: "script.js",
        // }),
        nodeResolve(),
        terser(),
        css(),
    ],
});
