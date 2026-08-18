import {
    expect,
    observeBrowser,
    openAffectedPaths,
    openModule,
    test
} from "./report-fixture";

test("navigates the complete offline report without browser errors",
    async ({page, report}) => {
        const diagnostics = observeBrowser(page);

        await page.goto(report.overallUrl);
        await expect(page).toHaveTitle("Impact Analysis Report");
        await page.getByRole("link", {
            name: "io.browserfixture:application:jar:1.0.0"
        }).click();
        await expect(page).toHaveTitle("Module summary");
        await expect(page.locator("#member-result-summary"))
            .toContainText("Showing 1–20 of 25");
        await page.getByRole("link", {name: "Affected Paths"}).click();
        await expect(page).toHaveTitle("Affected Paths");
        await expect(page.locator("#path-result-summary"))
            .toContainText("Showing 1–20 of 41");

        expect(diagnostics.pageErrors).toEqual([]);
        expect(diagnostics.consoleErrors).toEqual([]);
        expect(diagnostics.externalRequests).toEqual([]);
    });

test("keeps one search control and a separate dependency scope per table",
    async ({page, report}) => {
        await openModule(page, report);
        await expect(page.locator("#changed-members input[type=search]"))
            .toHaveCount(1);
        await expect(page.locator("#member-scope-form fieldset"))
            .toHaveCount(1);
        await expect(page.locator("#member-scope-form #member-search"))
            .toHaveCount(0);
        await expect(page.locator("#member-result-summary"))
            .toHaveAttribute("aria-live", "polite");

        await openAffectedPaths(page, report);
        await expect(page.locator("#paths input[type=search]"))
            .toHaveCount(1);
        await expect(page.locator("#path-scope-form fieldset"))
            .toHaveCount(1);
        await expect(page.locator("#path-scope-form #path-search"))
            .toHaveCount(0);
        await expect(page.locator("#path-result-summary"))
            .toHaveAttribute("aria-live", "polite");
    });
