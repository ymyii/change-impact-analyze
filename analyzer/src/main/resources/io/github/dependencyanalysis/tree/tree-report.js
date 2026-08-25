// Wiki: wiki/features/repository-dependency-tree-report.md - Behavior Contract
(() => {
    "use strict";

    const manifestNode = document.getElementById("tree-report-manifest");
    if (!manifestNode) {
        return;
    }
    const common = window.CIA_REPORT;
    if (!common) {
        throw new Error("Shared report behavior is unavailable");
    }
    const manifest = JSON.parse(manifestNode.textContent || "{}");
    if (manifest.schemaVersion !== 1 || !Array.isArray(manifest.modules)
            || !Array.isArray(manifest.dependencies)
            || !Array.isArray(manifest.scopes) || !manifest.shards) {
        const status = document.createElement("p");
        status.className = "error";
        status.textContent = "Tree Report data schema is incompatible. ";
        const retry = document.createElement("button");
        retry.type = "button";
        retry.textContent = "Retry";
        retry.addEventListener("click", () => window.location.reload());
        status.append(retry);
        manifestNode.before(status);
        return;
    }
    const dependencies = new Map(manifest.dependencies.map(value =>
        [value.id, value]));
    const modules = new Map(manifest.modules.map(value => [value.id, value]));
    const moduleStates = new Map();
    let moduleGeneration = 0;
    let dependencyGeneration = 0;
    let dependencyIndexes = null;
    let activeCode = null;

    function validRecord(kind, value) {
        if (!value || !Number.isInteger(value.id)) {
            return false;
        }
        if (kind === "dependency-ranges") {
            return Number.isInteger(value.rowStart)
                && Number.isInteger(value.rowCount);
        }
        if (kind === "dependency-index") {
            return Number.isInteger(value.dependencyId)
                && Number.isInteger(value.moduleId)
                && typeof value.searchText === "string";
        }
        if (kind === "dependency-rows") {
            return Number.isInteger(value.dependencyId)
                && Number.isInteger(value.moduleId)
                && typeof value.chain === "string";
        }
        if (kind === "internal-conflicts") {
            return Number.isInteger(value.moduleId)
                && Array.isArray(value.evidence);
        }
        if (kind === "class-conflicts") {
            return Number.isInteger(value.moduleId)
                && Array.isArray(value.sourceIds);
        }
        if (kind === "class-sources") {
            return typeof value.label === "string"
                && typeof value.available === "boolean";
        }
        return kind === "dependency-trees"
            && Number.isInteger(value.moduleId)
            && typeof value.text === "string";
    }

    const shards = common.createShardLoader(manifest, validRecord,
        (kind, value) => value.id, "__CIA_TREE_REPORT_SHARD__");

    function element(tag, className, text) {
        const value = document.createElement(tag);
        if (className) {
            value.className = className;
        }
        if (text !== undefined) {
            value.textContent = text;
        }
        return value;
    }

    function ids(start, count) {
        const result = new Set();
        for (let offset = 0; offset < count; offset += 1) {
            result.add(start + offset);
        }
        return result;
    }

    function rangesContain(ranges, id) {
        return ranges.some(range => id >= range.start
            && id < range.start + range.count);
    }

    function rangeCount(ranges) {
        return common.normalizeRanges(ranges)
            .reduce((total, value) => total + value.count, 0);
    }

    function pageIds(ranges, start, count) {
        const result = new Set();
        let skipped = 0;
        for (const range of common.normalizeRanges(ranges)) {
            if (result.size >= count) {
                break;
            }
            if (skipped + range.count <= start) {
                skipped += range.count;
                continue;
            }
            const offset = Math.max(0, start - skipped);
            for (let id = range.start + offset;
                 id < range.start + range.count && result.size < count; id += 1) {
                result.add(id);
            }
            skipped += range.count;
        }
        return result;
    }

    function rangesFromIds(values) {
        const sorted = [...values].sort((left, right) => left - right);
        const result = [];
        sorted.forEach(id => {
            const previous = result[result.length - 1];
            if (previous && previous.start + previous.count === id) {
                previous.count += 1;
            } else {
                result.push({start: id, count: 1});
            }
        });
        return result;
    }

    function showRetry(container, message, retry) {
        container.textContent = "";
        container.append(element("span", "error", message), " ");
        const button = element("button", "", "Retry");
        button.type = "button";
        button.addEventListener("click", retry);
        container.append(button);
    }

    function createCombobox(root, items, options) {
        const input = root.querySelector("input[role=combobox]");
        const toggle = root.querySelector(".combobox-toggle");
        const list = root.querySelector("[role=listbox]");
        const status = document.getElementById(`${root.dataset.combobox}-status`);
        let selected = null;
        let active = -1;
        let visible = [];

        function label(item) {
            return options.label(item);
        }

        function setExpanded(expanded) {
            const value = String(expanded);
            input.setAttribute("aria-expanded", value);
            toggle.setAttribute("aria-expanded", value);
            toggle.setAttribute("aria-label",
                `${expanded ? "收起" : "展开"} ${toggle.dataset.label} 候选`);
        }

        function close() {
            list.hidden = true;
            setExpanded(false);
            input.removeAttribute("aria-activedescendant");
            active = -1;
        }

        function activate(index) {
            if (!visible.length) {
                return;
            }
            active = Math.max(0, Math.min(index, visible.length - 1));
            [...list.children].forEach((item, itemIndex) => item
                .setAttribute("aria-selected", String(itemIndex === active)));
            const target = list.children[active];
            input.setAttribute("aria-activedescendant", target.id);
            target.scrollIntoView({block: "nearest"});
        }

        function choose(item, notify = true) {
            selected = item;
            input.value = item ? label(item) : "";
            close();
            if (notify) {
                options.onSelect(item);
            }
        }

        function render() {
            const query = input.value.trim().toLowerCase();
            if (selected && input.value !== label(selected)) {
                selected = null;
            }
            const matches = items.filter(item => options.search(item)
                .toLowerCase().includes(query));
            visible = matches.slice(0, 50);
            const fragment = document.createDocumentFragment();
            visible.forEach((item, index) => {
                const option = element("li");
                option.id = `${root.dataset.combobox}-option-${index}`;
                option.setAttribute("role", "option");
                option.setAttribute("aria-selected", "false");
                option.append(element("span", "", label(item)));
                if (options.badge) {
                    const count = options.badge(item);
                    const badge = element("span", "version-count", String(count));
                    badge.setAttribute("aria-label", `${count} resolved versions`);
                    badge.title = `${count} resolved versions`;
                    option.append(badge);
                }
                option.addEventListener("mousedown", event => {
                    event.preventDefault();
                    choose(item);
                });
                fragment.append(option);
            });
            list.replaceChildren(fragment);
            list.hidden = false;
            setExpanded(true);
            status.textContent = matches.length > 50
                ? `显示前 50 / ${matches.length}；继续输入可缩小范围。`
                : `${matches.length} 个候选。`;
            if (visible.length) {
                activate(0);
            } else {
                input.removeAttribute("aria-activedescendant");
            }
        }

        input.addEventListener("focus", render);
        input.addEventListener("input", () => {
            render();
            if (!input.value && !options.required) {
                selected = null;
                options.onSelect(null);
            }
        });
        input.addEventListener("keydown", event => {
            if (event.key === "Escape") {
                event.preventDefault();
                close();
            } else if (event.key === "ArrowDown") {
                event.preventDefault();
                if (list.hidden) {
                    render();
                } else {
                    activate(active + 1);
                }
            } else if (event.key === "ArrowUp") {
                event.preventDefault();
                activate(active - 1);
            } else if (event.key === "Home" && !list.hidden) {
                event.preventDefault();
                activate(0);
            } else if (event.key === "End" && !list.hidden) {
                event.preventDefault();
                activate(visible.length - 1);
            } else if (event.key === "Enter" && !list.hidden && active >= 0) {
                event.preventDefault();
                const exact = items.find(item =>
                    label(item) === input.value.trim());
                choose(exact || visible[active]);
            }
        });
        toggle.addEventListener("click", () => {
            const opening = list.hidden;
            input.focus();
            if (opening) {
                render();
            } else {
                close();
            }
        });
        root.addEventListener("focusout", event => {
            if (event.relatedTarget && root.contains(event.relatedTarget)) {
                return;
            }
            close();
            if (options.required && selected) {
                input.value = label(selected);
            }
        });

        return {
            clear(notify = true) {
                choose(null, notify);
            },
            select(item, notify = true) {
                choose(item, notify);
            },
            value() {
                const text = input.value.trim();
                if (!text && !options.required) {
                    return null;
                }
                const exact = selected && label(selected) === text
                    ? selected : items.find(item => label(item) === text);
                if (!exact) {
                    throw new Error(`${options.name} must be selected from candidates`);
                }
                if (exact !== selected) {
                    choose(exact, false);
                }
                return exact;
            }
        };
    }

    async function catalogRanges(catalog, generation) {
        if (!catalog || !catalog.rangeCount) {
            return [];
        }
        const records = await shards.recordsForIds("dependency-ranges",
            ids(catalog.firstRangeId, catalog.rangeCount));
        if (generation !== dependencyGeneration) {
            return null;
        }
        return common.normalizeRanges([...records.values()].map(value => ({
            start: value.rowStart, count: value.rowCount
        })));
    }

    async function allDependencyIndexes(generation, status) {
        if (dependencyIndexes) {
            return dependencyIndexes;
        }
        const result = [];
        const descriptors = shards.descriptors("dependency-index");
        for (let index = 0; index < descriptors.length; index += 1) {
            status.textContent = `Loading dependency index ${index + 1}`
                + ` of ${descriptors.length}…`;
            const values = await shards.loadShard("dependency-index",
                descriptors[index]);
            if (generation !== dependencyGeneration) {
                return null;
            }
            result.push(...values);
        }
        dependencyIndexes = result;
        return result;
    }

    function dependencySortValue(row, key) {
        if (key === "dependency") {
            return dependencies.get(row.dependencyId).value;
        }
        if (key === "module") {
            return modules.get(row.moduleId).coordinate;
        }
        return row[key] || "";
    }

    const dependencyState = {page: 0, size: 10, sort: "", direction: 1};
    const dependencyStatus = document.getElementById("dependency-result-summary");
    const dependencyBody = document.getElementById("dependency-rows");
    const dependencyPosition = document.getElementById("dependency-position");
    const dependencyPrevious = document.getElementById("dependency-previous");
    const dependencyNext = document.getElementById("dependency-next");
    const dependencyScope = document.getElementById("dependency-scope-filter");
    const dependencySearch = document.getElementById("dependency-search");
    const dependencyChoices = [...manifest.dependencies];
    const moduleChoices = [...manifest.modules];
    const dependencyCombo = createCombobox(
        document.querySelector('[data-combobox="dependency-filter"]'),
        dependencyChoices, {
            name: "Dependency filter", required: false,
            label: item => item.value,
            search: item => item.value,
            badge: item => item.resolvedVersionCount,
            onSelect: () => { dependencyState.page = 0; renderDependencies(); }
        });
    const dependencyModuleCombo = createCombobox(
        document.querySelector('[data-combobox="dependency-module-filter"]'),
        moduleChoices, {
            name: "Module filter", required: false,
            label: item => item.coordinate || item.label,
            search: item => `${item.coordinate} ${item.label} ${item.pom}`,
            onSelect: () => { dependencyState.page = 0; renderDependencies(); }
        });

    async function dependencyResult(generation) {
        let ranges = [{start: 0, count: manifest.dependencyRows}];
        const dependency = dependencyCombo.value();
        if (dependency) {
            const values = await catalogRanges(dependency, generation);
            if (!values) {
                return null;
            }
            ranges = common.intersectRanges(ranges, values);
        }
        const module = dependencyModuleCombo.value();
        if (module) {
            ranges = common.intersectRanges(ranges,
                [module.dependencyRange]);
        }
        const scope = manifest.scopes.find(value =>
            value.value === dependencyScope.value);
        if (scope) {
            const values = await catalogRanges(scope, generation);
            if (!values) {
                return null;
            }
            ranges = common.intersectRanges(ranges, values);
        }
        const query = dependencySearch.value.trim().toLowerCase();
        if (query || dependencyState.sort) {
            const indexes = await allDependencyIndexes(
                generation, dependencyStatus);
            if (!indexes) {
                return null;
            }
            const filtered = indexes.filter(value => rangesContain(ranges,
                value.id) && (!query || value.searchText.includes(query)));
            if (dependencyState.sort) {
                filtered.sort((left, right) => dependencyState.direction
                    * dependencySortValue(left, dependencyState.sort)
                        .localeCompare(dependencySortValue(
                            right, dependencyState.sort)));
            }
            const start = dependencyState.page * dependencyState.size;
            return {total: filtered.length,
                pageIds: new Set(filtered.slice(start,
                    start + dependencyState.size).map(value => value.id))};
        }
        const total = rangeCount(ranges);
        const start = dependencyState.page * dependencyState.size;
        return {total, pageIds: pageIds(ranges, start, dependencyState.size)};
    }

    async function renderDependencies() {
        const generation = ++dependencyGeneration;
        try {
            dependencyStatus.textContent = "Loading dependencies…";
            let result = await dependencyResult(generation);
            if (!result || generation !== dependencyGeneration) {
                return;
            }
            const pages = Math.max(1, Math.ceil(
                result.total / dependencyState.size));
            if (dependencyState.page >= pages) {
                dependencyState.page = pages - 1;
                result = await dependencyResult(generation);
                if (!result || generation !== dependencyGeneration) {
                    return;
                }
            }
            const rows = await shards.recordsForIds(
                "dependency-rows", result.pageIds);
            if (generation !== dependencyGeneration) {
                return;
            }
            const fragment = document.createDocumentFragment();
            result.pageIds.forEach(id => {
                const row = rows.get(id);
                const tr = element("tr");
                [dependencies.get(row.dependencyId).value, row.scope,
                    modules.get(row.moduleId).coordinate, row.chain,
                    row.originalVersion, row.resolvedVersion]
                    .forEach((value, index) => {
                        const td = element("td", index === 0 || index === 3
                            ? "dependency-cell" : "", value || "—");
                        tr.append(td);
                    });
                fragment.append(tr);
            });
            dependencyBody.replaceChildren(fragment);
            const start = result.total
                ? dependencyState.page * dependencyState.size + 1 : 0;
            const end = Math.min(result.total,
                (dependencyState.page + 1) * dependencyState.size);
            dependencyStatus.textContent = result.total
                ? `Showing ${start}–${end} of ${result.total}`
                : "未发现 dependency。";
            dependencyPosition.textContent = result.total
                ? `${start}-${end} / ${result.total}` : "0 / 0";
            dependencyPrevious.disabled = dependencyState.page === 0;
            dependencyNext.disabled = dependencyState.page >= pages - 1;
        } catch (error) {
            if (generation !== dependencyGeneration) {
                return;
            }
            showRetry(dependencyStatus, error.message, renderDependencies);
        }
    }

    document.getElementById("dependency-controls").addEventListener(
        "submit", event => {
            event.preventDefault();
            dependencyState.page = 0;
            renderDependencies();
        });
    dependencyScope.addEventListener("change", () => {
        dependencyState.page = 0;
        renderDependencies();
    });
    document.getElementById("dependency-page-size").addEventListener(
        "change", event => {
            dependencyState.size = Number(event.target.value);
            dependencyState.page = 0;
            renderDependencies();
        });
    dependencyPrevious.addEventListener("click", () => {
        dependencyState.page -= 1;
        renderDependencies();
    });
    dependencyNext.addEventListener("click", () => {
        dependencyState.page += 1;
        renderDependencies();
    });
    document.querySelectorAll("[data-dependency-sort]").forEach(button =>
        button.addEventListener("click", () => {
            const key = button.dataset.dependencySort;
            dependencyState.direction = dependencyState.sort === key
                ? -dependencyState.direction : 1;
            dependencyState.sort = key;
            dependencyState.page = 0;
            renderDependencies();
        }));
    document.getElementById("dependency-clear").addEventListener(
        "click", () => {
            dependencySearch.value = "";
            dependencyScope.value = "";
            dependencyCombo.clear(false);
            dependencyModuleCombo.clear(false);
            dependencyState.page = 0;
            dependencyState.sort = "";
            dependencyState.direction = 1;
            renderDependencies();
        });

    function moduleState(moduleId) {
        if (!moduleStates.has(moduleId)) {
            moduleStates.set(moduleId, {
                classPage: 0, classSize: 10, classSearch: "", classRisk: "",
                classSort: "", classDirection: 1,
                internalPage: 0, internalSize: 10,
                internalSearch: "", internalScope: "",
                internalSort: "", internalDirection: 1
            });
        }
        return moduleStates.get(moduleId);
    }

    function closeCode() {
        if (activeCode) {
            activeCode.button.setAttribute("aria-expanded", "false");
            activeCode.row.remove();
            activeCode = null;
        }
    }

    async function openCode(button, conflict, generation) {
        closeCode();
        const detail = element("tr", "class-code-row");
        const cell = element("td");
        cell.colSpan = 6;
        const panel = element("div", "class-code-panel", "Loading…");
        panel.setAttribute("role", "status");
        cell.append(panel);
        detail.append(cell);
        button.closest("tr").after(detail);
        button.setAttribute("aria-expanded", "true");
        activeCode = {button, row: detail};
        const load = async () => {
            try {
                panel.textContent = "Loading…";
                const records = await shards.recordsForIds("class-sources",
                    new Set(conflict.sourceIds));
                if (generation !== moduleGeneration || !activeCode
                        || activeCode.button !== button) {
                    return;
                }
                const values = conflict.sourceIds.map(id => records.get(id));
                const nav = element("div", "source-switches");
                const pre = element("pre");
                const buttons = [];
                const select = index => {
                    buttons.forEach((candidate, candidateIndex) => candidate
                        .setAttribute("aria-pressed",
                            String(candidateIndex === index)));
                    const value = values[index];
                    pre.textContent = value.available
                        ? value.sourceCode : "Unavailable";
                };
                values.forEach((value, index) => {
                    const candidate = element("button", "",
                        `${value.winner ? "Winner" : "Shadowed"} — ${value.label}`);
                    candidate.type = "button";
                    candidate.addEventListener("click", () => select(index));
                    buttons.push(candidate);
                    nav.append(candidate);
                });
                panel.replaceChildren(nav, pre);
                select(Math.max(0, values.findIndex(value => value.winner)));
            } catch (error) {
                showRetry(panel, error.message, load);
            }
        };
        await load();
    }

    function sectionPageIds(range, page, size) {
        const start = range.start + page * size;
        return ids(start, Math.max(0, Math.min(size,
            range.start + range.count - start)));
    }

    async function sectionResult(kind, range, state, options, generation) {
        const filteredMode = options.query || options.filter || options.sort;
        if (!filteredMode) {
            return {total: range.count,
                ids: sectionPageIds(range, options.page, options.size)};
        }
        const records = await shards.recordsForIds(kind,
            ids(range.start, range.count));
        if (generation !== moduleGeneration) {
            return null;
        }
        const values = [...records.values()].filter(options.predicate);
        if (options.sort) {
            values.sort((left, right) => options.direction
                * options.value(left, options.sort)
                    .localeCompare(options.value(right, options.sort)));
        }
        const start = options.page * options.size;
        return {total: values.length,
            ids: new Set(values.slice(start, start + options.size)
                .map(value => value.id)), records};
    }

    function controls(title, prefix, extraFilter) {
        const section = element("section", "card");
        section.dataset[`${prefix}Component`] = "";
        section.append(element("h3", "", title));
        const controlsNode = element("div", "controls");
        const search = element("input");
        search.type = "search";
        search.dataset[`${prefix}Search`] = "";
        const searchLabel = element("label", "", "检索 ");
        searchLabel.append(search);
        controlsNode.append(searchLabel);
        let filter = null;
        if (extraFilter) {
            filter = element("select");
            filter.dataset[`${prefix}Filter`] = "";
            extraFilter.options.forEach(value => {
                const option = element("option", "", value.label);
                option.value = value.value;
                filter.append(option);
            });
            const label = element("label", "", `${extraFilter.label} `);
            label.append(filter);
            controlsNode.append(label);
        }
        const size = element("select");
        size.dataset[`${prefix}PageSize`] = "";
        [10, 50, 100].forEach(value => {
            const option = element("option", "", String(value));
            size.append(option);
        });
        const sizeLabel = element("label", "", "每页 ");
        sizeLabel.append(size);
        controlsNode.append(sizeLabel);
        section.append(controlsNode);
        return {section, controls: controlsNode, search, filter, size};
    }

    function pager(prefix) {
        const wrap = element("div", "controls");
        const group = element("span", "pager");
        const previous = element("button", "", "上一页");
        previous.type = "button";
        previous.dataset[`${prefix}Previous`] = "";
        const position = element("span");
        position.dataset[`${prefix}Position`] = "";
        const next = element("button", "", "下一页");
        next.type = "button";
        next.dataset[`${prefix}Next`] = "";
        group.append(previous, " ", position, " ", next);
        wrap.append(group);
        return {wrap, previous, position, next};
    }

    function updatePager(pagerNodes, page, size, total) {
        const start = total ? page * size + 1 : 0;
        const end = Math.min(total, (page + 1) * size);
        pagerNodes.position.textContent = total
            ? `${start}-${end} / ${total}` : "0 / 0";
        pagerNodes.previous.disabled = page === 0;
        pagerNodes.next.disabled = page >= Math.max(1,
            Math.ceil(total / size)) - 1;
    }

    function classTable() {
        const tableWrap = element("div", "table-scroll");
        const table = element("table");
        table.dataset.classConflicts = "";
        const head = element("thead");
        const row = element("tr");
        [["Class", "className"], ["Risk", "riskOrder"],
            ["Winner", "winner"], ["Shadowed sources", "shadowed"],
            ["Selection", "selection"]].forEach(([label, key]) => {
            const header = element("th");
            const button = element("button", "sortable", label);
            button.type = "button";
            button.dataset.classSort = key;
            header.append(button);
            row.append(header);
        });
        row.append(element("th", "", "Decompiled code"));
        head.append(row);
        const body = element("tbody");
        table.append(head, body);
        tableWrap.append(table);
        return {tableWrap, body};
    }

    function internalTable() {
        const tableWrap = element("div", "table-scroll");
        const table = element("table");
        const head = element("thead");
        const row = element("tr");
        [["Dependency", "dependency"], ["Scope", "scope"],
            ["Resolved version", "resolvedVersion"]]
            .forEach(([label, key]) => {
                const header = element("th");
                const button = element("button", "sortable", label);
                button.type = "button";
                button.dataset.internalSort = key;
                header.append(button);
                row.append(header);
            });
        row.append(element("th", "", "Evidence"));
        head.append(row);
        const body = element("tbody");
        table.append(head, body);
        tableWrap.append(table);
        return {tableWrap, body};
    }

    function evidenceTable(values) {
        const table = element("table", "evidence-table");
        const head = element("thead");
        const header = element("tr");
        ["Source", "Dependency chain", "Original version", "Scope"]
            .forEach(value => header.append(element("th", "", value)));
        head.append(header);
        const body = element("tbody");
        values.forEach(value => {
            const row = element("tr");
            [value.source, value.chain, value.originalVersion, value.scope]
                .forEach(item => row.append(element("td", "", item || "—")));
            body.append(row);
        });
        table.append(head, body);
        return table;
    }

    async function renderClassSection(module, state, nodes, generation) {
        closeCode();
        const query = state.classSearch.toLowerCase();
        const options = {query, filter: state.classRisk,
            sort: state.classSort, direction: state.classDirection,
            page: state.classPage, size: state.classSize,
            predicate: value => (!query || value.searchText.includes(query))
                && (!state.classRisk || value.risk === state.classRisk),
            value: (value, key) => key === "riskOrder"
                ? String(value.riskOrder) : String(value[key] || "")};
        let result = await sectionResult("class-conflicts",
            module.classConflictRange, state, options, generation);
        if (!result || generation !== moduleGeneration) {
            return;
        }
        const pages = Math.max(1, Math.ceil(result.total / state.classSize));
        if (state.classPage >= pages) {
            state.classPage = pages - 1;
            options.page = state.classPage;
            result = await sectionResult("class-conflicts",
                module.classConflictRange, state, options, generation);
        }
        const records = result.records || await shards.recordsForIds(
            "class-conflicts", result.ids);
        if (generation !== moduleGeneration) {
            return;
        }
        const fragment = document.createDocumentFragment();
        result.ids.forEach(id => {
            const value = records.get(id);
            const row = element("tr");
            row.dataset.classConflictRow = "";
            [value.className, value.risk, value.winner, value.shadowed,
                value.selection].forEach(item => row.append(
                    element("td", "", item || "—")));
            const action = element("td");
            const button = element("button", "", "查看反编译代码");
            button.type = "button";
            button.dataset.viewClassCode = "";
            button.setAttribute("aria-expanded", "false");
            button.addEventListener("click", () => {
                if (activeCode && activeCode.button === button) {
                    closeCode();
                } else {
                    openCode(button, value, generation);
                }
            });
            action.append(button);
            row.append(action);
            fragment.append(row);
        });
        nodes.body.replaceChildren(fragment);
        updatePager(nodes.pager, state.classPage,
            state.classSize, result.total);
    }

    async function renderInternalSection(module, state, nodes, generation) {
        const query = state.internalSearch.toLowerCase();
        const options = {query, filter: state.internalScope,
            sort: state.internalSort, direction: state.internalDirection,
            page: state.internalPage, size: state.internalSize,
            predicate: value => (!query || value.searchText.includes(query))
                && (!state.internalScope || value.scope.split(", ")
                    .includes(state.internalScope)),
            value: (value, key) => String(value[key] || "")};
        let result = await sectionResult("internal-conflicts",
            module.internalConflictRange, state, options, generation);
        if (!result || generation !== moduleGeneration) {
            return;
        }
        const pages = Math.max(1, Math.ceil(result.total / state.internalSize));
        if (state.internalPage >= pages) {
            state.internalPage = pages - 1;
            options.page = state.internalPage;
            result = await sectionResult("internal-conflicts",
                module.internalConflictRange, state, options, generation);
        }
        const records = result.records || await shards.recordsForIds(
            "internal-conflicts", result.ids);
        if (generation !== moduleGeneration) {
            return;
        }
        const fragment = document.createDocumentFragment();
        result.ids.forEach(id => {
            const value = records.get(id);
            const row = element("tr");
            row.append(element("td", "dependency-cell", value.dependency),
                element("td", "", value.scope || "—"),
                element("td", "", value.resolvedVersion || "—"));
            const evidence = element("td");
            evidence.append(evidenceTable(value.evidence));
            row.append(evidence);
            fragment.append(row);
        });
        nodes.body.replaceChildren(fragment);
        updatePager(nodes.pager, state.internalPage,
            state.internalSize, result.total);
    }

    async function renderModule(module) {
        const generation = ++moduleGeneration;
        closeCode();
        const panel = document.getElementById("module-panel");
        panel.textContent = "Loading Module…";
        const state = moduleState(module.id);
        const content = document.createDocumentFragment();
        content.append(element("h3", "", module.coordinate || module.label));
        if (module.failure) {
            content.append(element("p", "FAILED", module.failure));
        }

        const classControls = controls("冲突类", "class", {
            label: "Risk", options: [{value: "", label: "全部"},
                {value: "HIGH", label: "HIGH"},
                {value: "LOW", label: "LOW"}]
        });
        classControls.section.dataset.classConflictComponent = "";
        classControls.search.value = state.classSearch;
        classControls.filter.value = state.classRisk;
        classControls.size.value = String(state.classSize);
        const classNodes = classTable();
        const classPager = pager("class");
        classControls.section.append(classNodes.tableWrap, classPager.wrap);
        content.append(classControls.section);

        const internalControls = controls("模块内部依赖冲突", "internal", {
            label: "Scope", options: [{value: "", label: "全部"},
                ...manifest.scopes.map(value => ({
                    value: value.value, label: value.value}))]
        });
        internalControls.search.value = state.internalSearch;
        internalControls.filter.value = state.internalScope;
        internalControls.size.value = String(state.internalSize);
        const internalNodes = internalTable();
        const internalPager = pager("internal");
        internalControls.section.append(internalNodes.tableWrap,
            internalPager.wrap);
        content.append(internalControls.section);

        const treeSection = element("section", "card");
        treeSection.append(element("h3", "", "Dependency tree"));
        const treeStatus = element("p", "muted", "Loading…");
        treeSection.append(treeStatus);
        content.append(treeSection);
        panel.replaceChildren(content);

        const rerenderClass = () => renderClassSection(module, state,
            {body: classNodes.body, pager: classPager}, generation)
            .catch(error => showRetry(classPager.position,
                error.message, rerenderClass));
        classControls.search.addEventListener("input", event => {
            state.classSearch = event.target.value;
            state.classPage = 0;
            rerenderClass();
        });
        classControls.filter.addEventListener("change", event => {
            state.classRisk = event.target.value;
            state.classPage = 0;
            rerenderClass();
        });
        classControls.size.addEventListener("change", event => {
            state.classSize = Number(event.target.value);
            state.classPage = 0;
            rerenderClass();
        });
        classNodes.tableWrap.querySelectorAll("[data-class-sort]")
            .forEach(button => button.addEventListener("click", () => {
                const key = button.dataset.classSort;
                state.classDirection = state.classSort === key
                    ? -state.classDirection : 1;
                state.classSort = key;
                state.classPage = 0;
                rerenderClass();
            }));
        classPager.previous.addEventListener("click", () => {
            state.classPage -= 1;
            rerenderClass();
        });
        classPager.next.addEventListener("click", () => {
            state.classPage += 1;
            rerenderClass();
        });

        const rerenderInternal = () => renderInternalSection(module, state,
            {body: internalNodes.body, pager: internalPager}, generation)
            .catch(error => showRetry(internalPager.position,
                error.message, rerenderInternal));
        internalControls.search.addEventListener("input", event => {
            state.internalSearch = event.target.value;
            state.internalPage = 0;
            rerenderInternal();
        });
        internalControls.filter.addEventListener("change", event => {
            state.internalScope = event.target.value;
            state.internalPage = 0;
            rerenderInternal();
        });
        internalControls.size.addEventListener("change", event => {
            state.internalSize = Number(event.target.value);
            state.internalPage = 0;
            rerenderInternal();
        });
        internalNodes.tableWrap.querySelectorAll("[data-internal-sort]")
            .forEach(button => button.addEventListener("click", () => {
                const key = button.dataset.internalSort;
                state.internalDirection = state.internalSort === key
                    ? -state.internalDirection : 1;
                state.internalSort = key;
                state.internalPage = 0;
                rerenderInternal();
            }));
        internalPager.previous.addEventListener("click", () => {
            state.internalPage -= 1;
            rerenderInternal();
        });
        internalPager.next.addEventListener("click", () => {
            state.internalPage += 1;
            rerenderInternal();
        });

        rerenderClass();
        rerenderInternal();
        try {
            const record = (await shards.recordsForIds("dependency-trees",
                new Set([module.treeId]))).get(module.treeId);
            if (generation !== moduleGeneration) {
                return;
            }
            if (record.failure) {
                treeStatus.className = "FAILED";
                treeStatus.textContent = "Dependency tree 未生成。";
            } else if (!record.text) {
                treeStatus.className = "muted";
                treeStatus.textContent = "未发现 dependency。";
            } else {
                const pre = element("pre", "dependency-tree", record.text);
                treeStatus.replaceWith(pre);
            }
        } catch (error) {
            if (generation === moduleGeneration) {
                showRetry(treeStatus, error.message,
                    () => renderModule(module));
            }
        }
    }

    if (moduleChoices.length) {
        const moduleSelector = createCombobox(
            document.querySelector('[data-combobox="module-selector"]'),
            moduleChoices, {
                name: "Module selector", required: true,
                label: item => item.coordinate || item.label,
                search: item => `${item.coordinate} ${item.label} ${item.pom}`,
                onSelect: item => { if (item) { renderModule(item); } }
            });
        moduleSelector.select(moduleChoices[0]);
    } else {
        document.getElementById("module-panel").textContent =
            "未发现 Module。";
    }
    renderDependencies();
})();
