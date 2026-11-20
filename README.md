# ColorPicker

ColorPicker is a client-side mod for Minecraft 1.7.10 that finds blocks by colour and builds block gradients.

## Features

| Feature | Description |
| --- | --- |
| Colour Picker | Pick a colour with RGB, HSB, hex, or the eyedropper. |
| Block matching | Find up to 100 matches among vanilla and modded blocks using the active resource pack and an adjustable tolerance. |
| Block filters | Narrow matches by shape, collision, opacity, texture uniformity, contrast, pattern, tile entities, or falling behaviour. |
| Gradient Helper | Fill gaps between input blocks, cycle through alternatives, and apply the gradient to your hotbar in Creative mode. |
| NEI integration | Sample blocks or use them as gradient inputs directly from Not Enough Items. |

## Development

Run `./gradlew build` to compile, check, and package the mod.

- `ColorPickerMod` is the Forge entry point and owns the client session.
- `client/colour` contains block matching and gradient generation. Texture sampling and block shape checks live alongside the colour index.
- `client/gui` contains the two screens and NEI hooks. `PickerSettings` saves the shared filters and match tolerance, while `PickerColour` holds the current colour for the client session.

## License

[MIT License](LICENSE)
