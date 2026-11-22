# ColorPicker

ColorPicker is a client-side mod for Minecraft 1.7.10 that finds blocks by color and builds block gradients.

## Features

| Feature | Description |
| --- | --- |
| Color Picker | Pick a color with RGB, HSB, hex, or the eyedropper. |
| Block matching | Find up to 100 matches among vanilla and modded blocks using the active resource pack and an adjustable tolerance. |
| Block filters | Narrow matches by shape, collision, opacity, texture uniformity, contrast, pattern, tile entities, or falling behaviour. |
| Gradient Helper | Fill gaps between input blocks, cycle through alternatives, and apply the gradient to your hotbar in Creative mode. |
| NEI integration | Sample blocks or use them as gradient inputs directly from Not Enough Items. |

## When block indexing runs

In `config/colorpicker.cfg` (or `config/colourpicker.cfg` on older installs), set `B:indexOnWorldJoin` in the `general` section and restart Minecraft.

- `true` (default) starts indexing in the background after you join a world.
- `false` waits until you open ColorPicker or Gradient Helper. Closing the tool pauses the submission of new indexing work.

## License

[MIT License](LICENSE)
