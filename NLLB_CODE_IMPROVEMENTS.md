# NLLB Code Improvements

## Optional Enhancement: Better Error Handling for KV Cache

The current code at `NLLBInferenceEngine.kt` lines 280-301 works correctly, but can be improved with better validation and error messages.

### Current Code (lines 280-301)

```kotlin
// Add past key-value cache if available
if (pastKeyValues != null && hasKVCache) {
    // Add all past_key_values inputs from previous iteration
    val outputNames = pastKeyValues.iterator().asSequence().map { it.key }.toList()
    var addedCount = 0

    for (outputName in outputNames) {
        // Skip the logits output (first output)
        if (outputName == "logits") continue

        // Convert "present.X.Y.Z" to "past_key_values.X.Y.Z"
        val inputName = outputName.replace("present.", "past_key_values.")

        val pastTensor = pastKeyValues[outputName] as? OnnxTensor
        if (pastTensor != null) {
            decoderInputs[inputName] = pastTensor
            addedCount++
        }
    }
    Log.d(TAG, "Added $addedCount past_key_values tensors to decoder_with_past")
}
```

### Suggested Improvement (Optional)

Replace with this more defensive version that validates tensor names:

```kotlin
// Add past key-value cache if available
if (pastKeyValues != null && hasKVCache) {
    // Add all past_key_values inputs from previous iteration
    val outputNames = pastKeyValues.iterator().asSequence().map { it.key }.toList()
    val expectedInputNames = decoderWithPastSession!!.inputNames.toSet()
    var addedCount = 0
    var skippedCount = 0

    for (outputName in outputNames) {
        // Skip the logits output (first output)
        if (outputName == "logits") continue

        // Convert "present.X.Y.Z" to "past_key_values.X.Y.Z"
        // NLLB standard format: present.{layer}.{type}.{key|value}
        val inputName = outputName.replace("present.", "past_key_values.")

        // Verify this input name is expected by decoder_with_past
        if (!expectedInputNames.contains(inputName)) {
            if (skippedCount == 0) {
                Log.w(TAG, "⚠️ Unexpected input name: $inputName")
                Log.w(TAG, "   Decoder output: $outputName")
                Log.w(TAG, "   Expected names contain 'past': ${expectedInputNames.filter { it.contains("past") }.take(3)}")
            }
            skippedCount++
            continue
        }

        val pastTensor = pastKeyValues[outputName] as? OnnxTensor
        if (pastTensor != null) {
            decoderInputs[inputName] = pastTensor
            addedCount++
        }
    }

    if (addedCount > 0) {
        Log.d(TAG, "✓ Added $addedCount past_key_values tensors to decoder_with_past")
    } else {
        Log.e(TAG, "❌ No past_key_values tensors added! KV cache won't work.")
        Log.e(TAG, "   This usually means tensor name mismatch between decoder outputs and decoder_with_past inputs")
    }

    if (skippedCount > 0) {
        Log.w(TAG, "⚠️ Skipped $skippedCount tensors due to name mismatch")
    }
}
```

### Benefits of Improvement

1. **Validates tensor names**: Checks if the converted input name actually exists in decoder_with_past
2. **Better logging**: Shows exactly what went wrong if tensors don't match
3. **Debugging**: Helps identify if the model was exported with different tensor naming conventions
4. **Early detection**: Alerts if KV cache won't work before wasting inference time

### When to Apply

- **OPTIONAL**: Current code works fine if model is exported correctly
- **RECOMMENDED**: Apply before testing with a new model export
- **REQUIRED**: Only if you see "Added 0 past_key_values tensors" in logs after adding decoder_with_past

---

## Summary of All Required Changes

### 1. Export NLLB Model (CRITICAL - MUST DO)

```bash
python export_nllb_with_cache.py
```

This creates `decoder_with_past_model_quantized.onnx` which is currently **missing** from your app.

### 2. Copy Models to Assets (CRITICAL - MUST DO)

Copy these files from `nllb-onnx-quantized/` to `app/src/main/assets/models/nllb-200-distilled/`:

- ✓ encoder_model_quantized.onnx
- ✓ decoder_model_quantized.onnx
- ✓ **decoder_with_past_model_quantized.onnx** ← CRITICAL MISSING FILE
- ✓ tokenizer.json

### 3. Code Improvement (OPTIONAL - NICE TO HAVE)

Apply the improved error handling code above to `NLLBInferenceEngine.kt` lines 280-301.

This is **optional** - your current code is correct and will work once you add the model file.

---

## Verification After Setup

After exporting and copying models, check Android logs for:

### ✓ Success Indicators

```
✓ Loaded decoder_with_past for KV cache
✓ Model SUPPORTS KV cache (has past_key_values inputs)
Using decoder_with_past_model (with cache)
✓ Added 48 past_key_values tensors to decoder_with_past
```

### ❌ Problem Indicators

```
⚠ decoder_with_past_model not found - KV cache disabled
Using decoder_model (no cache)
Added 0 past_key_values tensors
```

If you see problem indicators, re-check that `decoder_with_past_model_quantized.onnx` exists in assets.

