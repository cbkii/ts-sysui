package au.com.cb.ts18.statusbar.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class NavLayoutPolicy {
    enum FailureReason {
        NONE,
        NO_NAV_ROOT,
        ROOT_NOT_STABLE,
        AMBIGUOUS_HIERARCHY,
        NO_CONFIRMED_HOST,
        NO_SAFE_FREE_SPACE,
        TARGET_TOO_SMALL,
        STOCK_OVERLAP,
        UNSUPPORTED_REINFLATION
    }

    private NavLayoutPolicy() {}

    static Result place(int navbarHeightPx,
                        List<Interval> occupied,
                        int requestedActions,
                        int targetPx,
                        int minimumTouchPx,
                        int interButtonGapPx,
                        int stockGapPx) {
        if (navbarHeightPx <= 0) return Result.failure(FailureReason.NO_NAV_ROOT);
        if (requestedActions <= 0) return Result.success(Collections.emptyList(), true);
        if (minimumTouchPx <= 0 || targetPx < minimumTouchPx) {
            return Result.failure(FailureReason.TARGET_TOO_SMALL);
        }
        if (interButtonGapPx < 0 || stockGapPx < 0) {
            return Result.failure(FailureReason.AMBIGUOUS_HIERARCHY);
        }

        List<Interval> blocked = normaliseOccupied(
                navbarHeightPx, occupied, stockGapPx);
        if (blocked == null) return Result.failure(FailureReason.AMBIGUOUS_HIERARCHY);

        List<Interval> free = freeIntervals(navbarHeightPx, blocked);
        Interval best = null;
        int bestCapacity = 0;
        for (Interval candidate : free) {
            int capacity = capacity(candidate.height(), targetPx, interButtonGapPx);
            if (capacity > bestCapacity
                    || (capacity == bestCapacity && capacity > 0
                    && (best == null || candidate.height() > best.height()))) {
                best = candidate;
                bestCapacity = capacity;
            }
        }
        if (best == null || bestCapacity <= 0) {
            return Result.failure(FailureReason.NO_SAFE_FREE_SPACE);
        }

        int visible = Math.min(requestedActions, bestCapacity);
        int required = visible * targetPx + Math.max(0, visible - 1) * interButtonGapPx;
        int top = best.top + Math.max(0, (best.height() - required) / 2);
        List<Slot> slots = new ArrayList<>(visible);
        for (int i = 0; i < visible; i++) {
            int bottom = top + targetPx;
            Slot slot = new Slot(i, top, bottom);
            if (overlapsAny(slot, blocked)) {
                return Result.failure(FailureReason.STOCK_OVERLAP);
            }
            slots.add(slot);
            top = bottom + interButtonGapPx;
        }
        return Result.success(Collections.unmodifiableList(slots),
                visible == requestedActions);
    }

    private static List<Interval> normaliseOccupied(int height, List<Interval> occupied,
                                                    int stockGapPx) {
        List<Interval> blocked = new ArrayList<>();
        if (occupied != null) {
            for (Interval interval : occupied) {
                if (interval == null || interval.bottom <= interval.top) return null;
                if (interval.bottom <= 0 || interval.top >= height) continue;
                int top = Math.max(0, interval.top - stockGapPx);
                int bottom = Math.min(height, interval.bottom + stockGapPx);
                if (bottom > top) blocked.add(new Interval(top, bottom));
            }
        }
        blocked.sort(Comparator.comparingInt(value -> value.top));

        List<Interval> merged = new ArrayList<>();
        for (Interval current : blocked) {
            if (merged.isEmpty()) {
                merged.add(current);
                continue;
            }
            Interval last = merged.get(merged.size() - 1);
            if (current.top <= last.bottom) {
                merged.set(merged.size() - 1,
                        new Interval(last.top, Math.max(last.bottom, current.bottom)));
            } else {
                merged.add(current);
            }
        }
        return merged;
    }

    private static List<Interval> freeIntervals(int height, List<Interval> blocked) {
        List<Interval> free = new ArrayList<>();
        int cursor = 0;
        for (Interval interval : blocked) {
            if (interval.top > cursor) free.add(new Interval(cursor, interval.top));
            cursor = Math.max(cursor, interval.bottom);
        }
        if (cursor < height) free.add(new Interval(cursor, height));
        return free;
    }

    private static int capacity(int height, int targetPx, int gapPx) {
        if (height < targetPx) return 0;
        return (height + gapPx) / (targetPx + gapPx);
    }

    private static boolean overlapsAny(Slot slot, List<Interval> blocked) {
        for (Interval interval : blocked) {
            if (slot.top < interval.bottom && slot.bottom > interval.top) return true;
        }
        return false;
    }

    static final class Interval {
        final int top;
        final int bottom;

        Interval(int top, int bottom) {
            this.top = top;
            this.bottom = bottom;
        }

        int height() {
            return bottom - top;
        }
    }

    static final class Slot {
        final int actionIndex;
        final int top;
        final int bottom;

        Slot(int actionIndex, int top, int bottom) {
            this.actionIndex = actionIndex;
            this.top = top;
            this.bottom = bottom;
        }
    }

    static final class Result {
        final FailureReason failureReason;
        final List<Slot> slots;
        final boolean allRequestedFit;

        private Result(FailureReason failureReason, List<Slot> slots, boolean allRequestedFit) {
            this.failureReason = failureReason;
            this.slots = slots;
            this.allRequestedFit = allRequestedFit;
        }

        static Result failure(FailureReason reason) {
            return new Result(reason, Collections.emptyList(), false);
        }

        static Result success(List<Slot> slots, boolean allRequestedFit) {
            return new Result(FailureReason.NONE, slots, allRequestedFit);
        }

        boolean isSafe() {
            return failureReason == FailureReason.NONE;
        }
    }
}
