---
name: hyperframe
description: Generate short video compositions using HTML templates with animation data attributes.
---

# Hyperframe Video Composition Spec

Create video compositions by outputting an HTML document. The system automatically injects all scripts, fonts, viewport tags, and animation timelines. You only write structural tags and style the layout.

## Template

Start from this exact HTML template. Replace **only** the content inside the `<body>`:

```html
<!DOCTYPE html>
<html>
<head>
  <title>REPLACE WITH VIDEO TITLE</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #030206; color: #fff; overflow: hidden; }
    #root {
      position: relative;
      width: 1080px;
      height: 1920px;
      overflow: hidden;
      background: radial-gradient(circle at 50% 30%, #0d0b21 0%, #030206 70%);
    }
    .clip { position: absolute; opacity: 0; }
  </style>
</head>
<body>
  <div id="root" data-composition-id="REPLACE-WITH-ID" data-width="1080" data-height="1920" data-duration="5">
    <!-- Add your elements here -->
  </div>
</body>
</html>
```

## Strict Constraints (DO NOT DEVIATE)

1. **Root Dimensions:** 
   - `data-width` is always `1080`
   - `data-height` is always `1920`
   - `data-duration` is always `5` (seconds)
2. **Never include `<script>` or `<link>` tags:** GSAP and fonts are injected dynamically by the system.
3. **No custom CSS animations:** Do NOT write `@keyframes` or custom animation styles. The system parses your `data-anim` attributes to animate the elements.
4. **All elements must reside inside `#root`:** Never add text or content outside `<div id="root">`.
5. **No Flexbox on Body:** Do NOT style `body` with flexbox centering or `height: 100vh`. The scaling helper handles responsive centering automatically.

## Element Animating & Layering

Every visible element inside `#root` requires these attributes to animate correctly:

* `class="clip"` (Required to hide/show during track play)
* `data-start`: Time in seconds when the element appears (e.g. `0.5`, `1.5`, max `5.0`)
* `data-duration`: The active display length in seconds
* `data-track-index`: Integer layering index (higher numbers render on top of lower numbers)
* `data-anim`: Entrance animation type. Available options:
  - `fade-up`: Fades in while sliding upward (best for titles)
  - `fade-down`: Fades in while sliding downward
  - `slide-left`: Slides in from the right edge
  - `slide-right`: Slides in from the left edge
  - `scale-in`: Scales up from center
  - `none`: Appears instantly

## Styling Elements

Use inline styles for positioning and colors relative to the fixed 1080x1920 canvas:
- **Fonts:** Use `font-family: Orbitron` for titles, and `font-family: 'Share Tech Mono'` for body text.
- **Sizes:** Use large sizes matching the high resolution (e.g., `72px` for titles, `36px` for normal text).
- **Colors:** Use `#00f2fe` (cyan), `#a855f7` (purple), and `#ffffff` (white).
- **Positioning:** Use absolute coordinates (e.g., `top: 600px; left: 100px; width: 80%;`).

## Output

When ready, call the `saveHyperframe` tool with your complete HTML code and a filename ending in `.html`.
