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

  await page.goto(`${baseUrl}/support/1`, { waitUntil: "networkidle" });
  const firstFutureSlot = page.locator("[data-appointment-slot]").first();
  const slotDate = await firstFutureSlot.getAttribute("data-date");
  const slotValue = await firstFutureSlot.getAttribute("data-starts-at");
  if (!slotDate || !slotValue || new Date(slotValue) <= new Date()) {
    throw new Error("Facility booking did not provide a future appointment slot");
  }
  await page.getByLabel("Date").fill(slotDate);
  await page.getByLabel("Time").selectOption(slotValue);
  if (!(await page.getByRole("button", { name: "Book appointment" }).isEnabled())) {
    throw new Error("Book appointment remained disabled after a future date and time were selected");
  }
  const bookingResults = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();
  if (bookingResults.violations.length > 0) {
    throw new Error("Selected appointment form has accessibility violations");
  }
  console.log("✓ Future appointment date and time selection");

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${baseUrl}/dashboard`, { waitUntil: "networkidle" });
  const menuButton = page.getByRole("button", { name: "Open student portal menu" });
  await menuButton.click();
  await page.getByRole("complementary", { name: "Student portal" }).waitFor();
  await page.getByRole("button", { name: "Close student portal menu" }).first().click();
  await checkPage("Mobile dashboard", "/dashboard");
} finally {
  await browser.close();
}
