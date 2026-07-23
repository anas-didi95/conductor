import { isFailedTask } from "utils";
import { DropdownButton } from "components";
import { usePermissions } from "../../hooks/usePermissions";

import {
  ArrowCounterClockwise as ReplayIcon,
  ArrowUUpLeft as RestartIcon,
  Asterisk as RestartLatestIcon,
  Pause as PauseIcon,
  Play as ResumeIcon,
  Stop as StopIcon,
  ArrowSquareOut as RerunIcon,
} from "@phosphor-icons/react";

const style = {
  menuIcon: {
    marginRight: "10px",
  },
};

export default function ActionModule({
  execution,
  onRestartExecutionWithLatestDefinitions,
  onRestartExecutionWithCurrentDefinitions,
  onRetryExecutionFromFailed,
  onResumeExecution,
  onTerminateExecution,
  onPauseExecution,
  onRetryResumeSubworkflow,
  rerunExecutionWithLatestDefinitions,
  createSheduleWithLatestDefinitions,
}) {
  const { canExecute } = usePermissions();
  const { workflowDefinition } = execution;

  const { restartable } = workflowDefinition; // MOVE this cond
  const rerunWorkflowOption = {
    label: (
      <>
        <RerunIcon style={style.menuIcon} size="16" />
        Re-run workflow
      </>
    ),
    handler: rerunExecutionWithLatestDefinitions,
  };
  const createScheduleOption = {
    label: (
      <>
        <RerunIcon style={style.menuIcon} size="16" />
        Create Schedule
      </>
    ),
    handler: createSheduleWithLatestDefinitions,
  };

  if (execution.status === "COMPLETED") {
    const options = [];
    if (canExecute) {
      if (restartable) {
        options.push({
          label: (
            <>
              <RestartIcon style={style.menuIcon} size="16" />
              Restart with current definitions
            </>
          ),
          handler: onRestartExecutionWithCurrentDefinitions,
        });

        options.push({
          label: (
            <>
              <RestartLatestIcon style={style.menuIcon} size="16" />
              Restart with latest definitions
            </>
          ),
          handler: onRestartExecutionWithLatestDefinitions,
        });
      }

      options.push(rerunWorkflowOption);
      options.push(createScheduleOption);
    }

    if (options.length === 0) return null;

    return (
      <DropdownButton
        options={options}
        buttonProps={{
          size: "small",
          sx: { fontSize: "9pt" },
          id: "execution-actions-dropdown-btn",
        }}
      >
        Actions
      </DropdownButton>
    );
  } else if (execution.status === "RUNNING") {
    const options = [];
    if (canExecute) {
      options.push({
        label: (
          <>
            <StopIcon
              size="16"
              style={{ ...style.menuIcon, color: "red" }}
            />
            <span style={{ color: "red" }}>Terminate</span>
          </>
        ),
        handler: onTerminateExecution,
      });
      options.push({
        label: (
          <>
            <PauseIcon size="16" style={style.menuIcon} />
            Pause
          </>
        ),
        handler: onPauseExecution,
      });
      options.push(rerunWorkflowOption);
    }
    if (options.length === 0) return null;
    return (
      <DropdownButton
        buttonProps={{
          size: "small",
          sx: { zIndex: 5000, fontSize: "9pt" },
          id: "execution-actions-dropdown-btn",
        }}
        options={options}
      >
        Actions
      </DropdownButton>
    );
  } else if (execution.status === "PAUSED") {
    const options = [];
    if (canExecute) {
      options.push({
        label: (
          <>
            <StopIcon
              size="16"
              style={{ ...style.menuIcon, color: "red" }}
            />
            <span style={{ color: "red" }}>Terminate</span>
          </>
        ),
        handler: onTerminateExecution,
      });
      options.push({
        label: (
          <>
            <ResumeIcon size="16" style={style.menuIcon} />
            Resume
          </>
        ),
        handler: onResumeExecution,
      });
      options.push(rerunWorkflowOption);
    }
    if (options.length === 0) return null;
    return (
      <DropdownButton
        buttonProps={{
          size: "small",
          sx: { zIndex: 5000, fontSize: "9pt" },
          id: "execution-actions-dropdown-btn",
        }}
        options={options}
      >
        Actions
      </DropdownButton>
    );
  } else {
    // FAILED, TIMED_OUT, TERMINATED
    const options = [];

    if (canExecute) {
      if (["FAILED", "TIMED_OUT"].includes(execution.status)) {
        options.push({
          label: (
            <>
              <StopIcon size="16" style={{ ...style.menuIcon, color: "red" }} />
              <span style={{ color: "red" }}>Terminate</span>
            </>
          ),
          handler: onTerminateExecution,
        });
      }

      if (restartable) {
        options.push({
          label: (
            <>
              <RestartIcon style={style.menuIcon} size="16" />
              Restart with current definitions
            </>
          ),
          handler: onRestartExecutionWithCurrentDefinitions,
        });

        options.push({
          label: (
            <>
              <RestartLatestIcon style={style.menuIcon} size="16" />
              Restart with latest definitions
            </>
          ),
          handler: onRestartExecutionWithLatestDefinitions,
        });
      }

      if (
        execution?.tasks?.some(
          (task) => !task.retried && isFailedTask(task.status),
        )
      ) {
        options.push({
          label: (
            <>
              <ReplayIcon style={style.menuIcon} size="16" />
              Retry - from failed task
            </>
          ),
          handler: onRetryExecutionFromFailed,
        });
      }

      options.push(rerunWorkflowOption);

      if (
        (execution.status === "FAILED" || execution.status === "TIMED_OUT") &&
        execution.tasks.find(
          (task) =>
            task.workflowTask.type === "SUB_WORKFLOW" &&
            isFailedTask(task.status),
        )
      ) {
        options.push({
          label: (
            <>
              <ReplayIcon style={style.menuIcon} size="16" />
              Retry - resume subworkflow
            </>
          ),
          handler: onRetryResumeSubworkflow,
        });
      }
    }

    if (options.length === 0) return null;

    return (
      <DropdownButton
        options={options}
        buttonProps={{
          size: "small",
          sx: { fontSize: "9pt" },
          id: "execution-actions-dropdown-btn",
        }}
      >
        Actions
      </DropdownButton>
    );
  }
}
