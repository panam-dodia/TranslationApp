# NLLB Translation Model Setup Instructions

## Problem: Missing decoder_with_past Model

Your logs show:
```
⚠ decoder_with_past_model_quantized.onnx missing - KV cache will not work!
```

This causes:
- **Extremely slow translation** (recomputes everything each token)
- **Poor quality** (model hits EOS after 1-2 tokens)
- Translation outputs like "आज" instead of full sentences

## Solution: Export NLLB with KV Cache

### Option 1: Export Yourself (Recommended)

1. **Install dependencies** on your PC:
   ```bash
   pip install optimum[exporters,onnxruntime] transformers torch
   ```

2. **Run the export script**:
   ```bash
   cd D:/translationapp
   python export_nllb_with_cache.py
   ```

   This will:
   - Download NLLB-200-distilled-600M (~2.5GB)
   - Export to ONNX with KV cache (~1.2GB)
   - Create `nllb-onnx-quantized/decoder_with_past_model_quantized.onnx`

3. **Copy to Android assets**:
   ```
   Copy from: nllb-onnx-quantized/
   Copy to:   app/src/main/assets/models/nllb-200-distilled/

   Required files:
   ✓ encoder_model_quantized.onnx
   ✓ decoder_model_quantized.onnx
   ✓ decoder_with_past_model_quantized.onnx  ← CRITICAL!
   ✓ tokenizer.json
   ```

4. **Rebuild your app**

### Option 2: Manual Export with Optimum CLI

```bash
optimum-cli export onnx \
  --model facebook/nllb-200-distilled-600M \
  --task seq2seq-lm-with-past \
  --optimize O2 \
  --quantize arm64 \
  nllb-onnx-quantized/
```

### Option 3: Download Pre-exported (if available)

Check these repositories for pre-exported models with decoder_with_past:
- https://huggingface.co/models?library=onnx&search=nllb
- Look for models tagged with "onnx" and "seq2seq-lm-with-past"

## Verification

After copying the model, check Android logs for:
```
✓ Loaded decoder_with_past for KV cache
✓ Model SUPPORTS KV cache (has past_key_values inputs)
```

Instead of:
```
⚠ decoder_with_past_model not found - KV cache disabled
```

## Expected Behavior After Fix

**Before (current):**
- Step 0: Generate token 7648 ("आज")
- Step 1: Generate EOS token → STOP
- Output: "आज" (incomplete)

**After (with KV cache):**
- Step 0: Generate token (uses decoder_model)
- Step 1-N: Generate tokens (uses decoder_with_past_model)
- Reaches natural EOS after complete sentence
- Output: Full Hindi translation

## File Sizes

- `encoder_model_quantized.onnx`: ~300 MB
- `decoder_model_quantized.onnx`: ~250 MB
- `decoder_with_past_model_quantized.onnx`: ~600 MB ← You need this!
- `tokenizer.json`: ~5 MB

**Total: ~1.2 GB**

## Troubleshooting

### Export fails with "optimum not found"
```bash
pip install --upgrade optimum[exporters,onnxruntime]
```

### Export fails with "torch not found"
```bash
pip install torch --index-url https://download.pytorch.org/whl/cpu
```

### Out of memory during export
- Close other applications
- Export on a machine with 8GB+ RAM
- Or use a cloud VM

### App crashes with "Failed to load model"
- Verify all 4 files are in `app/src/main/assets/models/nllb-200-distilled/`
- Rebuild app completely (Clean Project → Rebuild)
- Check Android logs for specific error

## Current Code Status

Your NLLBInferenceEngine.kt is **correctly implemented** and ready to use KV cache:
- ✓ Loads decoder_with_past if available (line 86-89)
- ✓ Switches to decoder_with_past after first token (line 245-253)
- ✓ Correctly wires present.* outputs to past_key_values.* inputs (line 291)
- ✓ Reuses encoder hidden states (line 276)

**You just need the model file!**
