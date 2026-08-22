package au.com.cb.ts18.statusbar.input;

import android.graphics.Region;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Exact cached access to Android 10 InternalInsetsInfo state. */
final class InternalInsetsAccess {
    private static final Map<Class<?>, Contract> CONTRACTS = new ConcurrentHashMap<>();

    private InternalInsetsAccess() {}

    static Snapshot read(Object info) throws ReflectiveOperationException {
        Contract contract = contractFor(info);
        Region region = (Region) contract.region.get(info);
        int mode = contract.mode.getInt(info);
        return new Snapshot(region, mode, contract.regionMode);
    }

    /**
     * Explicitly establishes REGION mode and then updates the existing Region.
     * This is required for the ordinary Android-Q collapsed path, where each
     * InternalInsetsInfo computation begins in FRAME mode with an empty region.
     */
    static void setTouchableRegion(Object info, int left, int top, int right, int bottom)
            throws ReflectiveOperationException {
        Contract contract = contractFor(info);
        Region region = (Region) contract.region.get(info);
        if (region == null) throw new NoSuchFieldException("touchableRegion is null");
        contract.setTouchableInsets.invoke(info, contract.regionMode);
        region.set(left, top, right, bottom);
    }

    private static Contract contractFor(Object info) throws ReflectiveOperationException {
        if (info == null) throw new IllegalArgumentException("null InternalInsetsInfo");
        Contract contract = CONTRACTS.get(info.getClass());
        if (contract == null) {
            contract = Contract.resolve(info.getClass());
            CONTRACTS.put(info.getClass(), contract);
        }
        return contract;
    }

    static final class Snapshot {
        final Region region;
        final int mode;
        final int regionMode;

        Snapshot(Region region, int mode, int regionMode) {
            this.region = region;
            this.mode = mode;
            this.regionMode = regionMode;
        }
    }

    private static final class Contract {
        final Field region;
        final Field mode;
        final Method setTouchableInsets;
        final int regionMode;

        Contract(Field region, Field mode, Method setTouchableInsets, int regionMode) {
            this.region = region;
            this.mode = mode;
            this.setTouchableInsets = setTouchableInsets;
            this.regionMode = regionMode;
        }

        static Contract resolve(Class<?> infoClass) throws ReflectiveOperationException {
            Field region;
            try {
                region = infoClass.getField("touchableRegion");
            } catch (NoSuchFieldException e) {
                region = infoClass.getDeclaredField("touchableRegion");
                region.setAccessible(true);
            }
            if (!Region.class.isAssignableFrom(region.getType())) {
                throw new NoSuchFieldException("touchableRegion is not Region");
            }

            Field mode = infoClass.getDeclaredField("mTouchableInsets");
            mode.setAccessible(true);
            if (mode.getType() != int.class) {
                throw new NoSuchFieldException("mTouchableInsets is not int");
            }

            Method setter = infoClass.getDeclaredMethod("setTouchableInsets", int.class);
            setter.setAccessible(true);
            if (setter.getReturnType() != void.class) {
                throw new NoSuchMethodException("setTouchableInsets return mismatch");
            }

            Field constant = infoClass.getField("TOUCHABLE_INSETS_REGION");
            int regionMode = constant.getInt(null);
            return new Contract(region, mode, setter, regionMode);
        }
    }
}
