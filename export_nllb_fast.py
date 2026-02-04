#!/usr/bin/env python3
"""
Faster NLLB export - no quantization (larger files but faster export)
Use this if export_nllb_with_cache.py is too slow

Output files will be ~2.4GB instead of ~1.2GB (non-quantized)
"""

import sys
from pathlib import Path

def export_fast():
    try:
        from optimum.exporters.onnx import main_export
    except ImportError:
        print("❌ optimum not installed")
        print("   pip install optimum[exporters,onnxruntime]")
        sys.exit(1)

    model_id = "facebook/nllb-200-distilled-600M"
    output_dir = Path("nllb-onnx-fast")

    print(f"\n{'='*60}")
    print(f"Fast NLLB Export (No Quantization)")
    print(f"{'='*60}\n")
    print(f"Model: {model_id}")
    print(f"Output: {output_dir}")
    print(f"\nThis should take 10-15 minutes (faster than quantized version)\n")

    output_dir.mkdir(exist_ok=True)

    try:
        print("Exporting (no quantization - faster)...\n")

        main_export(
            model_name_or_path=model_id,
            output=output_dir,
            task="seq2seq-lm-with-past",  # KV cache enabled
            device="cpu",
            fp16=False,
            # No quantization - faster but larger files
        )

        print(f"\n{'='*60}")
        print("✓ Export completed!")
        print(f"{'='*60}\n")

        files = sorted(output_dir.glob("*.onnx"))
        print("Exported files:")
        for f in files:
            size_mb = f.stat().st_size / (1024 * 1024)
            print(f"  • {f.name:45s} ({size_mb:6.1f} MB)")

        required = [
            "encoder_model.onnx",
            "decoder_model.onnx",
            "decoder_with_past_model.onnx",
        ]

        print("\nVerifying required files:")
        for filename in required:
            exists = (output_dir / filename).exists()
            print(f"  {'✓' if exists else '❌'} {filename}")

        print(f"\nNext: Copy files to app/src/main/assets/models/nllb-200-distilled/")
        print("Note: Use .onnx files (not _quantized.onnx)")

        return True
    except Exception as e:
        print(f"\n❌ Export failed: {e}")
        return False

if __name__ == "__main__":
    export_fast()
