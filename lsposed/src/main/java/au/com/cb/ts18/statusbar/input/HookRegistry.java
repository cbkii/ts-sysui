package au.com.cb.ts18.statusbar.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;

final class HookRegistry {
    private final List<XC_MethodHook.Unhook> handles = new ArrayList<>();

    void addRequired(String name, Set<XC_MethodHook.Unhook> registered) {
        if (registered == null || registered.isEmpty()) {
            throw new IllegalStateException("no methods hooked for " + name);
        }
        handles.addAll(registered);
    }

    int size() { return handles.size(); }

    void unhookAll() {
        for (int i = handles.size() - 1; i >= 0; i--) {
            try {
                handles.get(i).unhook();
            } catch (Throwable t) {
                RateLimitedLog.error("install-rollback", "failed to unhook partial installation", t);
            }
        }
        handles.clear();
    }
}
