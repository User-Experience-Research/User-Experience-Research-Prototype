import AxeBuilder from "@axe-core/playwright";
import { chromium } from "playwright";

const baseUrl = process.env.BASE_URL ?? "http://127.0.0.1:8080";
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext();
const page = await context.newPage();

async function checkPage(name, path) {
  await page.goto(`${baseUrl}${path}`, { waitUntil: "networkidle" });
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();

  if (results.violations.length > 0) {
    const details = results.violations
      .map((violation) => {
        const targets = violation.nodes
          .flatMap((node) => node.target)
          .join(", ");
        return `${violation.id}: ${violation.help} (${targets})`;
      })
      .join("\n");
    throw new Error(`${name} has accessibility violations:\n${details}`);
  }

  console.log(`✓ ${name}`);
}

try {
  await checkPage("Login", "/login");

  await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
  await page.getByRole("button", { name: "Log in" }).click();
  await page.waitForURL("**/dashboard");

  for (const [name, path] of [
    ["Dashboard", "/dashboard"],
    ["Support search", "/support"],
    ["Facility detail", "/support/1"],
    ["Appointments", "/appointments"]
  ]) {
    await checkPage(name, path);
  }
} finally {
  await browser.close();
}
