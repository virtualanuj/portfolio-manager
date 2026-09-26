import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

describe("test setup", () => {
  it("renders React components with Testing Library matchers", () => {
    render(<p>Portfolio</p>);
    expect(screen.getByText("Portfolio")).toBeInTheDocument();
  });
});
