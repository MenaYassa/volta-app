# Volta ⚡ (Android)

**Local-first controller, real-time dashboard, and electrical telemetry engine for Korean smart power strips (TONLY / LG U+ MTTL-W01) built with Android, Kotlin, and Jetpack Compose.**

No cloud subscriptions. No Korean phone numbers. No vendor lock-in.

---

## Highlights

- **Native Android Jetpack Compose UI**: Fast, responsive Material Design 3 interface with dark/light theme toggle and live status.
- **Direct & Gateway Controller Modes**: Run as an independent local controller communicating directly with strips over LAN (port `10086`), or connect to a remote Volta server gateway via REST API with bearer token authentication.
- **Fast Real-Time Outlet Control**: Control individual outlets (1 to 4) or master switch in real time with individual safety lock protection and immediate socket confirmation.
- **Full Electrical Telemetry**: Monitor live power (W), cumulative energy (kWh), grid voltage (V), current (A), internal temperature (°C), and Wi-Fi signal (dBm).
- **Time-Series Analytics**: High-fidelity custom canvas line charts for power draw, grid voltage fluctuations, and temperature across 1h, 6h, 24h, and 7d intervals.
- **Top Energy Consumers Leaderboard**: Ranked breakdown of highest energy-consuming outlets with estimated cost calculation in custom currency.
- **Schedules & Automated Timers**: Configure daily outlet schedules with timezone support, day-of-week selectors, and automated retry queuing.
- **Automated Provisioning Wizard**: Step-by-step MTTL-W01 pairing tool for flashing home Wi-Fi credentials and dial-in controller IP over AP socket (`192.168.1.1:30300`).
- **Raw Protocol Terminal & Live Socket Monitor**: Direct ASCII command console (`up:getinfo:all`, `up:power_report:1:vol`, etc.) and live stream of socket events.
- **Persistent Local Store**: Built-in SQLite persistence for strips, custom names, schedules, settings, and telemetry history.

---

## Architecture

```
   [ MTTL-W01 Strips ]
            │
            │ Persistent TCP dial-out (Port 10086) / AP (Port 30300)
            ▼
┌────────────────────────────────────────────────────────┐
│  Volta Android App (Kotlin + Jetpack Compose)          │
│                                                        │
│   ui/           (Compose screens: Control, Analytics,  │
│                  Strips, Schedules, Setup, Tools, etc.)│
│   viewmodel/    (VoltaViewModel & reactive state)      │
│   repository/   (VoltaRepository: DB + Network + Sim)  │
│   protocol/     (TonlyProtocol ASCII parser/builder)   │
│   network/      (DirectSocketClient & VoltaApiClient)  │
│   db/           (VoltaDbHelper: SQLite persistence)    │
└────────────────────────────────────────────────────────┘
```

---

## Hardware Compatibility

- **Device**: LG U+ / TONLY MTTL-W01 Smart Power Strip (Korean 4-Outlet + 2 USB).
- **SoC**: Realtek RTL8711AF (ARM Cortex-M3).
- **Firmware Tested**: `0.1.32-1.0.38`, `0.1.50-1.0.60`, `0.1.52-1.0.62`, `0.1.54-1.0.105`.
