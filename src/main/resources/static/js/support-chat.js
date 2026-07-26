(() => {
    const launcher = document.querySelector("#support-chat-launcher");
    const panel = document.querySelector("#support-chat-panel");
    const closeButton = document.querySelector("#support-chat-close");
    const form = document.querySelector("#support-chat-form");
    const input = document.querySelector("#support-chat-input");
    const log = document.querySelector("#support-chat-log");
    const status = document.querySelector("#support-chat-status");
    const suggestions = Array.from(document.querySelectorAll("[data-chat-suggestion]"));

    if (!launcher || !panel || !closeButton || !form || !input || !log || !status) {
        return;
    }

    const openChat = () => {
        panel.hidden = false;
        launcher.setAttribute("aria-expanded", "true");
        input.focus();
    };

    const closeChat = () => {
        panel.hidden = true;
        launcher.setAttribute("aria-expanded", "false");
        launcher.focus();
    };

    const appendMessage = (role, text) => {
        const wrapper = document.createElement("div");
        wrapper.className = `chat-message chat-message--${role}`;
        const paragraph = document.createElement("p");
        paragraph.textContent = text;
        wrapper.append(paragraph);
        log.append(wrapper);
        log.scrollTop = log.scrollHeight;
    };

    const sendMessage = async (message) => {
        appendMessage("user", message);
        status.textContent = "Support Guide is considering your message.";
        input.disabled = true;
        form.querySelector("button[type='submit']").disabled = true;
        try {
            const response = await window.fetch("/api/chat", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({message}),
            });
            if (!response.ok) {
                throw new Error("The guide could not respond");
            }
            const payload = await response.json();
            appendMessage("assistant", payload.reply);
            status.textContent = "Support Guide replied.";
        } catch (_error) {
            appendMessage(
                "assistant",
                "I could not reach the guidance service. You can still open Support Navigator to compare all sources.",
            );
            status.textContent = "Support Guide is temporarily unavailable.";
        } finally {
            input.disabled = false;
            form.querySelector("button[type='submit']").disabled = false;
            input.focus();
        }
    };

    launcher.addEventListener("click", openChat);
    closeButton.addEventListener("click", closeChat);
    panel.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            closeChat();
        }
    });
    form.addEventListener("submit", (event) => {
        event.preventDefault();
        const message = input.value.trim();
        if (!message) {
            return;
        }
        input.value = "";
        void sendMessage(message);
    });
    suggestions.forEach((button) => {
        button.addEventListener("click", () => {
            input.value = button.dataset.chatSuggestion || "";
            input.focus();
        });
    });
})();

