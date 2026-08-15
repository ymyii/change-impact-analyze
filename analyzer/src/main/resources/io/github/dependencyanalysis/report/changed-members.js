(() => {
    "use strict";

    const dataNode = document.getElementById("changed-member-data");
    const rowsNode = document.getElementById("member-rows");
    const emptyNode = document.getElementById("member-empty");
    if (!dataNode || !rowsNode || !emptyNode) {
        return;
    }
    let data;
    try {
        data = JSON.parse(dataNode.textContent);
    } catch (error) {
        emptyNode.textContent = "Changed member data could not be loaded.";
        emptyNode.classList.remove("hidden");
        return;
    }
    dataNode.remove();

    const dependencies = new Map(data.dependencyUpgrades.map(value =>
        [value.id, value]));
    const members = new Map(data.changedMembers.map(value => [value.id, value]));
    const searchInput = document.getElementById("member-search");
    const kindSelect = document.getElementById("member-kind");
    const pageSizeSelect = document.getElementById("member-page-size");
    const summaryNode = document.getElementById("member-result-summary");
    const pageInput = document.getElementById("member-page");
    const pageCountNode = document.getElementById("member-page-count");
    const firstButton = document.getElementById("member-first");
    const previousButton = document.getElementById("member-previous");
    const nextButton = document.getElementById("member-next");
    const lastButton = document.getElementById("member-last");
    const state = {query: "", kind: "all", pageSize: 20, page: 1};

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

    function memberLabel(member) {
        return member.name ? `${member.owner}#${member.name}` : member.owner;
    }

    function dependencyLabel(member) {
        const dependency = dependencies.get(member.dependencyUpgradeId);
        return dependency
            ? `${dependency.oldArtifact} → ${dependency.newArtifact} (${dependency.scope})`
            : "Unavailable";
    }

    const metrics = data.memberMetrics.map(metric => {
        const member = members.get(metric.memberId);
        return {metric, member, dependency: dependencyLabel(member),
            label: memberLabel(member), total: metric.final + metric.structural};
    }).sort((left, right) => right.total - left.total
        || right.metric.final - left.metric.final
        || right.metric.structural - left.metric.structural
        || right.metric.candidate - left.metric.candidate
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
            const haystack = `${value.dependency} ${value.label}`
                .toLocaleLowerCase();
            return kindMatches && haystack.includes(state.query);
        });
    }

    function createRow(value) {
        const row = node("tr");
        appendTextCell(row, value.dependency, true);
        const kindCell = node("td");
        kindCell.append(node("span", "badge kind",
            value.member.changePointKind));
        row.append(kindCell);
        appendTextCell(row, value.label, true);
        appendNumberCell(row, value.metric.candidate);
        appendNumberCell(row, value.metric.filtered);
        appendNumberCell(row, value.metric.final);
        appendNumberCell(row, value.metric.structural);
        appendNumberCell(row, value.total);
        return row;
    }

    function render(resetPage) {
        if (resetPage) {
            state.page = 1;
        }
        const filtered = matches();
        const pageCount = Math.max(1,
            Math.ceil(filtered.length / state.pageSize));
        state.page = Math.min(Math.max(1, state.page), pageCount);
        const start = (state.page - 1) * state.pageSize;
        const end = Math.min(start + state.pageSize, filtered.length);
        const fragment = document.createDocumentFragment();
        filtered.slice(start, end).forEach(value =>
            fragment.append(createRow(value)));
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

    let searchTimer;
    searchInput.addEventListener("input", () => {
        window.clearTimeout(searchTimer);
        searchTimer = window.setTimeout(() => {
            state.query = searchInput.value.toLocaleLowerCase();
            render(true);
        }, 120);
    });
    kindSelect.addEventListener("change", () => {
        state.kind = kindSelect.value;
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
        state.page = Math.max(1, Math.ceil(matches().length / state.pageSize));
        render(false);
    });
    render(true);
})();
