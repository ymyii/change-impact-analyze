import {copyFile, readFile, readdir, writeFile} from "node:fs/promises";
import {basename, resolve} from "node:path";
import {
    expect,
    observeBrowser,
    openTree,
    test
} from "./report-fixture";

test("renders only current pages and combines searchable exact filters",
    async ({page, report}) => {
        const diagnostics = observeBrowser(page);
        const requests: string[] = [];
        page.on("request", request => {
            if (request.url().includes("-data/")) {
                requests.push(basename(new URL(request.url()).pathname));
            }
        });

        await openTree(page, report);
        await expect(page.locator("#dependency-rows > tr")).toHaveCount(10);
        await expect(page.locator("[data-class-conflict-row]")).toHaveCount(10);
        expect(requests).toContain("dependency-rows-00000.js");
        expect(requests).toContain("class-conflicts-00000.js");
        expect(requests).toContain("dependency-trees-00000.js");
        expect(requests.some(name => name.startsWith("dependency-index-")))
            .toBe(false);
        expect(requests).not.toContain("dependency-trees-00001.js");
        await expect(page.locator(".combobox-toggle")).toHaveCount(3);

        const dependency = page.locator(
            '[data-combobox="dependency-filter"] input');
        const dependencyToggle = page.locator(
            "#dependency-filter-toggle");
        await expect(dependencyToggle).toBeVisible();
        await expect(dependencyToggle)
            .toHaveAttribute("aria-label", "展开 Dependency 候选");
        await expect(dependency).toHaveAttribute("aria-expanded", "false");
        await dependencyToggle.click();
        await expect(dependency).toHaveAttribute("aria-expanded", "true");
        await expect(dependencyToggle)
            .toHaveAttribute("aria-expanded", "true");
        await expect(dependencyToggle)
            .toHaveAttribute("aria-label", "收起 Dependency 候选");
        await expect(page.locator(
            '[data-combobox="dependency-filter"] [role=option]'))
            .toHaveCount(50);
        await expect(page.locator("#dependency-filter-status"))
            .toContainText("显示前 50 / 75");
        await dependencyToggle.press("Enter");
        await expect(page.locator("#dependency-filter-options"))
            .toBeHidden();
        await expect(dependency).toHaveAttribute("aria-expanded", "false");
        await dependencyToggle.press("Space");
        await expect(page.locator("#dependency-filter-options"))
            .toBeVisible();
        await dependency.fill("org.browserfixture:dependency-025:jar:");
        const option = page.locator(
            '[data-combobox="dependency-filter"] [role=option]').first();
        await expect(option.locator(".version-count")).toHaveText("2");
        await expect(option.locator(".version-count"))
            .toHaveAttribute("aria-label", "2 resolved versions");
        await dependency.press("Enter");
        await expect(page.locator("#dependency-result-summary"))
            .toContainText(/Showing 1–10 of \d+/);

        const module = page.locator(
            '[data-combobox="dependency-module-filter"] input');
        await module.fill("io.browserfixture:application:1.0.0");
        await module.press("Enter");
        await expect(page.locator("#dependency-position"))
            .toContainText("1-1 / 1");
        await page.locator("#dependency-scope-filter").selectOption("test");
        await page.locator("#dependency-search").fill("middle:jar:1.0.0");
        await page.locator("#dependency-controls").evaluate(form =>
            (form as HTMLFormElement).requestSubmit());
        await expect(page.locator("#dependency-position"))
            .toContainText("1-1 / 1");
        const filteredRow = page.locator("#dependency-rows > tr").first();
        await expect(filteredRow)
            .toContainText("org.browserfixture:dependency-025:jar:");
        await expect(filteredRow)
            .toContainText("io.browserfixture:application:1.0.0");
        await expect(filteredRow).toContainText("0.9.0");

        await page.locator("#dependency-clear").click();
        await expect(page.locator("#dependency-position"))
            .toContainText("1-10 / 2626");
        await page.locator("[data-dependency-sort=resolvedVersion]").click();
        await expect(page.locator("#dependency-result-summary"))
            .toContainText("Showing 1–10 of 2626");
        expect(requests.filter(name => name
            .startsWith("dependency-index-"))).toHaveLength(3);
        expect(diagnostics.pageErrors).toEqual([]);
        expect(diagnostics.consoleErrors).toEqual([]);
        expect(diagnostics.externalRequests).toEqual([]);
    });

test("keeps the dependency filters compact across responsive breakpoints",
    async ({page, report}) => {
        const readLayout = () => page.evaluate(() => {
            const rect = (selector: string) => {
                const value = document.querySelector(selector);
                if (!(value instanceof HTMLElement)) {
                    throw new Error(`Missing layout node: ${selector}`);
                }
                const bounds = value.getBoundingClientRect();
                return {left: bounds.left, right: bounds.right,
                    top: bounds.top, bottom: bounds.bottom,
                    width: bounds.width};
            };
            return {
                viewport: document.documentElement.clientWidth,
                documentWidth: document.documentElement.scrollWidth,
                card: rect("#dependency-analysis"),
                primary: rect(".dependency-filter-primary"),
                search: rect("#dependency-search"),
                dependency: rect("#dependency-filter-input"),
                module: rect("#dependency-module-filter-input"),
                secondary: rect(".dependency-filter-secondary"),
                moduleSelector: rect(".module-selector-control"),
                table: rect("#dependency-analysis .table-scroll")
            };
        });

        await page.setViewportSize({width: 1920, height: 1080});
        await openTree(page, report);
        const wide = await readLayout();
        expect(wide.documentWidth).toBeLessThanOrEqual(wide.viewport);
        expect(wide.card.right).toBeLessThanOrEqual(wide.viewport);
        expect(wide.search.width).toBeLessThanOrEqual(281);
        expect(wide.dependency.width).toBeLessThanOrEqual(421);
        expect(wide.module.width).toBeLessThanOrEqual(421);
        expect(Math.abs(wide.search.top - wide.dependency.top))
            .toBeLessThanOrEqual(1);
        expect(Math.abs(wide.dependency.top - wide.module.top))
            .toBeLessThanOrEqual(1);
        expect(wide.secondary.top).toBeGreaterThan(wide.primary.bottom);
        expect(wide.moduleSelector.width).toBeLessThanOrEqual(721);
        expect(wide.table.right).toBeLessThanOrEqual(wide.viewport);

        await page.setViewportSize({width: 900, height: 900});
        const medium = await readLayout();
        expect(medium.documentWidth).toBeLessThanOrEqual(medium.viewport);
        expect(medium.dependency.top).toBeGreaterThan(medium.search.bottom);
        expect(Math.abs(medium.dependency.top - medium.module.top))
            .toBeLessThanOrEqual(1);
        expect(medium.secondary.top).toBeGreaterThan(medium.primary.bottom);

        await page.setViewportSize({width: 390, height: 844});
        const small = await readLayout();
        expect(small.documentWidth).toBeLessThanOrEqual(small.viewport);
        expect(small.dependency.top).toBeGreaterThan(small.search.bottom);
        expect(small.module.top).toBeGreaterThan(small.dependency.bottom);
        expect(small.secondary.top).toBeGreaterThan(small.primary.bottom);
        expect(small.table.right).toBeLessThanOrEqual(small.viewport);
    });

test("switches one Module card, restores light state, and ignores stale loads",
    async ({page, report}) => {
        const treeRequests: string[] = [];
        page.on("request", request => {
            const name = basename(new URL(request.url()).pathname);
            if (name.startsWith("dependency-trees-")) {
                treeRequests.push(name);
            }
        });
        await page.route("**/dependency-trees-00100.js", async route => {
            await new Promise(resolveDelay => setTimeout(resolveDelay, 200));
            await route.continue();
        });
        await openTree(page, report);

        const selector = page.locator(
            '[data-combobox="module-selector"] input');
        await selector.fill("");
        await expect(page.locator("#module-selector-status"))
            .toContainText("显示前 50 / 101");
        await page.locator("[data-class-search]").fill("conflict11");
        await expect(page.locator("[data-class-position]"))
            .toContainText("1-1 / 1");

        await selector.fill("io.browserfixture:module-100:1.0.0");
        await selector.press("Enter");
        await selector.fill("io.browserfixture:module-099:1.0.0");
        await selector.press("Enter");
        await expect(page.locator("#module-panel > h3"))
            .toHaveText("io.browserfixture:module-099:1.0.0");
        await expect(page.locator("[data-class-position]"))
            .toContainText("0 / 0");
        expect(treeRequests).toContain("dependency-trees-00099.js");

        await selector.fill("io.browserfixture:application:1.0.0");
        await selector.press("Enter");
        await expect(page.locator("[data-class-search]"))
            .toHaveValue("conflict11");
        await expect(page.locator("[data-class-position]"))
            .toContainText("1-1 / 1");
        await expect(page.locator("#module-panel > h3"))
            .toHaveCount(1);
    });

test("retries a corrupt dependency shard without executing report data",
    async ({page, report}) => {
        const entries = await readdir(report.treeShardDirectory);
        const shard = entries.find(name => name
            .startsWith("dependency-rows-00000"));
        expect(shard).toBeDefined();
        const shardPath = resolve(report.treeShardDirectory, shard!);
        const backup = resolve(report.root, "dependency-row-backup.js");
        await copyFile(shardPath, backup);
        await writeFile(shardPath,
            "window.__CIA_TREE_REPORT_SHARD__({schemaVersion:99,"
            + "kind:'dependency-rows',shardId:0,records:[]});");

        await page.goto(report.treeUrl);
        await expect(page.locator("#dependency-result-summary"))
            .toContainText("Invalid");
        await expect(page.getByRole("button", {name: "Retry"}).first())
            .toBeVisible();
        await writeFile(shardPath, await readFile(backup));
        await page.getByRole("button", {name: "Retry"}).first().click();
        await expect(page.locator("#dependency-position"))
            .toContainText("1-10 / 2626");
        await expect(page.locator("#dependency-rows > tr")).toHaveCount(10);
        await expect(page.locator("#dependency-rows script")).toHaveCount(0);
    });
