# TV Engineering Remote Pro

Professional Android remote-control / engineering-console architecture for TVs.

## Control engines

- Android Consumer IR (`ConsumerIrManager`)
- RAW microsecond IR
- Pronto learned format `0000`
- NEC
- Samsung 32-bit pulse-distance
- Sony SIRC 12/15/20
- LG webOS network control foundation
- Samsung Tizen WebSocket remote
- Roku ECP
- Philips JointSPACE
- Sony BRAVIA IRCC
- Generic HTTP profile
- SSDP LAN discovery

## UI

Two top-level modes:
1. **NORMAL** — full remote, Wi-Fi + IR.
2. **SERVICE** — engineer/service remote with explicit unlock and risk controls.

Additional tools:
- Device/profile manager
- Engineering command builder
- Service vault
- IR hardware diagnostics
- LAN discovery
- Network probing
- Event log

## Important technical limitation

There is no single universal service/factory command set for every TV. Hidden/service commands vary by exact brand, chassis, firmware, region, and panel. This project therefore **does not fabricate factory/service sequences**. Verified service commands can be stored per exact model as profile entries.

A phone also needs physical IR-emitter hardware for direct IR. Wi-Fi providers work only when the TV implements the corresponding protocol and pairing/security requirements are satisfied.

## IR payload examples

- `NEC:0x07:0x02`
- `SAMSUNG32:0xE0E040BF`
- `SIRC12:1:21`
- `RAW:38000:9000,4500,560,560,...`
- `PRONTO:0000 006D ...`

## Build

GitHub Actions is included. Push to `main` and download:

`Actions -> Build Android APK -> Artifacts -> TV-Engineering-Remote-Pro-APK`

## Research references

This project was implemented independently after reviewing public projects/protocol implementations, especially:
- madmicio/LG-WebOS-Remote-Control
- mazen-salah/Smart-TV-Remote-Control (MIT)
- klattimer/LGWebOSRemote (MIT)
- xchwarze/samsung-tv-ws-api (LGPL-3.0)
- iodn/android-ir-blaster (GPL-3.0) — concepts/formats only, no code copied
- probonopd/irdb — not bundled; its custom license has additional conditions

See `THIRD_PARTY_RESEARCH.md`.
