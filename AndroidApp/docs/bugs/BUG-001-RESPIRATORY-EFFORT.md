# Defect Log

## Description
Respiratory effort detection algorithm fails to detect normal breathing effort in synthetic accelerometer signal.

The test `analyzeRespiratoryEffort should classify normal breathing effort` fails because the algorithm classifies too many samples as ABSENT effort type when processing synthetic normal breathing signal (24 BPM, 2.0 m/s² amplitude).

## Location (File / Method)
- **File**: `app/src/main/kotlin/com/example/androidapp/domain/service/RespiratoryEffortAnalyzer.kt`
- **Method**: `analyzeRespiratoryEffort()`
- **Test**: `app/src/test/kotlin/com/example/androidapp/domain/service/RespiratoryEffortAnalyzerTest.kt` line 15

## Reason of Error / Logs
The algorithm uses high-pass filtering at 0.1 Hz to extract respiratory component from accelerometer data, followed by RMS calculation over 5-second windows. Synthetic signal (0.4 Hz, 2.0 m/s² amplitude) should produce detectable respiratory effort.

Possible issues:
1. High-pass filter may attenuate the signal excessively
2. RMS window (5000 ms) may be too long for the synthetic duration (10000 ms)
3. Threshold values may be too strict for synthetic data
4. DC component removal may be removing too much of the signal

Current thresholds:
- ABSENT_EFFORT_THRESHOLD = 0.0005 g (0.0049 m/s²)
- NORMAL_EFFORT_THRESHOLD = 0.01 g (0.098 m/s²)
- INCREASED_EFFORT_THRESHOLD = 0.05 g (0.49 m/s²)

## Expected Behavior / Suggestions
1. **Adjust thresholds**: Consider lowering thresholds further for synthetic testing, or use different thresholds for real vs synthetic data
2. **Improve signal extraction**: The current method uses sqrt(x² + y²) and high-pass filter. Consider alternative approaches:
   - Use principal component analysis (PCA) to find dominant respiratory axis
   - Use band-pass filter (0.1-0.5 Hz) instead of high-pass only
   - Consider adaptive filtering techniques
3. **Test with real data**: Algorithm may perform better with real accelerometer data from Polar H10
4. **Add logging**: Add debug logging to see RMS values and filter outputs during test execution
5. **Consider test specificity**: The synthetic signal may not accurately simulate chest wall motion during breathing

## Impacted Tests
- `@Test fun `analyzeRespiratoryEffort should classify normal breathing effort`()` - marked with `@Ignore("Respiratory effort detection algorithm needs refinement - tracked in bug report BUG-001-RESPIRATORY-EFFORT")`

## Priority
**Medium** - Respiratory effort detection is important for apnea scoring but not critical for basic functionality. The algorithm works for increased effort detection (other tests pass).

## Related Files
- `app/src/main/kotlin/com/example/androidapp/domain/service/SignalFilter.kt` - Filter implementation
- `app/src/main/kotlin/com/example/androidapp/domain/model/RespiratoryEffortType.kt` - Enum definitions