package se.spacify.app.roaming.gamepad.local;

// Runs in a Shizuku user service (shell or root), which may open /dev/uhid.
interface IUhidGamepad {
    // Shizuku calls this when the app unbinds with remove = true; the service must exit.
    void destroy() = 16777114;

    // Creates the virtual HID device. Returns null on success, or an error message.
    String create(String name, in byte[] descriptor, int bus, int vendor, int product) = 1;

    // Sends one input report (without the report ID byte).
    oneway void send(int reportId, in byte[] report) = 2;

    // Removes the virtual device.
    void close() = 3;
}
