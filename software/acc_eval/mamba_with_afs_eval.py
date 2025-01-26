import os, sys
# sys.path.append(os.path.abspath("."))
sys.path.append(os.path.abspath("./lm_evaluation_harness/"))

from lm_evaluation_harness.lm_eval.__main__ import cli_evaluate

if __name__ == "__main__":
    cli_evaluate()