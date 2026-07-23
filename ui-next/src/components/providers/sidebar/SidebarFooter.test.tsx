import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { Provider as ThemeProvider } from "theme/material/provider";
import { SidebarFooter } from "./SidebarFooter";

const mockIsEnabled = vi.hoisted(() => vi.fn());

vi.mock("utils", () => ({
  FEATURES: {
    PLAYGROUND: "PLAYGROUND",
    STATIC_RESOURCES_PROTECTION: "STATIC_RESOURCES_PROTECTION",
  },
  featureFlags: {
    isEnabled: (...args: Parameters<typeof mockIsEnabled>) =>
      mockIsEnabled(...args),
    getValue: vi.fn(),
  },
}));

vi.mock("./SidebarVersionBlock", () => ({
  SidebarVersionBlock: () => <div data-testid="version-block">Version</div>,
}));

vi.mock("images/svg/token.svg", () => ({ default: () => null }));

function renderSidebarFooter(
  props: Partial<Parameters<typeof SidebarFooter>[0]> = {},
) {
  const defaultProps = {
    open: true,
    isAuthenticated: false,
    isMobile: false,
    user: null,
    conductorUser: null,
    logOut: vi.fn(),
    conductorVersion: "1.0.0",
    uiVersion: "1.0.0",
    showCopyAlert: false,
    setShowCopyAlert: vi.fn(),
  };

  return render(
    <ThemeProvider>
      <SidebarFooter {...defaultProps} {...props} />
    </ThemeProvider>,
  );
}

describe("SidebarFooter", () => {
  beforeEach(() => {
    mockIsEnabled.mockReturnValue(false);
  });

  describe("collapsed sidebar (open=false)", () => {
    it("shows sign-out icon when static resources protection is enabled and not authenticated", () => {
      mockIsEnabled.mockImplementation(
        (feature: string) => feature === "STATIC_RESOURCES_PROTECTION",
      );
      renderSidebarFooter({ open: false, isAuthenticated: false });
      expect(
        screen.getByRole("button", { name: /sign out/i }),
      ).toBeInTheDocument();
    });

    it("hides sign-out icon when static resources protection is disabled and not authenticated", () => {
      mockIsEnabled.mockReturnValue(false);
      renderSidebarFooter({ open: false, isAuthenticated: false });
      expect(
        screen.queryByRole("button", { name: /sign out/i }),
      ).not.toBeInTheDocument();
    });

    it("shows sign-out icon when authenticated (enterprise mode)", () => {
      renderSidebarFooter({ open: false, isAuthenticated: true });
      expect(
        screen.getByRole("button", { name: /sign out/i }),
      ).toBeInTheDocument();
    });
  });

  describe("expanded sidebar (open=true)", () => {
    it("shows simplified sign-out button when static resources protection is enabled and not authenticated", () => {
      mockIsEnabled.mockImplementation(
        (feature: string) => feature === "STATIC_RESOURCES_PROTECTION",
      );
      renderSidebarFooter({ open: true, isAuthenticated: false });
      expect(
        screen.getByRole("button", { name: /sign out/i }),
      ).toBeInTheDocument();
      expect(screen.queryByTestId("user-avatar")).not.toBeInTheDocument();
    });

    it("hides sign-out when static resources protection is disabled and not authenticated", () => {
      mockIsEnabled.mockReturnValue(false);
      renderSidebarFooter({ open: true, isAuthenticated: false });
      expect(
        screen.queryByRole("button", { name: /sign out/i }),
      ).not.toBeInTheDocument();
    });

    it("shows full user info block when authenticated (enterprise mode)", () => {
      renderSidebarFooter({
        open: true,
        isAuthenticated: true,
        user: { given_name: "Test User", picture: "" } as any,
        conductorUser: { id: "user-123" },
      });
      expect(screen.getByTestId("user-avatar")).toBeInTheDocument();
      expect(screen.getByText("Test User")).toBeInTheDocument();
      expect(screen.getByText("user-123")).toBeInTheDocument();
    });

    it("calls logOut when simplified sign-out button is clicked", () => {
      const logOut = vi.fn();
      mockIsEnabled.mockImplementation(
        (feature: string) => feature === "STATIC_RESOURCES_PROTECTION",
      );
      renderSidebarFooter({ open: true, isAuthenticated: false, logOut });
      const button = screen.getByRole("button", { name: /sign out/i });
      fireEvent.click(button);
      expect(logOut).toHaveBeenCalledOnce();
    });
  });

  describe("customUserBlock", () => {
    it("short-circuits footer rendering when provided", () => {
      renderSidebarFooter({
        open: true,
        customUserBlock: <div data-testid="custom-block">Custom</div>,
      });
      expect(screen.getByTestId("custom-block")).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: /sign out/i }),
      ).not.toBeInTheDocument();
    });
  });
});
