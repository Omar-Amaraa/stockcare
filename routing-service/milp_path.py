"""
Resolves the location of the MILP CARE solver sources.

The solver lives in `milp_care/milp_care_delivery/src` in the repository. Inside the
Docker image it is copied to `/app/milp_src`. This module puts whichever exists on
`sys.path` so `import vrp_milp_pharma` works in both contexts, with no duplicated code.
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent

CANDIDATES = [
    ROOT / "milp_src",                                        # container layout
    ROOT.parent / "milp_care" / "milp_care_delivery" / "src",  # repository layout
]


def resolve_milp_src() -> Path:
    for candidate in CANDIDATES:
        if (candidate / "vrp_milp_pharma.py").exists():
            if str(candidate) not in sys.path:
                sys.path.insert(0, str(candidate))
            return candidate
    raise RuntimeError(
        "MILP CARE sources not found. Looked in:\n  "
        + "\n  ".join(str(c) for c in CANDIDATES)
    )


MILP_SRC = resolve_milp_src()
