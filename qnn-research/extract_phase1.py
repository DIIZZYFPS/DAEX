#!/usr/bin/env python3
"""Extract text from QNN SDK HTML docs for Phase 1."""
import os, re

def html_to_text(html_path):
    try:
        with open(html_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
        content = re.sub(r'<script[^>]*>.*?</script>', '', content, flags=re.DOTALL|re.IGNORECASE)
        content = re.sub(r'<style[^>]*>.*?</style>', '', content, flags=re.DOTALL|re.IGNORECASE)
        content = re.sub(r'<[^>]+>', ' ', content)
        content = re.sub(r'\s+', ' ', content).strip()
        return content
    except Exception as e:
        return f'ERROR: {e}'

phase1_docs = [
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/general/overview.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/general/setup.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/general/tutorial.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/general/tutorial/qnn_tutorial_linux_host_linux_target.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/HTP/htp-network-design-recommendations.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/HTP/migration.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/general/htp.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/general/hta.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/general/dsp.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/general/gpu.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/general/cpu.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/general/lpai.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/general/converters.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/general/blockop.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/QNN/general/saver.html',
]

output = []
for doc in phase1_docs:
    name = os.path.basename(doc).replace('.html', '')
    text = html_to_text(doc)
    output.append(f'=== {name} ===\n{text[:8000]}\n')

print('\n\n'.join(output))
