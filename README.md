# VoidRP Client Info

Client-only NeoForge mod (Minecraft 26.2) for the VoidRP launcher packs whose servers run
plugins (Origins). Once the server announces the `voidrp_client_info:report` channel — VoidRP
Guard does — it sends the client's mod list (`modid:version`) and an injection check (Java
agents, native libraries named like known cheats). The payload is optional: the client joins any
server, and nothing is sent where nobody listens.

Format (read by Guard's `ClientWatch`): VarInt format (1), then three lists — mods, agents,
libraries — each a VarInt count of VarInt-length UTF-8 strings, then one boolean byte.

```bash
./gradlew jar   # build/libs/voidrp_client_info-1.0.0+mc26.2.jar
```
