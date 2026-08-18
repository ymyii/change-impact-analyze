import {defineConfig} from "@playwright/test";

export default defineConfig({
    testDir: "./tests/report-ui",
    globalSetup: "./tests/report-ui/global-setup.ts",
    fullyParallel: false,
    workers: 1,
    retries: 0,
    forbidOnly: Boolean(process.env.CI),
    timeout: 30_000,
    expect: {
        timeout: 5_000
    },
    outputDir: "target/playwright/test-results",
    reporter: [
        ["list"],
        ["html", {
            outputFolder: "target/playwright/html-report",
            open: "never"
        }]
    ],
    use: {
        headless: true,
        trace: "retain-on-failure",
        screenshot: "only-on-failure",
        video: "off"
    },
    projects: [
        {
            name: "chromium-desktop",
            use: {
                browserName: "chromium",
                viewport: {width: 1280, height: 800}
            }
        },
        {
            name: "chromium-small-screen",
            use: {
                browserName: "chromium",
                viewport: {width: 390, height: 844}
            }
        }
    ]
});
