"""Validation types for builder v2."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Iterable


class Severity(str, Enum):
    ERROR = "ERROR"
    WARNING = "WARNING"


@dataclass(frozen=True)
class ValidationIssue:
    severity: Severity
    message: str


@dataclass
class ValidationReport:
    issues: list[ValidationIssue] = field(default_factory=list)

    def add_error(self, message: str) -> None:
        self.issues.append(ValidationIssue(Severity.ERROR, message))

    def add_warning(self, message: str) -> None:
        self.issues.append(ValidationIssue(Severity.WARNING, message))

    def errors(self) -> list[ValidationIssue]:
        return [entry for entry in self.issues if entry.severity == Severity.ERROR]

    def warnings(self) -> list[ValidationIssue]:
        return [entry for entry in self.issues if entry.severity == Severity.WARNING]

    def has_errors(self) -> bool:
        return any(entry.severity == Severity.ERROR for entry in self.issues)



def render_report(report: ValidationReport) -> str:
    lines: list[str] = []
    for issue in report.issues:
        lines.append(f"[{issue.severity.value}] {issue.message}")
    return "\n".join(lines)



def validate_pack(entries: Iterable[tuple[str, object]]) -> ValidationReport:
    report = ValidationReport()
    for label, value in entries:
        if value is None:
            report.add_error(f"{label}: entry is None")
    return report
