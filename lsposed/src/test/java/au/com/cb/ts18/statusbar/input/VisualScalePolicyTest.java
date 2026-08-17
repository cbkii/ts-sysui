package au.com.cb.ts18.statusbar.input;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class VisualScalePolicyTest {
    @Test public void newTargetLeafIsCaptured() { assertEquals(VisualScalePolicy.Action.CAPTURE_AND_APPLY, VisualScalePolicy.decide(false,false,true,false)); }
    @Test public void ownedTargetLeafCanBeReapplied() { assertEquals(VisualScalePolicy.Action.APPLY_OWNED, VisualScalePolicy.decide(true,false,true,true)); }
    @Test public void ownedLeafLeavingBarIsRestored() { assertEquals(VisualScalePolicy.Action.RESTORE_AND_RELEASE, VisualScalePolicy.decide(true,false,false,true)); }
    @Test public void externalScaleMutationReleasesWithoutOverwrite() { assertEquals(VisualScalePolicy.Action.RELEASE_CONFLICT, VisualScalePolicy.decide(true,false,true,false)); }
    @Test public void conflictedLeafStaysUntouchedUntilReset() { assertEquals(VisualScalePolicy.Action.SKIP, VisualScalePolicy.decide(false,true,true,false)); }
}
