"""Explicit inputs for fixed historical package-delta checks."""
import argparse
from pathlib import Path


def package_inputs(description):
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument('--baseline', required=True, type=Path, help='Historical package before this specific change')
    parser.add_argument('--candidate', required=True, type=Path, help='Historical package after this specific change')
    parser.add_argument('--output', required=True, type=Path, help='Directory for package_report.json')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    return args.baseline, args.candidate, args.output
