import argparse
import hashlib
import json
import re
from pathlib import Path


QUESTION_STARTERS = (
    "什么是", "为什么", "如何", "怎么", "怎样", "说说", "介绍一下", "谈谈",
    "请解释", "简述", "说明", "有哪些",
)
QUESTION_KEYWORDS = (
    "区别", "原理", "流程", "生命周期", "优缺点", "适用场景", "为什么要",
)
DEFAULT_SOURCE_ID = "source-javaguide"
DEFAULT_JOB_ID = "job-java-backend"
DEFAULT_DIFFICULTY = "中等"


PATH_MAPPINGS = [
    ("docs/java/concurrent", "kp-005", "tech-003"),
    ("docs/java/collection", "kp-004", "tech-002"),
    ("docs/java/basis", "kp-001", "tech-001"),
    ("docs/database/mysql", "kp-006", "tech-004"),
    ("docs/database/redis", "kp-007", "tech-005"),
    ("docs/cs-basics/network", "kp-008", "tech-006"),
    ("docs/system-design/framework/spring", "kp-003", "tech-001"),
    ("docs/system-design/framework/mybatis", "kp-003", "tech-004"),
]


def parse_args():
    parser = argparse.ArgumentParser(
        description="Extract question candidates from Markdown chunks."
    )
    parser.add_argument("--input-jsonl", required=True, help="Chunk JSONL produced by split_markdown_files.py.")
    parser.add_argument("--output-dir", required=True, help="Directory for candidate output files.")
    parser.add_argument("--source-base-url", default="https://github.com/Snailclimb/JavaGuide/blob/main")
    parser.add_argument("--include-source-prefix", action="append", default=[], help="Only include source paths with this prefix.")
    parser.add_argument("--max-answer-chars", type=int, default=1200)
    parser.add_argument("--min-score", type=int, default=2)
    return parser.parse_args()


def read_jsonl(path):
    with Path(path).open("r", encoding="utf-8") as reader:
        for line in reader:
            line = line.strip()
            if line:
                yield json.loads(line)


def clean_markdown(text):
    text = re.sub(r"```.*?```", " ", text, flags=re.S)
    text = re.sub(r"`([^`]+)`", r"\1", text)
    text = re.sub(r"!\[[^\]]*\]\([^)]+\)", " ", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    text = re.sub(r"^#{1,6}\s*", "", text, flags=re.M)
    text = re.sub(r"[*_>#|-]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def normalize_stem(title):
    title = clean_markdown(title)
    title = re.sub(r"^(Q\\d+[:：.]?|\\d+[、.])\\s*", "", title)
    return title.strip(" ：:。")


def score_question(title, content):
    stem = normalize_stem(title)
    score = 0
    reasons = []
    if "？" in stem or "?" in stem:
        score += 3
        reasons.append("title_has_question_mark")
    if stem.startswith(QUESTION_STARTERS):
        score += 3
        reasons.append("title_has_question_starter")
    if any(keyword in stem for keyword in QUESTION_KEYWORDS):
        score += 2
        reasons.append("title_has_question_keyword")
    if "面试" in content[:500] or "问题" in stem:
        score += 1
        reasons.append("interview_or_question_context")
    if len(stem) < 4 or stem.upper() == "ROOT":
        score -= 3
        reasons.append("weak_title")
    return stem, score, reasons


def should_include_source(source_path, prefixes):
    if not prefixes:
        return True
    return any(source_path.startswith(prefix) for prefix in prefixes)


def map_knowledge_and_tech(source_path):
    for prefix, knowledge_id, tech_id in PATH_MAPPINGS:
        if source_path.startswith(prefix):
            return knowledge_id, tech_id
    return "kp-003", "tech-001"


def build_candidate(chunk, args):
    content = chunk.get("content", "")
    title = chunk.get("title", "")
    stem, score, reasons = score_question(title, content)
    if score < args.min_score:
        return None
    answer = clean_markdown(content)
    if answer.startswith(stem):
        answer = answer[len(stem):].strip(" ：:。")
    answer = answer[: args.max_answer_chars].strip()
    source_path = chunk["source_path"]
    knowledge_id, tech_id = map_knowledge_and_tech(source_path)
    stable_key = f"{source_path}|{stem}"
    candidate_id = "candidate-" + hashlib.sha1(stable_key.encode("utf-8")).hexdigest()[:12]
    return {
        "candidate_id": candidate_id,
        "source_id": DEFAULT_SOURCE_ID,
        "source_path": source_path,
        "source_url": f"{args.source_base_url.rstrip('/')}/{source_path}",
        "chunk_id": chunk["chunk_id"],
        "question_type": "简答题",
        "stem": stem,
        "answer": answer,
        "answer_analysis": answer,
        "difficulty": DEFAULT_DIFFICULTY,
        "audit_status": "待审核",
        "knowledge_id": knowledge_id,
        "job_id": DEFAULT_JOB_ID,
        "tech_id": tech_id,
        "score": score,
        "reasons": reasons,
    }


def write_jsonl(path, records):
    with path.open("w", encoding="utf-8", newline="\n") as writer:
        for record in records:
            writer.write(json.dumps(record, ensure_ascii=False) + "\n")


def main():
    args = parse_args()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    candidates = []
    seen_stems = set()
    chunk_count = 0
    for chunk in read_jsonl(args.input_jsonl):
        chunk_count += 1
        source_path = chunk.get("source_path", "")
        if not should_include_source(source_path, args.include_source_prefix):
            continue
        candidate = build_candidate(chunk, args)
        if not candidate:
            continue
        dedupe_key = re.sub(r"\s+", "", candidate["stem"]).lower()
        if dedupe_key in seen_stems:
            continue
        seen_stems.add(dedupe_key)
        candidates.append(candidate)

    write_jsonl(output_dir / "question-candidates.jsonl", candidates)
    manifest = {
        "input_jsonl": str(Path(args.input_jsonl).resolve()),
        "chunk_count": chunk_count,
        "candidate_count": len(candidates),
        "min_score": args.min_score,
        "include_source_prefix": args.include_source_prefix,
    }
    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
