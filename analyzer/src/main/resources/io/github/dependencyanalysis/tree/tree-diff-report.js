// Wiki: wiki/c4/containers/dependency-analyzer-offline-report.md - UI boundary
(() => {
  "use strict";

  const root = document.querySelector("[data-tree-diff-root]");
  if (!root) return;

  const manifestNode = document.getElementById("tree-diff-manifest");
  const manifest = JSON.parse(manifestNode.textContent);
  if (manifest.schema !== "tree-diff-report" || manifest.version !== 1) {
    throw new Error("Unsupported tree diff report schema");
  }

  const records = new Map();
  const pending = new Map();
  const loaded = new Set();
  let generation = 0;
  let selectedModuleId = null;
  let activeSummary = null;
  let moduleDependencies = [];
  let dependencyCatalog = new Map();
  let selectedDependencyId = null;
  let expandedRowId = null;
  let modulePage = 0;
  let dependencyPage = 0;
  let chainPage = 0;
  let chainPageSize = 10;
  let treeVisible = false;

  const byId = (id) => document.getElementById(id);
  const moduleBody = byId("module-summary").tBodies[0];
  const dependencyBody = byId("dependency-table").tBodies[0];

  window.__CIA_TREE_DIFF_REPORT_SHARD__ = (payload) => {
    if (!payload || payload.schema !== manifest.schema
        || payload.schemaVersion !== manifest.version) {
      throw new Error("Tree diff shard schema mismatch");
    }
    const descriptors = manifest.shards[payload.kind] || [];
    const descriptor = descriptors.find((item) => item.id === payload.shardId);
    if (!descriptor) throw new Error("Unexpected tree diff shard");
    if (!Array.isArray(payload.records)
        || payload.records.length !== descriptor.records) {
      throw new Error("Tree diff shard record count mismatch");
    }
    const kindRecords = records.get(payload.kind) || new Map();
    for (const record of payload.records) {
      if (!Number.isInteger(record.id)
          || record.id < descriptor.firstId || record.id > descriptor.lastId) {
        throw new Error("Tree diff shard record range mismatch");
      }
      kindRecords.set(record.id, record);
    }
    records.set(payload.kind, kindRecords);
    const key = `${payload.kind}:${payload.shardId}`;
    const waiter = pending.get(key);
    if (waiter) waiter.resolve();
  };

  function loadShard(kind, descriptor) {
    const key = `${kind}:${descriptor.id}`;
    if (loaded.has(key)) return Promise.resolve();
    if (pending.has(key)) return pending.get(key).promise;
    let resolve;
    let reject;
    const promise = new Promise((onResolve, onReject) => {
      resolve = onResolve;
      reject = onReject;
    });
    pending.set(key, { promise, resolve, reject });
    const script = document.createElement("script");
    script.src = descriptor.file;
    script.async = true;
    script.onload = () => {
      script.remove();
      pending.delete(key);
      if (!records.get(kind)?.has(descriptor.firstId)) {
        reject(new Error(`Shard callback missing: ${descriptor.file}`));
      } else {
        loaded.add(key);
        resolve();
      }
    };
    script.onerror = () => {
      script.remove();
      pending.delete(key);
      reject(new Error(`Unable to load ${descriptor.file}`));
    };
    document.head.append(script);
    return promise;
  }

  async function loadIds(kind, ids) {
    if (!ids.length) return [];
    const wanted = new Set(ids);
    const descriptors = (manifest.shards[kind] || []).filter((descriptor) =>
      ids.some((id) => id >= descriptor.firstId && id <= descriptor.lastId));
    await Promise.all(descriptors.map((descriptor) => loadShard(kind, descriptor)));
    const kindRecords = records.get(kind) || new Map();
    const result = ids.map((id) => kindRecords.get(id));
    if (result.some((record) => !record)) {
      throw new Error(`Missing ${kind} record`);
    }
    return result.filter((record) => wanted.has(record.id));
  }

  function element(name, text, className) {
    const value = document.createElement(name);
    if (text !== undefined) value.textContent = text;
    if (className) value.className = className;
    return value;
  }

  function badge(code, text) {
    return element("span", text, `badge badge-${code.toLowerCase().replaceAll("_", "-")}`);
  }

  function appendCell(row, content) {
    const cell = element("td");
    if (content instanceof Node) cell.append(content);
    else cell.textContent = content;
    row.append(cell);
    return cell;
  }

  function sideValue(left, right, formatter = (value) => value) {
    const wrap = element("span");
    wrap.append(document.createTextNode(`${left == null ? "—" : formatter(left)} → ${right == null ? "—" : formatter(right)}`));
    return wrap;
  }

  function metricsValue(summary, field) {
    return summary.metrics == null ? "—" : String(summary.metrics[field]);
  }

  async function renderModuleSummary() {
    const size = Number(byId("module-page-size").value);
    const pages = Math.max(1, Math.ceil(manifest.modules.length / size));
    modulePage = Math.min(modulePage, pages - 1);
    const catalog = manifest.modules.slice(modulePage * size, (modulePage + 1) * size);
    const summaries = await loadIds("module-summaries", catalog.map((item) => item.moduleId));
    const summaryById = new Map(summaries.map((item) => [item.moduleId, item]));
    const fragment = document.createDocumentFragment();
    for (const module of catalog) {
      const summary = summaryById.get(module.moduleId);
      const row = element("tr");
      appendCell(row, module.baselineCoordinate || module.targetCoordinate || module.moduleKey);
      appendCell(row, badge(module.comparisonStatus, module.comparisonStatus));
      for (const field of ["versionChanged", "added", "removed", "resolvedUnchanged", "scopeChanged"]) {
        appendCell(row, metricsValue(summary, field));
      }
      fragment.append(row);
    }
    moduleBody.replaceChildren(fragment);
    const first = manifest.modules.length ? modulePage * size + 1 : 0;
    const last = Math.min((modulePage + 1) * size, manifest.modules.length);
    byId("module-range").textContent = `${first}–${last} of ${manifest.modules.length}`;
    byId("module-page").textContent = `Page ${modulePage + 1} of ${pages}`;
    byId("module-previous").disabled = modulePage === 0;
    byId("module-next").disabled = modulePage >= pages - 1;
  }

  function setupCombobox(input, list, options, select) {
    const render = () => {
      const query = input.value.toLocaleLowerCase();
      const matches = options().filter((item) => item.label.toLocaleLowerCase().includes(query)).slice(0, 50);
      const fragment = document.createDocumentFragment();
      for (const item of matches) {
        const li = element("li");
        li.setAttribute("role", "option");
        const button = element("button", item.label);
        button.type = "button";
        button.addEventListener("mousedown", (event) => event.preventDefault());
        button.addEventListener("click", () => {
          input.value = item.label;
          list.classList.remove("open");
          input.setAttribute("aria-expanded", "false");
          select(item);
        });
        li.append(button);
        fragment.append(li);
      }
      list.replaceChildren(fragment);
      list.classList.toggle("open", matches.length > 0);
      input.setAttribute("aria-expanded", String(matches.length > 0));
    };
    input.addEventListener("input", render);
    input.addEventListener("focus", render);
    input.addEventListener("blur", () => setTimeout(() => {
      list.classList.remove("open");
      input.setAttribute("aria-expanded", "false");
    }, 0));
  }

  function moduleOptions() {
    return manifest.modules.map((module) => ({
      id: module.moduleId,
      label: module.baselineCoordinate || module.targetCoordinate || module.moduleKey
    }));
  }

  async function selectModule(moduleId) {
    generation += 1;
    const current = generation;
    selectedModuleId = moduleId;
    selectedDependencyId = null;
    expandedRowId = null;
    dependencyPage = 0;
    chainPage = 0;
    const module = manifest.modules.find((item) => item.moduleId === moduleId);
    byId("module-selector").value = module.baselineCoordinate || module.targetCoordinate || module.moduleKey;
    const summary = (await loadIds("module-summaries", [moduleId]))[0];
    if (current !== generation) return;
    activeSummary = summary;
    const dependencyIds = Array.from({ length: summary.moduleDependencyCount }, (_, index) => summary.moduleDependencyStart + index);
    moduleDependencies = await loadIds("module-dependencies", dependencyIds);
    if (current !== generation) return;
    const catalog = await loadIds("dependencies", moduleDependencies.map((item) => item.dependencyId));
    if (current !== generation) return;
    dependencyCatalog = new Map(catalog.map((item) => [item.dependencyId, item]));
    byId("dependency-filter").value = "All";
    renderMetricCards(summary);
    renderIssues(summary);
    modulePage = Math.floor(moduleId / Number(byId("module-page-size").value));
    await renderModuleSummary();
    await renderDependencies(current);
    if (treeVisible) await renderTrees(current);
  }

  function renderMetricCards(summary) {
    const fields = [
      ["Version changed", "versionChanged"], ["Added", "added"], ["Removed", "removed"],
      ["Resolved version unchanged", "resolvedUnchanged"], ["Scope changed", "scopeChanged"]
    ];
    const fragment = document.createDocumentFragment();
    for (const [label, field] of fields) {
      const card = element("div", undefined, "metric");
      card.append(element("span", label), element("strong", metricsValue(summary, field)));
      fragment.append(card);
    }
    byId("metric-cards").replaceChildren(fragment);
  }

  function renderIssues(summary) {
    const fragment = document.createDocumentFragment();
    const issues = summary.issues.length ? summary.issues : ["No Module issues."];
    for (const issue of issues) fragment.append(element("li", issue));
    byId("module-issues").replaceChildren(fragment);
  }

  function dependencyOptions() {
    const values = [{ id: null, label: "All" }];
    for (const item of moduleDependencies) {
      values.push({ id: item.dependencyId, label: dependencyCatalog.get(item.dependencyId).display });
    }
    return values.sort((left, right) => left.id == null ? -1 : right.id == null ? 1 : left.label.localeCompare(right.label));
  }

  function normalizeRanges(ranges) {
    return [...ranges].sort((left, right) => left.start - right.start);
  }

  function intersectRanges(left, right) {
    const result = [];
    let i = 0;
    let j = 0;
    while (i < left.length && j < right.length) {
      const start = Math.max(left[i].start, right[j].start);
      const end = Math.min(left[i].start + left[i].count, right[j].start + right[j].count);
      if (start < end) result.push({ start, count: end - start });
      if (left[i].start + left[i].count < right[j].start + right[j].count) i += 1;
      else j += 1;
    }
    return result;
  }

  function activeRanges() {
    let ranges = [{ start: activeSummary.dependencyRowStart, count: activeSummary.dependencyRowCount }];
    if (selectedDependencyId != null) {
      const item = moduleDependencies.find((entry) => entry.dependencyId === selectedDependencyId);
      ranges = intersectRanges(ranges, [{ start: item.rowId, count: 1 }]);
    }
    const filter = byId("change-filter").value;
    if (filter !== "ALL") {
      const filtered = filter === "SCOPE_CHANGED"
        ? activeSummary.scopeChangedRanges
        : activeSummary.baseRanges[filter];
      ranges = intersectRanges(ranges, normalizeRanges(filtered || []));
    }
    return ranges;
  }

  function rangeCount(ranges) {
    return ranges.reduce((total, range) => total + range.count, 0);
  }

  function pageIds(ranges, offset, limit) {
    const result = [];
    let skipped = 0;
    for (const range of ranges) {
      if (skipped + range.count <= offset) {
        skipped += range.count;
        continue;
      }
      const first = range.start + Math.max(0, offset - skipped);
      const available = range.start + range.count - first;
      const take = Math.min(available, limit - result.length);
      for (let index = 0; index < take; index += 1) {
        result.push(first + index);
      }
      skipped += range.count;
      if (result.length === limit) break;
    }
    return result;
  }

  async function renderDependencies(current = generation) {
    if (!activeSummary) return;
    expandedRowId = null;
    const ranges = activeRanges();
    const count = rangeCount(ranges);
    const size = Number(byId("dependency-page-size").value);
    const pages = Math.max(1, Math.ceil(count / size));
    dependencyPage = Math.min(dependencyPage, pages - 1);
    const ids = pageIds(ranges, dependencyPage * size, size);
    try {
      const rows = await loadIds("rows", ids);
      if (current !== generation) return;
      const fragment = document.createDocumentFragment();
      for (const row of rows) fragment.append(mainRow(row, current));
      if (!rows.length) {
        const empty = element("tr");
        const cell = appendCell(empty, "No dependency matches the current filters.");
        cell.colSpan = 6;
        fragment.append(empty);
      }
      dependencyBody.replaceChildren(fragment);
      byId("dependency-error").hidden = true;
    } catch (error) {
      showDependencyError(error);
      return;
    }
    const first = count ? dependencyPage * size + 1 : 0;
    const last = Math.min((dependencyPage + 1) * size, count);
    byId("dependency-range").textContent = `${first}–${last} of ${count}; total ${activeSummary.dependencyRowCount}`;
    byId("dependency-page").textContent = `Page ${dependencyPage + 1} of ${pages}`;
    byId("dependency-previous").disabled = dependencyPage === 0;
    byId("dependency-next").disabled = dependencyPage >= pages - 1;
  }

  function mainRow(row, current) {
    const tr = element("tr");
    appendCell(tr, dependencyCatalog.get(row.dependencyId).display);
    appendCell(tr, sideValue(row.baselineResolvedVersion, row.targetResolvedVersion));
    appendCell(tr, sideValue(row.baselineScope, row.targetScope));
    appendCell(tr, sideValue(row.baselineDirect, row.targetDirect, (value) => value ? "Yes" : "No"));
    const changes = element("span");
    const labels = {
      VERSION_CHANGED: "Version changed", ADDED: "Added", REMOVED: "Removed",
      RESOLVED_UNCHANGED: "Resolved version unchanged"
    };
    changes.append(badge(row.baseChangeType, labels[row.baseChangeType]));
    if (row.scopeChanged) changes.append(badge("SCOPE_CHANGED", "Scope changed"));
    appendCell(tr, changes);
    const action = element("button", "View dependency chains", "table-action");
    action.type = "button";
    action.setAttribute("aria-expanded", "false");
    action.setAttribute("aria-controls", `chain-${row.rowId}`);
    action.addEventListener("click", async () => {
      if (expandedRowId === row.rowId) {
        expandedRowId = null;
        tr.nextElementSibling?.remove();
        action.setAttribute("aria-expanded", "false");
        action.textContent = "View dependency chains";
        return;
      }
      expandedRowId = row.rowId;
      chainPage = 0;
      await renderExpanded(row, tr, action, current);
    });
    appendCell(tr, action);
    return tr;
  }

  async function renderExpanded(row, tr, action, current) {
    dependencyBody.querySelectorAll(".chain-detail").forEach((node) => node.remove());
    dependencyBody.querySelectorAll("[aria-expanded=true]").forEach((button) => {
      button.setAttribute("aria-expanded", "false");
      button.textContent = "View dependency chains";
    });
    if (expandedRowId !== row.rowId) return;
    action.setAttribute("aria-expanded", "true");
    action.textContent = "Hide dependency chains";
    const detail = element("tr", undefined, "chain-detail");
    detail.id = `chain-${row.rowId}`;
    const cell = appendCell(detail, "Loading dependency chains…");
    cell.colSpan = 6;
    tr.after(detail);
    await renderChainPage(row, cell, current);
  }

  async function renderChainPage(row, cell, current) {
    const pages = Math.max(1, Math.ceil(row.chainCount / chainPageSize));
    chainPage = Math.min(chainPage, pages - 1);
    const count = Math.min(chainPageSize, Math.max(0, row.chainCount - chainPage * chainPageSize));
    const ids = Array.from({ length: count }, (_, index) => row.chainStart + chainPage * chainPageSize + index);
    const chains = await loadIds("chain-rows", ids);
    if (current !== generation || expandedRowId !== row.rowId) return;
    const wrapper = element("div");
    const toolbar = element("div", undefined, "chain-toolbar");
    const label = element("label", "Page size ", "chain-page-size-label");
    const size = element("select", undefined, "chain-page-size");
    for (const value of [10, 50, 100]) {
      const option = element("option", String(value));
      option.value = String(value);
      option.selected = value === chainPageSize;
      size.append(option);
    }
    size.addEventListener("change", () => {
      chainPageSize = Number(size.value);
      chainPage = 0;
      renderChainPage(row, cell, generation);
    });
    label.append(size);
    toolbar.append(label, element("span", `${row.chainCount} dependency chains`));
    const tableWrap = element("div", undefined, "table-scroll");
    const table = element("table", undefined, "chain-table");
    const head = element("thead");
    const headRow = element("tr");
    for (const text of ["Baseline dependency chain", "Target dependency chain", "Version", "Dependency chain structure change type"]) {
      const th = element("th", text);
      th.scope = "col";
      headRow.append(th);
    }
    head.append(headRow);
    const body = element("tbody");
    for (const chain of chains) {
      const chainRow = element("tr");
      for (const path of [chain.baselinePathDisplay, chain.targetPathDisplay]) {
        const code = element("code", path == null ? "—" : path);
        appendCell(chainRow, code);
      }
      const version = element("span");
      version.append(document.createTextNode(chain.baselineResolvedVersion ?? "—"));
      if (chain.baselineManagedFromVersion) version.append(badge("MANAGED", `Managed from ${chain.baselineManagedFromVersion}`));
      version.append(document.createTextNode(" → "));
      version.append(document.createTextNode(chain.targetResolvedVersion ?? "—"));
      if (chain.targetManagedFromVersion) version.append(badge("MANAGED", `Managed from ${chain.targetManagedFromVersion}`));
      appendCell(chainRow, version);
      const labels = { ADDED: "Added", REMOVED: "Removed", UNCHANGED: "Unchanged" };
      appendCell(chainRow, badge(chain.chainChangeType, labels[chain.chainChangeType]));
      body.append(chainRow);
    }
    table.append(head, body);
    tableWrap.append(table);
    const pager = element("div", undefined, "chain-pager");
    const previous = element("button", "Previous", "chain-page-button");
    const next = element("button", "Next", "chain-page-button");
    previous.disabled = chainPage === 0;
    next.disabled = chainPage >= pages - 1;
    previous.addEventListener("click", () => { chainPage -= 1; renderChainPage(row, cell, generation); });
    next.addEventListener("click", () => { chainPage += 1; renderChainPage(row, cell, generation); });
    pager.append(previous, element("span", `Page ${chainPage + 1} of ${pages}`), next);
    wrapper.append(toolbar, tableWrap, pager);
    cell.replaceChildren(wrapper);
  }

  function showDependencyError(error) {
    const box = byId("dependency-error");
    const retry = element("button", "Retry");
    retry.type = "button";
    retry.addEventListener("click", () => renderDependencies(generation));
    box.replaceChildren(document.createTextNode(`${error.message} `), retry);
    box.hidden = false;
  }

  async function renderTrees(current) {
    if (selectedModuleId == null) return;
    const tree = (await loadIds("trees", [selectedModuleId]))[0];
    if (current !== generation) return;
    renderTreeSide(byId("baseline-tree"), tree.baseline);
    renderTreeSide(byId("target-tree"), tree.target);
  }

  function renderTreeSide(node, side) {
    if (side.state === "ABSENT") node.textContent = "Module does not exist on this side.";
    else if (side.state === "UNAVAILABLE") node.textContent = `Dependency tree is unavailable.${side.issue ? ` ${side.issue}` : ""}`;
    else node.textContent = side.text || "No dependency occurrence was reported.";
  }

  byId("module-previous").addEventListener("click", () => { modulePage -= 1; renderModuleSummary(); });
  byId("module-next").addEventListener("click", () => { modulePage += 1; renderModuleSummary(); });
  byId("module-page-size").addEventListener("change", () => { modulePage = 0; renderModuleSummary(); });
  byId("dependency-previous").addEventListener("click", () => { generation += 1; dependencyPage -= 1; renderDependencies(generation); });
  byId("dependency-next").addEventListener("click", () => { generation += 1; dependencyPage += 1; renderDependencies(generation); });
  byId("dependency-page-size").addEventListener("change", () => { generation += 1; dependencyPage = 0; renderDependencies(generation); });
  byId("change-filter").addEventListener("change", () => { generation += 1; dependencyPage = 0; renderDependencies(generation); });

  setupCombobox(byId("module-selector"), byId("module-options"), moduleOptions,
    (item) => selectModule(item.id));
  setupCombobox(byId("dependency-filter"), byId("dependency-options"), dependencyOptions,
    (item) => {
      selectedDependencyId = item.id;
      generation += 1;
      dependencyPage = 0;
      renderDependencies(generation);
    });
  byId("dependency-filter").addEventListener("input", () => { selectedDependencyId = null; });

  const observer = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) {
      treeVisible = true;
      renderTrees(generation);
      observer.disconnect();
    }
  }, { rootMargin: "300px" });
  observer.observe(byId("tree-comparison"));

  const defaultModule = manifest.modules.find((module) => module.comparisonStatus === "COMPARABLE")
    || manifest.modules[0];
  renderModuleSummary().then(() => {
    if (defaultModule) return selectModule(defaultModule.moduleId);
    byId("module-detail").hidden = true;
    return null;
  }).catch(showDependencyError);
})();
