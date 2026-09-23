import {access, readdir} from "node:fs/promises";
import {resolve} from "node:path";

const FIXTURE_ROOT = resolve(
    process.cwd(), "target/playwright-report-fixture");

export default async function globalSetup(): Promise<void> {
    const major = Number(process.versions.node.split(".")[0]);
    if (major < 20) {
        throw new Error(
            `Impact report browser tests require Node.js 20 or newer; found ${process.version}.`
        );
    }
    try {
        await access(resolve(FIXTURE_ROOT, "impact", "index.html"));
        const modules = await readdir(resolve(FIXTURE_ROOT, "impact", "modules"));
        if (!modules.some(name => name.endsWith(".html")
                && !name.endsWith("-impact.html"))
                || !modules.some(name => name.endsWith("-impact.html"))) {
            throw new Error("Module report pages are missing.");
        }
        await access(resolve(FIXTURE_ROOT, "tree", "index.html"));
        const reactors = await readdir(resolve(
            FIXTURE_ROOT, "tree", "dependency-report", "reactors"));
        if (!reactors.some(name => name.endsWith(".html"))) {
            throw new Error("Tree reactor report page is missing.");
        }
    } catch (error) {
        const detail = error instanceof Error ? error.message : String(error);
        throw new Error(
            "Playwright report fixture is unavailable. Run `mvn clean verify` before `npm run test:report`. "
                + detail
        );
    }
}
