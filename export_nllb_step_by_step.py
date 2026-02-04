#!/usr/bin/env python3
"""
Step-by-step NLLB export with better error handling
Exports one model at a time to avoid crashes
"""

import sys
from pathlib import Path
import gc
import torch

def check_dependencies():
    try:
        import optimum
        import transformers
        print("✓ Dependencies OK")
        return True
    except ImportError as e:
        print(f"❌ {e}")
        return False

def export_step_by_step():
    if not check_dependencies():
        sys.exit(1)

    from transformers import AutoTokenizer, AutoModelForSeq2SeqLM
    from optimum.exporters.onnx import main_export
    import onnx
    from onnxruntime.quantization import quantize_dynamic, QuantType

    model_id = "facebook/nllb-200-distilled-600M"
    output_dir = Path("nllb-onnx-stepwise")
    output_dir.mkdir(exist_ok=True)

    print(f"\n{'='*60}")
    print("Step-by-Step NLLB Export")
    print(f"{'='*60}\n")

    try:
        # Step 1: Export non-quantized first
        print("STEP 1/2: Exporting ONNX models (no quantization)...")
        print("This may take 15-20 minutes...\n")

        main_export(
            model_name_or_path=model_id,
            output=output_dir,
            task="seq2seq-lm-with-past",
            device="cpu",
            fp16=False,
        )

        print("\n✓ ONNX export complete!")

        # Check what was created
        onnx_files = list(output_dir.glob("*.onnx"))
        print(f"\nExported {len(onnx_files)} ONNX files:")
        for f in onnx_files:
            size_mb = f.stat().st_size / (1024 * 1024)
            print(f"  • {f.name:45s} ({size_mb:6.1f} MB)")

        # Step 2: Quantize (optional - can skip if this step fails)
        print("\nSTEP 2/2: Quantizing models...")
        print("(This step is optional - you can use non-quantized models if this fails)\n")

        models_to_quantize = [
            "encoder_model.onnx",
            "decoder_model.onnx",
            "decoder_with_past_model.onnx"
        ]

        for model_name in models_to_quantize:
            input_path = output_dir / model_name
            if not input_path.exists():
                print(f"⚠️ Skipping {model_name} (not found)")
                continue

            output_name = model_name.replace(".onnx", "_quantized.onnx")
            output_path = output_dir / output_name

            try:
                print(f"Quantizing {model_name}...")
                quantize_dynamic(
                    str(input_path),
                    str(output_path),
                    weight_type=QuantType.QUInt8
                )
                size_mb = output_path.stat().st_size / (1024 * 1024)
                print(f"  ✓ Created {output_name} ({size_mb:.1f} MB)")

                # Free memory
                gc.collect()

            except Exception as e:
                print(f"  ⚠️ Quantization failed for {model_name}: {e}")
                print(f"  → You can use the non-quantized {model_name} instead")

        print(f"\n{'='*60}")
        print("Export Complete!")
        print(f"{'='*60}\n")

        # Show final files
        all_files = list(output_dir.glob("*.onnx"))
        print("All ONNX files created:")
        for f in sorted(all_files):
            size_mb = f.stat().st_size / (1024 * 1024)
            suffix = " ← USE THIS (quantized)" if "_quantized" in f.name else " (non-quantized backup)"
            print(f"  • {f.name:45s} ({size_mb:6.1f} MB){suffix}")

        print("\n📋 Next steps:")
        print(f"1. Copy files to: app/src/main/assets/models/nllb-200-distilled/")
        print(f"2. Use quantized versions if available, otherwise use non-quantized")
        print(f"3. Update NLLBModelDownloader.kt to look for correct filenames")

        return True

    except Exception as e:
        print(f"\n❌ Export failed: {e}")
        import traceback
        traceback.print_exc()

        print("\n💡 If export failed, try:")
        print("1. Close other applications to free memory")
        print("2. Use the fast export: python export_nllb_fast.py")
        print("3. Or download pre-exported models (see instructions)")

        return False

if __name__ == "__main__":
    export_step_by_step()
