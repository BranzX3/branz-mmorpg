import { load, dump } from "js-yaml";
import "./style.css";

const state = {
  documents: [],
  selected: 0,
  view: "graph",
  errors: [],
};

const app = document.querySelector("#app");

function validate(document) {
  const errors = [];
  if (!document || typeof document !== "object") return ["Document must be a YAML object."];
  if (!document.type) errors.push("Missing type.");
  if (!document.id && document.type !== "quest_migration") errors.push("Missing permanent content ID.");
  if (document.type === "quest") {
    if (!document.stages || !Object.keys(document.stages).length) errors.push("Quest has no stages.");
    if (!document.stages?.[document.start_stage]) errors.push("start_stage does not exist.");
    const reachable = new Set();
    const visiting = new Set();
    const walk = (id) => {
      if (!id || reachable.has(id)) return;
      if (visiting.has(id)) {
        errors.push(`Unbounded stage cycle at ${id}.`);
        return;
      }
      const stage = document.stages?.[id];
      if (!stage) {
        errors.push(`Unknown stage reference: ${id}.`);
        return;
      }
      visiting.add(id);
      reachable.add(id);
      walk(stage.next);
      walk(stage.failure);
      visiting.delete(id);
    };
    walk(document.start_stage);
    Object.keys(document.stages || {}).filter((id) => !reachable.has(id))
      .forEach((id) => errors.push(`Unreachable stage: ${id}.`));
  }
  if (document.type === "dialogue") {
    if (!document.nodes?.[document.start_node]) errors.push("start_node does not exist.");
    Object.entries(document.nodes || {}).forEach(([id, node]) => {
      const targets = [node.next, node.jump_target, ...(node.choices || []).map((c) => c.next)];
      targets.filter(Boolean).filter((next) => !document.nodes[next])
        .forEach((next) => errors.push(`${id} references unknown node ${next}.`));
    });
  }
  if (document.type === "cutscene") {
    const groups = ["setup", "timeline", "final_state", "skip_state", "cleanup"];
    const ids = groups.flatMap((key) => document[key] || []).map((action) => action.id);
    if (new Set(ids).size !== ids.length) errors.push("Cutscene action IDs must be unique.");
    if (!(document.cleanup || []).some((a) => a.action_type === "camera_restore")) {
      errors.push("Cleanup must restore the camera.");
    }
    if (!(document.cleanup || []).some((a) =>
      a.action_type === "freeze_input" && String(a.values?.enabled) === "false")) {
      errors.push("Cleanup must unfreeze player input.");
    }
  }
  return errors;
}

function render() {
  const active = state.documents[state.selected];
  state.errors = active ? validate(active.data) : [];
  app.innerHTML = `
    <header>
      <div><span class="eyebrow">LOCAL AUTHORING SURFACE</span>
        <h1>Branz Quest Director</h1></div>
      <div class="header-actions">
        <label class="button secondary">Open YAML<input id="files" type="file" accept=".yml,.yaml" multiple /></label>
        <button id="export" ${active ? "" : "disabled"}>Export staging YAML</button>
      </div>
    </header>
    <section class="status ${state.errors.length ? "bad" : "good"}">
      <strong>${active ? active.name : "No content loaded"}</strong>
      <span>${active ? (state.errors.length ? `${state.errors.length} validation issue(s)` : "Schema checks passed") : "Open quest, dialogue, cutscene, or migration YAML"}</span>
    </section>
    <div class="workspace">
      <aside>
        <h2>Content</h2>
        <div id="documents">${state.documents.map((doc, index) => `
          <button class="document ${index === state.selected ? "active" : ""}" data-index="${index}">
            <span>${doc.data.type || "unknown"}</span><strong>${doc.data.id || doc.name}</strong>
          </button>`).join("") || `<p class="muted">Files remain on this device. Nothing is uploaded.</p>`}
        </div>
        <h2>Validation</h2>
        <ul class="errors">${state.errors.map((error) => `<li>${escapeHtml(error)}</li>`).join("") || "<li>No local errors.</li>"}</ul>
      </aside>
      <section class="editor">
        <nav>
          ${["graph", "timeline", "source"].map((view) =>
            `<button data-view="${view}" class="${state.view === view ? "active" : ""}">${view}</button>`).join("")}
        </nav>
        <div id="canvas">${renderCanvas(active)}</div>
      </section>
    </div>`;
  bind();
}

function renderCanvas(active) {
  if (!active) return `<div class="empty"><div class="seal">B</div><h2>Build the playable path</h2><p>Inspect branching, action timing, and canonical cleanup before promoting YAML to the live catalog.</p></div>`;
  if (state.view === "source") return `<textarea id="source" spellcheck="false">${escapeHtml(dump(active.data, { noRefs: true, lineWidth: 100, sortKeys: false }))}</textarea>`;
  if (state.view === "timeline") return renderTimeline(active.data);
  return renderGraph(active.data);
}

function renderGraph(data) {
  const values = data.type === "quest" ? data.stages
    : data.type === "dialogue" ? data.nodes : null;
  if (!values) return `<div class="empty"><h2>${escapeHtml(data.id || data.type)}</h2><p>This content uses the timeline or source view.</p></div>`;
  return `<div class="graph">${Object.entries(values).map(([id, node], index) => `
    <article class="node" style="--order:${index}">
      <span>${data.type === "quest" ? (node.completion_policy || "all") : (node.node_type || "node")}</span>
      <h3>${escapeHtml(id)}</h3>
      <p>${data.type === "quest" ? `${(node.objectives || []).length} objective(s)` : escapeHtml(node.text || node.speaker || "Flow node")}</p>
      <footer>${escapeHtml(node.next || node.jump_target || "canonical end")}</footer>
    </article>`).join("")}</div>`;
}

function renderTimeline(data) {
  if (data.type !== "cutscene") return `<div class="empty"><h2>Timeline reserved for cutscenes</h2><p>Switch to graph or source for this content type.</p></div>`;
  const actions = [...(data.setup || []), ...(data.timeline || []), ...(data.final_state || [])]
    .sort((a, b) => (a.at_ms || 0) - (b.at_ms || 0) || (a.priority || 0) - (b.priority || 0));
  const maximum = Math.max(1, ...actions.map((a) => a.at_ms || 0));
  return `<div class="timeline"><div class="ruler">0 ms <span>${maximum} ms</span></div>
    ${["camera", "actor", "world", "player"].map((track) => `<div class="track"><strong>${track}</strong>
      <div class="rail">${actions.filter((a) => a.track === track).map((action) =>
        `<button class="keyframe" title="${escapeHtml(action.action_type)}" style="left:${((action.at_ms || 0) / maximum) * 92}%">${escapeHtml(action.id)}</button>`).join("")}</div>
    </div>`).join("")}</div>`;
}

function bind() {
  document.querySelector("#files")?.addEventListener("change", async (event) => {
    for (const file of event.target.files) {
      try {
        state.documents.push({ name: file.name, data: load(await file.text()) });
      } catch (error) {
        state.documents.push({ name: file.name, data: { type: "parse_error", error: error.message } });
      }
    }
    state.selected = Math.max(0, state.documents.length - event.target.files.length);
    render();
  });
  document.querySelectorAll(".document").forEach((button) => button.addEventListener("click", () => {
    state.selected = Number(button.dataset.index);
    render();
  }));
  document.querySelectorAll("[data-view]").forEach((button) => button.addEventListener("click", () => {
    state.view = button.dataset.view;
    render();
  }));
  document.querySelector("#source")?.addEventListener("input", (event) => {
    try {
      state.documents[state.selected].data = load(event.target.value);
      state.errors = validate(state.documents[state.selected].data);
    } catch (error) {
      state.errors = [error.message];
    }
  });
  document.querySelector("#export")?.addEventListener("click", () => {
    const active = state.documents[state.selected];
    const errors = validate(active.data);
    if (errors.length) return alert(`Fix validation before export:\n${errors.join("\n")}`);
    const blob = new Blob([dump(active.data, { noRefs: true, lineWidth: 100 })], { type: "text/yaml" });
    const link = Object.assign(document.createElement("a"), {
      href: URL.createObjectURL(blob),
      download: active.name.replace(/\.ya?ml$/i, "") + ".staging.yml",
    });
    link.click();
    URL.revokeObjectURL(link.href);
  });
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (character) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[character]);
}

render();
