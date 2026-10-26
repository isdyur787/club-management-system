import os
os.environ['HF_HUB_OFFLINE'] = '1'
from store.chroma_store import ChromaVectorStore

s = ChromaVectorStore()
col = s._get_collection('club_1')
data = col.get(include=['documents', 'metadatas'])

print(f"Total chunks: {len(data['ids'])}")
print()
for i, doc_id in enumerate(data['ids']):
    meta = data['metadatas'][i]
    content = data['documents'][i]
    title = meta.get('title', '?')
    doc_type = meta.get('doc_type', '?')
    section_type = meta.get('section_type', '?')
    preview = content[:80].replace('\n', ' ')
    has_garbled = '???' in content or '' in content
    flag = " [GARBLED]" if has_garbled else ""
    print(f"ID: {doc_id}")
    print(f"  title={title}, doc_type={doc_type}, section_type={section_type}{flag}")
    print(f"  preview: {preview}")
    print()
