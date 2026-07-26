(() => {
    const form = document.querySelector("[data-appointment-booking]");
    if (!form) {
        return;
    }

    const dateInput = form.querySelector("#appointment-date");
    const timeSelect = form.querySelector("#starts-at");
    const status = form.querySelector("#appointment-availability");
    const submitButton = form.querySelector('button[type="submit"]');
    const placeholderOption = timeSelect.querySelector("option:not([data-appointment-slot])");
    const slotOptions = Array.from(timeSelect.querySelectorAll("[data-appointment-slot]"));

    const browserDate = (date) => {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");
        return `${year}-${month}-${day}`;
    };

    const updateDateBounds = () => {
        const now = new Date();
        const futureOptions = slotOptions.filter((option) => new Date(option.dataset.startsAt) > now);
        const availableDates = futureOptions.map((option) => option.dataset.date).sort();
        dateInput.min = browserDate(now);
        dateInput.max = availableDates.at(-1) ?? dateInput.min;

        if (dateInput.value && dateInput.value < dateInput.min) {
            dateInput.value = "";
        }
    };

    const updateTimes = () => {
        const now = new Date();
        const selectedDate = dateInput.value;
        const previousSelection = timeSelect.value;
        let availableCount = 0;

        slotOptions.forEach((option) => {
            const isFuture = new Date(option.dataset.startsAt) > now;
            const isAvailable = Boolean(selectedDate) && option.dataset.date === selectedDate && isFuture;
            option.disabled = !isAvailable;
            option.hidden = !isAvailable;
            if (isAvailable) {
                availableCount += 1;
            }
        });

        timeSelect.disabled = availableCount === 0;
        const previousOption =
            slotOptions.find((option) => option.value === previousSelection && !option.disabled) ?? null;
        timeSelect.value = previousOption?.value ?? "";
        submitButton.disabled = previousOption === null;
        if (!selectedDate) {
            placeholderOption.textContent = "Choose a date first";
            status.textContent = "Select a date to see available times.";
        } else if (availableCount === 0) {
            placeholderOption.textContent = "No times available";
            status.textContent = "There are no future appointment times available on this date.";
        } else {
            placeholderOption.textContent = "Choose a time";
            status.textContent = `${availableCount} future appointment ${availableCount === 1 ? "time is" : "times are"} available on this date.`;
        }
    };

    dateInput.addEventListener("change", () => {
        updateDateBounds();
        updateTimes();
    });

    timeSelect.addEventListener("change", () => {
        const selectedOption = timeSelect.selectedOptions[0];
        const selectedTime = selectedOption?.dataset.startsAt;
        const isFuture = selectedTime && new Date(selectedTime) > new Date();
        submitButton.disabled = !isFuture;
        if (!isFuture && timeSelect.value) {
            timeSelect.value = "";
            status.textContent = "That time has passed. Choose another available time.";
        }
    });

    form.addEventListener("submit", (event) => {
        const selectedOption = timeSelect.selectedOptions[0];
        const selectedTime = selectedOption?.dataset.startsAt;
        const selectionIsValid =
            selectedTime &&
            selectedOption.dataset.date === dateInput.value &&
            new Date(selectedTime) > new Date();
        if (!selectionIsValid) {
            event.preventDefault();
            submitButton.disabled = true;
            status.textContent = "Choose an available future date and time before booking.";
            dateInput.focus();
        }
    });

    updateDateBounds();
    updateTimes();
    window.setInterval(() => {
        updateDateBounds();
        updateTimes();
    }, 60000);
})();
