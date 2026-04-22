# Third-party attribution

ToolNeuron is free software released under the **GNU General Public License, version 3** (see the `LICENSE` file at the repository root). This notice records the third-party projects whose source code is bundled into this application.

## Termux

The in-app shell and terminal emulator are derived from **Termux** (<https://github.com/termux/termux-app>), specifically the `terminal-emulator`, `terminal-view`, and `termux-shared` Gradle modules as of release **v0.118.3**.

The imported sources live under `termux/` in this repository. Java package names (`com.termux.*`) are preserved unchanged from upstream; only the `TermuxConstants.TERMUX_PACKAGE_NAME` value is rebranded to `com.dark.tool_neuron`.

Termux is primarily licensed under **GPLv3**, with parts of `termux-shared` multi-licensed (MIT, Apache 2.0, GPLv3-only) as recorded in `termux/termux-shared/LICENSE.md`. All upstream license notices are retained verbatim.

Copyright notices from Termux are reproduced by the per-file headers inside `termux/` and by the upstream `LICENSE.md` files that were copied along with the source.

## Bootstrap packages (planned)

The first-launch bootstrap archive is derived from **termux-packages** (<https://github.com/termux/termux-packages>), rebuilt with `$PREFIX` retargeted to `/data/data/com.dark.tool_neuron/files/usr`. Individual packages retain their own upstream licenses (bash: GPLv3+, coreutils: GPLv3+, busybox: GPLv2, apt: GPLv2+, etc.). The complete license tree is reproduced at `$PREFIX/share/doc/*/copyright` on-device.

## Other dependencies

All other third-party libraries are declared in `gradle/libs.versions.toml` and pulled from public Maven repositories. Their licenses apply as published by each upstream. A generated license report is produced at build time under `app/build/reports/licenses/`.

## Contact

For license clarifications or to request source for a particular release, open an issue at the project's public repository.
