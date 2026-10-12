#!/usr/bin/env python3
"""
Supplementary metrics that SonarQube Cloud does not report directly.

Per Java file it computes:
  - LOC (code lines), comment lines, Comment Density %
  - Approximate cyclomatic complexity (1 + decision points)
  - Halstead n1, n2, N1, N2, Volume, Difficulty, Effort
  - Maintainability Index (classic formula, normalised to 0-100)
And for FineCalculationEngine the custom metric:
  - OFRD  = fine-rule conditional branches per 100 LOC

Usage:  python3 scripts/metrics_extra.py [src/main/java] [--csv out.csv]
Numbers are approximations from a token scan; quote SonarQube Cloud for the official
LOC / cyclomatic / cognitive values and use this script for Halstead, MI and OFRD.
"""
import math
import re
import sys
from pathlib import Path

KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
    "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
    "for", "if", "implements", "import", "instanceof", "int", "interface", "long", "new", "package",
    "private", "protected", "public", "return", "short", "static", "super", "switch", "synchronized",
    "this", "throw", "throws", "try", "void", "volatile", "while", "record", "var",
}
OPERATOR_RE = re.compile(r"\+\+|--|->|::|==|!=|<=|>=|&&|\|\||[-+*/%&|^~!<>=?:;,.(){}\[\]]")
TOKEN_RE = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|[A-Za-z_]\w*|\d[\w.]*|' + OPERATOR_RE.pattern)

# Decision points counted for cyclomatic complexity and for OFRD rule branches.
DECISION_TOKENS = {"if", "for", "while", "case", "catch", "&&", "||", "?"}


def strip_comments(source):
    """Returns (code_without_comments, comment_line_count)."""
    comment_lines = 0
    for m in re.finditer(r"/\*.*?\*/|//[^\n]*", source, flags=re.S):
        comment_lines += m.group(0).count("\n") + 1
    code = re.sub(r"/\*.*?\*/|//[^\n]*", lambda m: "\n" * m.group(0).count("\n"), source, flags=re.S)
    return code, comment_lines


def tokens(code):
    return TOKEN_RE.findall(code)


def analyse(path):
    source = path.read_text(encoding="utf-8")
    code, comment_lines = strip_comments(source)
    loc = sum(1 for line in code.splitlines() if line.strip())
    toks = tokens(code)

    operators, operands = [], []
    for t in toks:
        if t in KEYWORDS or OPERATOR_RE.fullmatch(t):
            operators.append(t)
        else:
            operands.append(t)
    n1, n2 = len(set(operators)), len(set(operands))
    big_n1, big_n2 = len(operators), len(operands)
    vocab, length = n1 + n2, big_n1 + big_n2
    volume = length * math.log2(vocab) if vocab > 1 else 0.0
    difficulty = (n1 / 2.0) * (big_n2 / n2) if n2 else 0.0
    effort = difficulty * volume

    decisions = sum(1 for t in toks if t in DECISION_TOKENS)
    cyclomatic = 1 + decisions
    mi_raw = 171 - 5.2 * math.log(max(volume, 1)) - 0.23 * cyclomatic - 16.2 * math.log(max(loc, 1))
    mi = max(0.0, mi_raw * 100 / 171)

    density = 100.0 * comment_lines / (comment_lines + loc) if (comment_lines + loc) else 0.0
    return {
        "file": path.stem, "loc": loc, "comments": comment_lines, "density": density,
        "cyclomatic": cyclomatic, "decisions": decisions, "n1": n1, "n2": n2, "N1": big_n1, "N2": big_n2,
        "volume": volume, "difficulty": difficulty, "effort": effort, "mi": mi,
    }


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    root = Path(args[0] if args else "src/main/java")
    rows = [analyse(p) for p in sorted(root.rglob("*.java"))]

    header = f"{'Class':24}{'LOC':>6}{'Cmt':>6}{'Cmt%':>7}{'CC~':>6}{'Volume':>10}{'Effort':>12}{'MI':>7}"
    print(header)
    print("-" * len(header))
    for r in rows:
        print(f"{r['file']:24}{r['loc']:>6}{r['comments']:>6}{r['density']:>7.1f}{r['cyclomatic']:>6}"
              f"{r['volume']:>10.1f}{r['effort']:>12.1f}{r['mi']:>7.1f}")
    total_loc = sum(r["loc"] for r in rows)
    total_cmt = sum(r["comments"] for r in rows)
    print("-" * len(header))
    print(f"{'TOTAL':24}{total_loc:>6}{total_cmt:>6}{100.0 * total_cmt / (total_cmt + total_loc):>7.1f}")

    engine = next((r for r in rows if r["file"] == "FineCalculationEngine"), None)
    if engine:
        ofrd = 100.0 * engine["decisions"] / engine["loc"]
        print()
        print("Custom metric OFRD (Overdue Fine Rule Density)")
        print(f"  rule branches in FineCalculationEngine = {engine['decisions']}")
        print(f"  LOC of FineCalculationEngine           = {engine['loc']}")
        print(f"  OFRD = branches / LOC x 100            = {ofrd:.2f} branches per 100 LOC")

    if "--csv" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--csv") + 1])
        keys = list(rows[0].keys())
        out.write_text("\n".join([",".join(keys)] + [",".join(f"{r[k]:.2f}" if isinstance(r[k], float) else str(r[k])
                                                               for k in keys) for r in rows]) + "\n")
        print(f"\nCSV written to {out}")


if __name__ == "__main__":
    main()
