interface ReportPanelStateInput {
  hasReport: boolean;
  previousContent: string;
  nextContent: string;
  isManuallyCollapsed: boolean;
}

interface ReportPanelState {
  isOpen: boolean;
  isManuallyCollapsed: boolean;
}

export const getNextReportPanelState = ({
  hasReport,
  previousContent,
  nextContent,
  isManuallyCollapsed,
}: ReportPanelStateInput): ReportPanelState => {
  if (!hasReport) {
    return {
      isOpen: false,
      isManuallyCollapsed: false,
    };
  }

  if (isManuallyCollapsed) {
    return {
      isOpen: false,
      isManuallyCollapsed: true,
    };
  }

  if (!previousContent.trim() && nextContent.trim()) {
    return {
      isOpen: true,
      isManuallyCollapsed: false,
    };
  }

  return {
    isOpen: true,
    isManuallyCollapsed: false,
  };
};
