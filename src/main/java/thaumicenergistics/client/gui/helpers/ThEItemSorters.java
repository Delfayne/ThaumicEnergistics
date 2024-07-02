package thaumicenergistics.client.gui.helpers;

import appeng.api.config.SortDir;
import appeng.api.storage.data.IAEStack;
import appeng.integration.Integrations;
import appeng.integration.abstraction.IInvTweaks;
import appeng.util.Platform;
import thaumicenergistics.util.AEUtil;

import java.util.Comparator;

import static thaumicenergistics.util.AEUtil.getModID;

public class ThEItemSorters {

    private static SortDir Direction = SortDir.ASCENDING;

    public static final Comparator<IAEStack<?>> CONFIG_BASED_SORT_BY_NAME = (o1, o2) -> {
        final int cmp = AEUtil.getDisplayName(o1).compareToIgnoreCase(AEUtil.getDisplayName(o2));
        return applyDirection(cmp);
    };

    public static final Comparator<IAEStack<?>> CONFIG_BASED_SORT_BY_MOD = (o1, o2) -> {
        int cmp = getModID(o1).compareToIgnoreCase(getModID(o2));

        if (cmp == 0) {
            cmp = Platform.getItemDisplayName(o1).compareToIgnoreCase(Platform.getItemDisplayName(o2));
        }

        return applyDirection(cmp);
    };

    public static final Comparator<IAEStack<?>> CONFIG_BASED_SORT_BY_SIZE = (o1, o2) -> {
        final int cmp = Long.compare(o2.getStackSize(), o1.getStackSize());
        return applyDirection(cmp);
    };

    private static IInvTweaks api;

    public static final Comparator<IAEStack<?>> CONFIG_BASED_SORT_BY_INV_TWEAKS = (o1, o2) -> {
        if (api == null) {
            return CONFIG_BASED_SORT_BY_NAME.compare(o1, o2);
        }

        final int cmp = api.compareItems(o1.asItemStackRepresentation(), o2.asItemStackRepresentation());
        return applyDirection(cmp);
    };

    public static void init() {
        if (api != null) {
            return;
        }

        if (Integrations.invTweaks().isEnabled()) {
            api = Integrations.invTweaks();
        } else {
            api = null;
        }
    }

    private static SortDir getDirection() {
        return Direction;
    }

    public static void setDirection(final SortDir direction) {
        Direction = direction;
    }

    private static int applyDirection(int cmp) {
        if (getDirection() == SortDir.ASCENDING) {
            return cmp;
        }
        return -cmp;
    }
}
