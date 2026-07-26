(() => {
    const sidebar = document.querySelector("#portal-sidebar");
    const openButton = document.querySelector("[data-sidebar-open]");
    const closeButtons = Array.from(document.querySelectorAll("[data-sidebar-close]"));
    const workspace = document.querySelector("[data-portal-workspace]");
    const mobileQuery = window.matchMedia("(max-width: 54rem)");

    if (!sidebar || !openButton || !workspace) {
        return;
    }

    const focusableSelector =
        'a[href]:not([aria-disabled="true"]), button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

    const setMobileState = () => {
        if (mobileQuery.matches) {
            const isOpen = document.documentElement.classList.contains("sidebar-open");
            sidebar.setAttribute("aria-hidden", isOpen ? "false" : "true");
            sidebar.inert = !isOpen;
        } else {
            document.documentElement.classList.remove("sidebar-open");
            sidebar.removeAttribute("aria-hidden");
            sidebar.inert = false;
            openButton.setAttribute("aria-expanded", "false");
        }
    };

    const openSidebar = () => {
        document.documentElement.classList.add("sidebar-open");
        sidebar.setAttribute("aria-hidden", "false");
        sidebar.inert = false;
        openButton.setAttribute("aria-expanded", "true");
        sidebar.querySelector("[data-sidebar-close]")?.focus();
    };

    const closeSidebar = () => {
        document.documentElement.classList.remove("sidebar-open");
        openButton.setAttribute("aria-expanded", "false");
        if (mobileQuery.matches) {
            sidebar.setAttribute("aria-hidden", "true");
            sidebar.inert = true;
        }
        openButton.focus();
    };

    openButton.addEventListener("click", openSidebar);
    closeButtons.forEach((button) => button.addEventListener("click", closeSidebar));
    sidebar.querySelectorAll("nav a:not([aria-disabled='true'])").forEach((link) => {
        link.addEventListener("click", () => {
            if (mobileQuery.matches) {
                closeSidebar();
            }
        });
    });
    sidebar.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            closeSidebar();
            return;
        }
        if (event.key !== "Tab" || !mobileQuery.matches || !document.documentElement.classList.contains("sidebar-open")) {
            return;
        }
        const focusable = Array.from(sidebar.querySelectorAll(focusableSelector));
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last?.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first?.focus();
        }
    });
    mobileQuery.addEventListener("change", setMobileState);
    setMobileState();
})();
