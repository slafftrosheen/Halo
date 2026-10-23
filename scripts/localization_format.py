"""Formatting invariants shared by catalogue generation and native resource checks."""
from collections import Counter
import re


# Indexed arguments may move with sentence order; unindexed arguments may not.
# Ordinary prose such as "100% of" is not a formatting placeholder.
FORMAT_ARGUMENT = re.compile(r"(?<!%)%(?:(?:[1-9]\d*\$)[-#+ 0,(<]*\d*(?:\.\d+)?|[-#+0,(<]*\d*(?:\.\d+)?)[a-zA-Z]")


def format_signature(text):
    tokens = FORMAT_ARGUMENT.findall(text)
    return Counter(tokens), [token for token in tokens if "$" not in token]
