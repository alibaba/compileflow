# Workbench Design System

Workbench is an operational product surface, so its UI prioritizes scanability, predictable navigation, dense
information, and efficient repeated actions. This guide defines contribution rules; token values remain in code.

## Sources Of Truth

| Concern                                                                  | Source                                                    |
| ------------------------------------------------------------------------ | --------------------------------------------------------- |
| Color, spacing, type, radius, shadow, motion, layout, and z-index tokens | `apps/web/src/shared/styles/variables.css`                |
| Shared surface primitives                                                | `apps/web/src/shared/styles/surfaces.css`                 |
| Ant Design light/dark integration                                        | `apps/web/src/shared/contexts/ThemeContext.tsx`           |
| Reusable page structures                                                 | `apps/web/src/shared/components/page/`                    |
| Designer token bridge                                                    | `apps/web/src/authoring/designer/theme/design-tokens.css` |
| Component-specific styling                                               | Colocated `*.module.css` or designer component CSS        |

Do not copy token values into documentation or component-local variables. Add a semantic token to
[`variables.css`](../../apps/web/src/shared/styles/variables.css) when a value has meaning across multiple components.

## Visual Principles

- Keep application chrome quiet and consistent across Learn, Build, and Operate.
- Use full-width page sections and stable layout regions rather than nested decorative cards.
- Reserve cards for repeated records, metrics, or genuinely framed tools.
- Keep compact panel headings compact; large display type belongs only to true page-level introductions.
- Use semantic status colors in addition to text or icons, never as the only signal.
- Keep borders, shadows, gradients, and animation restrained in operational views.
- Preserve stable dimensions for toolbars, graph controls, counters, and table actions.
- Let content wrap before shrinking text; never allow labels or controls to overlap.

## Components

Use Ant Design or an existing shared component before creating another primitive:

| Need                   | Preferred control                                |
| ---------------------- | ------------------------------------------------ |
| Tool action            | Icon button with an accessible label and tooltip |
| Binary setting         | Switch or checkbox                               |
| Small mode set         | Segmented control                                |
| View selection         | Tabs                                             |
| Bounded number         | InputNumber, slider, or stepper                  |
| Option set             | Select or menu                                   |
| Status/type/level      | `SemanticTag`                                    |
| Page title and context | `PageHeader`                                     |
| Filters                | `FilterBar`                                      |
| Repeated metrics       | `MetricGrid`                                     |
| Framed work area       | `SurfacePanel`                                   |

Use the icon libraries already installed. Do not add hand-drawn SVG controls when a standard icon conveys the action,
and do not render untrusted SVG or HTML through `dangerouslySetInnerHTML`.

## Tokens And Themes

Components consume semantic variables such as backgrounds, text, borders, focus rings, and module accents. They must not
assume that a light-theme color remains readable in dark mode.

`ThemeContext` owns Ant Design's algorithm and applies the `data-theme` attribute used by CSS tokens. Theme behavior
must be tested through that context rather than by mutating document styles from individual components.

## Layout And Responsive Behavior

- Use the shared page width and application shell dimensions.
- Prefer CSS grid/flex constraints and `minmax()` over viewport-dependent font scaling.
- Keep desktop table workflows usable at narrow widths with explicit column priorities or horizontal scrolling.
- Keep the designer canvas as the primary unframed work surface; palettes and properties remain stable tools around it.
- Validate at mobile and desktop viewports when a change affects navigation, panels, modals, or fixed controls.

## Accessibility

- Every interactive element must be keyboard reachable.
- Icon-only controls require an accessible name and visible tooltip.
- Preserve visible focus states using the shared focus token.
- Associate form labels, descriptions, and validation errors with their controls.
- Honor `prefers-reduced-motion` for nonessential motion.
- Verify contrast in both themes; do not infer accessibility from token names alone.

## Change Checklist

1. Reuse an existing component or semantic token where it fits.
2. Add new tokens only for reusable meaning, not one-off values.
3. Check light and dark themes.
4. Check keyboard interaction and focus order.
5. Check narrow and wide layouts for overflow and overlap.
6. Run type-check, lint, focused unit tests, and the relevant Playwright smoke path.

Do not document screenshots, pixel values, or performance claims as permanent contracts unless an automated check keeps
them current.
