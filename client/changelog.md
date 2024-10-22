### Alpha Notice
This version still requires testing, especially for backports.
If you encounter any issues, please report them on Discord: https://discord.gg/uueEqzwCJJ.

Versions 2.0.x and 2.1.x are protocol-compatible,
so there’s no need to worry if the server hasn't been updated to 2.1.x.

### Changes in 2.1.1
- Build for 1.19.3 was reintroduced.
- Soften Minecraft version bounds:
    - 1.20.4 now allows 1.20.2, 1.20.3 and 1.20.4
    - 1.19.2 now allows 1.19, 1.19.1 and 1.19.2
- Updated to 1.21.2. 
- Updated [slib](https://github.com/plasmoapp/mc-slib) to fix crash with EssentialAddons on world join.
- Fixed audio sources causing a high CPU load. [#421](https://github.com/plasmoapp/plasmo-voice/issues/421)
- Attempt to fix "Cannot measure distance between worlds" exception, see [#422](https://github.com/plasmoapp/plasmo-voice/issues/422).