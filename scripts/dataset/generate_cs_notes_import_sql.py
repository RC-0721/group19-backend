import argparse
import csv
import hashlib
import json
from pathlib import Path


SOURCE_SQL = """INSERT INTO data_source (source_id, source_name, source_type, source_url, license, usage_note) VALUES
  ('source-cs-notes', 'CS-Notes', 'GitHub Repository', 'https://github.com/CyC2018/CS-Notes', 'CC-BY-SA-4.0', 'Imported after external review; preserve source URL and CC-BY-SA-4.0 attribution.')
ON DUPLICATE KEY UPDATE source_name=VALUES(source_name), source_type=VALUES(source_type), source_url=VALUES(source_url), license=VALUES(license), usage_note=VALUES(usage_note);
"""


def parse_args():
    parser = argparse.ArgumentParser(description="Generate idempotent SQL for approved CS-Notes chunks.")
    parser.add_argument("--content-tsv", default="data/processed/cs-notes-review/cs_notes_review_content.tsv")
    parser.add_argument("--audit-tsv", default="data/processed/cs-notes-review/cs_notes_review_audit.tsv")
    parser.add_argument("--output-sql", default="data/processed/cs-notes-review/cs_notes_import.sql")
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


def chapter_id(audit):
    target = audit.get("target_course", "")
    tags = audit.get("knowledge_tags", "")
    text = target + " " + tags
    if "数据库" in text or "MySQL" in text or "Redis" in text:
        return "chapter-db-001"
    if "网络" in text or "操作系统" in text or "计算机基础" in text or "系统设计" in text:
        return "chapter-cs-001"
    return "chapter-java-001"


def knowledge_id(audit):
    text = f"{audit.get('target_course', '')} {audit.get('knowledge_tags', '')} {audit.get('content_type', '')}"
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
    if "面向对象" in text:
        return "kp-002"
    if "Java" in text or "JVM" in text:
        return "kp-001"
    return None


def material_sql(material_id, row, audit, course_id):
    chapter = chapter_id(audit)
    return f"""INSERT INTO course_material (material_id, course_id, chapter_id, file_name, file_type, storage_path, parse_status) VALUES
  ({sql(material_id)}, {sql(course_id)}, {sql(chapter)}, {sql(row['source_path'])}, 'md', {sql(row['source_url'])}, '已发布')
ON DUPLICATE KEY UPDATE course_id=VALUES(course_id), chapter_id=VALUES(chapter_id), file_name=VALUES(file_name), file_type=VALUES(file_type), storage_path=VALUES(storage_path), parse_status=VALUES(parse_status);
"""


def chunk_sql(chunk_id, material_id, row, audit):
    return f"""INSERT INTO knowledge_chunk (chunk_id, material_id, knowledge_id, chunk_text, embedding_id, version, status) VALUES
  ({sql(chunk_id)}, {sql(material_id)}, {sql(knowledge_id(audit))}, {sql(row['content'])}, NULL, 'cs-notes-v1', '已发布')
ON DUPLICATE KEY UPDATE material_id=VALUES(material_id), knowledge_id=VALUES(knowledge_id), chunk_text=VALUES(chunk_text), version=VALUES(version), status=VALUES(status);
"""


def main():
    args = parse_args()
    content_rows = {row["record_id"]: row for row in read_tsv(args.content_tsv)}
    audit_rows = read_tsv(args.audit_tsv)
    approved = [row for row in audit_rows if row.get("review_status") == args.approved_status]

    output = Path(args.output_sql)
    output.parent.mkdir(parents=True, exist_ok=True)

    parts = ["SET NAMES utf8mb4;\n", SOURCE_SQL]
    seen_materials = set()
    for audit in approved:
        row = content_rows.get(audit["record_id"])
        if not row:
            continue
        material_id = stable_id("material-csn-", row["source_path"])
        if material_id not in seen_materials:
            parts.append(material_sql(material_id, row, audit, args.course_id))
            seen_materials.add(material_id)
        chunk_id = stable_id("chunk-csn-", row["record_id"])
        parts.append(chunk_sql(chunk_id, material_id, row, audit))

    with output.open("w", encoding="utf-8", newline="\n") as writer:
        writer.write("\n".join(parts))
    print(json.dumps({
        "output_sql": str(output.resolve()),
        "approved_records": len(approved),
        "materials": len(seen_materials),
        "chunks": len(approved),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
