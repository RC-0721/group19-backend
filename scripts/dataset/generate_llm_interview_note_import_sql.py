import argparse
import csv
import hashlib
import json
from pathlib import Path


SOURCE_SQL = """INSERT INTO data_source (source_id, source_name, source_type, source_url, license, usage_note) VALUES
  ('source-llm-interview-note', 'LLMs Interview Note', 'GitHub Repository', 'https://github.com/wdndev/llm_interview_note', 'Personal-NonCommercial-Allowed', 'Imported after external review; only for personal and non-commercial teaching use.')
ON DUPLICATE KEY UPDATE source_name=VALUES(source_name), source_type=VALUES(source_type), source_url=VALUES(source_url), license=VALUES(license), usage_note=VALUES(usage_note);
"""


def parse_args():
    parser = argparse.ArgumentParser(description="Generate idempotent SQL for approved llm_interview_note chunks.")
    parser.add_argument("--content-tsv", default="data/processed/llm-interview-note-review/llm_interview_note_review_content.tsv")
    parser.add_argument("--audit-tsv", default="data/processed/llm-interview-note-review/llm_interview_note_review_audit.tsv")
    parser.add_argument("--output-sql", default="data/processed/llm-interview-note-review/llm_interview_note_import.sql")
    parser.add_argument("--course-id", default="course-java-001")
    parser.add_argument("--approved-status", default="通过")
    return parser.parse_args()


def read_tsv(path):
    with Path(path).open("r", encoding="utf-8-sig", newline="") as reader:
        return list(csv.DictReader(reader, dialect="excel-tab"))


def sql(value):
    if value is None or value == "":
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def stable_id(prefix, *parts):
    raw = "|".join(str(part) for part in parts)
    return prefix + hashlib.sha1(raw.encode("utf-8")).hexdigest()[:12]


def text_for(audit, row):
    return " ".join([
        audit.get("target_course", ""),
        audit.get("knowledge_tags", ""),
        audit.get("content_type", ""),
        row.get("source_path", ""),
        row.get("title", ""),
    ])


def chapter_id(audit, row):
    text = text_for(audit, row)
    if any(word in text for word in ["数据库", "MySQL", "Redis", "缓存", "向量库", "检索存储"]):
        return "chapter-db-001"
    if any(word in text for word in ["Java", "工程实践", "代码说明", "MCP", "Agent", "Function Calling", "应用"]):
        return "chapter-java-001"
    return "chapter-cs-001"


def knowledge_id(audit, row):
    text = text_for(audit, row)
    if "Redis" in text or "缓存" in text:
        return "kp-007"
    if "MySQL" in text or "SQL" in text or "数据库" in text:
        return "kp-006"
    if "网络" in text or "HTTP" in text:
        return "kp-008"
    if "并发" in text or "线程" in text:
        return "kp-005"
    if "集合" in text or "容器" in text:
        return "kp-004"
    if "Java" in text or "JVM" in text:
        return "kp-001"
    return None


def material_sql(material_id, row, audit, course_id):
    return f"""INSERT INTO course_material (material_id, course_id, chapter_id, file_name, file_type, storage_path, parse_status) VALUES
  ({sql(material_id)}, {sql(course_id)}, {sql(chapter_id(audit, row))}, {sql(row['source_path'])}, 'md', {sql(row['source_url'])}, '已发布')
ON DUPLICATE KEY UPDATE course_id=VALUES(course_id), chapter_id=VALUES(chapter_id), file_name=VALUES(file_name), file_type=VALUES(file_type), storage_path=VALUES(storage_path), parse_status=VALUES(parse_status);
"""


def chunk_sql(chunk_id, material_id, row, audit):
    return f"""INSERT INTO knowledge_chunk (chunk_id, material_id, knowledge_id, chunk_text, embedding_id, version, status) VALUES
  ({sql(chunk_id)}, {sql(material_id)}, {sql(knowledge_id(audit, row))}, {sql(row['content'])}, NULL, 'llm-interview-note-v1', '已发布')
ON DUPLICATE KEY UPDATE material_id=VALUES(material_id), knowledge_id=VALUES(knowledge_id), chunk_text=VALUES(chunk_text), version=VALUES(version), status=VALUES(status);
"""


def validate_lengths(rows):
    max_file_name = max((len(row["source_path"]) for row in rows), default=0)
    max_source_url = max((len(row["source_url"]) for row in rows), default=0)
    max_chunk_bytes = max((len(row["content"].encode("utf-8")) for row in rows), default=0)
    if max_file_name > 200:
        raise SystemExit(f"source_path exceeds course_material.file_name limit: {max_file_name}")
    if max_source_url > 500:
        raise SystemExit(f"source_url exceeds course_material.storage_path limit: {max_source_url}")
    if max_chunk_bytes > 65535:
        raise SystemExit(f"content exceeds knowledge_chunk.chunk_text TEXT byte limit: {max_chunk_bytes}")
    return max_file_name, max_source_url, max_chunk_bytes


def main():
    args = parse_args()
    content_rows = {row["record_id"]: row for row in read_tsv(args.content_tsv)}
    audit_rows = read_tsv(args.audit_tsv)
    approved = [row for row in audit_rows if row.get("review_status") == args.approved_status]
    approved_pairs = [(content_rows[row["record_id"]], row) for row in approved if row.get("record_id") in content_rows]
    max_file_name, max_source_url, max_chunk_bytes = validate_lengths([row for row, _ in approved_pairs])

    output = Path(args.output_sql)
    output.parent.mkdir(parents=True, exist_ok=True)

    parts = ["SET NAMES utf8mb4;\n", SOURCE_SQL]
    seen_materials = set()
    for row, audit in approved_pairs:
        material_id = stable_id("material-llm-", row["source_path"])
        if material_id not in seen_materials:
            parts.append(material_sql(material_id, row, audit, args.course_id))
            seen_materials.add(material_id)
        chunk_id = stable_id("chunk-llm-", row["record_id"])
        parts.append(chunk_sql(chunk_id, material_id, row, audit))

    with output.open("w", encoding="utf-8", newline="\n") as writer:
        writer.write("\n".join(parts))
    print(json.dumps({
        "output_sql": str(output.resolve()),
        "approved_records": len(approved),
        "matched_records": len(approved_pairs),
        "materials": len(seen_materials),
        "chunks": len(approved_pairs),
        "max_file_name_chars": max_file_name,
        "max_source_url_chars": max_source_url,
        "max_chunk_text_utf8_bytes": max_chunk_bytes,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
