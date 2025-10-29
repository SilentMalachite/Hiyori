# Application Icons

This directory contains the application icons for Hiyori.

## Files

- `hiyori.icns` - macOS application icon (contains multiple sizes)
- `hiyori.ico` - Windows application icon (contains 16, 32, 48, 64, 128, 256px)

## Current Icon

The current icon is a placeholder featuring a blue gradient background with a white "H" in the center.

## Customizing the Icon

To replace the icon with your own design:

1. Create a high-resolution PNG (1024x1024 recommended) of your icon design
2. For macOS (`.icns`):
   - Create an iconset directory: `mkdir Hiyori.iconset`
   - Generate required sizes using `sips`:
     ```bash
     for size in 16 32 64 128 256 512 1024; do
       sips -z $size $size your_icon.png --out "Hiyori.iconset/icon_${size}x${size}.png"
       if [ $size -ne 1024 ]; then
         size2x=$((size * 2))
         sips -z $size2x $size2x your_icon.png --out "Hiyori.iconset/icon_${size}x${size}@2x.png"
       fi
     done
     ```
   - Convert to icns: `iconutil -c icns Hiyori.iconset -o hiyori.icns`

3. For Windows (`.ico`):
   - Use ImageMagick: `convert your_icon.png -define icon:auto-resize=256,128,64,48,32,16 hiyori.ico`
   - Or use Python PIL:
     ```python
     from PIL import Image
     img = Image.open('your_icon.png')
     sizes = [256, 128, 64, 48, 32, 16]
     images = [img.resize((s, s), Image.Resampling.LANCZOS) for s in sizes]
     images[0].save('hiyori.ico', format='ICO', sizes=[(s,s) for s in sizes], append_images=images[1:])
     ```

## Icon Guidelines

- Use a simple, recognizable design
- Ensure the icon is visible at small sizes (16x16)
- Use colors that work well on both light and dark backgrounds
- Avoid fine details that won't be visible when scaled down
- Test the icon at various sizes before finalizing
