export interface FakeEvent {
  type: string;
  key?: string;
  target: FakeElement;
  defaultPrevented: boolean;
  preventDefault: () => void;
  stopPropagation: () => void;
}

type Listener = (event: FakeEvent) => void;

class FakeClassList {
  private readonly names = new Set<string>();

  add(...names: string[]): void {
    for (const name of names) {
      this.names.add(name);
    }
  }

  remove(...names: string[]): void {
    for (const name of names) {
      this.names.delete(name);
    }
  }

  toggle(name: string, force?: boolean): boolean {
    const on = force ?? !this.names.has(name);
    if (on) {
      this.names.add(name);
    } else {
      this.names.delete(name);
    }
    return on;
  }

  contains(name: string): boolean {
    return this.names.has(name);
  }

  set(value: string): void {
    this.names.clear();
    this.add(...value.split(/\s+/).filter(Boolean));
  }

  toString(): string {
    return [...this.names].join(" ");
  }
}

interface SimpleSelector {
  tag?: string;
  classes: string[];
  attr?: [string, string];
}

function parseSelector(selector: string): SimpleSelector[] {
  return selector.split(",").map((part) => {
    const match = /^([a-z]+)?((?:\.[\w-]+)*)(?:\[([\w-]+)="([^"]*)"\])?$/i.exec(part.trim());
    if (!match) {
      throw new Error(`unsupported selector ${part}`);
    }
    const [, tag, classes, attrName, attrValue] = match;
    return {
      tag: tag?.toLowerCase(),
      classes: (classes ?? "").split(".").filter(Boolean),
      attr: attrName ? [attrName, attrValue ?? ""] : undefined,
    };
  });
}

export class FakeElement {
  readonly tagName: string;
  readonly classList = new FakeClassList();
  readonly dataset: Record<string, string> = {};
  readonly children: FakeElement[] = [];
  parentElement: FakeElement | null = null;
  tabIndex = 0;
  disabled = false;
  checked = false;
  indeterminate = false;
  value = "";
  type = "";
  href = "";
  title = "";
  download = "";
  private readonly attributes = new Map<string, string>();
  private readonly listeners = new Map<string, Listener[]>();
  private text = "";
  private html = "";

  private readonly owner: FakeDocument;

  constructor(owner: FakeDocument, tag: string) {
    this.owner = owner;
    this.tagName = tag.toUpperCase();
  }

  get id(): string {
    return this.getAttribute("id") ?? "";
  }

  set id(value: string) {
    this.setAttribute("id", value);
  }

  get className(): string {
    return this.classList.toString();
  }

  set className(value: string) {
    this.classList.set(value);
  }

  get textContent(): string {
    return this.text + this.children.map((child) => child.textContent).join("");
  }

  set textContent(value: string) {
    this.replaceChildren();
    this.text = value;
  }

  get innerHTML(): string {
    return this.html;
  }

  set innerHTML(value: string) {
    this.replaceChildren();
    this.text = "";
    this.html = value;
  }

  setAttribute(name: string, value: string): void {
    this.attributes.set(name, value);
  }

  getAttribute(name: string): string | null {
    return this.attributes.get(name) ?? null;
  }

  append(...nodes: FakeElement[]): void {
    for (const node of nodes) {
      node.remove();
      node.parentElement = this;
      this.children.push(node);
    }
  }

  remove(): void {
    const parent = this.parentElement;
    if (!parent) {
      return;
    }
    const index = parent.children.indexOf(this);
    if (index >= 0) {
      parent.children.splice(index, 1);
    }
    this.parentElement = null;
  }

  replaceChildren(): void {
    for (const child of this.children.splice(0)) {
      child.parentElement = null;
    }
    this.html = "";
  }

  addEventListener(type: string, listener: Listener): void {
    const list = this.listeners.get(type) ?? [];
    list.push(listener);
    this.listeners.set(type, list);
  }

  dispatch(type: string, init: { key?: string } = {}): FakeEvent {
    let stopped = false;
    const event: FakeEvent = {
      type,
      key: init.key,
      target: this,
      defaultPrevented: false,
      preventDefault: () => {
        event.defaultPrevented = true;
      },
      stopPropagation: () => {
        stopped = true;
      },
    };
    for (const node of [this, ...this.ancestors()]) {
      if (stopped) {
        break;
      }
      for (const listener of node.listeners.get(type) ?? []) {
        listener(event);
      }
    }
    return event;
  }

  click(): void {
    this.dispatch("click");
  }

  focus(): void {
    this.owner.activeElement = this;
  }

  matches(selector: string): boolean {
    return parseSelector(selector).some(
      (simple) =>
        (!simple.tag || simple.tag === this.tagName.toLowerCase()) &&
        simple.classes.every((name) => this.classList.contains(name)) &&
        (!simple.attr || this.getAttribute(simple.attr[0]) === simple.attr[1]),
    );
  }

  closest(selector: string): FakeElement | null {
    return [this, ...this.ancestors()].find((node) => node.matches(selector)) ?? null;
  }

  ancestors(): FakeElement[] {
    const parent = this.parentElement;
    return parent ? [parent, ...parent.ancestors()] : [];
  }

  descendants(): FakeElement[] {
    return this.children.flatMap((child) => [child, ...child.descendants()]);
  }

  querySelectorAll(selector: string): FakeElement[] {
    return this.descendants().filter((node) => node.matches(selector));
  }

  querySelector(selector: string): FakeElement | null {
    return this.querySelectorAll(selector)[0] ?? null;
  }
}

export class FakeDocument {
  readonly body: FakeElement;
  activeElement: FakeElement | null = null;

  constructor() {
    this.body = new FakeElement(this, "body");
  }

  createElement(tag: string): FakeElement {
    return new FakeElement(this, tag);
  }

  getElementById(id: string): FakeElement | null {
    return this.body.descendants().find((node) => node.id === id) ?? null;
  }

  querySelectorAll(selector: string): FakeElement[] {
    return this.body.querySelectorAll(selector);
  }

  add(parent: FakeElement, tag: string, id: string, className = ""): FakeElement {
    const node = this.createElement(tag);
    if (id) {
      node.id = id;
    }
    node.className = className;
    parent.append(node);
    return node;
  }
}

export function buildPage(): FakeDocument {
  const doc = new FakeDocument();
  const connect = doc.add(doc.body, "section", "connect", "panel");
  const tabs = doc.add(connect, "div", "", "tabs");
  const drive = doc.add(tabs, "button", "", "tab active");
  drive.dataset.mode = "drive";
  const raw = doc.add(tabs, "button", "", "tab");
  raw.dataset.mode = "raw";
  const paneDrive = doc.add(connect, "div", "pane-drive", "pane");
  doc.add(paneDrive, "button", "oauth-btn");
  doc.add(paneDrive, "p", "oauth-status", "status");
  doc.add(paneDrive, "textarea", "token");
  const paneRaw = doc.add(connect, "div", "pane-raw", "pane hidden");
  doc.add(paneRaw, "textarea", "body");
  doc.add(connect, "input", "password");
  doc.add(connect, "input", "remote");
  doc.add(connect, "input", "prefix");
  doc.add(connect, "button", "connect-btn", "primary");
  doc.add(connect, "p", "connect-status", "status");
  const browser = doc.add(doc.body, "section", "browser", "panel hidden");
  const label = doc.add(browser, "label", "", "select-all");
  doc.add(label, "input", "select-all").type = "checkbox";
  const download = doc.add(browser, "button", "download-btn", "primary");
  download.disabled = true;
  doc.add(browser, "button", "disconnect-btn");
  doc.add(browser, "p", "browser-status", "status");
  doc.add(browser, "div", "tree", "tree").setAttribute("role", "tree");
  return doc;
}
