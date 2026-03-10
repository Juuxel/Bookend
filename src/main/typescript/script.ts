import { BrowserCodeReader, BrowserMultiFormatReader } from '@zxing/browser';

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
    private currentDevice: string | undefined;
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
        const deviceSelect = shadowRoot.getElementById("device-select") as HTMLSelectElement;

        addDevicesTo(deviceSelect)
            .then(({ defaultDevice }) => {
                this.currentDevice = defaultDevice;
            });

        deviceSelect.onchange = () => {
            this.currentDevice = deviceSelect.value;
            this.scan();
        };
    }

    scan() {
        if (this.classList.contains("visible")) {
            scanToField(this.field!, this.currentDevice, this.video!, this.onscan)
                .then(() => this.hide());
        }
    }

    hide() {
        this.classList.remove("visible");
        BrowserCodeReader.releaseAllStreams();
    }

    clear() {
        if (this.field) {
            this.field.value = "";
        }
    }
}

async function scanToField(field: HTMLInputElement, deviceId: string | undefined, preview: HTMLVideoElement, callback: ((_: string) => void) | undefined) {
    const codeReader = new BrowserMultiFormatReader();
    const result = await codeReader.decodeOnceFromVideoDevice(deviceId, preview);
    field.value += result.getText();
    if (callback) {
        callback(result.getText());
    }
}

async function addDevicesTo(select: HTMLSelectElement): Promise<{ defaultDevice: string | undefined }> {
    const devices = await BrowserCodeReader.listVideoInputDevices();

    for (const dev of devices) {
        const optionElement = document.createElement("option");
        optionElement.value = dev.deviceId;
        optionElement.textContent = dev.label;
        select.appendChild(optionElement);
    }

    return { defaultDevice: devices[0].deviceId };
}
