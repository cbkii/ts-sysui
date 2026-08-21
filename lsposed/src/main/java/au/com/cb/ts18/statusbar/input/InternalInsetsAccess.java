package au.com.cb.ts18.statusbar.input;

import android.graphics.Region;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Exact cached access to Android 10 InternalInsetsInfo state. */
final class InternalInsetsAccess {
    private static final Map<Class<?>, Contract> CONTRACTS = new ConcurrentHashMap<>();

    private InternalInsetsAccess() {}

    static Snapshot read(Object info) throws ReflectiveOperationException {
        if (info == null) throw new IllegalArgumentException("null InternalInsetsInfo");
        Contract contract = CONTRACTS.get(info.getClass());
        if (contract == null) {
            contract = Contract.resolve(info.getClass());
            CONTRACTS.put(info.getClass(), contract);
        }
        Region region = (Region) contract.region.get(info);
        int mode = contract.mode.getInt(info);
        return new Snapshot(region, mode, contract.regionMode);
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
        final int regionMode;

        Contract(Field region, Field mode, int regionMode) {
            this.region = region;
            this.mode = mode;
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

            Field constant = infoClass.getField("TOUCHABLE_INSETS_REGION");
            int regionMode = constant.getInt(null);
            return new Contract(region, mode, regionMode);
        }
    }
}
