(() => {
    "use strict";

    const dataNode = document.getElementById("affected-path-data");
    const rowsNode = document.getElementById("path-rows");
    const emptyNode = document.getElementById("path-empty");
    if (!dataNode || !rowsNode || !emptyNode) {
        return;
    }

    let parsed;
    try {
        parsed = JSON.parse(dataNode.textContent);
    } catch (error) {
        emptyNode.textContent = "Affected path data could not be loaded.";
        emptyNode.classList.remove("hidden");
        return;
    }
    dataNode.remove();

    const paths = parsed.paths;
    const members = new Map(parsed.members.map(member => [member.id, member]));
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
    const labels = {
        final: "Final",
        filtered: "Equivalent filtered",
        structural: "Structural"
    };
    const emptyMessages = {
        final: "No affected call chain was found within the documented analysis scope.",
        filtered: "No candidate chain was filtered by method equivalence.",
        structural: "No Structural Reference Path was found within the documented analysis scope.",
        all: "No affected path was found within the documented analysis scope."
    };
    const state = {
        type: "final",
        query: "",
        pageSize: 10,
        page: 1,
        detailsRow: null,
        detailsButton: null,
        selectedPathId: null
    };

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

    function display(value) {
        return value === null || value === undefined || value === ""
            ? "Not applicable" : String(value);
    }

    function appendCell(row, text, className, code) {
        const cell = node("td", className);
        cell.append(node(code ? "code" : "span", "", text));
        row.append(cell);
        return cell;
    }

    function filteredPaths() {
        return paths.filter(path => {
            const typeMatches = state.type === "all" || path.type === state.type;
            const methodMatches = path.affected.toLocaleLowerCase()
                .includes(state.query);
            return typeMatches && methodMatches;
        });
    }

    function closeDetails() {
        if (state.detailsRow) {
            state.detailsRow.remove();
        }
        if (state.detailsButton) {
            state.detailsButton.setAttribute("aria-expanded", "false");
        }
        state.detailsRow = null;
        state.detailsButton = null;
        state.selectedPathId = null;
    }

    function createRow(path) {
        const member = members.get(path.memberId);
        const row = node("tr", "path-row");
        appendCell(row, labels[path.type], "", false);
        appendCell(row, path.impact, "", false);
        appendCell(row, path.affected, "", true);
        appendCell(row, member ? member.dependency : "Unavailable", "", true);
        appendCell(row, member
            ? `${member.changeKind}: ${member.member}` : "Unavailable", "", true);
        appendCell(row, path.segments.join(" → "), "path-sequence", true);
        const action = node("td");
        const button = node("button", "", "View details");
        button.type = "button";
        button.setAttribute("aria-expanded", "false");
        button.addEventListener("click", () => toggleDetails(path, row, button));
        action.append(button);
        row.append(action);
        return row;
    }

    function appendInfoTable(parent, entries) {
        const table = node("table");
        const body = node("tbody");
        entries.forEach(entry => {
            const row = node("tr");
            row.append(node("th", "", entry[0]));
            const value = node("td");
            value.append(node(entry[2] ? "code" : "span", "", display(entry[1])));
            row.append(value);
            body.append(row);
        });
        table.append(body);
        parent.append(table);
    }

    function evidenceEntries(path) {
        if (path.type === "structural") {
            const entries = [
                ["Origin", path.evidence.origin, false],
                ["Reference kind", path.evidence.referenceKind, false],
                ["Raw evidence", path.evidence.rendered, true]
            ];
            path.evidence.contexts.forEach((context, index) => {
                entries.push([`Context ${index + 1}`, context, true]);
            });
            return entries;
        }
        const location = path.evidence.bytecodePc < 0
            ? `${path.evidence.source}; bytecode PC=N/A`
            : `${path.evidence.source}; bytecode PC=${path.evidence.bytecodePc}`;
        return [
            ["Terminal evidence kind", path.evidence.kind, false],
            ["Terminal mechanism", path.evidence.mechanism, false],
            ["Terminal evidence", path.evidence.rendered, true],
            ["Terminal target", path.evidence.target, true],
            ["Terminal location", location, true],
            ["Terminal detail", path.evidence.detail, true]
        ];
    }

    function memberEntries(member) {
        const entries = [
            ["Scope", member.scope, true],
            ["Raw ChangePointKind", member.rawKind, true],
            ["Raw disposition", member.disposition || "Not recorded", true],
            ["Disposition explanation", member.dispositionExplanation, false],
            ["Old descriptor", member.oldDescriptor, true],
            ["New descriptor", member.newDescriptor, true],
            ["Old hash", member.oldHash, true],
            ["New hash", member.newHash, true],
            ["Old access", member.oldAccess, true],
            ["New access", member.newAccess, true],
            ["SSA status", member.ssaStatus, true],
            ["SSA reason", member.ssaReason, false],
            ["Old artifact", member.oldArtifact, true],
            ["New artifact", member.newArtifact, true],
            ["Access/reference observations", member.observations.length, false]
        ];
        member.observations.forEach((observation, index) => {
            entries.push([`Observation ${index + 1}`, observation, true]);
        });
        if (member.duplicate) {
            entries.push(["Duplicate winner", member.duplicate.winner, true]);
            entries.push(["Duplicate winner logical source",
                member.duplicate.logicalSource, true]);
            entries.push(["Duplicate precedence",
                member.duplicate.precedence, false]);
        }
        return entries;
    }

    function isUnifiedDiff(text) {
        return text.startsWith("--- ") && text.includes("\n+++ ");
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

    function appendDiff(parent, text) {
        const pre = node("pre", "diff");
        const code = node("code", "diff-code");
        if (!isUnifiedDiff(text)) {
            code.textContent = text;
        } else {
            text.replace(/\r\n?/g, "\n").split("\n").forEach(line => {
                code.append(node("span", diffClass(line), line || "\u00a0"));
            });
        }
        pre.append(code);
        parent.append(pre);
    }

    function appendComparison(parent, member) {
        parent.append(node("h4", "", "Code changes"));
        const comparison = member.comparison;
        if (!comparison) {
            parent.append(node("p", "muted", "Code comparison was not generated."));
            return;
        }
        if (comparison.status === "AVAILABLE") {
            parent.append(node("p", "muted", "Decompiled Java representation"));
            appendDiff(parent, comparison.unifiedDiff);
        } else if (comparison.status === "ASM_FALLBACK") {
            parent.append(node("p", "", "Decompiled Java text is identical."));
        } else {
            parent.append(node("p", "warn",
                `Code comparison unavailable: ${display(comparison.reason)}`));
        }
        if (comparison.asmFallback) {
            parent.append(node("h5", "", "ASM instruction diff"));
            appendDiff(parent, comparison.asmFallback);
        }
    }

    function buildDetails(path, member) {
        const details = node("div", "path-details");
        details.append(node("h3", "", `${labels[path.type]} path details`));
        const sequence = node("p");
        sequence.append(node("strong", "", "Complete path: "));
        sequence.append(node("code", "", path.segments.join(" → ")));
        details.append(sequence);
        details.append(node("h4", "", "Path evidence"));
        appendInfoTable(details, evidenceEntries(path));
        details.append(node("h4", "", `${member.changeKind}: ${member.member}`));
        appendInfoTable(details, memberEntries(member));
        appendComparison(details, member);
        return details;
    }

    function toggleDetails(path, row, button) {
        if (state.selectedPathId === path.id) {
            closeDetails();
            return;
        }
        closeDetails();
        const member = members.get(path.memberId);
        const detailsRow = node("tr", "path-details-row");
        const cell = node("td");
        cell.colSpan = 7;
        cell.append(buildDetails(path, member));
        detailsRow.append(cell);
        row.after(detailsRow);
        button.setAttribute("aria-expanded", "true");
        state.detailsRow = detailsRow;
        state.detailsButton = button;
        state.selectedPathId = path.id;
    }

    function render(resetPage) {
        closeDetails();
        if (resetPage) {
            state.page = 1;
        }
        const matches = filteredPaths();
        const pageCount = Math.max(1, Math.ceil(matches.length / state.pageSize));
        state.page = Math.min(Math.max(1, state.page), pageCount);
        const start = (state.page - 1) * state.pageSize;
        const end = Math.min(start + state.pageSize, matches.length);
        const fragment = document.createDocumentFragment();
        matches.slice(start, end).forEach(path => fragment.append(createRow(path)));
        rowsNode.replaceChildren(fragment);

        emptyNode.textContent = emptyMessages[state.type];
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
        const matches = filteredPaths();
        state.page = Math.max(1, Math.ceil(matches.length / state.pageSize));
        render(false);
    });

    render(true);
})();
