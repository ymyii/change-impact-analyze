(() => {
    "use strict";

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

    function sortedUnique(values) {
        return [...new Set(values)].sort((left, right) =>
            left.localeCompare(right));
    }

    function fillDatalist(id, values) {
        const list = document.getElementById(id);
        if (!list) {
            return;
        }
        const fragment = document.createDocumentFragment();
        sortedUnique(values).forEach(value => {
            const option = document.createElement("option");
            option.value = value;
            fragment.append(option);
        });
        list.replaceChildren(fragment);
    }

    function exactValue(input, values, label) {
        const value = input.value.trim();
        if (value && !values.has(value)) {
            throw new Error(`${label} must be selected from the available values`);
        }
        return value;
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

    function createDiffRow(comparison, columnCount, className) {
        const diffRow = node("tr", className || "path-diff-row");
        const cell = node("td", "path-diff");
        cell.colSpan = columnCount;
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

    function createShardLoader(manifest, validRecord, recordId) {
        const requests = new Map();
        const pending = new Map();

        function shardError(message) {
            const error = new Error(message);
            error.retryable = true;
            return error;
        }

        function shardKey(kind, id) {
            return `${kind}:${id}`;
        }

        function descriptors(kind) {
            return Array.isArray(manifest.shards[kind])
                ? manifest.shards[kind] : [];
        }

        function descriptorForId(kind, id) {
            return descriptors(kind).find(value => id >= value.firstId
                && id <= value.lastId);
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
                waiter.reject(shardError(
                    `Invalid ${waiter.file} shard payload or schema.`));
                return;
            }
            waiter.registered = true;
            waiter.resolve(payload.records);
        };

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
                        reject(shardError(
                            `Affected path shard did not register: ${descriptor.file}`));
                    }
                };
                script.onerror = () => reject(shardError(
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
                    throw shardError(
                        `No ${kind} shard contains record ${id}.`);
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
                    throw shardError(
                        `Missing ${kind} record ${id} in ${descriptor.file}.`);
                }
            });
            return result;
        }

        return {descriptors, loadShard, recordsForIds};
    }

    window.CIA_REPORT = Object.freeze({node, globs, sourceMatches,
        sortedUnique, fillDatalist, exactValue, normalizeRanges,
        intersectRanges, createDiffRow, createShardLoader});
})();
