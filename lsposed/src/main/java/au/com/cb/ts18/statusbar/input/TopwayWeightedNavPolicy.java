package au.com.cb.ts18.statusbar.input;

import java.util.List;

/** Pure preflight for proportional reflow inside the exact weighted Topway host. */
final class TopwayWeightedNavPolicy {
    enum FailureReason {
        NONE,
        INVALID_HOST,
        NO_ACTIONS,
        TARGET_BELOW_PRODUCTION,
        WIDTH_TOO_SMALL,
        AMBIGUOUS_STOCK_WEIGHTS,
        HEIGHT_TOO_SMALL
    }

    private static final float WEIGHT_EPSILON = 0.001f;
    private static final int PRODUCTION_MIN_TOUCH_DP = 56;

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
        if (requestedTouchDp < PRODUCTION_MIN_TOUCH_DP) {
            return Result.failure(FailureReason.TARGET_BELOW_PRODUCTION);
        }
        int minimumPx = (int) Math.ceil(requestedTouchDp * (double) density);
        if (hostWidthPx < minimumPx) {
            return Result.failure(FailureReason.WIDTH_TOO_SMALL);
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
        if (projectedCellPx < minimumPx) {
            return Result.failure(FailureReason.HEIGHT_TOO_SMALL);
        }
        return Result.success(unitWeight, groupWeight, minimumPx, projectedCellPx);
    }

    static final class Result {
        final boolean safe;
        final FailureReason failureReason;
        final float stockUnitWeight;
        final float mediaGroupWeight;
        final int minimumTouchPx;
        final int projectedCellPx;

        private Result(boolean safe,
                       FailureReason failureReason,
                       float stockUnitWeight,
                       float mediaGroupWeight,
                       int minimumTouchPx,
                       int projectedCellPx) {
            this.safe = safe;
            this.failureReason = failureReason;
            this.stockUnitWeight = stockUnitWeight;
            this.mediaGroupWeight = mediaGroupWeight;
            this.minimumTouchPx = minimumTouchPx;
            this.projectedCellPx = projectedCellPx;
        }

        static Result failure(FailureReason reason) {
            return new Result(false, reason, 0f, 0f, 0, 0);
        }

        static Result success(float unitWeight, float groupWeight,
                              int minimumPx, int projectedCellPx) {
            return new Result(true, FailureReason.NONE, unitWeight, groupWeight,
                    minimumPx, projectedCellPx);
        }
    }
}
