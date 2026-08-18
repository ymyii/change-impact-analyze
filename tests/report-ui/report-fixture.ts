import {test as base, expect, type Locator, type Page} from "@playwright/test";
import {cp, readdir} from "node:fs/promises";
import {basename, resolve} from "node:path";
import {pathToFileURL} from "node:url";

const SOURCE_ROOT = resolve(
    process.cwd(), "target/playwright-report-fixture");

export type ReportCopy = {
    root: string;
    modulePath: string;
    affectedPath: string;
    shardDirectory: string;
    overallUrl: string;
    moduleUrl: string;
    affectedUrl: string;
};

type Fixtures = {
    report: ReportCopy;
};

export const test = base.extend<Fixtures>({
    report: async ({}, use, testInfo) => {
        const root = testInfo.outputPath("report");
        await cp(SOURCE_ROOT, root, {recursive: true});
        const moduleDirectory = resolve(root, "impact-modules");
        const entries = await readdir(moduleDirectory);
        const moduleName = entries.find(name => name.endsWith(".html")
            && !name.endsWith("-impact.html"));
        const affectedName = entries.find(name => name.endsWith("-impact.html"));
        if (!moduleName || !affectedName) {
            throw new Error("Copied report fixture has no module pages.");
        }
        const modulePath = resolve(moduleDirectory, moduleName);
        const affectedPath = resolve(moduleDirectory, affectedName);
        const shardDirectory = resolve(moduleDirectory,
            affectedName.slice(0, -".html".length) + "-data");
        await use({
            root,
            modulePath,
            affectedPath,
            shardDirectory,
            overallUrl: pathToFileURL(resolve(root, "impact.html")).href,
            moduleUrl: pathToFileURL(modulePath).href,
            affectedUrl: pathToFileURL(affectedPath).href
        });
    }
});

export {expect};

export type BrowserDiagnostics = {
    pageErrors: string[];
    consoleErrors: string[];
    externalRequests: string[];
};

export function observeBrowser(page: Page): BrowserDiagnostics {
    const result: BrowserDiagnostics = {
        pageErrors: [],
        consoleErrors: [],
        externalRequests: []
    };
    page.on("pageerror", error => result.pageErrors.push(error.message));
    page.on("console", message => {
        if (message.type() === "error") {
            result.consoleErrors.push(message.text());
        }
    });
    page.on("request", request => {
        if (/^https?:/i.test(request.url())) {
            result.externalRequests.push(request.url());
        }
    });
    return result;
}

export function observeShardRequests(page: Page): string[] {
    const result: string[] = [];
    page.on("request", request => {
        const url = request.url();
        if (url.startsWith("file:") && url.includes("-impact-data/")) {
            result.push(basename(new URL(url).pathname));
        }
    });
    return result;
}

export async function openModule(
    page: Page,
    report: ReportCopy
): Promise<void> {
    await page.goto(report.moduleUrl);
    await expect(page.locator("#member-result-summary"))
        .toContainText("Showing");
}

export async function openAffectedPaths(
    page: Page,
    report: ReportCopy
): Promise<void> {
    await page.goto(report.affectedUrl);
    await expect(page.locator("#path-result-summary"))
        .toContainText("Showing");
}

export async function dispatchChange(locator: Locator): Promise<void> {
    await locator.dispatchEvent("change");
}

export async function submitAffectedSearch(
    page: Page,
    query: string
): Promise<void> {
    await page.locator("#path-search").fill(query);
    await page.locator("#path-search-submit").click();
    await expect(page.locator("#path-result-summary"))
        .not.toContainText("Loading");
}
