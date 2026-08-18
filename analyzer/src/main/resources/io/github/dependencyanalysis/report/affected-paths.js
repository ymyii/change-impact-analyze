(() => {
    "use strict";

    const common = window.CIA_REPORT;
    const manifestNode = document.getElementById("affected-path-manifest");
    const rowsNode = document.getElementById("path-rows");
    const emptyNode = document.getElementById("path-empty");
    if (!common || !manifestNode || !rowsNode || !emptyNode) {
        return;
    }

    let manifest;
    try {
        manifest = JSON.parse(manifestNode.textContent);
        if (manifest.schemaVersion !== 5 || !manifest.rowRanges
                || !manifest.shards || !Array.isArray(manifest.sources)) {
            throw new Error("Unsupported Affected Paths schema.");
        }
    } catch (error) {
        emptyNode.textContent = `Affected path data could not be loaded: ${error.message}`;
        emptyNode.classList.remove("hidden");
        return;
    }
    manifestNode.remove();

    const {node, globs, sourceMatches, fillDatalist, exactValue,
        normalizeRanges, intersectRanges, createDiffRow,
        createShardLoader} = common;
    const typeSelect = document.getElementById("path-type");
    const scopeForm = document.getElementById("path-scope-form");
    const searchForm = document.getElementById("path-search-form");
    const searchInput = document.getElementById("path-search");
    const includeInput = document.getElementById("path-dependency-include");
    const excludeInput = document.getElementById("path-dependency-exclude");
    const dependencyFilter = document.getElementById("path-dependency-filter");
    const memberFilter = document.getElementById("path-member-filter");
    const methodFilter = document.getElementById("path-method-filter");
    const pageSizeSelect = document.getElementById("path-page-size");
    const summaryNode = document.getElementById("path-result-summary");
    const pageInput = document.getElementById("path-page");
    const pageCountNode = document.getElementById("path-page-count");
    const firstButton = document.getElementById("path-first");
    const previousButton = document.getElementById("path-previous");
    const nextButton = document.getElementById("path-next");
    const lastButton = document.getElementById("path-last");
    const dependencyValues = new Set(manifest.sources.map(value =>
        value.source));
    const state = {type: "impact", pageSize: 20, page: 1,
        expandedRowId: null, baseRanges: [], ranges: [],
        applied: {query: "", dependency: "", member: "", method: "",
            includes: [], excludes: []},
        includes: [], excludes: [], searchGeneration: 0, renderGeneration: 0,
        members: null, dependencies: null, indexes: null};

    function recordId(kind, record) {
        if (kind === "index") {
            return record.pathId;
        }
        if (kind === "rows") {
            return record.rowId;
        }
        return record.id;
    }

    function validRanges(values) {
        return Array.isArray(values) && values.every(value =>
            Number.isInteger(value.start) && value.start >= 0
                && Number.isInteger(value.count) && value.count >= 0);
    }

    function validRecord(kind, record) {
        if (!record || typeof record !== "object") {
            return false;
        }
        const integer = value => Number.isInteger(value) && value >= 0;
        const text = value => typeof value === "string";
        if (kind === "index") {
            return integer(record.pathId) && text(record.type)
                && text(record.searchText) && Array.isArray(record.affectedMethods)
                && record.affectedMethods.every(text) && integer(record.rowStart)
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
                && text(record.changePointKind) && text(record.signature)
                && text(record.codeDiffStatus) && validRanges(record.rowRanges)
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

    const shards = createShardLoader(manifest, validRecord, recordId);
    fillDatalist("path-dependency-options", dependencyValues);

    function oneRange(name) {
        const value = manifest.rowRanges[name];
        return value && value.count > 0
            ? [{start: value.start, count: value.count}] : [];
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

    async function loadCatalog(kind, generation) {
        const records = [];
        const values = shards.descriptors(kind);
        for (let index = 0; index < values.length; index += 1) {
            summaryNode.textContent =
                `Loading ${kind} index ${index + 1} of ${values.length}…`;
            records.push(...await shards.loadShard(kind, values[index]));
            if (generation !== state.searchGeneration) {
                return null;
            }
        }
        return records;
    }

    async function members(generation) {
        if (!state.members) {
            const values = await loadCatalog("members", generation);
            if (!values) {
                return null;
            }
            state.members = values;
            fillDatalist("path-member-options", values
                .filter(value => value.rowRanges.length > 0)
                .map(value => value.signature));
        }
        return state.members;
    }

    async function dependencies(generation) {
        if (!state.dependencies) {
            const values = await loadCatalog("dependencies", generation);
            if (!values) {
                return null;
            }
            state.dependencies = new Map(values.map(value => [value.id, value]));
        }
        return state.dependencies;
    }

    async function indexes(generation) {
        if (!state.indexes) {
            const values = await loadCatalog("index", generation);
            if (!values) {
                return null;
            }
            state.indexes = values;
            fillDatalist("path-method-options", values.flatMap(value =>
                value.affectedMethods));
        }
        return state.indexes;
    }

    function selectedSources(includes, excludes) {
        return manifest.sources.filter(value => {
            const included = includes.length === 0 || includes.some(pattern =>
                sourceMatches(value.source, pattern));
            return included && !excludes.some(pattern =>
                sourceMatches(value.source, pattern));
        });
    }

    async function rangesForSources(sources, generation) {
        if (sources.length === manifest.sources.length) {
            return oneRange("all");
        }
        const ranges = [];
        for (let index = 0; index < sources.length; index += 1) {
            summaryNode.textContent =
                `Loading dependency ranges ${index + 1} of ${sources.length}…`;
            const ids = new Set();
            for (let offset = 0; offset < sources[index].count; offset += 1) {
                ids.add(sources[index].firstId + offset);
            }
            const records = await shards.recordsForIds("source-index", ids);
            if (generation !== state.searchGeneration) {
                return null;
            }
            records.forEach(record => ranges.push({
                start: record.rowStart, count: record.rowCount}));
        }
        return normalizeRanges(ranges);
    }

    function memberRanges(values, predicate) {
        return normalizeRanges(values.filter(predicate)
            .flatMap(value => value.rowRanges));
    }

    function indexRanges(values, predicate) {
        return normalizeRanges(values.filter(predicate).map(value => ({
            start: value.rowStart, count: value.rowCount})));
    }

    function dependencyLabel(dependency) {
        return dependency
            ? `${dependency.oldArtifact} → ${dependency.newArtifact} (${dependency.scope})`
            : "Unavailable";
    }

    async function calculateRanges(generation, includes, excludes) {
        const sourceScope = selectedSources(includes, excludes);
        let result = await rangesForSources(sourceScope, generation);
        if (!result || generation !== state.searchGeneration) {
            return null;
        }
        const dependency = dependencyFilter.value.trim();
        if (dependency) {
            exactValue(dependencyFilter, dependencyValues, "Dependency filter");
            const dependencyRanges = await rangesForSources(
                manifest.sources.filter(value => value.source === dependency),
                generation);
            if (!dependencyRanges) {
                return null;
            }
            result = intersectRanges(result, dependencyRanges);
        }

        const memberText = memberFilter.value.trim();
        let memberValues = null;
        if (memberText || searchInput.value.trim()) {
            memberValues = await members(generation);
            if (!memberValues) {
                return null;
            }
        }
        if (memberText) {
            const choices = new Set(memberValues.filter(value =>
                value.rowRanges.length > 0).map(value => value.signature));
            exactValue(memberFilter, choices, "Changed member filter");
            result = intersectRanges(result, memberRanges(memberValues,
                value => value.signature === memberText));
        }

        const methodText = methodFilter.value.trim();
        let indexValues = null;
        if (methodText || searchInput.value.trim()) {
            indexValues = await indexes(generation);
            if (!indexValues) {
                return null;
            }
        }
        if (methodText) {
            const choices = new Set(indexValues.flatMap(value =>
                value.affectedMethods));
            exactValue(methodFilter, choices,
                "Affected application method filter");
            result = intersectRanges(result, indexRanges(indexValues,
                value => value.affectedMethods.includes(methodText)));
        }

        const query = searchInput.value.trim().toLocaleLowerCase();
        if (query) {
            const dependencyValuesById = await dependencies(generation);
            if (!dependencyValuesById) {
                return null;
            }
            const queryRanges = [];
            queryRanges.push(...indexRanges(indexValues, value =>
                value.searchText.toLocaleLowerCase().includes(query)));
            queryRanges.push(...memberRanges(memberValues, value => {
                const dependencyValue = dependencyValuesById.get(
                    value.dependencyUpgradeId);
                return value.signature.toLocaleLowerCase().includes(query)
                    || dependencyLabel(dependencyValue)
                        .toLocaleLowerCase().includes(query)
                    || (dependencyValue && dependencyValue.source
                        .toLocaleLowerCase().includes(query));
            }));
            result = intersectRanges(result, normalizeRanges(queryRanges));
        }
        return result;
    }

    function appliedSummary() {
        const values = [];
        if (state.applied.query) {
            values.push(`search “${state.applied.query}”`);
        }
        if (state.applied.dependency) {
            values.push(`dependency ${state.applied.dependency}`);
        }
        if (state.applied.member) {
            values.push(`member ${state.applied.member}`);
        }
        if (state.applied.method) {
            values.push(`affected method ${state.applied.method}`);
        }
        if (state.applied.includes.length) {
            values.push(`include ${state.applied.includes.join(", ")}`);
        }
        if (state.applied.excludes.length) {
            values.push(`exclude ${state.applied.excludes.join(", ")}`);
        }
        return values.length ? ` Applied: ${values.join("; ")}.` : "";
    }

    async function applySearchAndFilters(
            includes = state.includes, excludes = state.excludes) {
        const generation = ++state.searchGeneration;
        state.renderGeneration += 1;
        try {
            const ranges = await calculateRanges(generation,
                includes, excludes);
            if (!ranges || generation !== state.searchGeneration) {
                return;
            }
            state.baseRanges = ranges;
            state.includes = includes;
            state.excludes = excludes;
            state.applied = {query: searchInput.value.trim(),
                dependency: dependencyFilter.value.trim(),
                member: memberFilter.value.trim(),
                method: methodFilter.value.trim(),
                includes: includes.map(value => value.expression),
                excludes: excludes.map(value => value.expression)};
            state.page = 1;
            state.expandedRowId = null;
            applyViewType();
            await render(false);
        } catch (error) {
            if (generation === state.searchGeneration) {
                if (error.retryable) {
                    showError(error, () => applySearchAndFilters(
                        includes, excludes));
                } else {
                    summaryNode.textContent =
                        `${error.message}. The last successful result was retained.`;
                }
            }
        }
    }

    function appendCell(row, text, className, code) {
        const cell = node("td", className);
        cell.append(node(code ? "code" : "span", "", text));
        row.append(cell);
        return cell;
    }

    function dependencyDisplay(member, dependencyValuesById) {
        return dependencyLabel(dependencyValuesById.get(
            member.dependencyUpgradeId));
    }

    function pathMethods(path, methodValues) {
        return path.methodIds.map(id => methodValues.get(id)).filter(Boolean);
    }

    function affectedMethods(path, methodValues) {
        const project = pathMethods(path, methodValues)
            .filter(method => method.project).map(method => method.label);
        if (project.length) {
            return [...new Set(project)].join(", ");
        }
        return path.type === "structural"
            ? path.applicationMember : "Unavailable";
    }

    function pathSegments(path, member, methodValues) {
        const values = pathMethods(path, methodValues).map(method => method.label);
        if (path.type === "structural") {
            values.push(path.applicationMember, path.relation,
                path.changedClass);
        }
        values.push(`Changed member: ${member.signature}`);
        return values;
    }

    function appendPathSequenceCell(row, path, member, methodValues) {
        const cell = node("td", "path-sequence");
        const code = node("code");
        const values = pathSegments(path, member, methodValues);
        values.forEach((value, index) => {
            code.append(document.createTextNode(value));
            if (index + 1 < values.length) {
                code.append(document.createTextNode(" → "));
                if (path.type === "impact" && (index + 1) % 2 === 0) {
                    code.append(document.createElement("br"));
                }
            }
        });
        cell.append(code);
        row.append(cell);
    }

    function appendCodeDiffCell(row, relation, member) {
        const cell = node("td", "code-diff-cell");
        if (member.codeDiffStatus === "UNAVAILABLE") {
            cell.append(node("span", "muted", "Unavailable"));
        } else if (member.codeDiffStatus === "JAVA_TEXT_IDENTICAL") {
            cell.append(node("span", "muted", "Java text identical"));
        } else if (member.codeDiffId === null) {
            cell.append(node("span", "muted", "Unavailable"));
        } else {
            const open = state.expandedRowId === relation.rowId;
            const button = node("button", "table-action",
                open ? "Hide Java diff" : "View Java diff");
            button.type = "button";
            button.setAttribute("aria-expanded", open ? "true" : "false");
            button.addEventListener("click", () => {
                state.expandedRowId = open ? null : relation.rowId;
                render(false);
            });
            cell.append(button);
        }
        row.append(cell);
    }

    function createRow(relation, path, member, methodValues,
            dependencyValuesById) {
        const row = node("tr", "path-row");
        const typeCell = node("td");
        typeCell.append(node("span", `badge ${path.type}`,
            path.type === "impact" ? "Impact" : "Structural"));
        row.append(typeCell);
        appendCell(row,
            `${path.classification === "DIRECT" ? "Direct" : "Transitive"} ${path.type === "impact" ? "dependency" : "structural"} impact`);
        appendCell(row, affectedMethods(path, methodValues), "method-cell", true);
        appendCell(row, dependencyDisplay(member, dependencyValuesById),
            "dependency-cell", true);
        const kindCell = node("td");
        kindCell.append(node("span", "badge kind", member.changePointKind));
        row.append(kindCell);
        appendCell(row, member.signature, "member-cell", true);
        appendPathSequenceCell(row, path, member, methodValues);
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
            const relations = await shards.recordsForIds("rows", rowIds);
            const pathIds = new Set([...relations.values()]
                .map(value => value.pathId));
            const memberIds = new Set([...relations.values()]
                .map(value => value.changedMemberId));
            const [paths, memberValues] = await Promise.all([
                shards.recordsForIds("paths", pathIds),
                shards.recordsForIds("members", memberIds)
            ]);
            const methodIds = new Set();
            paths.forEach(path => path.methodIds.forEach(id =>
                methodIds.add(id)));
            const dependencyIds = new Set([...memberValues.values()]
                .map(value => value.dependencyUpgradeId));
            const [methodValues, dependencyValuesById] = await Promise.all([
                shards.recordsForIds("methods", methodIds),
                shards.recordsForIds("dependencies", dependencyIds)
            ]);
            let expandedDiff = null;
            if (state.expandedRowId !== null
                    && relations.has(state.expandedRowId)) {
                const expandedMember = memberValues.get(relations.get(
                    state.expandedRowId).changedMemberId);
                if (expandedMember && expandedMember.codeDiffId !== null) {
                    expandedDiff = (await shards.recordsForIds("diffs",
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
                    memberValues.get(relation.changedMemberId),
                    methodValues, dependencyValuesById);
                fragment.append(row);
                if (state.expandedRowId === relation.rowId && expandedDiff) {
                    fragment.append(createDiffRow(expandedDiff, 8));
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

    scopeForm.addEventListener("submit", event => {
        event.preventDefault();
        try {
            const includes = globs(includeInput.value);
            const excludes = globs(excludeInput.value);
            applySearchAndFilters(includes, excludes);
        } catch (error) {
            summaryNode.textContent =
                `${error.message}. The last successful result was retained.`;
        }
    });
    searchForm.addEventListener("submit", event => {
        event.preventDefault();
        applySearchAndFilters();
    });
    [dependencyFilter, memberFilter, methodFilter].forEach(input =>
        input.addEventListener("change", () => applySearchAndFilters()));
    memberFilter.addEventListener("focus", () => {
        const generation = state.searchGeneration;
        members(generation).then(values => {
            if (values && generation === state.searchGeneration) {
                render(false);
            }
        }).catch(error => showError(error,
            () => members(state.searchGeneration)));
    }, {once: true});
    methodFilter.addEventListener("focus", () => {
        const generation = state.searchGeneration;
        indexes(generation).then(values => {
            if (values && generation === state.searchGeneration) {
                render(false);
            }
        }).catch(error => showError(error,
            () => indexes(state.searchGeneration)));
    }, {once: true});
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
        state.expandedRowId = null;
        render(false);
    });
    firstButton.addEventListener("click", () => {
        state.page = 1;
        state.expandedRowId = null;
        render(false);
    });
    previousButton.addEventListener("click", () => {
        state.page -= 1;
        state.expandedRowId = null;
        render(false);
    });
    nextButton.addEventListener("click", () => {
        state.page += 1;
        state.expandedRowId = null;
        render(false);
    });
    lastButton.addEventListener("click", () => {
        state.page = Math.max(1,
            Math.ceil(matchingCount() / state.pageSize));
        state.expandedRowId = null;
        render(false);
    });

    state.baseRanges = oneRange("all");
    applyViewType();
    render(true);
})();
