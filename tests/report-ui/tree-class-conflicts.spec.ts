import {copyFile, readFile, unlink, writeFile} from "node:fs/promises";
import {basename, resolve} from "node:path";
import {readdir} from "node:fs/promises";
import {
    expect,
    observeBrowser,
    openTree,
    test
} from "./report-fixture";

test("loads one class source lazily and switches logical sources",
    async ({page, report}) => {
        const diagnostics = observeBrowser(page);
        const shards: string[] = [];
        page.on("request", request => {
            if (request.url().includes("-data/class-sources-")) {
                shards.push(basename(new URL(request.url()).pathname));
            }
        });
        await openTree(page, report);
        expect(shards).toEqual([]);

        const first = page.locator("[data-class-conflict-row]:visible").first();
        const view = first.locator("[data-view-class-code]");
        await expect(view).toHaveAttribute("aria-expanded", "false");
        await view.click();
        await expect(view).toHaveAttribute("aria-expanded", "true");
        await expect(page.locator(".class-code-panel pre"))
            .toContainText("return \"winner\"");
        const winner = page.getByRole("button", {name: /Winner —/});
        const shadowed = page.getByRole("button", {name: /Shadowed —/});
        await expect(winner).toHaveAttribute("aria-pressed", "true");
        await expect(shadowed).toHaveAttribute("aria-pressed", "false");
        const inactiveStyle = await shadowed.evaluate(element => {
            const style = getComputedStyle(element);
            return {
                background: style.backgroundColor,
                border: style.borderColor,
                color: style.color,
                weight: style.fontWeight
            };
        });
        const activeStyle = await winner.evaluate(element => {
            const style = getComputedStyle(element);
            return {
                background: style.backgroundColor,
                border: style.borderColor,
                color: style.color,
                weight: style.fontWeight
            };
        });
        expect(activeStyle).not.toEqual(inactiveStyle);
        expect(shards).toHaveLength(1);
        expect(await page.evaluate(() =>
            (window as Window & {__treeInjected?: boolean}).__treeInjected))
            .toBeUndefined();

        await winner.focus();
        await page.keyboard.press("Tab");
        await expect(shadowed).toBeFocused();
        const focusedOutline = await shadowed.evaluate(element =>
            getComputedStyle(element).outlineStyle);
        expect(focusedOutline).not.toBe("none");
        await shadowed.press("Enter");
        await expect(shadowed).toBeFocused();
        await expect(shadowed).toHaveAttribute("aria-pressed", "true");
        await expect(winner).toHaveAttribute("aria-pressed", "false");
        await expect(page.locator(".class-code-panel pre"))
            .toContainText("return \"shadowed\"");

        const second = page.locator("[data-class-conflict-row]:visible").nth(1);
        await second.locator("[data-view-class-code]").click();
        await expect(page.locator(".class-code-row")).toHaveCount(1);
        expect(shards).toHaveLength(2);
        expect(diagnostics.pageErrors).toEqual([]);
        expect(diagnostics.consoleErrors).toEqual([]);
        expect(diagnostics.externalRequests).toEqual([]);
    });

test("filters, sorts, paginates, closes source, and stays responsive",
    async ({page, report}) => {
        await openTree(page, report);
        const component = page.locator(
            "[data-class-conflict-component]").first();
        await component.locator("[data-view-class-code]").first().click();
        await expect(page.locator(".class-code-row")).toHaveCount(1);

        await component.locator("[data-class-filter]").selectOption("LOW");
        await expect(page.locator(".class-code-row")).toHaveCount(0);
        await expect(component.locator("[data-class-position]"))
            .toContainText("1-6 / 6");
        const visibleRisk = component.locator(
            "[data-class-conflict-row]:visible [data-view-class-code]");
        await expect(visibleRisk).toHaveCount(6);

        await component.locator("[data-class-filter]").selectOption("");
        await component.locator("[data-class-search]").fill("conflict11");
        await expect(component.locator("[data-class-position]"))
            .toContainText("1-1 / 1");
        await expect(component.locator("[data-class-conflict-row]:visible"))
            .toContainText("fixture.Conflict11");

        await component.locator("[data-class-search]").fill("");
        await component.locator("[data-class-sort=className]").click();
        await component.locator("[data-class-next]").click();
        await expect(component.locator("[data-class-position]"))
            .toContainText("11-12 / 12");
        await component.locator("[data-class-page-size]").selectOption("50");
        await expect(component.locator("[data-class-position]"))
            .toContainText("1-12 / 12");

        const module = page.locator(
            '[data-combobox="module-selector"] input');
        await module.fill("io.browserfixture:library:1.0.0");
        await module.press("Enter");
        await expect(module).toHaveValue("io.browserfixture:library:1.0.0");
        await expect(page.locator("#module-panel > h3"))
            .toHaveText("io.browserfixture:library:1.0.0");
        await expect(page.locator("[data-class-position]"))
            .toContainText("1-1 / 1");
        const overflow = await page.evaluate(() => ({
            documentWidth: document.documentElement.scrollWidth,
            viewportWidth: document.documentElement.clientWidth
        }));
        expect(overflow.documentWidth).toBeLessThanOrEqual(
            overflow.viewportWidth);
    });

test("shows Unavailable and retries a missing source shard",
    async ({page, report}) => {
        await openTree(page, report);
        const component = page.locator(
            "[data-class-conflict-component]").first();
        await component.locator("[data-class-search]").fill("conflict02");
        await component.locator(
            "[data-class-conflict-row]:visible [data-view-class-code]").click();
        await expect(page.locator(".class-code-panel pre"))
            .toHaveText("Unavailable");

        await component.locator("[data-class-search]").fill("conflict00");
        const row = component.locator("[data-class-conflict-row]:visible");
        const entries = await readdir(report.treeShardDirectory);
        const shard = entries.find(name => name
            .startsWith("class-sources-00000"));
        expect(shard).toBeDefined();
        const shardPath = resolve(report.treeShardDirectory, shard!);
        const backup = resolve(report.root, "class-conflict-backup.js");
        await copyFile(shardPath, backup);
        await unlink(shardPath);

        await row.locator("[data-view-class-code]").click();
        await expect(page.locator(".class-code-panel"))
            .toContainText(`Report shard could not be loaded`);
        const original = await readFile(backup);
        await writeFile(shardPath, original);
        await page.getByRole("button", {name: "Retry"}).click();
        await expect(page.locator(".class-code-panel pre"))
            .toContainText("return \"winner\"");
    });
