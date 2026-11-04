import type * as React from "react";

type ModelViewerAttrs = React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement> & {
  src?: string;
  alt?: string;
  "camera-controls"?: boolean;
  "camera-orbit"?: string;
  "shadow-intensity"?: string;
  "interaction-prompt"?: string;
  "touch-action"?: string;
  "environment-image"?: string;
  exposure?: string;
};

declare module "react" {
  namespace JSX {
    interface IntrinsicElements {
      "model-viewer": ModelViewerAttrs;
    }
  }
}
