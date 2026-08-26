import {
    expect,
    observeBrowser,
    openTreeDiff,
    test
} from "./report-fixture";

test("loads Tree Diff pages lazily and keeps one chain table",
    async ({page, report}) => {
        const diagnostics = observeBrowser(page);
        const requests: string[] = [];
        page.on("request", request => {
            if (request.url().includes("-data/")) {
                requests.push(new URL(request.url()).pathname);
            }
        });

        await openTreeDiff(page, report);
        await expect(page.locator("#module-summary tbody > tr"))
            .toHaveCount(10);
        await expect(page.locator("#module-summary thead th"))
            .toHaveCount(7);
        await expect(page.locator("#module-summary tbody button"))
            .toHaveCount(0);
        await expect(page.locator("#dependency-table tbody > tr"))
            .toHaveCount(10);
        expect(requests.some(name => name.includes("rows-00000.js")))
            .toBe(true);
        expect(requests.some(name => name.includes("chain-rows")))
            .toBe(false);

        const dependency = page.locator("#dependency-filter");
        await dependency.fill("");
        await dependency.focus();
        await expect(page.locator("#dependency-options [role=option]"))
            .toHaveCount(27);
        const changeFilter = page.locator("#change-filter");
        await changeFilter.focus();
        await changeFilter.selectOption("VERSION_CHANGED");
        await expect(page.locator("#dependency-range"))
            .toContainText("of 7; total 26");

        const actions = page.getByRole("button", {
            name: "View dependency chains"
        });
        await actions.first().click();
        await expect(page.locator(".chain-detail")).toHaveCount(1);
        await expect(page.locator(".chain-table tbody > tr")).toHaveCount(10);
        await expect(page.locator(".chain-toolbar")).toHaveCount(1);
        await expect(page.locator(".chain-pager")).toHaveCount(1);
        await expect(page.locator(".chain-detail .table-toolbar"))
            .toHaveCount(0);
        await expect(page.locator(".chain-detail .pager")).toHaveCount(0);
        const controlStyles = await page.evaluate(() => {
            const mainSelect = document.querySelector(
                "#dependency-page-size") as HTMLElement;
            const chainSelect = document.querySelector(
                ".chain-page-size") as HTMLElement;
            const mainButton = document.querySelector(
                "#dependency-previous") as HTMLElement;
            const chainButton = document.querySelector(
                ".chain-page-button") as HTMLElement;
            const pager = document.querySelector(
                ".chain-pager") as HTMLElement;
            return {
                mainSelectHeight: mainSelect.getBoundingClientRect().height,
                chainSelectHeight: chainSelect.getBoundingClientRect().height,
                mainButtonFont: parseFloat(getComputedStyle(mainButton)
                    .fontSize),
                chainButtonFont: parseFloat(getComputedStyle(chainButton)
                    .fontSize),
                pagerAlignment: getComputedStyle(pager).justifyContent
            };
        });
        expect(controlStyles.chainSelectHeight)
            .toBeLessThan(controlStyles.mainSelectHeight);
        expect(controlStyles.chainButtonFont)
            .toBeLessThan(controlStyles.mainButtonFont);
        expect(controlStyles.pagerAlignment).toBe("flex-end");

        await page.locator(".chain-pager").getByRole("button", {
            name: "Next"
        }).click();
        await expect(page.locator(".chain-table tbody > tr")).toHaveCount(2);
        await expect(page.locator(".chain-pager")).toContainText("Page 2 of 2");
        await page.locator(".chain-page-size").selectOption("50");
        await expect(page.locator(".chain-table tbody > tr")).toHaveCount(12);
        await expect(page.locator(".chain-pager")).toContainText("Page 1 of 1");

        await actions.nth(1).click();
        await expect(page.locator(".chain-detail")).toHaveCount(1);
        await expect(page.locator(".chain-table tbody > tr")).toHaveCount(1);
        await expect(page.getByRole("button", {
            name: "Hide dependency chains"
        })).toHaveCount(1);

        const moduleSelector = page.locator("#module-selector");
        await moduleSelector.fill("module-010");
        await moduleSelector.focus();
        await page.locator("#module-options [role=option] button")
            .filter({hasText: "io.browserfixture:module-010:1.0.0"})
            .click();
        await expect(page.locator("#module-page"))
            .toContainText("Page 2");
        await expect(moduleSelector)
            .toHaveValue("io.browserfixture:module-010:1.0.0");
        await expect(page.locator("#dependency-range"))
            .toContainText("total 26");
        expect(await page.locator("body").innerText())
            .not.toMatch(/\p{Script=Han}/u);
        expect(diagnostics.pageErrors).toEqual([]);
        expect(diagnostics.consoleErrors).toEqual([]);
        expect(diagnostics.externalRequests).toEqual([]);
    });

test("uses the viewport width and shows complete dependency trees",
    async ({page, report}) => {
        await page.setViewportSize({width: 1920, height: 1080});
        await openTreeDiff(page, report);
        const gutters = await page.locator("main > section").first()
            .evaluate(node => {
                const bounds = node.getBoundingClientRect();
                return {
                    left: bounds.left,
                    right: window.innerWidth - bounds.right
                };
            });
        expect(gutters.left).toBeLessThanOrEqual(16);
        expect(gutters.right).toBeLessThanOrEqual(16);

        await page.locator("#tree-comparison").scrollIntoViewIfNeeded();
        await expect(page.locator("#baseline-tree"))
            .toContainText("org.browserfixture");
        const trees = await page.locator(".tree-pair pre")
            .evaluateAll(nodes => nodes.map(node => {
                const element = node as HTMLElement;
                const style = getComputedStyle(element);
                return {
                    height: element.getBoundingClientRect().height,
                    clientHeight: element.clientHeight,
                    scrollHeight: element.scrollHeight,
                    overflowX: style.overflowX,
                    overflowY: style.overflowY
                };
            }));
        expect(trees).toHaveLength(2);
        for (const tree of trees) {
            expect(tree.height).toBeGreaterThan(520);
            expect(tree.scrollHeight).toBeLessThanOrEqual(
                tree.clientHeight + 1);
            expect(tree.overflowX).toBe("auto");
            expect(tree.overflowY).toBe("hidden");
        }
    });

test("stacks baseline and target trees on a narrow viewport",
    async ({page, report}) => {
        await page.setViewportSize({width: 390, height: 844});
        await openTreeDiff(page, report);
        await page.locator("#tree-comparison").scrollIntoViewIfNeeded();
        const positions = await page.locator(".tree-pair article")
            .evaluateAll(nodes => nodes.map(node => {
                const bounds = node.getBoundingClientRect();
                return {top: bounds.top, left: bounds.left};
            }));
        expect(positions).toHaveLength(2);
        expect(positions[1].top).toBeGreaterThan(positions[0].top);
        expect(Math.abs(positions[1].left - positions[0].left))
            .toBeLessThanOrEqual(1);
        const overflow = await page.evaluate(() => ({
            document: document.documentElement.scrollWidth,
            viewport: document.documentElement.clientWidth
        }));
        expect(overflow.document).toBeLessThanOrEqual(overflow.viewport);
    });
