import argparse
import hashlib
import json
import random
import re
from pathlib import Path


HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
FENCE_PREFIXES = ("```", "~~~")


def parse_args():
    parser = argparse.ArgumentParser(
        description="Split Markdown files into bounded JSONL chunks for later cleaning."
    )
    parser.add_argument("--input-file", action="append", default=[], help="Markdown file to split.")
    parser.add_argument("--input-dir", help="Directory containing Markdown files.")
    parser.add_argument("--output-dir", required=True, help="Directory for split output files.")
    parser.add_argument("--source-root", help="Root used to create stable relative source paths.")
    parser.add_argument("--sample-size", type=int, default=0, help="Random sample size when using --input-dir.")
    parser.add_argument("--seed", type=int, default=20260628, help="Random seed for repeatable sampling.")
    parser.add_argument("--min-file-size", type=int, default=0, help="Only sample files at least this many bytes.")
    parser.add_argument("--max-chars", type=int, default=3000, help="Maximum characters per chunk.")
    parser.add_argument("--min-chars", type=int, default=80, help="Drop chunks shorter than this length.")
    parser.add_argument(
        "--allow-all",
        action="store_true",
        help="Allow processing every Markdown file under --input-dir. Disabled by default.",
    )
    return parser.parse_args()


def collect_files(args):
    files = [Path(item).resolve() for item in args.input_file]
    if args.input_dir:
        input_dir = Path(args.input_dir).resolve()
        dir_files = sorted(input_dir.rglob("*.md"))
        if args.min_file_size > 0:
            dir_files = [item for item in dir_files if item.stat().st_size >= args.min_file_size]
        if args.sample_size > 0:
            rng = random.Random(args.seed)
            sample_size = min(args.sample_size, len(dir_files))
            dir_files = sorted(rng.sample(dir_files, sample_size))
        elif not args.allow_all:
            raise SystemExit("Refusing to process a directory without --sample-size or --allow-all.")
        files.extend(dir_files)
    unique_files = []
    seen = set()
    for file_path in files:
        if file_path.suffix.lower() != ".md":
            continue
        key = str(file_path)
        if key not in seen:
            seen.add(key)
            unique_files.append(file_path)
    if not unique_files:
        raise SystemExit("No Markdown files selected.")
    return unique_files


def read_text(file_path):
    try:
        return file_path.read_text(encoding="utf-8-sig")
    except UnicodeDecodeError:
        return file_path.read_text(encoding="utf-8", errors="ignore")


def is_fence(line):
    stripped = line.strip()
    return stripped.startswith(FENCE_PREFIXES)


def clean_heading(raw_title):
    title = raw_title.strip().strip("#").strip()
    return re.sub(r"\s+", " ", title)


def split_sections(markdown_text):
    lines = markdown_text.splitlines()
    sections = []
    heading_stack = []
    current = {
        "level": 0,
        "title": "ROOT",
        "heading_path": [],
        "start_line": 1,
        "lines": [],
    }
    in_code = False

    for index, line in enumerate(lines, start=1):
        if is_fence(line):
            in_code = not in_code
        match = None if in_code else HEADING_RE.match(line)
        if match:
            finish_section(sections, current, index - 1)
            level = len(match.group(1))
            title = clean_heading(match.group(2))
            heading_stack = heading_stack[: level - 1]
            heading_stack.append(title)
            current = {
                "level": level,
                "title": title,
                "heading_path": heading_stack.copy(),
                "start_line": index,
                "lines": [line],
            }
        else:
            current["lines"].append(line)
    finish_section(sections, current, len(lines))
    return sections


def finish_section(sections, current, end_line):
    content = "\n".join(current["lines"]).strip()
    if content:
        sections.append({
            "level": current["level"],
            "title": current["title"],
            "heading_path": current["heading_path"],
            "start_line": current["start_line"],
            "end_line": end_line,
            "content": content,
        })


def split_blocks(content):
    blocks = []
    buffer = []
    in_code = False
    for line in content.splitlines():
        if is_fence(line):
            in_code = not in_code
        if not in_code and not line.strip():
            if buffer:
                blocks.append("\n".join(buffer).strip())
                buffer = []
            continue
        buffer.append(line)
    if buffer:
        blocks.append("\n".join(buffer).strip())
    return [block for block in blocks if block]


def chunk_large_section(section, max_chars):
    blocks = split_blocks(section["content"])
    chunks = []
    buffer = []
    buffer_chars = 0
    for block in blocks:
        block_chars = len(block)
        if block_chars > max_chars:
            if buffer:
                chunks.append("\n\n".join(buffer))
                buffer = []
                buffer_chars = 0
            chunks.extend(split_long_block(block, max_chars))
            continue
        next_size = buffer_chars + block_chars + (2 if buffer else 0)
        if buffer and next_size > max_chars:
            chunks.append("\n\n".join(buffer))
            buffer = [block]
            buffer_chars = block_chars
        else:
            buffer.append(block)
            buffer_chars = next_size
    if buffer:
        chunks.append("\n\n".join(buffer))
    return chunks


def split_long_block(block, max_chars):
    chunks = []
    buffer = []
    buffer_chars = 0
    for line in block.splitlines():
        line_chars = len(line) + 1
        if buffer and buffer_chars + line_chars > max_chars:
            chunks.append("\n".join(buffer))
            buffer = [line]
            buffer_chars = line_chars
        else:
            buffer.append(line)
            buffer_chars += line_chars
    if buffer:
        chunks.append("\n".join(buffer))
    return chunks


def build_chunks(file_path, source_root, max_chars, min_chars):
    text = read_text(file_path)
    source_path = file_path.relative_to(source_root).as_posix()
    chunks = []
    for section in split_sections(text):
        parts = [section["content"]]
        if len(section["content"]) > max_chars:
            parts = chunk_large_section(section, max_chars)
        for part in parts:
            if len(part.strip()) < min_chars:
                continue
            chunks.append({
                "source_path": source_path,
                "heading_path": section["heading_path"],
                "level": section["level"],
                "title": section["title"],
                "line_start": section["start_line"],
                "line_end": section["end_line"],
                "char_count": len(part),
                "sha256": hashlib.sha256(part.encode("utf-8")).hexdigest(),
                "content": part,
            })
    for index, chunk in enumerate(chunks, start=1):
        chunk["chunk_id"] = f"{hashlib.sha1(source_path.encode('utf-8')).hexdigest()[:10]}-{index:04d}"
    return chunks


def write_jsonl(output_file, records):
    with output_file.open("w", encoding="utf-8", newline="\n") as writer:
        for record in records:
            writer.write(json.dumps(record, ensure_ascii=False) + "\n")


def main():
    args = parse_args()
    files = collect_files(args)
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    source_root = Path(args.source_root).resolve() if args.source_root else files[0].parent

    all_chunks = []
    file_summaries = []
    for file_path in files:
        chunks = build_chunks(file_path, source_root, args.max_chars, args.min_chars)
        all_chunks.extend(chunks)
        file_summaries.append({
            "source_path": file_path.relative_to(source_root).as_posix(),
            "file_size": file_path.stat().st_size,
            "chunk_count": len(chunks),
        })

    write_jsonl(output_dir / "chunks.jsonl", all_chunks)
    manifest = {
        "file_count": len(files),
        "chunk_count": len(all_chunks),
        "max_chars": args.max_chars,
        "min_chars": args.min_chars,
        "sample_size": args.sample_size,
        "seed": args.seed,
        "files": file_summaries,
    }
    (output_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
