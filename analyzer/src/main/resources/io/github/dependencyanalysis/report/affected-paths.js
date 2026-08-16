(() => {
    "use strict";

    const manifestNode = document.getElementById("affected-path-manifest");
    const rowsNode = document.getElementById("path-rows");
    const emptyNode = document.getElementById("path-empty");
    if (!manifestNode || !rowsNode || !emptyNode) {
        return;
    }

    let manifest;
    try {
        manifest = JSON.parse(manifestNode.textContent);
        if (manifest.schemaVersion !== 4 || !manifest.rowRanges
                || !manifest.shards || !Array.isArray(manifest.sources)) {
            throw new Error("Unsupported Affected Paths schema.");
        }
    } catch (error) {
        emptyNode.textContent = `Affected path data could not be loaded: ${error.message}`;
        emptyNode.classList.remove("hidden");
        return;
    }
    manifestNode.remove();

    const typeSelect = document.getElementById("path-type");
    const searchForm = document.getElementById("path-search-form");
    const searchInput = document.getElementById("path-search");
    const includeInput = document.getElementById("path-dependency-include");
    const excludeInput = document.getElementById("path-dependency-exclude");
    const pageSizeSelect = document.getElementById("path-page-size");
    const summaryNode = document.getElementById("path-result-summary");
    const pageInput = document.getElementById("path-page");
    const pageCountNode = document.getElementById("path-page-count");
    const firstButton = document.getElementById("path-first");
    const previousButton = document.getElementById("path-previous");
    const nextButton = document.getElementById("path-next");
    const lastButton = document.getElementById("path-last");
    const state = {type: "impact", pageSize: 20, page: 1,
        expandedRowId: null, baseRanges: [], ranges: [],
        applied: {query: "", includes: [], excludes: []},
        searchGeneration: 0, renderGeneration: 0};
    const requests = new Map();
    const pending = new Map();

    function node(tag, className, text) {
        const value = document.createElement(tag);
        if (className) {
            value.className = className;
        }
        if (text !== undefined) {
            value.textContent = text;
        }
        return value;
    }

    function shardKey(kind, id) {
        return `${kind}:${id}`;
    }

    function recordId(kind, record) {
        if (kind === "index") {
            return record.pathId;
        }
        if (kind === "rows") {
            return record.rowId;
        }
        return record.id;
    }

    function validRecord(kind, record) {
        if (!record || typeof record !== "object") {
            return false;
        }
        const integer = value => Number.isInteger(value) && value >= 0;
        const text = value => typeof value === "string";
        if (kind === "index") {
            return integer(record.pathId) && text(record.type)
                && text(record.searchText) && integer(record.rowStart)
                && integer(record.rowCount);
        }
        if (kind === "source-index") {
            return integer(record.id) && integer(record.rowStart)
                && integer(record.rowCount);
        }
        if (kind === "rows") {
            return integer(record.rowId) && integer(record.pathId)
                && integer(record.changedMemberId);
        }
        if (kind === "paths") {
            return integer(record.id) && text(record.type)
                && text(record.classification) && text(record.rootKind)
                && typeof record.cycle === "boolean"
                && text(record.applicationMember) && text(record.relation)
                && text(record.changedClass) && Array.isArray(record.methodIds)
                && record.methodIds.every(integer);
        }
        if (kind === "methods") {
            return integer(record.id) && text(record.label)
                && typeof record.project === "boolean";
        }
        if (kind === "members") {
            return integer(record.id) && integer(record.dependencyUpgradeId)
                && text(record.changePointKind) && text(record.owner)
                && (record.name === null || text(record.name))
                && text(record.codeDiffStatus)
                && (record.codeDiffId === null || integer(record.codeDiffId));
        }
        if (kind === "dependencies") {
            return integer(record.id) && text(record.oldArtifact)
                && text(record.newArtifact) && text(record.scope)
                && text(record.source);
        }
        return kind === "diffs" && integer(record.id)
            && text(record.unifiedDiff);
    }

    window.__CIA_AFFECTED_PATH_SHARD__ = payload => {
        const current = document.currentScript;
        const key = current && current.dataset
            ? current.dataset.ciaAffectedPathShard : "";
        const waiter = pending.get(key);
        if (!waiter) {
            return;
        }
        const validRecords = payload && Array.isArray(payload.records)
            && payload.records.length === waiter.descriptor.records
            && payload.records.every((record, index) =>
                validRecord(waiter.kind, record)
                    && recordId(waiter.kind, record)
                    === waiter.descriptor.firstId + index);
        if (!payload || payload.schemaVersion !== manifest.schemaVersion
                || payload.kind !== waiter.kind
                || payload.shardId !== waiter.descriptor.id || !validRecords) {
            waiter.reject(new Error(
                `Invalid ${waiter.file} shard payload or schema.`));
            return;
        }
        waiter.registered = true;
        waiter.resolve(payload.records);
    };

    function descriptors(kind) {
        return Array.isArray(manifest.shards[kind])
            ? manifest.shards[kind] : [];
    }

    function descriptorForId(kind, id) {
        return descriptors(kind).find(value => id >= value.firstId
            && id <= value.lastId);
    }

    function loadShard(kind, descriptor) {
        const key = shardKey(kind, descriptor.id);
        if (requests.has(key)) {
            return requests.get(key);
        }
        let script;
        const request = new Promise((resolve, reject) => {
            const waiter = {resolve, reject, file: descriptor.file,
                kind, descriptor, registered: false};
            pending.set(key, waiter);
            script = document.createElement("script");
            script.src = descriptor.file;
            script.async = true;
            script.dataset.ciaAffectedPathShard = key;
            script.onload = () => {
                if (!waiter.registered) {
                    reject(new Error(
                        `Affected path shard did not register: ${descriptor.file}`));
                }
            };
            script.onerror = () => reject(new Error(
                `Affected path shard could not be loaded: ${descriptor.file}`));
            document.head.append(script);
        }).finally(() => {
            pending.delete(key);
            requests.delete(key);
            if (script) {
                script.remove();
            }
        });
        requests.set(key, request);
        return request;
    }

    async function recordsForIds(kind, ids) {
        if (!ids.size) {
            return new Map();
        }
        const selected = new Map();
        ids.forEach(id => {
            const descriptor = descriptorForId(kind, id);
            if (!descriptor) {
                throw new Error(`No ${kind} shard contains record ${id}.`);
            }
            selected.set(descriptor.id, descriptor);
        });
        const chunks = await Promise.all([...selected.values()].map(value =>
            loadShard(kind, value)));
        const result = new Map();
        chunks.flat().forEach(record => {
            const id = recordId(kind, record);
            if (ids.has(id)) {
                result.set(id, record);
            }
        });
        ids.forEach(id => {
            if (!result.has(id)) {
                const descriptor = descriptorForId(kind, id);
                throw new Error(`Missing ${kind} record ${id} in ${descriptor.file}.`);
            }
        });
        return result;
    }

    function oneRange(name) {
        const value = manifest.rowRanges[name];
        return value && value.count > 0
            ? [{start: value.start, count: value.count}] : [];
    }

    function normalizeRanges(values) {
        const result = [];
        [...values].filter(value => value.count > 0)
            .sort((left, right) => left.start - right.start)
            .forEach(value => {
                const previous = result[result.length - 1];
                const end = value.start + value.count;
                if (previous && value.start <= previous.start + previous.count) {
                    previous.count = Math.max(previous.start + previous.count,
                        end) - previous.start;
                } else {
                    result.push({start: value.start, count: value.count});
                }
            });
        return result;
    }

    function intersectRanges(leftValues, rightValues) {
        const left = normalizeRanges(leftValues);
        const right = normalizeRanges(rightValues);
        const result = [];
        let leftIndex = 0;
        let rightIndex = 0;
        while (leftIndex < left.length && rightIndex < right.length) {
            const start = Math.max(left[leftIndex].start, right[rightIndex].start);
            const end = Math.min(left[leftIndex].start + left[leftIndex].count,
                right[rightIndex].start + right[rightIndex].count);
            if (end > start) {
                result.push({start, count: end - start});
            }
            if (left[leftIndex].start + left[leftIndex].count <
                    right[rightIndex].start + right[rightIndex].count) {
                leftIndex += 1;
            } else {
                rightIndex += 1;
            }
        }
        return result;
    }

    function applyViewType() {
        state.ranges = state.type === "all" ? normalizeRanges(state.baseRanges)
            : intersectRanges(state.baseRanges, oneRange(state.type));
    }

    function matchingCount() {
        return state.ranges.reduce((total, value) => total + value.count, 0);
    }

    function rowIdsForPage(start, end) {
        const result = [];
        let offset = 0;
        for (const range of state.ranges) {
            const rangeEnd = offset + range.count;
            if (end <= offset) {
                break;
            }
            if (start < rangeEnd && end > offset) {
                const localStart = Math.max(0, start - offset);
                const localEnd = Math.min(range.count, end - offset);
                for (let value = localStart; value < localEnd; value += 1) {
                    result.push(range.start + value);
                }
            }
            offset = rangeEnd;
        }
        return result;
    }

    function glob(expression) {
        const separator = expression.indexOf(":");
        if (!expression || separator <= 0
                || separator !== expression.lastIndexOf(":")
                || separator === expression.length - 1 || /\s/.test(expression)) {
            throw new Error(`Invalid dependency Glob: ${expression}`);
        }
        const segment = value => new RegExp(`^${[...value].map(character => {
            if (character === "*") {
                return ".*";
            }
            if (character === "?") {
                return ".";
            }
            return character.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        }).join("")}$`);
        return {expression,
            group: segment(expression.slice(0, separator)),
            artifact: segment(expression.slice(separator + 1))};
    }

    function globs(value) {
        return value.split(",").map(item => item.trim()).filter(Boolean)
            .map(glob);
    }

    function sourceMatches(source, pattern) {
        const separator = source.indexOf(":");
        return pattern.group.test(source.slice(0, separator))
            && pattern.artifact.test(source.slice(separator + 1));
    }

    function selectedSources(includes, excludes) {
        return manifest.sources.filter(value => {
            const included = includes.length === 0 || includes.some(pattern =>
                sourceMatches(value.source, pattern));
            return included && !excludes.some(pattern =>
                sourceMatches(value.source, pattern));
        });
    }

    async function sourceRanges(includes, excludes, generation) {
        if (includes.length === 0 && excludes.length === 0) {
            return oneRange("all");
        }
        const sources = selectedSources(includes, excludes);
        const ranges = [];
        for (let index = 0; index < sources.length; index += 1) {
            summaryNode.textContent = `Loading source ranges ${index + 1} of ${sources.length}…`;
            const ids = new Set();
            for (let offset = 0; offset < sources[index].count; offset += 1) {
                ids.add(sources[index].firstId + offset);
            }
            const records = await recordsForIds("source-index", ids);
            if (generation !== state.searchGeneration) {
                return null;
            }
            records.forEach(record => ranges.push({
                start: record.rowStart, count: record.rowCount}));
        }
        return normalizeRanges(ranges);
    }

    async function methodRanges(query, generation) {
        if (!query) {
            return oneRange("all");
        }
        const ranges = [];
        const values = descriptors("index");
        for (let index = 0; index < values.length; index += 1) {
            summaryNode.textContent =
                `Searching affected methods: index shard ${index + 1} of ${values.length}…`;
            const records = await loadShard("index", values[index]);
            if (generation !== state.searchGeneration) {
                return null;
            }
            records.forEach(record => {
                if (record.searchText.toLocaleLowerCase().includes(query)) {
                    ranges.push({start: record.rowStart, count: record.rowCount});
                }
            });
        }
        return normalizeRanges(ranges);
    }

    function appliedSummary() {
        const values = [];
        if (state.applied.query) {
            values.push(`affected method “${state.applied.query}”`);
        }
        if (state.applied.includes.length) {
            values.push(`include ${state.applied.includes.join(", ")}`);
        }
        if (state.applied.excludes.length) {
            values.push(`exclude ${state.applied.excludes.join(", ")}`);
        }
        return values.length ? ` Applied: ${values.join("; ")}.` : "";
    }

    async function submitSearch() {
        let includes;
        let excludes;
        try {
            includes = globs(includeInput.value);
            excludes = globs(excludeInput.value);
        } catch (error) {
            summaryNode.textContent = `${error.message}. The last successful result was retained.`;
            return;
        }
        const query = searchInput.value.toLocaleLowerCase();
        const generation = ++state.searchGeneration;
        state.renderGeneration += 1;
        try {
            const [sources, methods] = await Promise.all([
                sourceRanges(includes, excludes, generation),
                methodRanges(query, generation)
            ]);
            if (generation !== state.searchGeneration || !sources || !methods) {
                return;
            }
            state.baseRanges = intersectRanges(sources, methods);
            state.applied = {query,
                includes: includes.map(value => value.expression),
                excludes: excludes.map(value => value.expression)};
            state.page = 1;
            state.expandedRowId = null;
            applyViewType();
            await render(false);
        } catch (error) {
            if (generation === state.searchGeneration) {
                showError(error, submitSearch);
            }
        }
    }

    function appendCell(row, text, className, code) {
        const cell = node("td", className);
        cell.append(node(code ? "code" : "span", "", text));
        row.append(cell);
        return cell;
    }

    function memberLabel(member) {
        return member.name ? `${member.owner}#${member.name}` : member.owner;
    }

    function dependencyLabel(member, dependencies) {
        const dependency = dependencies.get(member.dependencyUpgradeId);
        return dependency
            ? `${dependency.oldArtifact} → ${dependency.newArtifact} (${dependency.scope})`
            : "Unavailable";
    }

    function pathMethods(path, methods) {
        return path.methodIds.map(id => methods.get(id)).filter(Boolean);
    }

    function affectedMethods(path, methods) {
        const project = pathMethods(path, methods)
            .filter(method => method.project).map(method => method.label);
        if (project.length) {
            return [...new Set(project)].join(", ");
        }
        return path.type === "structural"
            ? path.applicationMember : "Unavailable";
    }

    function pathSequence(path, member, methods) {
        const values = pathMethods(path, methods).map(method => method.label);
        if (path.type === "structural") {
            values.push(path.applicationMember, path.relation,
                path.changedClass);
        }
        values.push(`Changed member: ${memberLabel(member)}`);
        return values.join(" → ");
    }

    function diffClass(line) {
        if (line.startsWith("--- ")) {
            return "diff-line diff-file-old";
        }
        if (line.startsWith("+++ ")) {
            return "diff-line diff-file-new";
        }
        if (line.startsWith("@@")) {
            return "diff-line diff-hunk";
        }
        if (line.startsWith("+")) {
            return "diff-line diff-add";
        }
        if (line.startsWith("-")) {
            return "diff-line diff-delete";
        }
        return "diff-line";
    }

    function createDiffRow(comparison) {
        const diffRow = node("tr", "path-diff-row");
        const cell = node("td", "path-diff");
        cell.colSpan = 8;
        const pre = node("pre", "diff");
        const code = node("code", "diff-code");
        comparison.unifiedDiff.replace(/\r\n?/g, "\n").split("\n")
            .forEach(line => code.append(node("span", diffClass(line),
                line || "\u00a0")));
        pre.append(code);
        cell.append(pre);
        diffRow.append(cell);
        return diffRow;
    }

    function appendCodeDiffCell(row, relation, member) {
        const cell = node("td");
        if (member.codeDiffStatus === "UNAVAILABLE") {
            cell.append(node("span", "muted", "Unavailable"));
        } else if (member.codeDiffStatus === "JAVA_TEXT_IDENTICAL") {
            cell.append(node("span", "muted", "Java text identical"));
        } else {
            const button = node("button", "table-action", "View Java diff");
            button.type = "button";
            button.setAttribute("aria-expanded",
                state.expandedRowId === relation.rowId ? "true" : "false");
            button.addEventListener("click", () => {
                const open = state.expandedRowId === relation.rowId;
                state.expandedRowId = open ? null : relation.rowId;
                render(false);
            });
            cell.append(button);
        }
        row.append(cell);
    }

    function createRow(relation, path, member, methods, dependencies) {
        const row = node("tr", "path-row");
        const typeCell = node("td");
        typeCell.append(node("span", `badge ${path.type}`,
            path.type === "impact" ? "Impact" : "Structural"));
        row.append(typeCell);
        appendCell(row,
            `${path.classification === "DIRECT" ? "Direct" : "Transitive"} ${path.type === "impact" ? "dependency" : "structural"} impact`);
        appendCell(row, affectedMethods(path, methods), "method-cell", true);
        appendCell(row, dependencyLabel(member, dependencies),
            "dependency-cell", true);
        const kindCell = node("td");
        kindCell.append(node("span", "badge kind", member.changePointKind));
        row.append(kindCell);
        appendCell(row, memberLabel(member), "member-cell", true);
        appendCell(row, pathSequence(path, member, methods),
            "path-sequence", true);
        appendCodeDiffCell(row, relation, member);
        return row;
    }

    function showError(error, retry) {
        emptyNode.replaceChildren(document.createTextNode(
            `Affected path data could not be loaded: ${error.message} `));
        const button = node("button", "table-action", "Retry");
        button.type = "button";
        button.addEventListener("click", retry, {once: true});
        emptyNode.append(button);
        emptyNode.classList.remove("hidden");
        summaryNode.textContent = "The last successfully rendered page was retained.";
    }

    async function render(resetPage) {
        const generation = ++state.renderGeneration;
        if (resetPage) {
            state.page = 1;
            state.expandedRowId = null;
        }
        const matches = matchingCount();
        const pageCount = Math.max(1, Math.ceil(matches / state.pageSize));
        state.page = Math.min(Math.max(1, state.page), pageCount);
        const start = (state.page - 1) * state.pageSize;
        const end = Math.min(start + state.pageSize, matches);
        summaryNode.textContent = matches === 0
            ? `Showing 0 matching records.${appliedSummary()}`
            : `Loading ${start + 1}–${end} of ${matches} matching records…`;
        try {
            const rowIds = new Set(rowIdsForPage(start, end));
            const relations = await recordsForIds("rows", rowIds);
            const pathIds = new Set([...relations.values()]
                .map(value => value.pathId));
            const memberIds = new Set([...relations.values()]
                .map(value => value.changedMemberId));
            const [paths, members] = await Promise.all([
                recordsForIds("paths", pathIds),
                recordsForIds("members", memberIds)
            ]);
            const methodIds = new Set();
            paths.forEach(path => path.methodIds.forEach(id =>
                methodIds.add(id)));
            const dependencyIds = new Set([...members.values()]
                .map(value => value.dependencyUpgradeId));
            const [methods, dependencies] = await Promise.all([
                recordsForIds("methods", methodIds),
                recordsForIds("dependencies", dependencyIds)
            ]);
            let expandedDiff = null;
            if (state.expandedRowId !== null
                    && relations.has(state.expandedRowId)) {
                const expandedMember = members.get(relations.get(
                    state.expandedRowId).changedMemberId);
                if (expandedMember && expandedMember.codeDiffId !== null) {
                    expandedDiff = (await recordsForIds("diffs",
                        new Set([expandedMember.codeDiffId])))
                        .get(expandedMember.codeDiffId);
                }
            }
            if (generation !== state.renderGeneration) {
                return;
            }
            const fragment = document.createDocumentFragment();
            [...relations.values()].sort((left, right) =>
                left.rowId - right.rowId).forEach(relation => {
                    const row = createRow(relation,
                        paths.get(relation.pathId),
                        members.get(relation.changedMemberId),
                        methods, dependencies);
                    fragment.append(row);
                    if (state.expandedRowId === relation.rowId && expandedDiff) {
                        fragment.append(createDiffRow(expandedDiff));
                    }
                });
            rowsNode.replaceChildren(fragment);
            emptyNode.textContent =
                "No affected path matched the current filters.";
            emptyNode.classList.toggle("hidden", matches !== 0);
            summaryNode.textContent = matches === 0
                ? `Showing 0 matching records.${appliedSummary()}`
                : `Showing ${start + 1}–${end} of ${matches} matching records.${appliedSummary()}`;
            pageInput.value = state.page;
            pageInput.max = pageCount;
            pageCountNode.textContent = `of ${pageCount}`;
            const atStart = state.page <= 1 || matches === 0;
            const atEnd = state.page >= pageCount || matches === 0;
            firstButton.disabled = atStart;
            previousButton.disabled = atStart;
            nextButton.disabled = atEnd;
            lastButton.disabled = atEnd;
        } catch (error) {
            if (generation === state.renderGeneration) {
                showError(error, () => render(false));
            }
        }
    }

    searchForm.addEventListener("submit", event => {
        event.preventDefault();
        submitSearch();
    });
    typeSelect.addEventListener("change", () => {
        state.type = typeSelect.value;
        applyViewType();
        render(true);
    });
    pageSizeSelect.addEventListener("change", () => {
        state.pageSize = Number(pageSizeSelect.value);
        render(true);
    });
    pageInput.addEventListener("change", () => {
        state.page = Number(pageInput.value) || 1;
        render(false);
    });
    firstButton.addEventListener("click", () => {
        state.page = 1;
        render(false);
    });
    previousButton.addEventListener("click", () => {
        state.page -= 1;
        render(false);
    });
    nextButton.addEventListener("click", () => {
        state.page += 1;
        render(false);
    });
    lastButton.addEventListener("click", () => {
        state.page = Math.max(1,
            Math.ceil(matchingCount() / state.pageSize));
        render(false);
    });

    state.baseRanges = oneRange("all");
    applyViewType();
    render(true);
})();
