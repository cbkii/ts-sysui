package au.com.cb.ts18.statusbar.input;

import java.util.List;

/** Pure preflight for proportional reflow inside the exact weighted Topway host. */
final class TopwayWeightedNavPolicy {
    enum FailureReason {
        NONE,
        INVALID_HOST,
        NO_ACTIONS,
        TARGET_BELOW_PRODUCTION,
        WIDTH_BELOW_ABSOLUTE_FLOOR,
        AMBIGUOUS_STOCK_WEIGHTS,
        HEIGHT_TOO_SMALL
    }

    private static final float WEIGHT_EPSILON = 0.001f;
    private static final int PRODUCTION_VERTICAL_TOUCH_DP = 56;
    private static final int ABSOLUTE_HORIZONTAL_TOUCH_DP = 48;

    private TopwayWeightedNavPolicy() {}

    static Result evaluate(int hostWidthPx,
                           int hostHeightPx,
                           float density,
                           List<Float> visibleStockWeights,
                           int actionCount,
                           int requestedTouchDp) {
        if (hostWidthPx <= 0 || hostHeightPx <= 0 || density <= 0f
                || Float.isNaN(density) || Float.isInfinite(density)) {
            return Result.failure(FailureReason.INVALID_HOST);
        }
        if (actionCount <= 0 || actionCount > NavAction.values().length) {
            return Result.failure(FailureReason.NO_ACTIONS);
        }
        if (requestedTouchDp < PRODUCTION_VERTICAL_TOUCH_DP) {
            return Result.failure(FailureReason.TARGET_BELOW_PRODUCTION);
        }

        // The OEM strip itself defines the horizontal interaction width. Do not
        // reject a full-width module cell merely because density/padding makes the
        // stock strip fractionally narrower than 56dp. Keep a hard 48dp floor,
        // report whether 56dp is preferred/met, and never widen the strip here.
        int minimumHorizontalPx = (int) Math.ceil(
                ABSOLUTE_HORIZONTAL_TOUCH_DP * (double) density);
        int preferredHorizontalPx = (int) Math.ceil(
                PRODUCTION_VERTICAL_TOUCH_DP * (double) density);
        if (hostWidthPx < minimumHorizontalPx) {
            return Result.failure(FailureReason.WIDTH_BELOW_ABSOLUTE_FLOOR);
        }

        if (visibleStockWeights == null || visibleStockWeights.isEmpty()) {
            return Result.failure(FailureReason.AMBIGUOUS_STOCK_WEIGHTS);
        }

        float unitWeight = visibleStockWeights.get(0) == null
                ? 0f : visibleStockWeights.get(0);
        if (!(unitWeight > 0f) || Float.isInfinite(unitWeight) || Float.isNaN(unitWeight)) {
            return Result.failure(FailureReason.AMBIGUOUS_STOCK_WEIGHTS);
        }
        float stockWeight = 0f;
        for (Float value : visibleStockWeights) {
            if (value == null || !(value > 0f) || Float.isNaN(value)
                    || Float.isInfinite(value)
                    || Math.abs(value - unitWeight) > WEIGHT_EPSILON) {
                return Result.failure(FailureReason.AMBIGUOUS_STOCK_WEIGHTS);
            }
            stockWeight += value;
        }

        float groupWeight = unitWeight * actionCount;
        float totalWeight = stockWeight + groupWeight;
        int projectedCellPx = (int) Math.floor(hostHeightPx * (double) unitWeight
                / totalWeight);
        int minimumVerticalPx = (int) Math.ceil(requestedTouchDp * (double) density);
        if (projectedCellPx < minimumVerticalPx) {
            return Result.failure(FailureReason.HEIGHT_TOO_SMALL);
        }
        return Result.success(unitWeight, groupWeight,
                minimumVerticalPx, minimumHorizontalPx, preferredHorizontalPx,
                hostWidthPx >= preferredHorizontalPx, projectedCellPx, hostWidthPx);
    }

    static final class Result {
        final boolean safe;
        final FailureReason failureReason;
        final float stockUnitWeight;
        final float mediaGroupWeight;
        /** Backward-compatible alias for the required vertical target. */
        final int minimumTouchPx;
        final int minimumHorizontalPx;
        final int preferredHorizontalPx;
        final boolean horizontalPreferredMet;
        final int projectedCellPx;
        final int hostWidthPx;

        private Result(boolean safe,
                       FailureReason failureReason,
                       float stockUnitWeight,
                       float mediaGroupWeight,
                       int minimumTouchPx,
                       int minimumHorizontalPx,
                       int preferredHorizontalPx,
                       boolean horizontalPreferredMet,
                       int projectedCellPx,
                       int hostWidthPx) {
            this.safe = safe;
            this.failureReason = failureReason;
            this.stockUnitWeight = stockUnitWeight;
            this.mediaGroupWeight = mediaGroupWeight;
            this.minimumTouchPx = minimumTouchPx;
            this.minimumHorizontalPx = minimumHorizontalPx;
            this.preferredHorizontalPx = preferredHorizontalPx;
            this.horizontalPreferredMet = horizontalPreferredMet;
            this.projectedCellPx = projectedCellPx;
            this.hostWidthPx = hostWidthPx;
        }

        static Result failure(FailureReason reason) {
            return new Result(false, reason, 0f, 0f,
                    0, 0, 0, false, 0, 0);
        }

        static Result success(float unitWeight, float groupWeight,
                              int minimumVerticalPx, int minimumHorizontalPx,
                              int preferredHorizontalPx, boolean horizontalPreferredMet,
                              int projectedCellPx, int hostWidthPx) {
            return new Result(true, FailureReason.NONE, unitWeight, groupWeight,
                    minimumVerticalPx, minimumHorizontalPx, preferredHorizontalPx,
                    horizontalPreferredMet, projectedCellPx, hostWidthPx);
        }
    }
}
