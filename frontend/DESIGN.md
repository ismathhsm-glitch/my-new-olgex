# Olgax Invoice design system

The local UI follows the supplied `design/` references: restrained enterprise clarity, navy navigation/actions, and semantic outcomes expressed with both labels and color. The complete interactive specimen page is available at `/design-system`.

## Tokens

| Token | Value |
|---|---|
| Primary / navy | `#142B4D` |
| Sidebar / deep navy | `#0D1D35` |
| Link and focus | `#2458B8` |
| Canvas | `#F4F6F9` |
| Surface | `#FFFFFF` |
| Border | `#DCE3ED` |
| Text | `#172B45` |
| Muted text | `#627187` |
| Success | `#147653` |
| Warning | `#905B0B` |
| Error / destructive | `#B53D3D` |
| Disabled | `#E7EBF1` |
| Active surface | `#EAF0FA` |
| Success / warning / error tints | `#EAF6F0` / `#FFF4DF` / `#FDEEEF` |

Tokens are declared as CSS custom properties in `src/styles/tokens.css` and mirrored in the Tailwind theme in `tailwind.config.js`. Typography uses Inter, with a system fallback. The rhythm is based on 8 px spacing with 4 px fine increments. The desktop sidebar is 232 px; controls are 44–46 px high; cards use a 12 px radius, inputs an 8 px radius, and borders are 1 px.

## Reusable components

`src/components/ui/index.tsx` provides:

- `Button`: primary, secondary, destructive, disabled/unavailable, loading/verifying, and invite treatment.
- `Input`: label, helper text, disabled and error states with `aria-describedby`.
- `Badge` and `MetricCard`: explicit, text-labeled status and label/value/context presentation.
- `DataTable`: horizontally scrollable table wrapper; pages provide real column headers.
- `Dialog`: Radix modal with focus trapping, Escape handling, focus return and an accessible title/description.
- `Callout`, `Feedback`, `EmptyState`, `NavItem`, `Logo`, `SectionHeading`, and `Skeleton`.

Semantic colors are always accompanied by text and icons. Errors and async feedback are announced through alert/status regions. The shell switches to a keyboard-accessible drawer below 768 px. Protected decisions are enforced by the API; role-aware rendering is only a usability layer.
