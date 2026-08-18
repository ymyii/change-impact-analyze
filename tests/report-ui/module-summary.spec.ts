import {
    dispatchChange,
    expect,
    observeShardRequests,
    openModule,
    test
} from "./report-fixture";

const AVAILABLE_MEMBER =
    "com.acme.orders.OrderApi#lookup(Ljava/lang/String;)Ljava/lang/String;";
const STRUCTURAL_MEMBER =
    "com.acme.orders.OrderState#status:I → "
        + "com.acme.orders.OrderState#status:Ljava/lang/String;";

test("paginates all changed members and changes page size",
    async ({page, report}) => {
        await openModule(page, report);
        await expect(page.locator("#member-rows > tr.member-row"))
            .toHaveCount(20);
        await expect(page.locator("#member-page-count")).toHaveText("of 2");

        await page.locator("#member-next").click();
        await expect(page.locator("#member-result-summary"))
            .toContainText("Showing 21–25 of 25");
        await expect(page.locator("#member-rows > tr.member-row"))
            .toHaveCount(5);

        await page.locator("#member-page-size").selectOption("50");
        await expect(page.locator("#member-result-summary"))
            .toContainText("Showing 1–25 of 25");
        await expect(page.locator("#member-rows > tr.member-row"))
            .toHaveCount(25);
    });

test("searches dependency and complete member signatures case-insensitively",
    async ({page, report}) => {
        await openModule(page, report);
        const search = page.locator("#member-search");

        await search.fill("ORDERS-API");
        await expect(page.locator("#member-result-summary"))
            .toContainText("of 15 matching members");
        await expect(page.locator("#member-rows"))
            .not.toContainText("billing-spi");

        await search.fill("CHANGEDMEMBERNEEDLE(JLjava/lang/String;)V");
        await expect(page.locator("#member-result-summary"))
            .toContainText("Showing 1–1 of 1");
        await expect(page.locator("#member-rows"))
            .toContainText("org.browserfixture.billing.BillingGateway"
                + "#changedMemberNeedle(JLjava/lang/String;)V");
    });

test("combines scope, search, dependency, member, chain and kind filters",
    async ({page, report}) => {
        await openModule(page, report);
        await page.locator("#member-dependency-include").fill("com.acme:*");
        await page.locator("#member-dependency-exclude")
            .fill("com.acme:ignored-*");
        await page.locator("#member-dependency-exclude").press("Enter");
        await expect(page.locator("#member-result-summary"))
            .toContainText("of 15 matching members");

        await page.locator("#member-search").fill("lookup");
        await page.locator("#member-dependency-filter")
            .fill("com.acme:orders-api");
        await dispatchChange(page.locator("#member-dependency-filter"));
        await page.locator("#member-member-filter").fill(AVAILABLE_MEMBER);
        await dispatchChange(page.locator("#member-member-filter"));
        await page.locator("#member-chain").selectOption("has");
        await page.locator("#member-kind")
            .selectOption("METHOD_BODY_CHANGED");

        await expect(page.locator("#member-result-summary"))
            .toContainText("Showing 1–1 of 1");
        await expect(page.locator("#member-rows"))
            .toContainText(AVAILABLE_MEMBER);
    });

test("rejects invalid exact values and globs without replacing rows",
    async ({page, report}) => {
        await openModule(page, report);
        const rows = page.locator("#member-rows");
        const original = await rows.textContent();

        await page.locator("#member-dependency-filter")
            .fill("not:a-candidate");
        await dispatchChange(page.locator("#member-dependency-filter"));
        await expect(page.locator("#member-result-summary"))
            .toContainText("must be selected from the available values");
        expect(await rows.textContent()).toBe(original);

        await page.locator("#member-dependency-filter").fill("");
        await dispatchChange(page.locator("#member-dependency-filter"));
        await page.locator("#member-dependency-include").fill("invalid-glob");
        await page.locator("#member-dependency-include").press("Enter");
        await expect(page.locator("#member-result-summary"))
            .toContainText("Invalid dependency Glob: invalid-glob");
        expect(await rows.textContent()).toBe(original);
    });

test("loads Java diffs lazily and keeps at most one expanded row",
    async ({page, report}) => {
        const shardRequests = observeShardRequests(page);
        await openModule(page, report);
        expect(shardRequests.filter(name => name.startsWith("diffs-")))
            .toEqual([]);
        await expect(page.locator("#member-rows"))
            .toContainText("Unavailable");
        await expect(page.locator("#member-rows"))
            .toContainText("Not generated — no impact path");

        const buttons = page.getByRole("button", {name: "View Java diff"});
        await expect(buttons).toHaveCount(2);
        await buttons.first().click();
        await expect(page.locator("tr.member-diff-row")).toHaveCount(1);
        await expect(page.locator("tr.member-diff-row"))
            .toContainText("return \"after\"");
        await expect(page.getByRole("button", {name: "Hide Java diff"}))
            .toHaveAttribute("aria-expanded", "true");
        expect(shardRequests.some(name => name.startsWith("diffs-")))
            .toBe(true);

        await page.getByRole("button", {name: "View Java diff"}).click();
        await expect(page.locator("tr.member-diff-row")).toHaveCount(1);
        await expect(page.locator("button[aria-expanded=true]"))
            .toHaveCount(1);

        await page.locator("#member-member-filter").fill(STRUCTURAL_MEMBER);
        await dispatchChange(page.locator("#member-member-filter"));
        await expect(page.locator("tr.member-diff-row")).toHaveCount(0);
        await expect(page.locator("button[aria-expanded=true]"))
            .toHaveCount(0);
    });
