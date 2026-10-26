import logging
from docx import Document

logger = logging.getLogger(__name__)

def parse_document(file_path: str) -> str:
    if file_path.endswith('.txt') or file_path.endswith('.md'):
        with open(file_path, 'r', encoding='utf-8') as f:
            return f.read()
    elif file_path.endswith('.docx'):
        doc = Document(file_path)
        paragraphs = [p.text for p in doc.paragraphs if p.text.strip()]
        return "\n\n".join(paragraphs)
    else:
        raise ValueError(f"不支持的文件格式: {file_path}")
