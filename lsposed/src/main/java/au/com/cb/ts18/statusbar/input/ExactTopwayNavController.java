package au.com.cb.ts18.statusbar.input;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns one reversible media group in the exact weighted Topway navbar host. */
final class ExactTopwayNavController {
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String MODULE_PACKAGE = "au.com.cb.ts18.statusbar.input";
    private static final long CONFIGURATION_POLL_MS = 2500L;
    private static final Object OWNER = new Object();

    // Exact names recovered from the supplied SystemUI.apk. The five controls
    // physically reported on the current unit are mandatory. Screen/power and
    // the app slot are known decoded children but may be GONE/omitted at runtime.
    private static final String[] REQUIRED_STOCK_IDS = {
            "navbar_home", "navbar_back", "navbar_history",
            "navbar_volume_plus", "navbar_volume_reduce"
    };
    private static final String[] OPTIONAL_STOCK_IDS = {
            "navbar_guanping", "navbar_app"
    };

    private static Binding current;

    private ExactTopwayNavController() {}

    static synchronized void onInflated(View root) {
        if (root == null || !NavFeatureRuntime.isOperational()) return;
        if (current != null && current.root == root) {
            current.reconcileSoon();
            return;
        }
        if (current != null) current.detach();
        current = new Binding(root);
        current.attach();
    }

    static synchronized void requestReconcile() {
        if (current != null) current.reconcileSoon();
    }

    static synchronized void appendStatus(Bundle out) {
        if (out == null) return;
        Binding binding = current;
        out.putBoolean("nav_root_seen", binding != null);
        out.putBoolean("nav_root_attached", binding != null && binding.root.isAttachedToWindow());
        out.putBoolean("nav_host_seen", binding != null && binding.hostSeen);
        out.putString("nav_preflight_reason",
                binding == null ? "navigation-root-not-seen" : binding.lastStopReason);
        out.putInt("nav_host_width_px", binding == null ? 0 : binding.lastHostWidthPx);
        out.putInt("nav_host_height_px", binding == null ? 0 : binding.lastHostHeightPx);
        out.putFloat("nav_density", binding == null ? 0f : binding.lastDensity);
        out.putInt("nav_projected_cell_px", binding == null ? 0 : binding.lastProjectedCellPx);
        out.putInt("nav_horizontal_width_px", binding == null ? 0 : binding.lastHostWidthPx);
        out.putInt("nav_horizontal_floor_px", binding == null ? 0 : binding.lastHorizontalFloorPx);
        out.putInt("nav_horizontal_preferred_px", binding == null ? 0 : binding.lastHorizontalPreferredPx);
        out.putBoolean("nav_horizontal_preferred_met",
                binding != null && binding.lastHorizontalPreferredMet);
        out.putString("nav_stock_summary", binding == null ? "" : binding.lastStockSummary);
        out.putString("nav_direct_children", binding == null ? "" : binding.lastDirectChildren);
        out.putString("nav_injected_actions", binding == null ? "none"
                : actionIds(binding.ownedActions));
        NavMediaSessionRepository.Snapshot media = binding == null
                ? NavMediaSessionRepository.Snapshot.empty() : binding.lastMediaSnapshot;
        out.putInt("nav_media_controller_count", media.controllerCount);
        out.putString("nav_media_selected_package", media.selectedPackage);
        out.putInt("nav_media_playback_state", media.playbackState);
        out.putLong("nav_media_action_bits", media.actionBits);
        out.putBoolean("nav_media_previous_enabled", media.previousEnabled);
        out.putBoolean("nav_media_play_pause_enabled", media.playPauseEnabled);
        out.putBoolean("nav_media_next_enabled", media.nextEnabled);
        VehicleStatePolicy.Decision vehicle = ExactXtServiceObserver.vehicleDecision();
        out.putBoolean("nav_vehicle_veto", !vehicle.allowNavMedia);
        out.putString("nav_vehicle_veto_reason", vehicle.reason);
    }

    static void failOpen() {
        Binding target;
        synchronized (ExactTopwayNavController.class) {
            target = current;
            current = null;
        }
        if (target == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            target.detach();
        } else {
            new Handler(Looper.getMainLooper()).post(target::detach);
        }
    }

    private static final class Binding implements View.OnAttachStateChangeListener,
            View.OnLayoutChangeListener {
        final View root;
        final Map<NavAction, ImageButton> buttons = new EnumMap<>(NavAction.class);
        LinearLayout host;
        LinearLayout ownedGroup;
        List<NavAction> ownedActions = Collections.emptyList();
        NavMediaSessionRepository media;
        NavMediaSessionRepository.Snapshot lastMediaSnapshot =
                NavMediaSessionRepository.Snapshot.empty();
        boolean reconciling;
        boolean detached;
        boolean hostSeen;
        String lastStopReason = "not-reconciled";
        int lastHostWidthPx;
        int lastHostHeightPx;
        float lastDensity;
        int lastProjectedCellPx;
        int lastHorizontalFloorPx;
        int lastHorizontalPreferredPx;
        boolean lastHorizontalPreferredMet;
        String lastStockSummary = "";
        String lastDirectChildren = "";
        final Runnable reconcileRunnable = this::reconcile;
        final Runnable configurationPoll = this::reconcile;

        Binding(View root) {
            this.root = root;
        }

        void attach() {
            root.addOnAttachStateChangeListener(this);
            root.addOnLayoutChangeListener(this);
            ExactSystemUiIdentity.start(root.getContext());
            ExactSystemUiIdentity.whenResolved(this::reconcileSoon);
            reconcileSoon();
        }

        void detach() {
            if (detached) return;
            detached = true;
            root.removeOnAttachStateChangeListener(this);
            root.removeOnLayoutChangeListener(this);
            root.removeCallbacks(reconcileRunnable);
            root.removeCallbacks(configurationPoll);
            removeOwnedGroup();
            NavHierarchyProbe.detach(root);
            if (NavBarState.root() == root) NavBarState.clear();
        }

        @Override public void onViewAttachedToWindow(View view) {
            reconcileSoon();
        }

        @Override public void onViewDetachedFromWindow(View view) {
            root.removeCallbacks(reconcileRunnable);
            root.removeCallbacks(configurationPoll);
            removeOwnedGroup();
            NavHierarchyProbe.detach(root);
        }

        @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                             int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                reconcileSoon();
            }
        }

        void reconcileSoon() {
            if (detached) return;
            root.removeCallbacks(reconcileRunnable);
            root.post(reconcileRunnable);
        }

        void reconcile() {
            if (detached || reconciling || !NavFeatureRuntime.isOperational()) return;
            reconciling = true;
            NavConfig.Snapshot config = null;
            try {
                NavBarState.Capture capture = NavBarState.capture(root, root.getLayoutParams());
                NavConfig.invalidate();
                config = NavConfig.get(root.getContext());
                if (config.probeEnabled) {
                    NavHierarchyProbe.sync(root, capture.generation);
                } else {
                    NavHierarchyProbe.detach(root);
                }

                if (!config.enabled || config.actions.isEmpty()) {
                    removeOwnedGroup();
                    stop(config.actions.isEmpty() ? "disabled-or-no-actions" : "disabled",
                            config.debug);
                    return;
                }
                if (!root.isAttachedToWindow() || root.getWidth() <= 0 || root.getHeight() <= 0) {
                    removeOwnedGroup();
                    stop("root-not-laid-out", config.debug);
                    return;
                }
                if (!ExactSystemUiIdentity.isSupported()) {
                    removeOwnedGroup();
                    stop("identity-" + ExactSystemUiIdentity.state() + '-'
                            + ExactSystemUiIdentity.detail(), config.debug);
                    return;
                }
                VehicleStatePolicy.Decision vehicle = ExactXtServiceObserver.vehicleDecision();
                if (!vehicle.allowNavMedia) {
                    removeOwnedGroup();
                    stop("vehicle-" + vehicle.reason, config.debug);
                    return;
                }

                Preflight preflight = preflight(config);
                if (!preflight.safe) {
                    removeOwnedGroup();
                    stop(preflight.reason, config.debug);
                    return;
                }
                if (ownedGroup != null && ownedGroup.getParent() == preflight.host
                        && ownedActions.equals(config.actions)) {
                    host = preflight.host;
                    lastStopReason = "active";
                    return;
                }

                removeOwnedGroup();
                inject(preflight, config);
                lastStopReason = "active";
                RateLimitedLog.always("exact right-nav media group active: actions="
                        + actionIds(config.actions) + " projectedCellPx="
                        + preflight.policy.projectedCellPx + " verticalMinimumPx="
                        + preflight.policy.minimumTouchPx + " hostWidthPx="
                        + preflight.policy.hostWidthPx + " horizontalPreferredMet="
                        + preflight.policy.horizontalPreferredMet);
            } catch (Throwable t) {
                removeOwnedGroup();
                lastStopReason = "exception-" + t.getClass().getSimpleName();
                NavFeatureRuntime.recordFailure("reconcile", t);
            } finally {
                reconciling = false;
                root.removeCallbacks(configurationPoll);
                if (!detached && config != null
                        && (config.enabled || config.probeEnabled)) {
                    root.postDelayed(configurationPoll, CONFIGURATION_POLL_MS);
                }
            }
        }

        private Preflight preflight(NavConfig.Snapshot config) {
            Resources resources = root.getResources();
            int hostId = resources.getIdentifier("navbar_left", "id", SYSTEM_UI_PACKAGE);
            if (hostId == 0) return Preflight.failure("missing-navbar_left-id");
            View candidate = root.findViewById(hostId);
            if (!(candidate instanceof LinearLayout)) {
                return Preflight.failure("navbar_left-not-linear-layout");
            }
            LinearLayout exactHost = (LinearLayout) candidate;
            hostSeen = true;
            lastHostWidthPx = exactHost.getWidth()
                    - exactHost.getPaddingLeft() - exactHost.getPaddingRight();
            lastHostHeightPx = exactHost.getHeight()
                    - exactHost.getPaddingTop() - exactHost.getPaddingBottom();
            lastDensity = exactHost.getResources().getDisplayMetrics().density;
            lastDirectChildren = directChildSummary(resources, exactHost);
            lastStockSummary = "direct=" + lastDirectChildren;
            if (exactHost.getOrientation() != LinearLayout.VERTICAL) {
                return Preflight.failure("navbar_left-not-vertical");
            }
            if (exactHost.getWeightSum() > 0f) {
                return Preflight.failure("explicit-host-weight-sum");
            }

            Set<Integer> knownIds = new HashSet<>();
            Set<Integer> requiredIds = new HashSet<>();
            List<Float> visibleWeights = new ArrayList<>();
            StringBuilder summary = new StringBuilder();
            int insertionIndex = Integer.MAX_VALUE;

            for (String name : REQUIRED_STOCK_IDS) {
                int id = resources.getIdentifier(name, "id", SYSTEM_UI_PACKAGE);
                if (id == 0 || !knownIds.add(id) || !requiredIds.add(id)) {
                    return Preflight.failure("missing-or-duplicate-stock-id-" + name);
                }
                View child = exactHost.findViewById(id);
                if (child == null || child.getParent() != exactHost) {
                    return Preflight.failure("stock-child-not-direct-" + name);
                }
                String failure = inspectKnownChild(exactHost, child, name,
                        visibleWeights, summary);
                if (failure != null) return Preflight.failure(failure);
                if ("navbar_volume_plus".equals(name)
                        || "navbar_volume_reduce".equals(name)) {
                    insertionIndex = Math.min(insertionIndex, exactHost.indexOfChild(child));
                }
            }

            for (String name : OPTIONAL_STOCK_IDS) {
                int id = resources.getIdentifier(name, "id", SYSTEM_UI_PACKAGE);
                if (id == 0) continue;
                if (!knownIds.add(id)) return Preflight.failure("duplicate-known-id-" + name);
                View child = exactHost.findViewById(id);
                if (child == null) {
                    appendSummary(summary, name + "=absent");
                    continue;
                }
                if (child.getParent() != exactHost) {
                    return Preflight.failure("optional-stock-child-not-direct-" + name);
                }
                String failure = inspectKnownChild(exactHost, child, name,
                        visibleWeights, summary);
                if (failure != null) return Preflight.failure(failure);
            }

            Set<Integer> seenDirectIds = new HashSet<>();
            for (int i = 0; i < exactHost.getChildCount(); i++) {
                View child = exactHost.getChildAt(i);
                if (child == ownedGroup && isOwned(child)) continue;
                if (!knownIds.contains(child.getId()) || !seenDirectIds.add(child.getId())) {
                    return Preflight.failure("unknown-or-duplicate-direct-child-"
                            + resourceEntryName(resources, child.getId()));
                }
            }
            if (!seenDirectIds.containsAll(requiredIds)) {
                return Preflight.failure("missing-required-direct-child");
            }
            if (insertionIndex == Integer.MAX_VALUE) {
                return Preflight.failure("volume-anchor-missing");
            }
            if (ownedGroup != null && ownedGroup.getParent() == exactHost
                    && exactHost.indexOfChild(ownedGroup) < insertionIndex) {
                insertionIndex--;
            }

            lastStockSummary = "direct=" + lastDirectChildren + "; known=" + summary;
            TopwayWeightedNavPolicy.Result policy = TopwayWeightedNavPolicy.evaluate(
                    lastHostWidthPx, lastHostHeightPx, lastDensity,
                    visibleWeights, config.actions.size(), config.minTouchDp);
            if (!policy.safe) {
                return Preflight.failure("weighted-policy-" + policy.failureReason);
            }
            lastProjectedCellPx = policy.projectedCellPx;
            lastHorizontalFloorPx = policy.minimumHorizontalPx;
            lastHorizontalPreferredPx = policy.preferredHorizontalPx;
            lastHorizontalPreferredMet = policy.horizontalPreferredMet;
            return Preflight.success(exactHost, insertionIndex, policy);
        }

        private String inspectKnownChild(LinearLayout exactHost, View child, String name,
                                         List<Float> visibleWeights, StringBuilder summary) {
            ViewGroup.LayoutParams raw = child.getLayoutParams();
            if (!(raw instanceof LinearLayout.LayoutParams)) {
                return "stock-layout-params-" + name;
            }
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) raw;
            if (params.height != 0 || params.weight <= 0f
                    || Float.isNaN(params.weight) || Float.isInfinite(params.weight)) {
                return "stock-not-weighted-" + name;
            }
            if (child.getVisibility() != View.GONE) visibleWeights.add(params.weight);
            appendSummary(summary, name + "=" + visibility(child.getVisibility())
                    + ",index:" + exactHost.indexOfChild(child)
                    + ",weight:" + params.weight);
            return null;
        }

        private void inject(Preflight preflight, NavConfig.Snapshot config) throws Exception {
            Context moduleContext = root.getContext().createPackageContext(
                    MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
            LinearLayout group = new LinearLayout(root.getContext());
            group.setOrientation(LinearLayout.VERTICAL);
            group.setId(View.generateViewId());
            group.setTag(R.id.ts18_nav_owner_tag, OWNER);
            group.setClipChildren(false);
            group.setClipToPadding(false);
            group.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0,
                    preflight.policy.mediaGroupWeight));

            buttons.clear();
            for (NavAction action : config.actions) {
                ImageButton button = createButton(moduleContext, action);
                group.addView(button, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                buttons.put(action, button);
            }
            preflight.host.addView(group, preflight.insertionIndex);
            host = preflight.host;
            ownedGroup = group;
            ownedActions = Collections.unmodifiableList(new ArrayList<>(config.actions));

            media = new NavMediaSessionRepository(root.getContext(), ownedActions,
                    this::applySnapshot);
            applySnapshot(NavMediaSessionRepository.Snapshot.empty());
            media.start();
        }

        private ImageButton createButton(Context moduleContext, NavAction action) {
            ImageButton button = new ImageButton(root.getContext());
            button.setId(View.generateViewId());
            button.setTag(R.id.ts18_nav_owner_tag, OWNER);
            TypedValue selectable = new TypedValue();
            if (root.getContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackgroundBorderless, selectable, true)
                    && selectable.resourceId != 0) {
                button.setBackgroundResource(selectable.resourceId);
            } else {
                button.setBackgroundColor(Color.TRANSPARENT);
            }
            button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            int padding = Math.max(0, Math.round(8f
                    * root.getResources().getDisplayMetrics().density));
            button.setPadding(padding, padding, padding, padding);
            button.setImageDrawable(drawable(moduleContext, iconFor(action, false)));
            button.setContentDescription(moduleContext.getString(labelFor(action)));
            button.setVisibility(View.VISIBLE);
            button.setEnabled(false);
            button.setAlpha(0.38f);
            button.setOnClickListener(view -> {
                NavMediaSessionRepository repository = media;
                VehicleStatePolicy.Decision vehicle = ExactXtServiceObserver.vehicleDecision();
                if (repository != null && view.isEnabled() && vehicle.allowNavMedia) {
                    repository.dispatch(action);
                } else if (!vehicle.allowNavMedia) {
                    RateLimitedLog.always("media tap suppressed by vehicle-state veto: "
                            + vehicle.reason);
                    ExactTopwayNavVisibilityMonitor.requestVehicleStateReevaluation();
                }
            });
            return button;
        }

        private void applySnapshot(NavMediaSessionRepository.Snapshot snapshot) {
            if (detached || ownedGroup == null || snapshot == null) return;
            lastMediaSnapshot = snapshot;
            try {
                Context moduleContext = root.getContext().createPackageContext(
                        MODULE_PACKAGE, Context.CONTEXT_IGNORE_SECURITY);
                for (Map.Entry<NavAction, ImageButton> entry : buttons.entrySet()) {
                    NavAction action = entry.getKey();
                    ImageButton button = entry.getValue();
                    boolean enabled = snapshot.enabled(action);
                    button.setVisibility(View.VISIBLE);
                    button.setEnabled(enabled);
                    button.setAlpha(enabled ? 1f : 0.38f);
                    if (action == NavAction.PLAY_PAUSE) {
                        button.setImageDrawable(drawable(moduleContext,
                                iconFor(action, snapshot.playing)));
                        button.setContentDescription(moduleContext.getString(
                                snapshot.playing ? R.string.ts18_nav_pause
                                        : R.string.ts18_nav_play));
                    }
                }
            } catch (Throwable t) {
                NavFeatureRuntime.recordFailure("presentation", t);
            }
        }

        private void removeOwnedGroup() {
            NavMediaSessionRepository repository = media;
            media = null;
            if (repository != null) repository.stop();
            lastMediaSnapshot = NavMediaSessionRepository.Snapshot.empty();
            LinearLayout group = ownedGroup;
            ownedGroup = null;
            ownedActions = Collections.emptyList();
            buttons.clear();
            if (group != null && isOwned(group) && group.getParent() instanceof ViewGroup) {
                ((ViewGroup) group.getParent()).removeView(group);
            }
            host = null;
        }

        private void stop(String reason, boolean debug) {
            if (!reason.equals(lastStopReason)) {
                lastStopReason = reason;
                RateLimitedLog.always("right-nav kept stock: " + reason
                        + " directChildren=" + lastDirectChildren);
            } else {
                RateLimitedLog.debug(debug, "right-nav kept stock: " + reason
                        + " directChildren=" + lastDirectChildren);
            }
        }
    }

    private static String directChildSummary(Resources resources, LinearLayout host) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < host.getChildCount(); i++) {
            View child = host.getChildAt(i);
            if (child == null) continue;
            if (out.length() > 0) out.append(';');
            out.append(i).append(':').append(resourceEntryName(resources, child.getId()))
                    .append(':').append(visibility(child.getVisibility()));
        }
        return out.toString();
    }

    private static String resourceEntryName(Resources resources, int id) {
        if (id == View.NO_ID) return "NO_ID";
        try {
            return resources.getResourcePackageName(id) + ":id/"
                    + resources.getResourceEntryName(id);
        } catch (Throwable ignored) {
            return "id=" + id;
        }
    }

    private static void appendSummary(StringBuilder out, String value) {
        if (out.length() > 0) out.append("; ");
        out.append(value);
    }

    private static String visibility(int value) {
        if (value == View.VISIBLE) return "VISIBLE";
        if (value == View.INVISIBLE) return "INVISIBLE";
        if (value == View.GONE) return "GONE";
        return Integer.toString(value);
    }

    private static boolean isOwned(View view) {
        return view != null && view.getTag(R.id.ts18_nav_owner_tag) == OWNER;
    }

    private static Drawable drawable(Context moduleContext, int id) {
        return moduleContext.getDrawable(id);
    }

    private static int iconFor(NavAction action, boolean playing) {
        switch (action) {
            case PREVIOUS: return R.drawable.ic_ts18_media_previous;
            case PLAY_PAUSE:
                return playing ? R.drawable.ic_ts18_media_pause : R.drawable.ic_ts18_media_play;
            case NEXT: return R.drawable.ic_ts18_media_next;
            default: throw new IllegalArgumentException("unsupported nav action");
        }
    }

    private static int labelFor(NavAction action) {
        switch (action) {
            case PREVIOUS: return R.string.ts18_nav_previous;
            case PLAY_PAUSE: return R.string.ts18_nav_play;
            case NEXT: return R.string.ts18_nav_next;
            default: throw new IllegalArgumentException("unsupported nav action");
        }
    }

    private static String actionIds(List<NavAction> actions) {
        if (actions == null || actions.isEmpty()) return "none";
        StringBuilder out = new StringBuilder();
        for (NavAction action : actions) {
            if (out.length() > 0) out.append(',');
            out.append(action.id());
        }
        return out.toString();
    }

    private static final class Preflight {
        final boolean safe;
        final String reason;
        final LinearLayout host;
        final int insertionIndex;
        final TopwayWeightedNavPolicy.Result policy;

        private Preflight(boolean safe, String reason, LinearLayout host,
                          int insertionIndex, TopwayWeightedNavPolicy.Result policy) {
            this.safe = safe;
            this.reason = reason;
            this.host = host;
            this.insertionIndex = insertionIndex;
            this.policy = policy;
        }

        static Preflight failure(String reason) {
            return new Preflight(false, reason, null, -1, null);
        }

        static Preflight success(LinearLayout host, int insertionIndex,
                                 TopwayWeightedNavPolicy.Result policy) {
            return new Preflight(true, "safe", host, insertionIndex, policy);
        }
    }
}
