package au.com.cb.ts18.statusbar.input;

import android.graphics.Point;
import android.view.Display;
import android.view.View;

final class CoordinateSpaceVerifier {
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private CoordinateSpaceVerifier() {}

    @SuppressWarnings("deprecation")
    static Snapshot inspect(View root) {
        Display display = root.getDisplay();
        if (display == null) return Snapshot.invalid("no-display", 0, 0, root.getWidth(), 0);
        Scratch scratch = SCRATCH.get();
        root.getLocationOnScreen(scratch.location);
        display.getRealSize(scratch.realSize);
        CoordinateSpacePolicy.Result policy = CoordinateSpacePolicy.evaluate(
                scratch.location[0], scratch.location[1], root.getWidth(), scratch.realSize.x);
        return new Snapshot(policy.valid, policy.reason,
                scratch.location[0], scratch.location[1], root.getWidth(), scratch.realSize.x);
    }

    static final class Snapshot {
        final boolean valid;
        final String reason;
        final int rootX;
        final int rootY;
        final int rootWidth;
        final int physicalWidth;

        Snapshot(boolean valid, String reason, int rootX, int rootY, int rootWidth, int physicalWidth) {
            this.valid = valid;
            this.reason = reason;
            this.rootX = rootX;
            this.rootY = rootY;
            this.rootWidth = rootWidth;
            this.physicalWidth = physicalWidth;
        }

        static Snapshot invalid(String reason, int x, int y, int width, int physicalWidth) {
            return new Snapshot(false, reason, x, y, width, physicalWidth);
        }
    }

    private static final class Scratch {
        final int[] location = new int[2];
        final Point realSize = new Point();
    }
}
