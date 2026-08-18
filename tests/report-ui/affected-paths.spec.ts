import {readFile, readdir, rename, writeFile} from "node:fs/promises";
import {resolve} from "node:path";

import {
    dispatchChange,
    expect,
    observeShardRequests,
    openAffectedPaths,
    submitAffectedSearch,
    test
} from "./report-fixture";

const AVAILABLE_MEMBER =
    "com.acme.orders.OrderApi#lookup(Ljava/lang/String;)Ljava/lang/String;";
const MULTI_CONTROLLER =
    "io.browserfixture.application.MultiMethodOnlyController"
        + "#review(Ljava/lang/String;)V";
const MULTI_SERVICE =
    "io.browserfixture.application.MultiMethodOnlyService"
        + "#load(Ljava/lang/String;I)Ljava/lang/String;";

test("paginates the selected view type and changes page size",
    async ({page, report}) => {
        await openAffectedPaths(page, report);
        await expect(page.locator("#path-rows > tr.path-row")).toHaveCount(20);
        await expect(page.locator("#path-page-count")).toHaveText("of 3");

        await page.locator("#path-next").click();
        await expect(page.locator("#path-result-summary"))
            .toContainText("Showing 21–40 of 41");
        await expect(page.locator("#path-rows > tr.path-row")).toHaveCount(20);
        await page.locator("#path-next").click();
        await expect(page.locator("#path-result-summary"))
            .toContainText("Showing 41–41 of 41");
        await expect(page.locator("#path-rows > tr.path-row")).toHaveCount(1);

        await page.locator("#path-page-size").selectOption("50");
        await expect(page.locator("#path-result-summary"))
            .toContainText("Showing 1–41 of 41");
        await expect(page.locator("#path-rows > tr.path-row")).toHaveCount(41);
    });

test("does not scan search indexes until Search or Enter submits",
    async ({page, report}) => {
        const shards = observeShardRequests(page);
        await openAffectedPaths(page, report);
        expect(shards.some(name => name.startsWith("index-"))).toBe(false);
        const originalRows = await page.locator("#path-rows").textContent();

        await page.locator("#path-search").fill("MultiMethodOnlyService");
        await page.waitForTimeout(200);
        expect(shards.some(name => name.startsWith("index-"))).toBe(false);
        expect(await page.locator("#path-rows").textContent())
            .toBe(originalRows);

        await page.locator("#path-search").press("Enter");
        await expect(page.locator("#path-result-summary"))
            .toContainText("Showing 1–1 of 1");
        expect(shards.some(name => name.startsWith("index-"))).toBe(true);
    });

test("global search matches each supported column",
    async ({page, report}) => {
        await openAffectedPaths(page, report);
        const cases = [
            ["MultiMethodOnlyService", "MultiMethodOnlyService"],
            ["org.browserfixture:billing-spi", "billing-spi"],
            ["changedMemberNeedle", "changedMemberNeedle"],
            ["PathOnlyNeedleController", "PathOnlyNeedleController"]
        ];
        for (const [query, expected] of cases) {
            await submitAffectedSearch(page, query);
            await expect(page.locator("#path-result-summary"))
                .toContainText("Showing 1–1 of 1");
            await expect(page.locator("#path-rows")).toContainText(expected);
        }
    });

test("combines scope and exact dependency, member, method and view filters",
    async ({page, report}) => {
        await openAffectedPaths(page, report);
        await page.locator("#path-member-filter").focus();
        await expect(page.locator("#path-member-options option"))
            .toHaveCount(3);
        await page.locator("#path-method-filter").focus();
        await expect(page.locator("#path-method-options option"))
            .not.toHaveCount(0);

        await page.locator("#path-dependency-filter")
            .fill("com.acme:orders-api");
        await dispatchChange(page.locator("#path-dependency-filter"));
        await page.locator("#path-member-filter").fill(AVAILABLE_MEMBER);
        await dispatchChange(page.locator("#path-member-filter"));
        await page.locator("#path-method-filter").fill(MULTI_SERVICE);
        await dispatchChange(page.locator("#path-method-filter"));
        await page.locator("#path-type").selectOption("impact");
        await page.locator("#path-dependency-include").fill("com.acme:*");
        await page.locator("#path-dependency-exclude")
            .fill("org.browserfixture:*");
        await page.locator("#path-dependency-exclude").press("Enter");

        await expect(page.locator("#path-result-summary"))
            .toContainText("Showing 1–1 of 1");
        await expect(page.locator("#path-rows")).toContainText(MULTI_SERVICE);
        await expect(page.locator("#path-rows")).toContainText(AVAILABLE_MEMBER);

        await page.locator("#path-method-filter").fill(MULTI_CONTROLLER);
        await dispatchChange(page.locator("#path-method-filter"));
        await expect(page.locator("#path-result-summary"))
            .toContainText("Showing 1–1 of 1");
        await expect(page.locator("#path-rows")).toContainText(MULTI_CONTROLLER);
    });

test("rejects an invalid exact value without replacing the successful DOM",
    async ({page, report}) => {
        await openAffectedPaths(page, report);
        const rows = page.locator("#path-rows");
        const original = await rows.textContent();

        await page.locator("#path-member-filter").fill("not-a-member");
        await dispatchChange(page.locator("#path-member-filter"));
        await expect(page.locator("#path-result-summary"))
            .toContainText("must be selected from the available values");
        expect(await rows.textContent()).toBe(original);
    });

test("switches Impact, Structural and All without rescanning the query",
    async ({page, report}) => {
        const shards = observeShardRequests(page);
        await openAffectedPaths(page, report);
        await page.locator("#path-type").selectOption("all");
        await expect(page.locator("#path-result-summary"))
            .toContainText("of 42 matching records");
        await page.locator("#path-type").selectOption("structural");
        await expect(page.locator("#path-result-summary"))
            .toContainText("Showing 1–1 of 1");
        await expect(page.locator("#path-rows"))
            .toContainText("FIELD_DESCRIPTOR_CHANGED");
        expect(shards.some(name => name.startsWith("index-"))).toBe(false);
    });

test("loads candidate catalogs and Java diff shards only when needed",
    async ({page, report}) => {
        const shards = observeShardRequests(page);
        const files = await readdir(report.shardDirectory);
        const memberShardCount = files.filter(name =>
            name.startsWith("members-")).length;
        const indexShardCount = files.filter(name =>
            name.startsWith("index-")).length;
        const pagedKinds = ["rows-", "paths-", "methods-", "members-"];

        await openAffectedPaths(page, report);
        for (const prefix of pagedKinds) {
            const available = files.filter(name => name.startsWith(prefix))
                .length;
            const loaded = new Set(shards.filter(name =>
                name.startsWith(prefix))).size;
            expect(loaded).toBeLessThan(available);
        }
        expect(new Set(shards.filter(name => name.startsWith("members-"))).size)
            .toBeLessThan(memberShardCount);
        expect(shards.some(name => name.startsWith("index-"))).toBe(false);
        expect(shards.some(name => name.startsWith("diffs-"))).toBe(false);

        await page.locator("#path-member-filter").focus();
        await expect.poll(() => new Set(shards.filter(name =>
            name.startsWith("members-"))).size).toBe(memberShardCount);
        await page.locator("#path-method-filter").focus();
        await expect.poll(() => new Set(shards.filter(name =>
            name.startsWith("index-"))).size).toBe(indexShardCount);

        await submitAffectedSearch(page, "MultiMethodOnlyService");
        await page.getByRole("button", {name: "View Java diff"}).click();
        await expect(page.locator("tr.path-diff-row")).toHaveCount(1);
        expect(shards.some(name => name.startsWith("diffs-"))).toBe(true);
    });

test("keeps only the newest result from rapid search submissions",
    async ({page, report}) => {
        await openAffectedPaths(page, report);
        await page.evaluate(() => {
            const form = document.querySelector<HTMLFormElement>(
                "#path-search-form");
            const input = document.querySelector<HTMLInputElement>(
                "#path-search");
            if (!form || !input) {
                throw new Error("Search controls are unavailable");
            }
            input.value = "BillingController";
            form.requestSubmit();
            input.value = "PathOnlyNeedleController";
            form.requestSubmit();
        });
        await expect(page.locator("#path-result-summary"))
            .toContainText("search “PathOnlyNeedleController”");
        await expect(page.locator("#path-rows"))
            .toContainText("PathOnlyNeedleController");
        await expect(page.locator("#path-rows"))
            .not.toContainText("BillingController");
    });

for (const variant of ["missing", "corrupt"] as const) {
    test(`retains rows and retries a ${variant} search shard`,
        async ({page, report}) => {
            const shard = resolve(report.shardDirectory, "index-00000.js");
            const backup = `${shard}.backup`;
            const original = await readFile(shard);
            let restored = false;
            if (variant === "missing") {
                await rename(shard, backup);
            } else {
                await writeFile(shard,
                    "window.__CIA_AFFECTED_PATH_SHARD__({broken:true});");
            }
            try {
                await openAffectedPaths(page, report);
                const rows = page.locator("#path-rows");
                const originalRows = await rows.textContent();
                await submitAffectedSearch(page, "BillingController");
                await expect(page.locator("#path-empty"))
                    .toContainText("index-00000.js");
                await expect(page.locator("#path-empty"))
                    .toContainText("Retry");
                await expect(page.locator("#path-result-summary"))
                    .toContainText("last successfully rendered page");
                expect(await rows.textContent()).toBe(originalRows);

                if (variant === "missing") {
                    await rename(backup, shard);
                } else {
                    await writeFile(shard, original);
                }
                restored = true;
                await page.locator("#path-empty")
                    .getByRole("button", {name: "Retry"}).click();
                await expect(page.locator("#path-result-summary"))
                    .toContainText("Showing 1–1 of 1");
                await expect(page.locator("#path-empty"))
                    .toHaveClass(/hidden/);
                await expect(page.locator("#path-rows"))
                    .toContainText("BillingController");
            } finally {
                if (!restored) {
                    if (variant === "missing") {
                        await rename(backup, shard).catch(() => undefined);
                    } else {
                        await writeFile(shard, original);
                    }
                }
            }
        });
}
