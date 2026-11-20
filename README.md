# World Border Event — Forge 1.20.1 (prototype)

Target: Minecraft Java 1.20.1, Forge 47.x, Java 17.

## Commands (OP level 2)
- `/wbe set <radius>` — sets the Overworld square radius from center (0,0). Nether radius is automatically Overworld radius / 8.
- `/wbe event start` — starts a 10-second rumble/prelude followed by a 20-second smooth retreat of +500 Overworld blocks (+62.5 Nether blocks).
- `/wbe event restart` — restarts the event timer from the currently configured radius.
- `/wbe event stop` — stops the event and resets the radius to the event's starting radius.
- `/wbe event status` — prints current state and radii.

## Important prototype notes
This first source scaffold implements server-side radius enforcement and the timed, eased radius change. It does **not yet** include the custom striped-wall renderer, fade-out shader, hit detection/ripple effects, synced earthquake camera/audio, persistence, or gun/tag integration. Those are the next client/server implementation stages. The current enforcement is square and dimension-aware; the vanilla World Border is intentionally not used because it is shared across dimensions.

## Build
Use a Forge 1.20.1 MDK or Gradle installation and run:

`gradle build`

The jar will be in `build/libs`. A local build has not been run in this environment (Gradle is unavailable here), so compilation is not yet verified.
