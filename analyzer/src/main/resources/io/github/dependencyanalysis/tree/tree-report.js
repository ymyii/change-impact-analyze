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
    if (manifest.schemaVersion !== 2 || !Array.isArray(manifest.modules)
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
                && typeof value.resolutionSource === "string"
                && typeof value.resolutionDetail === "string"
                && typeof value.searchText === "string";
        }
        if (kind === "dependency-rows") {
            return Number.isInteger(value.dependencyId)
                && Number.isInteger(value.moduleId)
                && typeof value.chain === "string"
                && typeof value.resolutionSource === "string"
                && typeof value.resolutionDetail === "string";
        }
        if (kind === "module-dependency-catalog") {
            return Number.isInteger(value.moduleId)
                && Number.isInteger(value.dependencyId)
                && Number.isInteger(value.resolvedVersionCount)
                && Number.isInteger(value.uniqueVersionCount);
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
                option.append(element("span", "combobox-option-label",
                    label(item)));
                if (options.badges) {
                    const badges = element("span", "version-counts");
                    options.badges(item).forEach(value => {
                        const badge = element("span", value.className,
                            value.text);
                        badge.setAttribute("aria-label", value.label);
                        badge.title = value.label;
                        badges.append(badge);
                    });
                    option.append(badges);
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

    async function catalogRanges(catalog, isCurrent) {
        if (!catalog || !catalog.rangeCount) {
            return [];
        }
        const records = await shards.recordsForIds("dependency-ranges",
            ids(catalog.firstRangeId, catalog.rangeCount));
        if (!isCurrent()) {
            return null;
        }
        return common.normalizeRanges([...records.values()].map(value => ({
            start: value.rowStart, count: value.rowCount
        })));
    }

    async function allDependencyIndexes(isCurrent, status) {
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
            if (!isCurrent()) {
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
        if (key === "resolutionSource") {
            return `${row.resolutionSource} ${row.resolutionDetail}`;
        }
        return row[key] || "";
    }

    function versionBadges(item) {
        return [{
            className: "version-count",
            text: String(item.resolvedVersionCount),
            label: `${item.resolvedVersionCount} resolved versions`
        }, {
            className: "unique-version-count",
            text: `${item.uniqueVersionCount} unique`,
            label: `${item.uniqueVersionCount} unique original/resolved versions`
        }];
    }

    function resolutionCell(row) {
        const cell = element("td", "resolution-cell");
        cell.append(element("span", "resolution-source",
            row.resolutionSource || "—"));
        if (row.resolutionDetail) {
            cell.append(element("span", "resolution-detail",
                row.resolutionDetail));
        }
        return cell;
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
            badges: versionBadges,
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
            const values = await catalogRanges(dependency,
                () => generation === dependencyGeneration);
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
            const values = await catalogRanges(scope,
                () => generation === dependencyGeneration);
            if (!values) {
                return null;
            }
            ranges = common.intersectRanges(ranges, values);
        }
        const query = dependencySearch.value.trim().toLowerCase();
        if (query || dependencyState.sort) {
            const indexes = await allDependencyIndexes(
                () => generation === dependencyGeneration,
                dependencyStatus);
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
                tr.append(resolutionCell(row));
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
                internalDependencyId: null,
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
        table.dataset.moduleDependencies = "";
        const head = element("thead");
        const row = element("tr");
        [["Dependency", "dependency"], ["Scope", "scope"],
            ["Dependency chain", "chain"],
            ["Original version", "originalVersion"],
            ["Resolved version", "resolvedVersion"],
            ["Resolution source", "resolutionSource"]]
            .forEach(([label, key]) => {
                const header = element("th");
                const button = element("button", "sortable", label);
                button.type = "button";
                button.dataset.internalSort = key;
                header.append(button);
                row.append(header);
            });
        head.append(row);
        const body = element("tbody");
        table.append(head, body);
        tableWrap.append(table);
        return {tableWrap, body};
    }

    function comboboxControl(id, label) {
        const control = element("div",
            "report-control dependency-combobox-control");
        const labelNode = element("label", "", label);
        labelNode.htmlFor = `${id}-input`;
        const root = element("div", "combobox");
        root.dataset.combobox = id;
        const input = element("input");
        input.id = `${id}-input`;
        input.type = "text";
        input.setAttribute("role", "combobox");
        input.setAttribute("autocomplete", "off");
        input.setAttribute("aria-autocomplete", "list");
        input.setAttribute("aria-expanded", "false");
        input.setAttribute("aria-controls", `${id}-options`);
        const toggle = element("button", "combobox-toggle");
        toggle.id = `${id}-toggle`;
        toggle.type = "button";
        toggle.dataset.label = label;
        toggle.setAttribute("aria-label", `展开 ${label} 候选`);
        toggle.setAttribute("aria-expanded", "false");
        toggle.setAttribute("aria-controls", `${id}-options`);
        const list = element("ul", "combobox-options");
        list.id = `${id}-options`;
        list.setAttribute("role", "listbox");
        list.hidden = true;
        const status = element("p", "muted candidate-status");
        status.id = `${id}-status`;
        status.setAttribute("aria-live", "polite");
        root.append(input, toggle, list);
        control.append(labelNode, root, status);
        return {control, root};
    }

    function selectControl(label, values, selected) {
        const control = element("select");
        values.forEach(value => {
            const option = element("option", "", value.label);
            option.value = value.value;
            control.append(option);
        });
        control.value = selected;
        const wrapper = element("label", "report-control", label);
        wrapper.append(control);
        return {wrapper, control};
    }

    function internalComponent(state) {
        const form = element("form",
            "dependency-filter-form module-dependency-filter-form");
        const primary = element("div",
            "dependency-filter-primary module-dependency-filter-primary");
        const search = element("input");
        search.type = "search";
        search.dataset.internalSearch = "";
        search.value = state.internalSearch;
        const searchControl = element("label",
            "report-control dependency-search-control", "检索");
        searchControl.append(search);
        const dependencyControl = comboboxControl(
            "module-dependency-filter", "Dependency");
        primary.append(searchControl, dependencyControl.control);

        const secondary = element("div", "dependency-filter-secondary");
        const scope = selectControl("Scope", [{value: "", label: "全部"},
            ...manifest.scopes.map(value => ({
                value: value.value, label: value.value}))],
        state.internalScope);
        scope.control.dataset.internalFilter = "";
        const size = selectControl("每页", [10, 50, 100].map(value => ({
            value: String(value), label: String(value)})),
        String(state.internalSize));
        size.control.dataset.internalPageSize = "";
        const buttons = element("div", "dependency-filter-buttons");
        const submit = element("button", "", "检索");
        submit.type = "submit";
        const clear = element("button", "", "清空");
        clear.type = "button";
        clear.dataset.internalClear = "";
        buttons.append(submit, clear);
        secondary.append(scope.wrapper, size.wrapper, buttons);
        form.append(primary, secondary);

        const summary = element("p", "muted", "Loading dependencies…");
        summary.dataset.internalSummary = "";
        summary.setAttribute("aria-live", "polite");
        const table = internalTable();
        const page = pager("internal");
        return {form, search, dependencyRoot: dependencyControl.root,
            scope: scope.control, size: size.control, clear, summary,
            table, pager: page};
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

    async function internalResult(module, state, combo, generation, status) {
        const isCurrent = () => generation === moduleGeneration;
        let ranges = [module.dependencyRange];
        const dependency = combo.value();
        if (dependency) {
            const values = await catalogRanges(
                dependencies.get(dependency.id), isCurrent);
            if (!values) {
                return null;
            }
            ranges = common.intersectRanges(ranges, values);
        }
        const scope = manifest.scopes.find(value =>
            value.value === state.internalScope);
        if (scope) {
            const values = await catalogRanges(scope, isCurrent);
            if (!values) {
                return null;
            }
            ranges = common.intersectRanges(ranges, values);
        }
        const query = state.internalSearch.trim().toLowerCase();
        if (query || state.internalSort) {
            const indexes = await allDependencyIndexes(isCurrent, status);
            if (!indexes) {
                return null;
            }
            const filtered = indexes.filter(value => rangesContain(ranges,
                value.id) && (!query || value.searchText.includes(query)));
            if (state.internalSort) {
                filtered.sort((left, right) => state.internalDirection
                    * dependencySortValue(left, state.internalSort)
                        .localeCompare(dependencySortValue(
                            right, state.internalSort)));
            }
            const start = state.internalPage * state.internalSize;
            return {total: filtered.length,
                pageIds: new Set(filtered.slice(start,
                    start + state.internalSize).map(value => value.id))};
        }
        const total = rangeCount(ranges);
        return {total, pageIds: pageIds(ranges,
            state.internalPage * state.internalSize, state.internalSize)};
    }

    async function renderInternalSection(
        module, state, nodes, generation) {
        nodes.summary.textContent = "Loading dependencies…";
        let result = await internalResult(module, state,
            nodes.combo, generation, nodes.summary);
        if (!result || generation !== moduleGeneration) {
            return;
        }
        const pages = Math.max(1, Math.ceil(result.total / state.internalSize));
        if (state.internalPage >= pages) {
            state.internalPage = pages - 1;
            result = await internalResult(module, state,
                nodes.combo, generation, nodes.summary);
        }
        if (!result || generation !== moduleGeneration) {
            return;
        }
        const records = await shards.recordsForIds(
            "dependency-rows", result.pageIds);
        if (generation !== moduleGeneration) {
            return;
        }
        const fragment = document.createDocumentFragment();
        result.pageIds.forEach(id => {
            const value = records.get(id);
            const row = element("tr");
            row.dataset.moduleDependencyRow = "";
            [dependencies.get(value.dependencyId).value, value.scope,
                value.chain, value.originalVersion, value.resolvedVersion]
                .forEach((item, index) => row.append(element("td",
                    index === 0 || index === 2 ? "dependency-cell" : "",
                    item || "—")));
            row.append(resolutionCell(value));
            fragment.append(row);
        });
        nodes.body.replaceChildren(fragment);
        const start = result.total
            ? state.internalPage * state.internalSize + 1 : 0;
        const end = Math.min(result.total,
            (state.internalPage + 1) * state.internalSize);
        nodes.summary.textContent = result.total
            ? `Showing ${start}–${end} of ${result.total}`
            : "当前 Module 未发现 dependency。";
        updatePager(nodes.pager, state.internalPage,
            state.internalSize, result.total);
    }

    async function initializeInternalSection(
        module, state, section, generation) {
        const load = async () => {
            const title = element("h3", "", "模块内部依赖分析");
            const loading = element("p", "muted",
                "Loading Dependency candidates…");
            section.replaceChildren(title, loading);
            try {
                const range = module.dependencyCatalogRange;
                const records = await shards.recordsForIds(
                    "module-dependency-catalog",
                    ids(range.start, range.count));
                if (generation !== moduleGeneration) {
                    return;
                }
                const choices = [...records.values()]
                    .sort((left, right) => left.dependencyId
                        - right.dependencyId)
                    .map(value => ({
                        id: value.dependencyId,
                        value: dependencies.get(value.dependencyId).value,
                        resolvedVersionCount: value.resolvedVersionCount,
                        uniqueVersionCount: value.uniqueVersionCount
                    }));
                const component = internalComponent(state);
                section.replaceChildren(title, component.form,
                    component.summary, component.table.tableWrap,
                    component.pager.wrap);
                let rerender;
                const combo = createCombobox(component.dependencyRoot,
                    choices, {
                        name: "Module Dependency filter", required: false,
                        label: item => item.value,
                        search: item => item.value,
                        badges: versionBadges,
                        onSelect: item => {
                            state.internalDependencyId = item ? item.id : null;
                            state.internalPage = 0;
                            rerender();
                        }
                    });
                const selected = choices.find(value =>
                    value.id === state.internalDependencyId);
                if (selected) {
                    combo.select(selected, false);
                } else {
                    state.internalDependencyId = null;
                }
                const nodes = {combo, summary: component.summary,
                    body: component.table.body, pager: component.pager};
                rerender = () => renderInternalSection(
                    module, state, nodes, generation)
                    .catch(error => {
                        if (generation === moduleGeneration) {
                            showRetry(component.summary, error.message,
                                rerender);
                        }
                    });
                component.form.addEventListener("submit", event => {
                    event.preventDefault();
                    state.internalSearch = component.search.value;
                    state.internalPage = 0;
                    rerender();
                });
                component.scope.addEventListener("change", event => {
                    state.internalScope = event.target.value;
                    state.internalPage = 0;
                    rerender();
                });
                component.size.addEventListener("change", event => {
                    state.internalSize = Number(event.target.value);
                    state.internalPage = 0;
                    rerender();
                });
                component.clear.addEventListener("click", () => {
                    component.search.value = "";
                    component.scope.value = "";
                    combo.clear(false);
                    state.internalSearch = "";
                    state.internalScope = "";
                    state.internalDependencyId = null;
                    state.internalPage = 0;
                    state.internalSort = "";
                    state.internalDirection = 1;
                    rerender();
                });
                component.table.tableWrap.querySelectorAll(
                    "[data-internal-sort]").forEach(button =>
                    button.addEventListener("click", () => {
                        const key = button.dataset.internalSort;
                        state.internalDirection = state.internalSort === key
                            ? -state.internalDirection : 1;
                        state.internalSort = key;
                        state.internalPage = 0;
                        rerender();
                    }));
                component.pager.previous.addEventListener("click", () => {
                    state.internalPage -= 1;
                    rerender();
                });
                component.pager.next.addEventListener("click", () => {
                    state.internalPage += 1;
                    rerender();
                });
                rerender();
            } catch (error) {
                if (generation === moduleGeneration) {
                    showRetry(loading, error.message, load);
                }
            }
        };
        await load();
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

        const internalSection = element("section", "card");
        internalSection.dataset.internalComponent = "";
        internalSection.append(element("h3", "", "模块内部依赖分析"),
            element("p", "muted", "Loading Dependency candidates…"));
        content.append(internalSection);

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

        rerenderClass();
        initializeInternalSection(module, state, internalSection, generation);
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
