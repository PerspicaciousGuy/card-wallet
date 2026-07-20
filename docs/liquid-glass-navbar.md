# Building Liquid-Glass UI in Jetpack Compose

This is a complete walkthrough of how Card Wallet's liquid-glass UI works — a
floating navbar with real refraction and dispersion, a draggable selection pill
with squash-and-stretch, an accent color that appears *through* the glass, plus
glass **buttons** and a glass **toggle**. It is written so that someone who has
never touched the underlying library can rebuild the whole thing.

The effect is built on [**AndroidLiquidGlass**](https://github.com/Kyant0/AndroidLiquidGlass)
by [Kyant](https://github.com/Kyant0) (Apache-2.0) — the `backdrop` library does the
rendering; the navbar component is adapted from its catalog app (see `NOTICE`).

---

## 1. What you get

```
╭──────────────────────────────╮   ╭────╮
│   Cards          Settings    │   │ +  │
╰──────────────────────────────╯   ╰────╯
```

- The **capsule bar** is real glass: the page content behind it is refracted at the
  rim, blurred, and saturated — not a translucent gray rectangle.
- A **selection pill** slides between tabs. You can tap a tab *or drag the pill*;
  while dragging it squashes and stretches like liquid, and the tab it passes over
  turns the accent color *inside* the pill — as if the glass reveals a hidden layer
  (because it literally does; see §5).
- A **detached circular glass button** sits beside the capsule, tinted with the
  accent, refracting the same background.

## 2. Requirements and setup

**Android 13+ (API 33) is a hard floor.** The refraction is an AGSL
`RuntimeShader`; below 13 the `lens()` effect silently does nothing (the library
no-ops it), so decide up front whether you ship a blur-only fallback or set
`minSdk 33` like we did.

```kotlin
// gradle/libs.versions.toml
backdrop = "2.0.0"          // io.github.kyant0:backdrop
kyantShapes = "1.2.0"       // io.github.kyant0:shapes  (Capsule)
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
}
```

Two build gotchas we hit:

1. `backdrop` 2.0.0 is a **Compose Multiplatform** library. Its dependencies are the
   `org.jetbrains.compose.*` artifacts. The cleanest consumption path is applying
   the JetBrains Compose plugin (`org.jetbrains.compose`) and using its
   `compose.foundation` / `compose.material3` accessors — on Android they resolve
   to normal AndroidX Compose underneath.
2. Its AAR metadata **demands `compileSdk 37`** (and `shapes` demands 36). Your
   `compileSdk` must be at least that, or the build fails at the metadata check.

## 3. Core concepts: Backdrop, capture, consume

The library's model has two halves:

- **Capture.** A `LayerBackdrop` is a GPU layer holding a live copy of some
  composable's content. You create one with `rememberLayerBackdrop { ... }` and
  attach it to the content you want *behind* the glass with
  `Modifier.layerBackdrop(backdrop)`. Every frame, that content is recorded into
  the layer.
- **Consume.** Any other composable can draw that captured layer *through effects*
  with `Modifier.drawBackdrop(backdrop, shape, effects, ...)`. Because the capture
  is position-aware, the glass shows exactly the pixels that are geometrically
  behind it — scrolling content scrolls under the glass in real time.

```kotlin
Box(Modifier.fillMaxSize()) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)   // ← critical, see below
        drawContent()
    }

    PageContent(Modifier.fillMaxSize().layerBackdrop(backdrop))

    GlassBar(backdrop, Modifier.align(Alignment.BottomCenter))
}
```

**Why the `drawRect(backgroundColor)` first:** the capture only contains what the
attached composable draws. If your page has transparent regions (padding around a
column, area below the last list item), the glass would refract *transparent
pixels* and look broken. Painting the background color into the layer first
guarantees the glass always has real pixels to bend.

### The effects pipeline

Inside `drawBackdrop`, effects apply in a fixed, meaningful order —
**color filter → blur → lens**:

```kotlin
Modifier.drawBackdrop(
    backdrop = backdrop,
    shape = { Capsule() },              // must be a CornerBasedShape for lens()
    effects = {
        vibrancy()                      // saturation ×1.5 — makes colors pop
        blur(8f.dp.toPx())              // frosted-glass smoothing
        lens(24f.dp.toPx(), 24f.dp.toPx())  // THE refraction
    },
    onDrawSurface = { drawRect(containerColor) },  // readability scrim on top
)
```

- `lens(refractionHeight, refractionAmount, depthEffect, chromaticAberration)` is
  the star: an AGSL shader that bends the backdrop at the shape's rim like thick
  glass. `chromaticAberration = true` splits color fringes at the edge like a real
  prism. It requires the shape to be corner-based (`Capsule`, `RoundedCornerShape`).
- `onDrawSurface` draws *between* the refracted backdrop and your content — a
  low-alpha scrim (`0.4f` in our bar) buys text readability. **The single most
  common mistake is making this too opaque**: at ~0.5+ the refraction becomes
  invisible and you've built an ordinary translucent bar. If you can't see the
  effect, lower the scrim before touching anything else — and check there is
  actually detailed content *under* the bar; a lens over a flat gradient has
  nothing visible to bend.

`Capsule()` comes from the `shapes` library — a G2-continuous capsule whose
corners blend smoother than `CircleShape` at glass rims.

## 4. A minimal glass bar

With just the above you already have a credible glass bar:

```kotlin
@Composable
fun MinimalGlassBar(backdrop: Backdrop, modifier: Modifier = Modifier) {
    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx())
                },
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.4f)) },
            )
            .height(64.dp)
            .fillMaxWidth(),
    ) { /* tabs */ }
}
```

Everything after this point is what turns "glass bar" into "liquid glass navbar".

## 5. The three-layer sandwich (the accent-reveal trick)

This is the part nobody guesses from screenshots. The component
(`ui/glass/LiquidBottomTabs.kt`) stacks **three siblings** in one
`BoxWithConstraints`:

```
Layer 3   the selection pill
          drawBackdrop(backdrop = rememberCombinedBackdrop(pageBackdrop, tabsBackdrop))

Layer 2   an INVISIBLE duplicate of the tab row
          alpha(0f) + tinted accent via graphicsLayer(colorFilter = tint(accent))
          + layerBackdrop(tabsBackdrop)     ← recorded, never displayed

Layer 1   the base glass bar (glass over the page, tabs in normal color)
```

Read layer 2 carefully: the same tab content is composed a second time, tinted
fully accent-colored, made invisible with `alpha(0f)`, and **captured into its own
backdrop** (`tabsBackdrop`). It costs one extra composition of the row but never
appears on screen.

Layer 3 — the pill — consumes `rememberCombinedBackdrop(pageBackdrop, tabsBackdrop)`.
Its backdrop is therefore *the page plus the hidden accent-colored icons*. As the
pill slides over a tab, the accent version of that icon becomes visible **through
the pill**, refracted by its lens like everything else. The color isn't painted on;
it's a real layer being revealed through glass. That's why it looks physical.

Two supporting details:

- The pill's surface is a *neutral* scrim (`Black/White at 0.1f`) that fades out
  while pressed — all color comes from the combined backdrop. (An earlier attempt
  tinted the pill surface directly with `BlendMode.Hue`; over colorful content it
  flooded the pill with flat paint and killed the refraction. Reveal > tint.)
- The pill's `fillMaxWidth(1f / tabsCount)` + `translationX = value × tabWidth`
  keeps slot geometry uniform, which the drag math below relies on.

## 6. Why the pill feels liquid: the motion system

Three pieces, all in `ui/glass/`:

**`DampedDragAnimation`** owns the pill's position as a spring-animated `Float`
(0..tabs-1), plus press progress and a velocity estimate. The signature moves:

- *Squash and stretch from velocity* — inside the pill's `layerBlock`:

  ```kotlin
  layerBlock = {
      scaleX = dampedDragAnimation.scaleX
      scaleY = dampedDragAnimation.scaleY
      val velocity = dampedDragAnimation.velocity / 10f
      scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
      scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
  }
  ```

  Fast drags stretch the pill along the motion axis and thin it vertically —
  the "liquid" illusion.

- **`layerBlock`, not `Modifier.graphicsLayer`.** This matters: `layerBlock`
  scales the pill's *content and surface* but the library inverse-transforms the
  backdrop sampling, so the refracted image underneath does NOT scale with the
  pill. Put the same scale in a plain `graphicsLayer` and the world stretches with
  the pill — instantly fake-looking.

- *Effects bloom on press* — the pill's `lens`, `Highlight`, `Shadow` and
  `InnerShadow` are all multiplied by `pressProgress`. At rest the pill is nearly
  flat; refraction blooms under your finger and relaxes on release.

**`InteractiveHighlight`** adds a specular glow that tracks the touch point — a
small AGSL radial shader drawn in `BlendMode.Plus` (with a flat-scrim fallback
below API 33).

**Rubber-banding** — dragging past the last tab shifts the whole bar a few dp with
an eased fraction of the overshoot, then springs back.

**A bug worth knowing about:** we auto-fire nothing while fingers are down. More
generally — any layout that *moves* between states will eat taps. Our first lock
screen shifted its PIN pad when an error message appeared and automation (and
humans) started missing keys; the fix is fixed-height slots for anything that
appears/disappears. The same principle applies to the bar: its geometry is
constant across all states.

## 7. The detached glass action button

Add-card is **not** a third tab slot. The capsule holds tabs only, and a separate
circular glass button sits beside it in a plain `Row`:

```kotlin
Row(Modifier.height(64.dp)) {
    LiquidBottomTabs(..., modifier = Modifier.weight(1f)) { /* tabs */ }
    Spacer(Modifier.width(8.dp))
    GlassIconButton(
        onClick = onAddCard,
        backdrop = backdrop,          // the SAME page backdrop
        tint = accentColor,
        contentDescription = "Add card",
        modifier = Modifier.fillMaxHeight().width(64.dp),
    ) { PlusIcon() }
}
```

`GlassIconButton` (`ui/glass/GlassIconButton.kt`) is a tiny `drawBackdrop` circle
using the library's tinted-glass recipe on its surface:

```kotlin
onDrawSurface = {
    drawRect(tint, blendMode = BlendMode.Hue)   // adapt backdrop hue to the tint
    drawRect(tint.copy(alpha = 0.75f))          // then a strong tint layer
}
```

Here (unlike the pill) a strong tint is *correct* — an action button is supposed
to read as a solid accent object made of glass.

Why detached instead of a center slot in the bar? Scalability: a capsule that
holds only tabs gains a third or fourth tab by just adding slots. A center action
would permanently split the bar 2+2 and force pill-geometry surgery. The detached
button also needs zero changes to the vendored pill math.

## 7b. Glass buttons

`ui/glass/LiquidButton.kt` is a faithful port of the catalog's `LiquidButton`,
and it is where most of the "feels alive" comes from. Three behaviours stack:

```kotlin
layerBlock = {
    val progress = interactiveHighlight.pressProgress
    val scale = lerp(1f, 1f + 4.dp.toPx() / height, progress)   // 1. press lift

    // 2. finger-follow, bounded by tanh so it leans but never runs away
    val maxOffset = size.minDimension
    val offset = interactiveHighlight.offset
    translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
    translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)

    // 3. directional stretch along the drag axis
    val offsetAngle = atan2(offset.y, offset.x)
    scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) * ...
    scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) * ...
}
```

The `tanh` is the elegant bit: raw translation would let the button slide away
under a long drag, while `tanh` asymptotically approaches a limit — so it leans
toward your finger and stops, exactly like something held by a spring.

**Tint here is the opposite call from the navbar pill.** A button *should* read
as a solid accent object, so the strong recipe is right:

```kotlin
onDrawSurface = {
    if (tint.isSpecified) {
        drawRect(tint, blendMode = BlendMode.Hue)   // hue-adapt the backdrop
        drawRect(tint.copy(alpha = 0.75f))          // then commit to the color
    }
}
```

Compare §5: on the *pill* that same recipe destroyed the effect, because a
selection indicator must reveal rather than paint. Same library, opposite
choice, driven by what the component means.

Our `GlassIconButton` (the navbar's **+**) is now just a wrapper over
`LiquidButton` with `height = null` so the navbar row owns its size — it
inherits all the physics for free.

## 7c. The glass toggle

`ui/glass/LiquidToggle.kt` ports the catalog's toggle, with the accent mapped to
our honey token. It is the most intricate component in the app:

- **The thumb's backdrop is combined**: `rememberCombinedBackdrop(page, scaledTrack)`,
  where the track copy is squeezed via `rememberBackdrop(trackBackdrop) { scale(...) }`.
  So the track's color physically bends through the thumb.
- **Blur and lens are inverted by press**: `blur(8.dp * (1f - progress))` and
  `lens(5.dp * progress, ...)`. At rest it's a soft frosted pill; pressed, the
  blur falls away and refraction blooms.
- **It's draggable, not just tappable.** `DampedDragAnimation` (the same class
  driving the navbar pill) tracks a 0..1 fraction; `didDrag` distinguishes a
  slide (settle to the nearest side) from a tap (flip the value).
- Velocity squash-and-stretch, ambient highlight, shadow and inner shadow all
  scale with press progress.

Replaced both Material `Switch`es (biometric unlock, field masking).

## 7d. Security: where glass may and may not go

Rule (plan §3.4): **a backdrop keeps a live GPU copy of whatever it wraps**, so
revealed secrets must never be inside one. That is a structural constraint, not
a style preference, and it shaped the card-detail screen:

```kotlin
// CardDetailScreen — the capture is attached ONLY to the header banner
val headerBackdrop = rememberLayerBackdrop { drawRect(bannerColor); drawContent() }

HeaderBanner(card, headerBackdrop)   // title, type, accent — no secrets
// …field rows with revealed values live OUTSIDE the captured layer…
```

The Back / Edit / Delete buttons refract the *banner*, so the screen gets glass
without any revealed card value ever entering a GPU capture. The lock screen is
safe to capture wholesale for a similar reason: it renders a dot **count**,
never the PIN digits.

When adding glass to a new screen, ask first: *what does this backdrop capture?*
If the answer includes a secret, scope the capture down instead of skipping the
rule.

## 8. Wiring it into an app

Our `features/home/HomeScreen.kt` puts it together:

1. Create the backdrop **once** at the shell level, seeded with the theme
   background color.
2. The tab content area (everything that scrolls) carries `layerBackdrop`.
3. The bar + button row floats at `Alignment.BottomCenter` over it, inside
   `navigationBarsPadding()`.
4. Give lists bottom `contentPadding` (~120dp) so the last item can scroll clear
   of the floating bar.
5. All colors come from theme tokens (`accentColor`, `containerColor` are
   *required* parameters on our component — no hardcoded colors in the glass).

## 9. Security note (if your app shows secrets)

`layerBackdrop` keeps a live GPU copy of whatever it wraps. In a secrets app,
treat that as a data flow: put glass over **chrome and non-sensitive content
only**. In Card Wallet, list tiles show only titles/types, and the card-detail
screen (where values can be revealed) renders **no glass at all** — nothing
samples those pixels.

## 10. Pitfalls checklist

| Symptom | Cause | Fix |
|---|---|---|
| Glass looks like flat translucent gray | `onDrawSurface` scrim too opaque, or nothing detailed under the bar | Lower scrim toward 0.1–0.4; ensure content scrolls under the glass |
| Transparent/garbage pixels at the rim | Backdrop captured transparency | `drawRect(background)` first in `rememberLayerBackdrop { }` |
| Effect entirely absent on a device | Android < 13 (`RuntimeShader` missing) | Accept blur-only, or set `minSdk 33` |
| Build fails on AAR metadata | Library requires `compileSdk 37` | Raise `compileSdk` |
| Refraction scales/warps while pill is pressed | Scale applied via `graphicsLayer` | Move transforms into `drawBackdrop`'s `layerBlock` |
| Pill color looks painted-on | Tinting the pill surface | Use the hidden accent layer + `rememberCombinedBackdrop` (§5) |
| Crash: SIGSEGV in RenderThread | A backdrop drawing itself (capture/consume loop) | Never `layerBackdrop` a node that also draws that same backdrop; use `exportedBackdrop` for glass-on-glass |
| Taps missing controls near the bar | Layout shifting between states | Fixed-height slots; constant bar geometry |

## 11. Files in this repo

| File | Role |
|---|---|
| `ui/glass/LiquidBottomTabs.kt` | The three-layer navbar (adapted from Kyant's catalog) |
| `ui/glass/DampedDragAnimation.kt` | Spring position + press progress + velocity squash/stretch |
| `ui/glass/InteractiveHighlight.kt` | Touch-tracking specular glow (AGSL) |
| `ui/glass/DragGestureInspector.kt` | Press-immediate drag detector |
| `ui/glass/LiquidButton.kt` | Glass button: press lift, tanh finger-follow, directional stretch |
| `ui/glass/LiquidToggle.kt` | Draggable glass switch over a combined track backdrop |
| `ui/glass/GlassIconButton.kt` | The navbar's **+** — a thin `LiquidButton` wrapper |
| `features/home/HomeScreen.kt` | Backdrop creation + bar/button row wiring |

Licensing: the four adapted files and the two libraries are Apache-2.0 from the
AndroidLiquidGlass project — keep the attribution in `NOTICE` if you reuse them.
