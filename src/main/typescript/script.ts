/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { BarcodeDetectorPolyfill } from '@undecaf/barcode-detector-polyfill';

const styles: CSSStyleSheet[] = [];

export function addStyleSheet(styleSheet: CSSStyleSheet) {
    styles.push(styleSheet);
}

export class ScannableTextBoxElement extends HTMLElement {
    private scannerBox: ScannerElement | undefined;

    constructor() {
        super();
    }

    connectedCallback() {
        const template = document.getElementById("scannable-text-box-template") as HTMLTemplateElement;
        const content = template.content;

        const shadowRoot = this.attachShadow({ mode: "open" });
        shadowRoot.appendChild(document.importNode(content, true));
        shadowRoot.adoptedStyleSheets.push(...styles);

        const scannerButton = shadowRoot.getElementById('camera-button') as HTMLButtonElement;
        const scannerBox = document.createElement('code-scanner') as ScannerElement;
        scannerBox.field = shadowRoot.getElementById("text-box") as HTMLInputElement;
        scannerBox.classList.add('attached-popup');
        scannerButton.onclick = () => {
            if (scannerBox.classList.toggle('visible')) {
                scannerBox.scan();
            } else {
                scannerBox.hide();
            }
        };

        const onscan = this.getAttribute("onscan");
        if (onscan) {
            scannerBox.onscan = (text) => (eval(onscan) as ((_: string) => void))(text);
        }

        this.scannerBox = scannerBox;
        shadowRoot.getElementById("main")!.appendChild(scannerBox);
    }

    public clear() {
        this.scannerBox?.clear();
    }
}

export class ScannerElement extends HTMLElement {
    field: HTMLInputElement | undefined;
    private video: HTMLVideoElement | undefined;
    onscan: ((_: string) => void) | undefined;

    constructor() {
        super();
    }

    connectedCallback() {
        const template = document.getElementById("scanner-template") as HTMLTemplateElement;
        const content = template.content;

        const shadowRoot = this.attachShadow({ mode: "open" });
        shadowRoot.appendChild(document.importNode(content, true));
        shadowRoot.adoptedStyleSheets.push(...styles);

        const targetFieldId = this.getAttribute("target-field");
        if (targetFieldId) {
            this.field = document.getElementById(targetFieldId) as HTMLInputElement;
        }

        this.video = shadowRoot.getElementById("preview-video") as HTMLVideoElement;
    }

    scan() {
        if (this.classList.contains("visible")) {
            navigator.mediaDevices
                .getUserMedia({ audio: false, video: { facingMode: "environment" } })
                .then((stream) => {
                    this.video!.srcObject = stream;
                    return scanToField(this.field!, this.video!, this.onscan);
                })
                .then(() => this.hide());
        }
    }

    hide() {
        this.classList.remove("visible");

        const videoSrc = this.video!.srcObject;
        if (videoSrc instanceof MediaStream) {
            for (const track of videoSrc.getTracks()) {
                track.stop();
            }
        }
    }

    clear() {
        if (this.field) {
            this.field.value = "";
        }
    }
}

async function scanToField(field: HTMLInputElement, source: HTMLVideoElement, callback: ((_: string) => void) | undefined) {
    const detector = new BarcodeDetectorPolyfill();
    const result = await detector.detect(source);
    const resultValue = result[0].rawValue;
    field.value += resultValue;
    if (callback) {
        callback(resultValue);
    }
}
