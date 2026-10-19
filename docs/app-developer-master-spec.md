# Volta Mobile App Developer Master Specification (v4.1)

## Architecture Overview & Integration Guide

This document specifies the core hardware telemetry protocols, fail-safe protection mechanisms, authentication standards, and remote control procedures for the Volta smart power strip ecosystem (`volta.03092017.xyz`).

---

### A. Telemetry & Current Display
1. **Format to 2 Decimals**:
   - Always display outlet current with two decimal places (e.g. `0.43 A` or `0.00 A`).
2. **Never Calculate Current Locally**:
   - The backend already supplies calibrated `current_a` per outlet and aggregate `current_a` for the power strip in `GET /api/live` using real-time grid voltage and hardware sensor offsets.
   - Mobile clients MUST display the server's `current_a` directly and never compute `power_w / voltage_v` locally.
3. **Maintain Uniform Spec Item Ordering**:
   - In each outlet sub-row, the status items must strictly follow this exact 5-point sequence:
     ```
     [ON/OFF] • [POWER W] • [CURRENT A] • [ENERGY kWh] • [TEMP °C]
     ```

---

### B. Hardware Event Codes & Protection Flags
1. **Fail-Safe Circuit Flags**:
   - Each outlet object returned from `GET /api/live` provides:
     - `overload_ok: Boolean` (`true` = safe, `false` = tripped >16A current overload).
     - `overheat_ok: Boolean` (`true` = safe, `false` = thermal trip cutoff).
     - `event_code: String` (`"00"` = Normal, `"01"` = Overload, `"02"` = Overheat, `"04"` = Pushbutton, etc.).
     - `event_desc: String` (Human-readable description).
2. **Visual Warning Tags**:
   - If `overload_ok == false`, the mobile app must prominently display an alert tag in the outlet title: `⚠️ OVERLOAD`.
   - If `overheat_ok == false`, the mobile app must prominently display an alert tag in the outlet title: `⚠️ OVERHEAT`.

---

### C. Remote Reboot Action (`POST /api/reboot`)
1. **Action & Placement**:
   - Include a `🔄 Reboot` action directly on the strip card (and in strip inventory/settings).
2. **Confirmation Dialog Prompt**:
   - Because rebooting causes the physical ESP/MCU to restart, drop Wi-Fi, and re-establish the TCP connection (~5 seconds), the mobile app must present a confirmation dialog:
     > *"Reboot physical strip '<Name>'? It will briefly restart and reconnect."*
3. **Endpoint & Payload**:
   ```http
   POST /api/reboot
   Authorization: Bearer <TOKEN>
   Content-Type: application/json

   {
     "mac": "88D039132171"
   }
   ```
   - Success response: HTTP 200 `{"ok": true, "message": "Reboot command dispatched"}`.

---

### D. SoftAP Default Password Derivation
1. **Deterministic WPA2 Hotspot Password**:
   - When pairing a strip in SoftAP mode, if the SSID is `TONLY_TAP_ABC123` (or `U+TAP_ABC123`), the WPA2 password is deterministically:
     ```
     "LGU_" + suffix_after_underscore.toUpperCase()
     ```
   - Example: For SSID `TONLY_TAP_132171`, the WPA2 password is `LGU_132171`.
   - Example: For SSID `TONLY_TAP_abc123`, the WPA2 password is `LGU_ABC123`.
2. **Client Auto-Fill & Copy**:
   - When asking the user to connect to the strip's hotspot or auto-binding via `WifiNetworkSpecifier`, auto-fill and provide one-tap clipboard copy for this password.

---

### E. Multi-Tenancy & Authorization
1. **Google Identity & Tenant Token**:
   - Mobile authentication uses Google Sign-In with Web Client ID.
   - The ID token is exchanged via `POST /api/auth/google` with `{"credential": "<idToken>"}`.
   - All subsequent requests include `Authorization: Bearer <TOKEN>` and `X-Token: <TOKEN>`.
2. **Tenant Scoping & Unclaiming**:
   - Non-admin users are scoped to their claimed devices.
   - Unclaiming is performed via `POST /api/unclaim` with `{"mac": "<MAC>"}`.
   - Admin accounts can execute `DELETE /api/strip?mac=<MAC>`.
