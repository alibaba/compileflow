"""Tests for bilingual documentation quality checks."""

from __future__ import annotations

import unittest

from scripts.check_bilingual_parity import avoidable_chinese_spacing_lines


class ChineseTypographyTest(unittest.TestCase):
    def test_detects_spaces_inside_chinese_prose(self) -> None:
        text = "\n".join(
            ("中文 之间", "中文 。", "。 中文", "Runtime 。", "。 Runtime")
        )

        self.assertEqual([1, 2, 3, 4, 5], avoidable_chinese_spacing_lines(text))

    def test_allows_spaces_at_language_and_code_boundaries(self) -> None:
        text = "中文 Runtime 中文\n中文 `Program` 中文"

        self.assertEqual([], avoidable_chinese_spacing_lines(text))

    def test_ignores_fenced_code_but_checks_surrounding_prose(self) -> None:
        text = "\n".join(
            (
                "中文 之前",
                "```java",
                'String message = "中文 内容";',
                "```",
                "~~~text",
                "中文 内容",
                "~~~~",
                "中文 之后",
            )
        )

        self.assertEqual([1, 8], avoidable_chinese_spacing_lines(text))


if __name__ == "__main__":
    unittest.main()
