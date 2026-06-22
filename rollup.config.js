import { defineConfig } from "rollup";
import { nodeResolve } from "@rollup/plugin-node-resolve";
import typescript from "@rollup/plugin-typescript";
// import multi from "@rollup/plugin-multi-entry";
import terser from "@rollup/plugin-terser";
import css from "rollup-plugin-import-css";
import { ZBAR_WASM_REPOSITORY } from "@undecaf/barcode-detector-polyfill/zbar-wasm";
import replace from '@rollup/plugin-replace';
import copy from 'rollup-plugin-copy';

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
        replace({
            values: {
                [ZBAR_WASM_REPOSITORY]: '../../zbar-wasm',
            },
            preventAssignment: true,
        }),
        copy({
            targets: [
                {
                    src: 'node_modules/@undecaf/zbar-wasm/dist/zbar.wasm',
                    dest: 'build/'
                }
            ],
        }),
        nodeResolve(),
        terser(),
        css(),
    ],
});
