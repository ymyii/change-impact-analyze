(() => {
    "use strict";

    const dataNode = document.getElementById("affected-path-data");
    const rowsNode = document.getElementById("path-rows");
    const emptyNode = document.getElementById("path-empty");
    if (!dataNode || !rowsNode || !emptyNode) {
        return;
    }

    let data;
    try {
        data = JSON.parse(dataNode.textContent);
    } catch (error) {
        emptyNode.textContent = "Affected path data could not be loaded.";
        emptyNode.classList.remove("hidden");
        return;
    }
    dataNode.remove();

    const dependencies = new Map(data.dependencyUpgrades.map(value =>
        [value.id, value]));
    const members = new Map(data.changedMembers.map(value => [value.id, value]));
    const methods = new Map(data.methods.map(value => [value.id, value]));
    const callPaths = new Map(data.paths.map(value => [value.id, value]));
    const structuralPaths = new Map(data.structuralPaths.map(value =>
        [value.id, value]));
    const codeDiffs = new Map(data.codeDiffs.map(value => [value.id, value]));
    const stepsByPath = new Map();
    data.pathSteps.forEach(step => {
        const steps = stepsByPath.get(step.pathId) || [];
        steps.push(step);
        stepsByPath.set(step.pathId, steps);
    });
    stepsByPath.forEach(steps => steps.sort((left, right) =>
        left.ordinal - right.ordinal));

    const typeSelect = document.getElementById("path-type");
    const searchInput = document.getElementById("path-search");
    const pageSizeSelect = document.getElementById("path-page-size");
    const summaryNode = document.getElementById("path-result-summary");
    const pageInput = document.getElementById("path-page");
    const pageCountNode = document.getElementById("path-page-count");
    const firstButton = document.getElementById("path-first");
    const previousButton = document.getElementById("path-previous");
    const nextButton = document.getElementById("path-next");
    const lastButton = document.getElementById("path-last");
    const state = {type: "impact", query: "", pageSize: 20, page: 1,
        expandedRowId: null};

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

    function appendCell(row, text, className, code) {
        const cell = node("td", className);
        cell.append(node(code ? "code" : "span", "", text));
        row.append(cell);
        return cell;
    }

    function pathType(pathId) {
        return structuralPaths.has(pathId) ? "structural" : "impact";
    }

    function pathEntity(pathId) {
        return callPaths.get(pathId) || structuralPaths.get(pathId);
    }

    function pathMethods(pathId) {
        return (stepsByPath.get(pathId) || []).map(step =>
            methods.get(step.methodId)).filter(Boolean);
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

    function affectedMethods(pathId) {
        const project = pathMethods(pathId).filter(method => method.project)
            .map(method => method.label);
        if (project.length) {
            return [...new Set(project)].join(", ");
        }
        const structural = structuralPaths.get(pathId);
        return structural ? structural.applicationMember : "Unavailable";
    }

    function pathSequence(pathId, member) {
        const values = pathMethods(pathId).map(method => method.label);
        const structural = structuralPaths.get(pathId);
        if (structural) {
            values.push(structural.applicationMember, structural.relation,
                structural.changedClass);
        }
        values.push(`Changed member: ${memberLabel(member)}`);
        return values.join(" → ");
    }

    function matchingPathIds() {
        const result = new Set();
        const allIds = [...callPaths.keys(), ...structuralPaths.keys()];
        allIds.forEach(pathId => {
            const typeMatches = state.type === "all"
                || state.type === pathType(pathId);
            const methodMatches = affectedMethods(pathId).toLocaleLowerCase()
                .includes(state.query);
            if (typeMatches && methodMatches) {
                result.add(pathId);
            }
        });
        return result;
    }

    function matchingRows() {
        const pathIds = matchingPathIds();
        return data.pathMemberRows.filter(row => pathIds.has(row.pathId));
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

    function createDiffRow(row, comparison) {
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
        row.after(diffRow);
    }

    function appendCodeDiffCell(row, relation, member) {
        const cell = node("td");
        const comparison = member.codeDiffId === null
            ? null : codeDiffs.get(member.codeDiffId);
        if (!comparison || comparison.status === "UNAVAILABLE") {
            cell.append(node("span", "muted", "Unavailable"));
        } else if (comparison.status === "JAVA_TEXT_IDENTICAL") {
            cell.append(node("span", "muted", "Java text identical"));
        } else {
            const button = node("button", "table-action", "View Java diff");
            button.type = "button";
            button.setAttribute("aria-expanded", "false");
            button.addEventListener("click", () => {
                const open = state.expandedRowId === relation.rowId;
                state.expandedRowId = open ? null : relation.rowId;
                render(false);
            });
            cell.append(button);
        }
        row.append(cell);
        return comparison;
    }

    function createRow(relation, fragment) {
        const member = members.get(relation.changedMemberId);
        const path = pathEntity(relation.pathId);
        const type = pathType(relation.pathId);
        const row = node("tr", "path-row");
        const typeCell = node("td");
        typeCell.append(node("span", `badge ${type}`, type === "impact"
            ? "Impact" : "Structural"));
        row.append(typeCell);
        appendCell(row, `${path.classification === "DIRECT" ? "Direct" : "Transitive"} ${type === "impact" ? "dependency" : "structural"} impact`);
        appendCell(row, affectedMethods(relation.pathId), "method-cell", true);
        appendCell(row, dependencyLabel(member), "dependency-cell", true);
        const kindCell = node("td");
        kindCell.append(node("span", "badge kind", member.changePointKind));
        row.append(kindCell);
        appendCell(row, memberLabel(member), "member-cell", true);
        appendCell(row, pathSequence(relation.pathId, member),
            "path-sequence", true);
        const comparison = appendCodeDiffCell(row, relation, member);
        fragment.append(row);
        if (state.expandedRowId === relation.rowId && comparison
                && comparison.status === "AVAILABLE") {
            createDiffRow(row, comparison);
            const button = row.querySelector("button");
            if (button) {
                button.setAttribute("aria-expanded", "true");
            }
        }
    }

    function render(resetPage) {
        if (resetPage) {
            state.page = 1;
            state.expandedRowId = null;
        }
        const matches = matchingRows();
        const pageCount = Math.max(1, Math.ceil(matches.length / state.pageSize));
        state.page = Math.min(Math.max(1, state.page), pageCount);
        const start = (state.page - 1) * state.pageSize;
        const end = Math.min(start + state.pageSize, matches.length);
        const fragment = document.createDocumentFragment();
        matches.slice(start, end).forEach(row => createRow(row, fragment));
        rowsNode.replaceChildren(fragment);
        emptyNode.textContent = "No affected path matched the current filters.";
        emptyNode.classList.toggle("hidden", matches.length !== 0);
        summaryNode.textContent = matches.length === 0
            ? "Showing 0 matching records."
            : `Showing ${start + 1}–${end} of ${matches.length} matching records.`;
        pageInput.value = state.page;
        pageInput.max = pageCount;
        pageCountNode.textContent = `of ${pageCount}`;
        const atStart = state.page <= 1 || matches.length === 0;
        const atEnd = state.page >= pageCount || matches.length === 0;
        firstButton.disabled = atStart;
        previousButton.disabled = atStart;
        nextButton.disabled = atEnd;
        lastButton.disabled = atEnd;
    }

    typeSelect.addEventListener("change", () => {
        state.type = typeSelect.value;
        render(true);
    });
    let searchTimer;
    searchInput.addEventListener("input", () => {
        window.clearTimeout(searchTimer);
        searchTimer = window.setTimeout(() => {
            state.query = searchInput.value.toLocaleLowerCase();
            render(true);
        }, 120);
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
            Math.ceil(matchingRows().length / state.pageSize));
        render(false);
    });
    render(true);
})();
