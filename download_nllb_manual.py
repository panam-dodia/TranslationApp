#!/usr/bin/env python3
"""
Manual download of NLLB ONNX models from Hugging Face
Use this if export scripts keep failing

WARNING: Xenova's models don't include decoder_with_past
You'll need to export that separately or find another source
"""

import requests
from pathlib import Path
from tqdm import tqdm

def download_file(url, dest_path):
    """Download file with progress bar"""
    response = requests.get(url, stream=True)
    total_size = int(response.headers.get('content-length', 0))

    dest_path.parent.mkdir(parents=True, exist_ok=True)

    with open(dest_path, 'wb') as f, tqdm(
        desc=dest_path.name,
        total=total_size,
        unit='B',
        unit_scale=True,
        unit_divisor=1024,
    ) as pbar:
        for chunk in response.iter_content(chunk_size=8192):
            f.write(chunk)
            pbar.update(len(chunk))

def download_xenova_models():
    """Download pre-exported ONNX models from Xenova"""

    base_url = "https://huggingface.co/Xenova/nllb-200-distilled-600M/resolve/main/onnx"
    output_dir = Path("nllb-onnx-downloaded")

    # Files available on Xenova (no decoder_with_past!)
    files = [
        "encoder_model_quantized.onnx",
        "decoder_model_quantized.onnx",
        # "decoder_with_past_model_quantized.onnx",  # NOT AVAILABLE!
    ]

    print("⚠️ WARNING: Xenova's repo does NOT have decoder_with_past!")
    print("   KV cache will not work with these models.")
    print("   This is only for testing - you still need to export decoder_with_past\n")

    proceed = input("Download anyway for testing? (y/n): ")
    if proceed.lower() != 'y':
        print("Cancelled.")
        return

    print(f"\nDownloading to {output_dir}/\n")

    for filename in files:
        url = f"{base_url}/{filename}"
        dest = output_dir / filename

        if dest.exists():
            print(f"✓ {filename} already exists, skipping")
            continue

        print(f"Downloading {filename}...")
        try:
            download_file(url, dest)
            print(f"✓ {filename} downloaded\n")
        except Exception as e:
            print(f"❌ Failed: {e}\n")

    # Download tokenizer
    print("Downloading tokenizer...")
    tokenizer_url = "https://huggingface.co/Xenova/nllb-200-distilled-600M/resolve/main/tokenizer.json"
    try:
        download_file(tokenizer_url, output_dir / "tokenizer.json")
        print("✓ tokenizer.json downloaded\n")
    except Exception as e:
        print(f"❌ Failed: {e}\n")

    print(f"\n{'='*60}")
    print("Downloaded files:")
    for f in output_dir.glob("*"):
        size_mb = f.stat().st_size / (1024 * 1024)
        print(f"  • {f.name:45s} ({size_mb:6.1f} MB)")

    print(f"\n⚠️ IMPORTANT: decoder_with_past_model_quantized.onnx is MISSING!")
    print("   You still need to export this file using one of the export scripts.")
    print("   Without it, translations will be slow and incomplete.\n")

if __name__ == "__main__":
    try:
        import requests
        from tqdm import tqdm
    except ImportError:
        print("Installing required packages...")
        import subprocess
        subprocess.check_call([sys.executable, "-m", "pip", "install", "requests", "tqdm"])
        import requests
        from tqdm import tqdm

    download_xenova_models()
