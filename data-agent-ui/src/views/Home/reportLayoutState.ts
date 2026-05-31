export const REPORT_PANEL_MIN_WIDTH = 480;
export const REPORT_PANEL_DEFAULT_WIDTH = 640;
export const REPORT_PANEL_MAX_WIDTH = 880;

export const clampReportPanelWidth = (width: number): number => {
  return Math.min(REPORT_PANEL_MAX_WIDTH, Math.max(REPORT_PANEL_MIN_WIDTH, Math.round(width)));
};

export const getReportPanelWidthFromDrag = ({
  viewportWidth,
  pointerClientX,
}: {
  viewportWidth: number;
  pointerClientX: number;
}): number => {
  return clampReportPanelWidth(viewportWidth - pointerClientX);
};
