#!/usr/bin/env python3
"""Build the claude.ai artifact variant of the app: the page body without the document skeleton
(the artifact host adds its own) plus the css/js/fonts as supporting files. Usage:
  python3 tools/artifact-page.py <out-dir>"""
import re, sys, shutil, os
root = os.path.join(os.path.dirname(__file__), '..', 'app')
out = sys.argv[1]
os.makedirs(out, exist_ok=True)
html = open(os.path.join(root, 'index.html')).read()
head = re.search(r'<head>(.*)</head>', html, re.S).group(1)
body = re.search(r'<body[^>]*>(.*)</body>', html, re.S).group(1)
keep = [l for l in head.splitlines() if re.search(r'<title>|claude:viewport|rel="stylesheet"', l)]
open(os.path.join(out, 'index.html'), 'w').write('\n'.join(keep) + '\n' + body)
for d in ('css', 'js', 'fonts'):
    shutil.copytree(os.path.join(root, d), os.path.join(out, d), dirs_exist_ok=True)
print('wrote', out)
