#!/usr/bin/env python3
"""Extract text from Genie docs and headers for Phase 2-3."""
import os, re, sys

def html_to_text(html_path, max_len=15000):
    try:
        with open(html_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()
        content = re.sub(r'<script[^>]*>.*?</script>', '', content, flags=re.DOTALL|re.IGNORECASE)
        content = re.sub(r'<style[^>]*>.*?</style>', '', content, flags=re.DOTALL|re.IGNORECASE)
        content = re.sub(r'<[^>]+>', ' ', content)
        content = re.sub(r'\s+', ' ', content).strip()
        return content[:max_len]
    except Exception as e:
        return f'ERROR: {e}'

def read_file_text(path, max_len=15000):
    try:
        with open(path, 'r', encoding='utf-8', errors='ignore') as f:
            return f.read()[:max_len]
    except Exception as e:
        return f'ERROR: {e}'

# Phase 2: Genie Library docs
phase2_docs = [
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/introduction/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/library/engine/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/library/dialog/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/library/sampler/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/library/tokenizer/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/library/pipeline/index.html',
]

# Phase 3: Android HTP tutorials (most critical)
phase3_docs = [
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/tutorials/dialog/llama-2-7b/htp/android/basic/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/tutorials/dialog/llama-2-7b/htp/android/basic/no_lora/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/tutorials/dialog/llama-2-7b/htp/android/basic/lora/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/tutorials/dialog/llama-2-7b/htp/android/spd/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/tutorials/dialog/llama-2-7b/htp/android/ssd-q1/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/tutorials/dialog/llama-3-3b/htp/android/eaglet/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/tutorials/dialog/engine_sharing/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/tutorials/dialog/kvshare/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/tutorials/dialog/batchQuery/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/tutorials/dialog/lora/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/tutorials/dialog/update_sampler/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/tutorials/dialog/stopseq/index.html',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/docs/QAIRT-Docs/Genie/general/tutorials/dialog/signal/index.html',
]

# Genie C API headers
genie_headers = [
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/include/Genie/GenieCommon.h',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/include/Genie/GenieDialog.h',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/include/Genie/GenieEngine.h',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/include/Genie/GenieSampler.h',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/include/Genie/GenieTokenizer.h',
    '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/include/Genie/GeniePipeline.h',
]

# QNN C API header
qnn_header = '/home/diizzy/Projects/qnn-sdk/qairt/2.46.0.260424/include/Qnn/QnnCommon.h'

output = []

# Phase 2
output.append("=== PHASE 2: GENIE LIBRARY ===")
for doc in phase2_docs:
    name = os.path.basename(os.path.dirname(doc))
    text = html_to_text(doc, 12000)
    output.append(f'\n--- {name} ---\n{text}\n')

# Phase 3
output.append("\n=== PHASE 3: GENIE ANDROID HTP TUTORIALS ===")
for doc in phase3_docs:
    name = os.path.basename(doc.replace('.html', ''))
    text = html_to_text(doc, 12000)
    output.append(f'\n--- {name} ---\n{text}\n')

# Genie headers
output.append("\n=== PHASE 2b: GENIE C API HEADERS ===")
for hdr in genie_headers:
    name = os.path.basename(hdr)
    text = read_file_text(hdr, 15000)
    output.append(f'\n--- {name} ---\n{text}\n')

# QNN header
output.append("\n=== PHASE 1b: QNN C API HEADER ===")
text = read_file_text(qnn_header, 20000)
output.append(f'\n--- QnnCommon.h ---\n{text}\n')

print('\n\n'.join(output))
