import argparse
import csv
import hashlib
import json
import re
from pathlib import Path
from urllib.parse import quote

from split_markdown_files import build_chunks


MD_IMAGE_RE = re.compile(r"!\[[^\]]*]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
HTML_IMAGE_RE = re.compile(r"<img\b[^>]*\bsrc=[\"']?([^\"'\s>]+)", re.I)
IMAGE_EXTS = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg"}


def parse_args():
    parser = argparse.ArgumentParser(description="Export llm_interview_note chunks for external review.")
    parser.add_argument("--source-dir", default="data/Personal-NonCommercial-Allowed/llm_interview_note")
    parser.add_argument("--output-dir", default="data/processed/llm-interview-note-review")
    parser.add_argument("--source-base-url", default="https://github.com/wdndev/llm_interview_note/blob/main")
    parser.add_argument("--asset-prefix", default="llm-interview-note/images")
    parser.add_argument("--max-chars", type=int, default=3000)
    parser.add_argument("--min-chars", type=int, default=80)
    return parser.parse_args()


def image_refs(text):
    refs = MD_IMAGE_RE.findall(text)
    refs.extend(HTML_IMAGE_RE.findall(text))
    return sorted(set(ref.strip("<>").strip() for ref in refs if ref.strip()))


def normalize_content(text):
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = MD_IMAGE_RE.sub(r"[图片: \1]", text)
    text = HTML_IMAGE_RE.sub(r"[图片: \1]", text)
    return re.sub(r"\r\n?", "\n", text).strip()


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as reader:
        for chunk in iter(lambda: reader.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_tsv(path, fieldnames, rows):
    with path.open("w", encoding="utf-8-sig", newline="") as writer_file:
        writer = csv.DictWriter(writer_file, fieldnames=fieldnames, dialect="excel-tab")
        writer.writeheader()
        writer.writerows(rows)


def is_skipped(path, source_dir):
    if ".git" in path.parts:
        return True
    if path.name in {"_navbar.md", "_sidebar.md"}:
        return True
    if path.name.lower() == "readme.md" and path.parent == source_dir:
        return True
    return False


def local_image_row(path, source_dir, asset_prefix):
    digest = sha256_file(path)
    suffix = path.suffix.lower()
    return {
        "image_id": digest[:16],
        "kind": "local_file",
        "source_ref": path.relative_to(source_dir).as_posix(),
        "local_path": path.as_posix(),
        "file_size": path.stat().st_size,
        "sha256": digest,
        "upload_key": f"{asset_prefix.rstrip('/')}/{digest[:16]}{suffix}",
        "public_url": "",
    }


def remote_image_row(ref, asset_prefix):
    digest = hashlib.sha256(ref.encode("utf-8")).hexdigest()
    name = ref.rstrip("/").split("/")[-1] or digest[:16]
    return {
        "image_id": digest[:16],
        "kind": "remote_ref",
        "source_ref": ref,
        "local_path": "",
        "file_size": "",
        "sha256": digest,
        "upload_key": f"{asset_prefix.rstrip('/')}/remote/{digest[:16]}-{name}",
        "public_url": "",
    }


def pdf_row(path, source_dir):
    digest = sha256_file(path)
    return {
        "pdf_id": digest[:16],
        "source_path": path.relative_to(source_dir).as_posix(),
        "local_path": path.as_posix(),
        "file_size": path.stat().st_size,
        "sha256": digest,
        "parse_status": "未解析",
        "usage_note": "manifest only; PDF content is not imported in this batch",
    }


def main():
    args = parse_args()
    source_dir = Path(args.source_dir).resolve()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    md_files = sorted(
        path for path in source_dir.rglob("*.md")
        if path.is_file() and not is_skipped(path, source_dir)
    )
    content_rows = []
    referenced_images = set()
    for file_path in md_files:
        for chunk in build_chunks(file_path, source_dir, args.max_chars, args.min_chars):
            refs = image_refs(chunk["content"])
            referenced_images.update(refs)
            source_path = chunk["source_path"]
            content = normalize_content(chunk["content"])
            content_rows.append({
                "record_id": "llmnote-" + chunk["chunk_id"],
                "source_id": "source-llm-interview-note",
                "source_path": source_path,
                "source_url": f"{args.source_base_url.rstrip('/')}/{quote(source_path, safe='/')}",
                "license_status": "Personal-NonCommercial-Allowed",
                "title": chunk["title"],
                "heading_path": " > ".join(chunk["heading_path"]),
                "line_start": chunk["line_start"],
                "line_end": chunk["line_end"],
                "char_count": len(content),
                "sha256": hashlib.sha256(content.encode("utf-8")).hexdigest(),
                "image_refs": json.dumps(refs, ensure_ascii=False),
                "pdf_refs": "[]",
                "review_status": "待审核",
                "content": content,
            })

    image_rows = [
        local_image_row(path, source_dir, args.asset_prefix)
        for path in sorted(source_dir.rglob("*"))
        if path.is_file() and ".git" not in path.parts and path.suffix.lower() in IMAGE_EXTS
    ]
    image_rows.extend(
        remote_image_row(ref, args.asset_prefix)
        for ref in sorted(referenced_images)
        if re.match(r"https?://", ref)
    )
    pdf_rows = [
        pdf_row(path, source_dir)
        for path in sorted(source_dir.rglob("*.pdf"))
        if path.is_file() and ".git" not in path.parts
    ]

    write_tsv(output_dir / "llm_interview_note_review_content.tsv", [
        "record_id", "source_id", "source_path", "source_url", "license_status", "title", "heading_path",
        "line_start", "line_end", "char_count", "sha256", "image_refs", "pdf_refs", "review_status", "content",
    ], content_rows)
    write_tsv(output_dir / "llm_interview_note_image_manifest.tsv", [
        "image_id", "kind", "source_ref", "local_path", "file_size", "sha256", "upload_key", "public_url",
    ], image_rows)
    write_tsv(output_dir / "llm_interview_note_pdf_manifest.tsv", [
        "pdf_id", "source_path", "local_path", "file_size", "sha256", "parse_status", "usage_note",
    ], pdf_rows)

    manifest = {
        "markdown_files": len(md_files),
        "content_records": len(content_rows),
        "image_records": len(image_rows),
        "local_image_files": sum(1 for row in image_rows if row["kind"] == "local_file"),
        "remote_image_refs": sum(1 for row in image_rows if row["kind"] == "remote_ref"),
        "pdf_records": len(pdf_rows),
        "license_status": "Personal-NonCommercial-Allowed",
        "output_dir": output_dir.as_posix(),
    }
    (output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
