import argparse
import csv
import hashlib
import json
import re
from pathlib import Path

from split_markdown_files import build_chunks


MD_IMAGE_RE = re.compile(r"!\[[^\]]*]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
HTML_IMAGE_RE = re.compile(r"<img\b[^>]*\bsrc=[\"']([^\"']+)[\"']", re.I)


def parse_args():
    parser = argparse.ArgumentParser(description="Export CS-Notes Markdown chunks for external review.")
    parser.add_argument("--notes-dir", default="data/CC-BY-SA-4.0/CS-Notes/notes")
    parser.add_argument("--pics-dir", default="data/CC-BY-SA-4.0/CS-Notes/notes/pics")
    parser.add_argument("--output-dir", default="data/processed/cs-notes-review")
    parser.add_argument("--source-base-url", default="https://github.com/CyC2018/CS-Notes/blob/master/notes")
    parser.add_argument("--asset-prefix", default="cs-notes/pics")
    parser.add_argument("--asset-public-base", default="", help="Optional URL prefix after image upload.")
    parser.add_argument("--max-chars", type=int, default=3000)
    parser.add_argument("--min-chars", type=int, default=80)
    return parser.parse_args()


def image_refs(text):
    refs = MD_IMAGE_RE.findall(text)
    refs.extend(HTML_IMAGE_RE.findall(text))
    return sorted(set(ref.strip() for ref in refs if ref.strip()))


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as reader:
        for chunk in iter(lambda: reader.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def normalize_content(text):
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = MD_IMAGE_RE.sub(r"[图片: \1]", text)
    text = HTML_IMAGE_RE.sub(r"[图片: \1]", text)
    return re.sub(r"\r\n?", "\n", text).strip()


def write_tsv(path, fieldnames, rows):
    with path.open("w", encoding="utf-8-sig", newline="") as writer_file:
        writer = csv.DictWriter(writer_file, fieldnames=fieldnames, dialect="excel-tab")
        writer.writeheader()
        writer.writerows(rows)


def local_image_row(path, pics_dir, asset_prefix, asset_public_base):
    digest = sha256_file(path)
    suffix = path.suffix.lower()
    upload_key = f"{asset_prefix.rstrip('/')}/{digest[:16]}{suffix}"
    return {
        "image_id": digest[:16],
        "kind": "local_file",
        "source_ref": path.relative_to(pics_dir).as_posix(),
        "local_path": path.as_posix(),
        "file_size": path.stat().st_size,
        "sha256": digest,
        "upload_key": upload_key,
        "public_url": f"{asset_public_base.rstrip('/')}/{upload_key}" if asset_public_base else "",
    }


def remote_image_row(ref, asset_public_base):
    digest = hashlib.sha256(ref.encode("utf-8")).hexdigest()
    name = ref.rstrip("/").split("/")[-1] or digest[:16]
    upload_key = f"cs-notes/remote/{digest[:16]}-{name}"
    return {
        "image_id": digest[:16],
        "kind": "remote_ref",
        "source_ref": ref,
        "local_path": "",
        "file_size": "",
        "sha256": digest,
        "upload_key": upload_key,
        "public_url": f"{asset_public_base.rstrip('/')}/{upload_key}" if asset_public_base else "",
    }


def main():
    args = parse_args()
    notes_dir = Path(args.notes_dir).resolve()
    pics_dir = Path(args.pics_dir).resolve()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    content_rows = []
    referenced_images = set()
    md_files = sorted(path for path in notes_dir.rglob("*.md") if pics_dir not in path.parents)
    for file_path in md_files:
        for chunk in build_chunks(file_path, notes_dir, args.max_chars, args.min_chars):
            refs = image_refs(chunk["content"])
            referenced_images.update(refs)
            source_path = chunk["source_path"]
            source_url = f"{args.source_base_url.rstrip('/')}/{source_path}"
            content = normalize_content(chunk["content"])
            content_rows.append({
                "record_id": "csnotes-" + chunk["chunk_id"],
                "source_id": "source-cs-notes",
                "source_path": source_path,
                "source_url": source_url,
                "license": "CC-BY-SA-4.0",
                "title": chunk["title"],
                "heading_path": " > ".join(chunk["heading_path"]),
                "line_start": chunk["line_start"],
                "line_end": chunk["line_end"],
                "char_count": len(content),
                "sha256": hashlib.sha256(content.encode("utf-8")).hexdigest(),
                "image_refs": json.dumps(refs, ensure_ascii=False),
                "review_status": "待审核",
                "content": content,
            })

    image_rows = []
    if pics_dir.exists():
        image_rows.extend(
            local_image_row(path, pics_dir, args.asset_prefix, args.asset_public_base)
            for path in sorted(pics_dir.rglob("*"))
            if path.is_file()
        )
    image_rows.extend(remote_image_row(ref, args.asset_public_base) for ref in sorted(referenced_images) if re.match(r"https?://", ref))

    write_tsv(output_dir / "cs_notes_review_content.tsv", [
        "record_id", "source_id", "source_path", "source_url", "license", "title", "heading_path",
        "line_start", "line_end", "char_count", "sha256", "image_refs", "review_status", "content",
    ], content_rows)
    write_tsv(output_dir / "cs_notes_image_manifest.tsv", [
        "image_id", "kind", "source_ref", "local_path", "file_size", "sha256", "upload_key", "public_url",
    ], image_rows)

    manifest = {
        "markdown_files": len(md_files),
        "content_records": len(content_rows),
        "image_records": len(image_rows),
        "local_image_files": sum(1 for row in image_rows if row["kind"] == "local_file"),
        "remote_image_refs": sum(1 for row in image_rows if row["kind"] == "remote_ref"),
        "output_dir": output_dir.as_posix(),
    }
    (output_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
