# Third-party research notes

No source code from repositories with incompatible or unclear licensing is copied into this project.

## LG webOS

### madmicio/LG-WebOS-Remote-Control
https://github.com/madmicio/LG-WebOS-Remote-Control

Useful as a UI/feature reference for a rich LG remote. The repository did not expose a standard license in the repository metadata during review, so code was not copied.

### klattimer/LGWebOSRemote
https://github.com/klattimer/LGWebOSRemote

MIT licensed. Useful reference for the webOS registration / SSAP model.

## Universal smart TV discovery/network

### mazen-salah/Smart-TV-Remote-Control
https://github.com/mazen-salah/Smart-TV-Remote-Control

MIT licensed. Its public description documents Samsung Tizen + LG webOS, UPnP/mDNS discovery, pairing, reconnect, and Wake-on-LAN concepts.

## Samsung Tizen

### xchwarze/samsung-tv-ws-api
https://github.com/xchwarze/samsung-tv-ws-api

LGPL-3.0. Used as a protocol/reference source for Samsung WebSocket remote-control concepts. This Android project implements its own client.

## IR engines/import concepts

### iodn/android-ir-blaster
https://github.com/iodn/android-ir-blaster

GPL-3.0. Public project supports custom remotes and formats such as hex/raw/Flipper/LIRC/IRPLUS. This project uses only the general idea of extensible IR profiles and implements its own encoder.

### probonopd/irdb
https://github.com/probonopd/irdb

Large crowd-sourced IR code database. Its custom license requires:
1. informing the irdb project before using the database in a product,
2. including a specific notice, and
3. making up to three licensed product copies/units available on request.

For that reason this project does NOT bundle or access irdb by default.
