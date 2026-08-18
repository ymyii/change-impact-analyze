import {
    expect,
    openAffectedPaths,
    openModule,
    submitAffectedSearch,
    test
} from "./report-fixture";

const LONG_MEMBER =
    "com.acme.orders.ExtremelyLongCompatibilitySurfaceFor"
        + "ResponsiveLayoutVerification"
        + "#calculateCustomerSpecificOrderCompatibilityResult"
        + "(Ljava/lang/String;Ljava/util/List;Ljava/util/Map;)"
        + "Ljava/util/concurrent/CompletionStage;";

test("keeps diff actions horizontal and confines table overflow",
    async ({page, report}) => {
        await openModule(page, report);
        const button = page.getByRole("button", {name: "View Java diff"})
            .first();
        const geometry = await button.evaluate(element => {
            const style = getComputedStyle(element);
            const rect = element.getBoundingClientRect();
            return {
                display: style.display,
                whiteSpace: style.whiteSpace,
                writingMode: style.writingMode,
                width: rect.width,
                height: rect.height,
                clientWidth: element.clientWidth,
                scrollWidth: element.scrollWidth
            };
        });
        expect(geometry.display).toBe("inline-flex");
        expect(geometry.whiteSpace).toBe("nowrap");
        expect(geometry.writingMode).toBe("horizontal-tb");
        expect(geometry.width).toBeGreaterThanOrEqual(112);
        expect(geometry.height).toBeLessThan(60);
        expect(geometry.scrollWidth).toBeLessThanOrEqual(geometry.clientWidth);

        const overflow = await page.evaluate(() => {
            const root = document.documentElement;
            const table = document.querySelector<HTMLElement>(
                "#changed-members .table-scroll");
            if (!table) {
                throw new Error("Changed members table wrapper is missing");
            }
            return {
                documentWidth: root.scrollWidth,
                viewportWidth: root.clientWidth,
                tableWidth: table.scrollWidth,
                tableViewport: table.clientWidth
            };
        });
        expect(overflow.documentWidth)
            .toBeLessThanOrEqual(overflow.viewportWidth);
        expect(overflow.tableWidth).toBeGreaterThan(overflow.tableViewport);
    });

test("renders complete long signatures without clipping or ellipsis",
    async ({page, report}) => {
        await openModule(page, report);
        await page.locator("#member-search")
            .fill("ExtremelyLongCompatibilitySurface");
        await expect(page.locator("#member-result-summary"))
            .toContainText("Showing 1–1 of 1");
        const member = page.locator("#member-rows tr.member-row td")
            .nth(2);
        await expect(member).toContainText(LONG_MEMBER);
        const style = await member.evaluate(element => {
            const computed = getComputedStyle(element);
            return {
                overflow: computed.overflow,
                textOverflow: computed.textOverflow,
                whiteSpace: computed.whiteSpace
            };
        });
        expect(style.textOverflow).not.toBe("ellipsis");
        expect(style.overflow).not.toBe("hidden");
        expect(style.whiteSpace).not.toBe("nowrap");
    });

test("exposes focus rings and accessible diff expansion state",
    async ({page, report}) => {
        await openModule(page, report);
        const search = page.locator("#member-search");
        await search.focus();
        const outline = await search.evaluate(element => {
            const style = getComputedStyle(element);
            return {style: style.outlineStyle, width: style.outlineWidth};
        });
        expect(outline.style).toBe("solid");
        expect(outline.width).toBe("3px");

        const button = page.getByRole("button", {name: "View Java diff"})
            .first();
        await expect(button).toHaveAttribute("aria-expanded", "false");
        await button.press("Enter");
        await expect(page.getByRole("button", {name: "Hide Java diff"}))
            .toHaveAttribute("aria-expanded", "true");

        await openAffectedPaths(page, report);
        await submitAffectedSearch(page, "MultiMethodOnlyService");
        await expect(page.locator("#path-rows"))
            .toContainText("MultiMethodOnlyController");
        await expect(page.locator("#path-rows"))
            .toContainText("MultiMethodOnlyService");
    });
