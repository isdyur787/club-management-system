import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from tools.document_parser import resolve_document_path, parse_document


class DocumentPathTests(unittest.TestCase):
    def test_repository_document_resolves_from_ai_service_directory(self):
        relative = 'docs/knowledge/street-dance-faq.md'
        path = resolve_document_path(relative)
        self.assertTrue(path.is_absolute())
        self.assertTrue(path.is_file())
        self.assertIn('街舞社', parse_document(relative))

    def test_absolute_upload_path_is_preserved(self):
        path = Path(__file__).resolve()
        self.assertEqual(resolve_document_path(str(path)), path)
