(() => {
    const STORAGE_KEY = "nmsi-large-font";
    const root = document.documentElement;
    const toggles = Array.from(document.querySelectorAll("[data-font-toggle]"));

    const applySetting = (enabled) => {
        root.classList.toggle("large-font", enabled);
        toggles.forEach((button) => {
            button.setAttribute("aria-pressed", String(enabled));
            button.textContent = `Large font: ${enabled ? "On" : "Off"}`;
        });
    };

    const stored = window.localStorage.getItem(STORAGE_KEY) === "true";
    applySetting(stored);

    toggles.forEach((button) => {
        button.addEventListener("click", () => {
            const enabled = !root.classList.contains("large-font");
            window.localStorage.setItem(STORAGE_KEY, String(enabled));
            applySetting(enabled);
        });
    });
})();

