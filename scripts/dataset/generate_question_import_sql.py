import argparse
import hashlib
import json
from pathlib import Path


SOURCE_SQL = """INSERT INTO data_source (source_id, source_name, source_type, source_url, license, usage_note) VALUES
  ('source-javaguide', 'JavaGuide', 'GitHub Repository', 'https://github.com/Snailclimb/JavaGuide', 'Apache-2.0', 'Keep source URL and license when displaying imported question records.')
ON DUPLICATE KEY UPDATE source_name=VALUES(source_name), source_type=VALUES(source_type), source_url=VALUES(source_url), license=VALUES(license), usage_note=VALUES(usage_note);
"""


def parse_args():
    parser = argparse.ArgumentParser(description="Generate MySQL import SQL from question candidates.")
    parser.add_argument("--input-jsonl", required=True)
    parser.add_argument("--output-sql", required=True)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--offset", type=int, default=0)
    parser.add_argument("--audit-status", default="待审核")
    return parser.parse_args()


def read_jsonl(path):
    with Path(path).open("r", encoding="utf-8") as reader:
        for line in reader:
            line = line.strip()
            if line:
                yield json.loads(line)


def sql(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("\\", "\\\\").replace("'", "''") + "'"


def stable_id(prefix, *parts):
    raw = "|".join(str(part) for part in parts)
    return prefix + hashlib.sha1(raw.encode("utf-8")).hexdigest()[:12]


def selected(records, offset, limit):
    for index, record in enumerate(records):
        if index < offset:
            continue
        if limit and index >= offset + limit:
            break
        yield record


def question_sql(record, audit_status):
    question_id = stable_id("jg-q-", record["candidate_id"], record["stem"])
    return f"""INSERT INTO question (question_id, source_id, source_path, source_url, question_type, stem, difficulty, answer, answer_analysis, audit_status) VALUES
  ({sql(question_id)}, {sql(record.get("source_id"))}, {sql(record.get("source_path"))}, {sql(record.get("source_url"))}, {sql(record.get("question_type"))}, {sql(record.get("stem"))}, {sql(record.get("difficulty"))}, {sql(record.get("answer"))}, {sql(record.get("answer_analysis"))}, {sql(audit_status)})
ON DUPLICATE KEY UPDATE source_id=VALUES(source_id), source_path=VALUES(source_path), source_url=VALUES(source_url), question_type=VALUES(question_type), stem=VALUES(stem), difficulty=VALUES(difficulty), answer=VALUES(answer), answer_analysis=VALUES(answer_analysis), audit_status=VALUES(audit_status);
"""


def relation_sql(record):
    question_id = stable_id("jg-q-", record["candidate_id"], record["stem"])
    qk_id = stable_id("qk-", question_id, record.get("knowledge_id"))
    qj_id = stable_id("qj-", question_id, record.get("job_id"), record.get("tech_id"))
    return f"""INSERT INTO question_knowledge_relation (relation_id, question_id, knowledge_id, weight) VALUES
  ({sql(qk_id)}, {sql(question_id)}, {sql(record.get("knowledge_id"))}, 1.0)
ON DUPLICATE KEY UPDATE weight=VALUES(weight);

INSERT INTO question_job_relation (relation_id, question_id, job_id, tech_id, match_level) VALUES
  ({sql(qj_id)}, {sql(question_id)}, {sql(record.get("job_id"))}, {sql(record.get("tech_id"))}, '核心')
ON DUPLICATE KEY UPDATE match_level=VALUES(match_level);
"""


def main():
    args = parse_args()
    records = list(selected(read_jsonl(args.input_jsonl), args.offset, args.limit))
    output = Path(args.output_sql)
    output.parent.mkdir(parents=True, exist_ok=True)
    parts = ["SET NAMES utf8mb4;\n", SOURCE_SQL]
    for record in records:
        parts.append(question_sql(record, args.audit_status))
        parts.append(relation_sql(record))
    with output.open("w", encoding="utf-8", newline="\n") as writer:
        writer.write("\n".join(parts))
    print(json.dumps({"output_sql": str(output.resolve()), "record_count": len(records)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
