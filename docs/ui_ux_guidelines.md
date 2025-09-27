# Sleek Black & Brown Glassmorphic UI Guidelines

These guidelines capture the desired aesthetic and interaction principles for the conversational assistant. Apply them consistently across Flutter widgets to maintain a premium, glassmorphic experience that remains accessible.

## Visual Identity

- **Palette Core**
  - Background: #050505 (near-black) gradient shifting to #1B0B07 for warm brown undertones.
  - Surface glass panels: semi-transparent overlays (`Colors.black.withOpacity(0.35)` to `0.55`) with subtle brown tint (#5A341A at 12–18% opacity).
  - Accent highlights: copper #C27D4F for interactive states (buttons, sliders) with desaturated hover state (#A3673D).
  - Secondary text/icons: warm gray #D0C4BA for high contrast; tertiary captions #8F8175.
- **Lighting & Depth**
  - Use blurred backdrops (sigma 20–24) to create glass layers floating above the dark base.
  - Apply soft inner highlights (linear gradient top-left to bottom-right) to imply translucency without harsh borders.
  - Shadows: dual-layer drop shadow (`Color(0x80000000)` offset (0, 18), blur 45; plus warm rim `Color(0x33C27D4F)` offset (0, 4), blur 12).

## Typography

- Primary font: Urbanist or Inter (semibold for headings, medium for body).
- Body text size 16 sp, line height 1.5; message bubbles can drop to 15 sp for dense content.
- Numerical readouts (token rate, timers) use tabular figures with letter spacing 0.5.

## Layout & Components

- **Glass Panels**: Wrap core panels in `ClipRRect` with 24 px radius, `BackdropFilter` blur, and gradient overlays.
- **Chat Bubbles**: Assistant responses use brown-tinted glass (#5A341A @ 22% opacity) with subtle border (#C27D4F @ 28% opacity). User bubbles lean into near-black glass (#0E0E0E @ 40% opacity).
- **Controls**: Floating bottom bar with pill-shaped buttons; active states use inner glow (boxShadow inset) to mimic illuminated glass.
- **Visualizer**: Blend waveform copper gradient (#C27D4F → #E5B68A) over translucent panel; spectrum bars fade to transparent tips.

## Motion & Feedback

- Micro-interactions capped at 200 ms ease-in-out; prefer `Curves.easeOutCubic` for entrance, `easeInCubic` for exit.
- Provide subtle parallax on scroll (glass layers shifting by 4–8 px) to emphasize depth.
- Haptic taps on primary actions (Android medium impact).

## Accessibility

- Maintain minimum contrast ratio 4.5:1 for body text against glass surfaces by adjusting opacity dynamically.
- Offer `High Contrast` toggle increasing opacity of surfaces to 0.65 and switching text to pure white.
- Provide reduced motion option disabling parallax and transition animations.

## Implementation Notes

- Centralize theming via Riverpod providers to ensure consistency and enable runtime switching between presets (Battery/Balanced/Turbo may influence UI intensity).
- Encapsulate glass surfaces in reusable widgets (e.g., `GlassContainer`) with parameters for tint/opacity.
- Log theme changes and user preference updates using the canonical log schema for traceability.

## Continuous Skepticism (Sherlock Protocol)

- Could glassmorphic overlays degrade performance on low-end devices? Profile blur effects and provide fallback to flat surfaces if GPU usage spikes.
- Any hidden dependencies? Ensure color constants live in a dedicated palette file to prevent duplication.
- Edge cases: verify readability under bright sunlight and when accessibility toggles are enabled.
- If the design feels too dark, experiment with gradient noise overlays to preserve depth without sacrificing contrast.
