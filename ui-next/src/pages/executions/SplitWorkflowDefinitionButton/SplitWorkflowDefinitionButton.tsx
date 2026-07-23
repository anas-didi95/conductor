import { usePushHistory } from "utils/hooks/usePushHistory";
import SplitButton from "components/ui/buttons/ConductorSplitButton";
import AddIcon from "components/icons/AddIcon";
import { WORKFLOW_DEFINITION_URL } from "utils/constants/route";
import { useAuth } from "components/features/auth";
import { useMemo, useState } from "react";
import { ImportBPNFileDialog } from "./ImportBPNFileDialog";
import { featureFlags, FEATURES } from "utils/flags";
import { removeCopyFromStorage } from "pages/runWorkflow/runWorkflowUtils";
import { usePermissions } from "hooks/usePermissions";

const SplitWorkflowDefinitionButton = ({
  disabled,
}: {
  disabled?: boolean;
}) => {
  const pushHistory = usePushHistory();
  const { isTrialExpired } = useAuth();
  const { canWrite } = usePermissions();
  const [openBPMNModal, setOpenBPMNModal] = useState(false);
  const isImportBpmnHidden = featureFlags.isEnabled(FEATURES.HIDE_IMPORT_BPMN);

  const clearNewWorkflowStorage = () => {
    removeCopyFromStorage({
      workflowName: "newWorkflowDef",
      currentVersion: undefined,
      isNewWorkflow: true,
    });
  };

  const splitButtonOptions = useMemo(() => {
    const options = [
      {
        label: "New Workflow",
        onClick: () => {
          clearNewWorkflowStorage();
          pushHistory(WORKFLOW_DEFINITION_URL.NEW);
        },
      },
    ];
    if (!isImportBpmnHidden) {
      options.push({
        label: "Import BPMN",
        onClick: () => setOpenBPMNModal(true),
      });
    }
    return options;
  }, [isImportBpmnHidden, pushHistory]);

  if (!canWrite) {
    return null;
  }

  return (
    <>
      <SplitButton
        startIcon={<AddIcon />}
        options={splitButtonOptions}
        primaryOnClick={() => {
          clearNewWorkflowStorage();
          pushHistory(WORKFLOW_DEFINITION_URL.NEW);
        }}
        disabled={disabled || isTrialExpired}
      >
        Define workflow
      </SplitButton>
      <ImportBPNFileDialog
        open={openBPMNModal}
        onClose={() => setOpenBPMNModal(false)}
      />
    </>
  );
};

export default SplitWorkflowDefinitionButton;
