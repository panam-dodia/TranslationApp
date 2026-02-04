#!/usr/bin/env python3
"""
Export NLLB-200-distilled-600M to ONNX with KV cache support
This generates decoder_with_past_model.onnx which is required for efficient inference

Requirements:
    pip install optimum[exporters,onnxruntime] transformers torch

Usage:
    python export_nllb_with_cache.py

Output:
    nllb-onnx-quantized/
    ├── encoder_model_quantized.onnx
    ├── decoder_model_quantized.onnx
    ├── decoder_with_past_model_quantized.onnx  ← This is the critical file!
    ├── tokenizer.json
    └── other config files
"""

import os
import sys
from pathlib import Path

def check_dependencies():
    """Check if required packages are installed"""
    try:
        import optimum
        import transformers
        import torch
        print("✓ All dependencies found")
        return True
    except ImportError as e:
        print(f"❌ Missing dependency: {e}")
        print("\nPlease install required packages:")
        print("  pip install optimum[exporters,onnxruntime] transformers torch")
        return False

def export_nllb_with_cache():
    """Export NLLB model with KV cache support"""
    if not check_dependencies():
        sys.exit(1)

    from optimum.exporters.onnx import main_export

    model_id = "facebook/nllb-200-distilled-600M"
    output_dir = Path("nllb-onnx-quantized")

    print(f"\n{'='*60}")
    print(f"Exporting NLLB-200-distilled-600M with KV cache")
    print(f"{'='*60}\n")
    print(f"Model: {model_id}")
    print(f"Output: {output_dir}")
    print(f"\nThis will download ~2.5GB and export to ~1.2GB ONNX\n")

    # Create output directory
    output_dir.mkdir(exist_ok=True)

    try:
        # Export with KV cache and quantization
        print("Starting export (this may take 5-10 minutes)...\n")

        main_export(
            model_name_or_path=model_id,
            output=output_dir,
            task="seq2seq-lm-with-past",  # This enables decoder_with_past
            device="cpu",
            fp16=False,
            optimize="O2",  # Optimization level
            batch_size=1,
            sequence_length=128,
            # Quantization for ARM64 (Android)
            quantize="arm64",
        )

        print(f"\n{'='*60}")
        print("✓ Export completed successfully!")
        print(f"{'='*60}\n")

        # List exported files
        files = sorted(output_dir.glob("*.onnx"))
        print("Exported ONNX files:")
        for f in files:
            size_mb = f.stat().st_size / (1024 * 1024)
            print(f"  • {f.name:45s} ({size_mb:6.1f} MB)")

        # Check for critical files
        required_files = [
            "encoder_model_quantized.onnx",
            "decoder_model_quantized.onnx",
            "decoder_with_past_model_quantized.onnx",
        ]

        print("\nVerifying required files:")
        all_present = True
        for filename in required_files:
            exists = (output_dir / filename).exists()
            status = "✓" if exists else "❌"
            print(f"  {status} {filename}")
            if not exists:
                all_present = False

        if not all_present:
            print("\n⚠️  Warning: Some required files are missing!")
            print("   The export may not have completed correctly.")
            return False

        print("\n" + "="*60)
        print("Next steps:")
        print("="*60)
        print(f"1. Copy the files from {output_dir}/ to your Android assets:")
        print(f"   app/src/main/assets/models/nllb-200-distilled/")
        print()
        print("2. Required files to copy:")
        for filename in required_files + ["tokenizer.json"]:
            print(f"   • {filename}")
        print()
        print("3. Rebuild your app - KV cache will now work!")
        print()

        return True

    except Exception as e:
        print(f"\n❌ Export failed: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = export_nllb_with_cache()
    sys.exit(0 if success else 1)
