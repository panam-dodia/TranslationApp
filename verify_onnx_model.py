#!/usr/bin/env python3
"""
Verify NLLB ONNX model structure and check decoder_with_past input/output names
This helps debug tensor name mismatches in Android code

Usage:
    python verify_onnx_model.py nllb-onnx-quantized/
"""

import sys
from pathlib import Path

def verify_model(model_dir):
    """Verify ONNX model inputs/outputs"""
    try:
        import onnxruntime as ort
    except ImportError:
        print("❌ onnxruntime not installed")
        print("   pip install onnxruntime")
        sys.exit(1)

    model_path = Path(model_dir)

    print(f"\n{'='*70}")
    print(f"NLLB ONNX Model Verification")
    print(f"{'='*70}\n")
    print(f"Directory: {model_path}\n")

    # Check files exist
    files_to_check = {
        "encoder": "encoder_model_quantized.onnx",
        "decoder": "decoder_model_quantized.onnx",
        "decoder_with_past": "decoder_with_past_model_quantized.onnx",
    }

    sessions = {}
    for name, filename in files_to_check.items():
        file_path = model_path / filename
        if not file_path.exists():
            # Try non-quantized version
            filename_non_quant = filename.replace("_quantized", "")
            file_path = model_path / filename_non_quant
            if not file_path.exists():
                print(f"❌ {name}: {filename} NOT FOUND")
                if name == "decoder_with_past":
                    print(f"   ⚠️  This is CRITICAL - KV cache won't work!")
                continue

        try:
            sessions[name] = ort.InferenceSession(str(file_path))
            size_mb = file_path.stat().st_size / (1024 * 1024)
            print(f"✓ {name}: {filename} ({size_mb:.1f} MB)")
        except Exception as e:
            print(f"❌ {name}: Failed to load - {e}")

    if not sessions:
        print("\n❌ No models could be loaded!")
        sys.exit(1)

    # Analyze each model
    for name, session in sessions.items():
        print(f"\n{'-'*70}")
        print(f"{name.upper()}")
        print(f"{'-'*70}")

        print(f"\nInputs ({len(session.get_inputs())}):")
        for inp in session.get_inputs():
            print(f"  • {inp.name:50s} {inp.shape} {inp.type}")

        print(f"\nOutputs ({len(session.get_outputs())}):")
        for i, out in enumerate(session.get_outputs()):
            if i < 10:  # Show first 10
                print(f"  • {out.name:50s} {out.shape} {out.type}")
            elif i == 10:
                print(f"  ... and {len(session.get_outputs()) - 10} more outputs")
                break

    # Verify decoder_with_past compatibility
    if "decoder_with_past" in sessions:
        print(f"\n{'='*70}")
        print("DECODER_WITH_PAST COMPATIBILITY CHECK")
        print(f"{'='*70}\n")

        dwp_session = sessions["decoder_with_past"]
        decoder_session = sessions["decoder"]

        # Get input/output names
        dwp_inputs = {inp.name for inp in dwp_session.get_inputs()}
        dwp_outputs = {out.name for out in dwp_session.get_outputs()}
        decoder_outputs = {out.name for out in decoder_session.get_outputs()}

        # Check if past_key_values inputs exist
        past_inputs = [name for name in dwp_inputs if "past" in name.lower()]

        if past_inputs:
            print(f"✓ Found {len(past_inputs)} past_key_values inputs")
            print("\nPast input naming pattern:")
            if past_inputs:
                print(f"  Example: {past_inputs[0]}")
                print(f"  Pattern: {get_pattern(past_inputs[0])}")
        else:
            print("❌ No past_key_values inputs found!")
            print("   KV cache won't work!")

        # Check if decoder outputs match decoder_with_past inputs
        print("\n" + "-"*70)
        print("Checking output → input mapping:")
        print("-"*70 + "\n")

        decoder_present = [name for name in decoder_outputs if "present" in name]
        if decoder_present:
            print(f"Decoder outputs {len(decoder_present)} 'present.*' tensors")
            print(f"  Example: {decoder_present[0]}")

            # Show mapping
            if past_inputs:
                example_present = decoder_present[0]
                example_past = past_inputs[0]

                print(f"\nExpected mapping in Android code:")
                print(f"  Decoder output:           {example_present}")
                print(f"  decoder_with_past input:  {example_past}")

                # Suggest conversion
                if "present." in example_present and "past" in example_past:
                    suggested_conversion = suggest_conversion(example_present, example_past)
                    print(f"\nSuggested Kotlin conversion:")
                    print(f'  val inputName = outputName{suggested_conversion}')

                # Verify it matches current code
                print(f"\nCurrent code uses:")
                print(f'  val inputName = outputName.replace("present.", "past_key_values.")')

                current_result = example_present.replace("present.", "past_key_values.")
                if current_result == example_past:
                    print(f"  ✓ MATCHES! Current code is correct.")
                else:
                    print(f"  ❌ MISMATCH!")
                    print(f"     Current code produces: {current_result}")
                    print(f"     Expected:              {example_past}")
                    print(f"\n  FIX NEEDED in NLLBInferenceEngine.kt line 291")

    print(f"\n{'='*70}")
    print("Summary")
    print(f"{'='*70}\n")

    if "decoder_with_past" not in sessions:
        print("❌ decoder_with_past model is MISSING")
        print("   → Run: python export_nllb_with_cache.py")
    elif not past_inputs:
        print("❌ decoder_with_past model has no past_key_values inputs")
        print("   → Re-export with --task seq2seq-lm-with-past")
    else:
        print("✓ All required models present")
        print("✓ decoder_with_past has KV cache inputs")
        print("\n📋 Next steps:")
        print("   1. Copy models to: app/src/main/assets/models/nllb-200-distilled/")
        print("   2. Rebuild Android app")
        print("   3. Verify Android logs show: '✓ Model SUPPORTS KV cache'")

def get_pattern(name):
    """Extract pattern from tensor name"""
    if "past_key_values" in name:
        return "past_key_values.{layer}.{type}.{kv}"
    elif "present" in name:
        return "present.{layer}.{type}.{kv}"
    return "unknown"

def suggest_conversion(decoder_output, dwp_input):
    """Suggest string replacement for converting output to input name"""
    # Extract common patterns
    parts_out = decoder_output.split(".")
    parts_in = dwp_input.split(".")

    if len(parts_out) > 0 and len(parts_in) > 0:
        if parts_out[0] != parts_in[0]:
            return f'.replace("{parts_out[0]}", "{parts_in[0]}")'

    return ".replace(?, ?)"

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python verify_onnx_model.py <model_directory>")
        print("Example: python verify_onnx_model.py nllb-onnx-quantized/")
        sys.exit(1)

    verify_model(sys.argv[1])
