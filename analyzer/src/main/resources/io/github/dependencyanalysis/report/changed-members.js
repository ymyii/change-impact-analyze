(() => {
    "use strict";

    const common = window.CIA_REPORT;
    const dataNode = document.getElementById("changed-member-data");
    const manifestNode = document.getElementById("changed-member-diff-manifest");
    const rowsNode = document.getElementById("member-rows");
    const emptyNode = document.getElementById("member-empty");
    if (!common || !dataNode || !manifestNode || !rowsNode || !emptyNode) {
        return;
    }
    let data;
    let manifest;
    try {
        data = JSON.parse(dataNode.textContent);
        manifest = JSON.parse(manifestNode.textContent);
        if (manifest.schemaVersion !== 5 || !manifest.shards) {
            throw new Error("Unsupported changed-member diff schema.");
        }
    } catch (error) {
        emptyNode.textContent = `Changed member data could not be loaded: ${error.message}`;
        emptyNode.classList.remove("hidden");
        return;
    }
    dataNode.remove();
    manifestNode.remove();

    const {node, globs, sourceMatches, fillDatalist, exactValue,
        createDiffRow, createShardLoader} = common;
    const dependencies = new Map(data.dependencyUpgrades.map(value =>
        [value.id, value]));
    const members = new Map(data.changedMembers.map(value => [value.id, value]));
    const scopeForm = document.getElementById("member-scope-form");
    const searchInput = document.getElementById("member-search");
    const includeInput = document.getElementById("member-dependency-include");
    const excludeInput = document.getElementById("member-dependency-exclude");
    const dependencyFilter = document.getElementById("member-dependency-filter");
    const memberFilter = document.getElementById("member-member-filter");
    const chainSelect = document.getElementById("member-chain");
    const kindSelect = document.getElementById("member-kind");
    const pageSizeSelect = document.getElementById("member-page-size");
    const summaryNode = document.getElementById("member-result-summary");
    const pageInput = document.getElementById("member-page");
    const pageCountNode = document.getElementById("member-page-count");
    const firstButton = document.getElementById("member-first");
    const previousButton = document.getElementById("member-previous");
    const nextButton = document.getElementById("member-next");
    const lastButton = document.getElementById("member-last");
    const dependencyValues = new Set(data.dependencyUpgrades.map(value =>
        value.source));
    const memberValues = new Set(data.changedMembers.map(value =>
        value.signature));
    const state = {query: "", includes: [], excludes: [], dependency: "",
        member: "", chain: "all", kind: "all", pageSize: 20, page: 1,
        expandedMemberId: null, renderGeneration: 0};

    function recordId(kind, record) {
        return record.id;
    }

    function validRecord(kind, record) {
        return kind === "diffs" && record && Number.isInteger(record.id)
            && record.id >= 0 && typeof record.unifiedDiff === "string";
    }

    const shards = createShardLoader(manifest, validRecord, recordId);
    fillDatalist("member-dependency-options", dependencyValues);
    fillDatalist("member-member-options", memberValues);

    function selectedSource(source) {
        const included = state.includes.length === 0
            || state.includes.some(pattern => sourceMatches(source, pattern));
        return included && !state.excludes.some(pattern =>
            sourceMatches(source, pattern));
    }

    function dependencyLabel(member) {
        const dependency = dependencies.get(member.dependencyUpgradeId);
        return dependency
            ? `${dependency.oldArtifact} → ${dependency.newArtifact} (${dependency.scope})`
            : "Unavailable";
    }

    const metrics = data.memberMetrics.map(metric => {
        const member = members.get(metric.memberId);
        return {metric, member, source: member.source,
            dependency: dependencyLabel(member),
            total: metric.impact + metric.structural};
    }).sort((left, right) => right.total - left.total
        || right.metric.impact - left.metric.impact
        || right.metric.structural - left.metric.structural
        || left.metric.memberId - right.metric.memberId);

    function appendTextCell(row, text, code) {
        const cell = node("td");
        cell.append(node(code ? "code" : "span", "", text));
        row.append(cell);
    }

    function appendNumberCell(row, value) {
        row.append(node("td", "numeric", String(value)));
    }

    function matches() {
        return metrics.filter(value => {
            const kindMatches = state.kind === "all"
                || value.member.changePointKind === state.kind;
            const chainMatches = state.chain === "all"
                || (state.chain === "has" ? value.total > 0 : value.total === 0);
            const dependencyMatches = !state.dependency
                || value.source === state.dependency;
            const memberMatches = !state.member
                || value.member.signature === state.member;
            const haystack = `${value.dependency} ${value.source} ${value.member.signature}`
                .toLocaleLowerCase();
            return kindMatches && chainMatches && dependencyMatches
                && memberMatches && selectedSource(value.source)
                && haystack.includes(state.query);
        });
    }

    function appendCodeDiffCell(row, value) {
        const cell = node("td", "code-diff-cell");
        if (value.total === 0) {
            cell.append(node("span", "muted",
                "Not generated — no impact path"));
        } else if (value.member.codeDiffStatus === "UNAVAILABLE") {
            cell.append(node("span", "muted", "Unavailable"));
        } else if (value.member.codeDiffStatus === "JAVA_TEXT_IDENTICAL") {
            cell.append(node("span", "muted", "Java text identical"));
        } else if (value.member.codeDiffId === null) {
            cell.append(node("span", "muted", "Unavailable"));
        } else {
            const open = state.expandedMemberId === value.member.id;
            const button = node("button", "table-action",
                open ? "Hide Java diff" : "View Java diff");
            button.type = "button";
            button.setAttribute("aria-expanded", open ? "true" : "false");
            button.addEventListener("click", () => {
                state.expandedMemberId = open ? null : value.member.id;
                render(false);
            });
            cell.append(button);
        }
        row.append(cell);
    }

    function createRow(value) {
        const row = node("tr", "member-row");
        appendTextCell(row, value.dependency, true);
        const kindCell = node("td");
        kindCell.append(node("span", "badge kind",
            value.member.changePointKind));
        row.append(kindCell);
        appendTextCell(row, value.member.signature, true);
        appendNumberCell(row, value.metric.impact);
        appendNumberCell(row, value.metric.structural);
        appendNumberCell(row, value.total);
        appendCodeDiffCell(row, value);
        return row;
    }

    async function render(resetPage) {
        const generation = ++state.renderGeneration;
        if (resetPage) {
            state.page = 1;
            state.expandedMemberId = null;
        }
        const filtered = matches();
        const pageCount = Math.max(1,
            Math.ceil(filtered.length / state.pageSize));
        state.page = Math.min(Math.max(1, state.page), pageCount);
        const start = (state.page - 1) * state.pageSize;
        const end = Math.min(start + state.pageSize, filtered.length);
        let expandedDiff = null;
        const expanded = filtered.slice(start, end).find(value =>
            value.member.id === state.expandedMemberId);
        try {
            if (expanded && expanded.member.codeDiffId !== null) {
                summaryNode.textContent = "Loading Java diff…";
                expandedDiff = (await shards.recordsForIds("diffs",
                    new Set([expanded.member.codeDiffId])))
                    .get(expanded.member.codeDiffId);
            }
        } catch (error) {
            if (generation === state.renderGeneration) {
                summaryNode.textContent =
                    `${error.message}. The last successful result was retained.`;
            }
            return;
        }
        if (generation !== state.renderGeneration) {
            return;
        }
        const fragment = document.createDocumentFragment();
        filtered.slice(start, end).forEach(value => {
            fragment.append(createRow(value));
            if (value.member.id === state.expandedMemberId && expandedDiff) {
                fragment.append(createDiffRow(expandedDiff, 7,
                    "member-diff-row"));
            }
        });
        rowsNode.replaceChildren(fragment);
        emptyNode.textContent = "No changed member matched the current filters.";
        emptyNode.classList.toggle("hidden", filtered.length !== 0);
        summaryNode.textContent = filtered.length === 0
            ? "Showing 0 matching members."
            : `Showing ${start + 1}–${end} of ${filtered.length} matching members.`;
        pageInput.value = state.page;
        pageInput.max = pageCount;
        pageCountNode.textContent = `of ${pageCount}`;
        const atStart = state.page <= 1 || filtered.length === 0;
        const atEnd = state.page >= pageCount || filtered.length === 0;
        firstButton.disabled = atStart;
        previousButton.disabled = atStart;
        nextButton.disabled = atEnd;
        lastButton.disabled = atEnd;
    }

    function applyExactFilters() {
        try {
            state.dependency = exactValue(dependencyFilter,
                dependencyValues, "Dependency filter");
            state.member = exactValue(memberFilter,
                memberValues, "Changed member filter");
            state.chain = chainSelect.value;
            state.kind = kindSelect.value;
            render(true);
        } catch (error) {
            summaryNode.textContent =
                `${error.message}. The last successful result was retained.`;
        }
    }

    scopeForm.addEventListener("submit", event => {
        event.preventDefault();
        try {
            state.includes = globs(includeInput.value);
            state.excludes = globs(excludeInput.value);
            render(true);
        } catch (error) {
            summaryNode.textContent =
                `${error.message}. The last successful result was retained.`;
        }
    });
    let searchTimer;
    searchInput.addEventListener("input", () => {
        window.clearTimeout(searchTimer);
        searchTimer = window.setTimeout(() => {
            state.query = searchInput.value.trim().toLocaleLowerCase();
            render(true);
        }, 120);
    });
    [dependencyFilter, memberFilter, chainSelect, kindSelect]
        .forEach(input => input.addEventListener("change", applyExactFilters));
    pageSizeSelect.addEventListener("change", () => {
        state.pageSize = Number(pageSizeSelect.value);
        render(true);
    });
    pageInput.addEventListener("change", () => {
        state.page = Number(pageInput.value) || 1;
        state.expandedMemberId = null;
        render(false);
    });
    firstButton.addEventListener("click", () => {
        state.page = 1;
        state.expandedMemberId = null;
        render(false);
    });
    previousButton.addEventListener("click", () => {
        state.page -= 1;
        state.expandedMemberId = null;
        render(false);
    });
    nextButton.addEventListener("click", () => {
        state.page += 1;
        state.expandedMemberId = null;
        render(false);
    });
    lastButton.addEventListener("click", () => {
        state.page = Math.max(1, Math.ceil(matches().length / state.pageSize));
        state.expandedMemberId = null;
        render(false);
    });
    render(true);
})();
