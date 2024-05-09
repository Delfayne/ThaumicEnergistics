package thaumicenergistics.client.gui.helpers;

import appeng.api.config.SearchBoxMode;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;
import invtweaks.api.InvTweaksAPI;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumicenergistics.api.ThEApi;
import thaumicenergistics.api.config.PrefixSetting;
import thaumicenergistics.integration.invtweaks.ThEInvTweaks;
import thaumicenergistics.integration.jei.ThEJEI;
import thaumicenergistics.util.AEUtil;
import thaumicenergistics.util.TCUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static appeng.api.config.ViewItems.CRAFTABLE;
import static appeng.api.config.ViewItems.STORED;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static java.util.regex.Pattern.UNICODE_CASE;

/**
 * Based on ItemRepo and FluidRepo
 *
 * @author BrockWS
 * @author Alex811
 */
public class MERepo<T extends IAEStack<T>> {

    private final IItemList<T> list;
    /**
     * Contains all stacks currently in the view.
     */
    private ArrayList<T> view = new ArrayList<>();
    private String searchString = "";
    private String innerSearch = "";
    private ViewItems viewMode;
    private SortDir sortDir;
    private SortOrder sortOrder;
    private SearchBoxMode searchBoxMode;
    private GuiScrollBar scrollBar;
    private int rowSize = 9;

    private ViewItems lastView;
    private SearchBoxMode lastSearchMode;
    private SortOrder lastSortBy;
    private SortDir lastSortDir;
    private String lastSearch = "";

    private boolean resort = true;
    private boolean changed = false;

    public MERepo(Class<? extends IStorageChannel<T>> clazz) {
        this.list = AEUtil.getList(clazz);
        this.viewMode = ViewItems.ALL;
        this.sortDir = SortDir.ASCENDING;
        this.sortOrder = SortOrder.NAME;
        this.searchBoxMode = ThEApi.instance().config().searchBoxMode();
    }

    public void updateView() {

        if (lastView != viewMode) {
            resort = true;
            lastView = viewMode;
        }

        if (lastSearchMode != searchBoxMode) {
            resort = true;
            lastSearchMode = searchBoxMode;
        }

        if (!lastSearch.equals(searchString)) {
            resort = true;
            lastSearch = searchString;
        }

        if (lastSortBy != sortOrder) {
            resort = true;
            lastSortBy = sortOrder;
        }

        if (lastSortDir != sortDir) {
            resort = true;
            lastSortDir = sortDir;
        }

        if (!changed && !resort) {
            return;
        }

        changed = false;
        resort = false;
        view = new ArrayList<>();

        innerSearch = searchString.toLowerCase();
        boolean searchMod = false;
        boolean searchAspect = false;
        boolean searchSpecific = false;

        PrefixSetting modSearchSetting = ThEApi.instance().config().modSearchSetting();
        PrefixSetting aspectSearchSetting = ThEApi.instance().config().aspectSearchSetting();

        if (Stream.of(SearchBoxMode.JEI_AUTOSEARCH, SearchBoxMode.JEI_MANUAL_SEARCH, SearchBoxMode.JEI_AUTOSEARCH_KEEP, SearchBoxMode.JEI_MANUAL_SEARCH_KEEP).anyMatch(m -> m == this.searchBoxMode)) {
            ThEJEI.setSearchText(searchString);
        }

        // DISABLED = Don't search and ignore what it starts with
        // REQUIRE_PREFIX = If search starts with prefix, drop prefix and search ONLY by that search
        // ENABLED = Always search and add to result
        // ENABLED WITH SEARCH = Act like REQUIRE_PREFIX

        switch (modSearchSetting) {
            case ENABLED:
                searchMod = true;
            case REQUIRE_PREFIX:
                String modSearchPrefix = ThEApi.instance().config().modSearchPrefix();
                if (!innerSearch.startsWith(modSearchPrefix))
                    break;
                innerSearch = innerSearch.substring(modSearchPrefix.length());
                searchSpecific = true;
                searchMod = true;
            default:
        }

        if (!searchSpecific) {
            switch (aspectSearchSetting) {
                case ENABLED:
                    searchAspect = true;
                case REQUIRE_PREFIX:
                    String aspectSearchPrefix = ThEApi.instance().config().aspectSearchPrefix();
                    if (!innerSearch.startsWith(aspectSearchPrefix))
                        break;
                    innerSearch = innerSearch.substring(aspectSearchPrefix.length());
                    searchSpecific = true;
                    searchAspect = true;

                    searchMod = false; // Set to false so when MOD is ENABLED but we want to only search aspects
                default:
            }
        }

        Pattern pattern;
        try {
            pattern = Pattern.compile(innerSearch, CASE_INSENSITIVE | UNICODE_CASE);
        } catch (Throwable ignored) {
            try {
                pattern = Pattern.compile(Pattern.quote(innerSearch), CASE_INSENSITIVE | UNICODE_CASE);
            } catch (Throwable ignored2) {
                return;
            }
        }

        // Can't use non-final in lambdas....
        final Pattern p = pattern;
        final boolean searchByMod = searchMod;
        final boolean searchByAspect = searchAspect;

        Stream<T> stream = StreamSupport.stream(list.spliterator(), false);

        stream = stream.filter(t ->
                !(this.getViewMode() == CRAFTABLE && !t.isCraftable()) ||
                        !(this.getViewMode() == STORED && t.getStackSize() == 0)
        );

        if (searchSpecific) {
            if (searchByAspect) {
                stream = stream.filter(t -> this.searchAspects(t, p));
            } else if (searchByMod) {
                stream = stream.filter(t -> this.searchMod(t, p));
            }
        } else {
            stream = stream.filter(t -> {
                if (searchByAspect && this.searchAspects(t, p))
                    return true;
                if (searchByMod && this.searchMod(t, p))
                    return true;
                return this.searchName(t, p) || this.searchTooltip(t, p);
            });
        }

        stream.forEach(t -> {
            T stack = t.copy();
            if (this.getViewMode().equals(CRAFTABLE)) {
                if (!stack.isCraftable())
                    return;
                stack.setStackSize(0);
            } else if (this.getViewMode().equals(STORED) && stack.getStackSize() < 1) {
                return;
            }
            this.view.add(stack);
        });

        if (sortOrder == SortOrder.MOD)
            this.sortByMod();
        else if (sortOrder == SortOrder.NAME)
            this.sortByName();
        else if (sortOrder == SortOrder.AMOUNT)
            this.sortByCount();
        else if (sortOrder == SortOrder.INVTWEAKS)
            this.sortByInvTweaks();

        // TODO: Check if this is even needed anymore
        if (this.getScrollBar() != null) {
            if (this.view.size() <= this.getRowSize() * 6) { // We don't need to have scrolling
                this.getScrollBar().setRows(6);
                this.getScrollBar().click(this.scrollBar.getY());
            } else {
                this.getScrollBar().setRows((int) Math.ceil(this.view.size() * this.getRowSize()));
            }
        }
    }

    public void postUpdate(T stack) {
        T existing = this.list.findPrecise(stack);
        if (existing != null) { // Already exists in the list
            existing.reset();
            existing.add(stack);
        } else { // Doesn't exist in the list yet
            this.list.add(stack);
        }

        changed = true;
    }

    public T getReferenceStack(int i) {
        int scroll = (int) Math.max(Math.min(this.scrollBar.getCurrentPosition(), Math.ceil((double) this.view.size() / this.rowSize)), 0);
        i += scroll * this.rowSize;
        if (i < this.view.size())
            return this.view.get(i);
        return null;
    }

    public int size() {
        return this.view.size();
    }

    public void clear() {
        this.list.resetStatus();
    }

    public void setScrollBar(GuiScrollBar scrollBar) {
        this.scrollBar = scrollBar;
    }

    public GuiScrollBar getScrollBar() {
        return this.scrollBar;
    }

    public void setRowSize(int rowSize) {
        this.rowSize = rowSize;
    }

    public int getRowSize() {
        return this.rowSize;
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString;
    }

    public String getSearchString() {
        return this.searchString;
    }

    public void setViewMode(ViewItems view) {
        this.viewMode = view;
    }

    public ViewItems getViewMode() {
        return this.viewMode;
    }

    public SortDir getSortDir() {
        return this.sortDir;
    }

    public void setSortDir(SortDir sortDir) {
        this.sortDir = sortDir;
    }

    public SortOrder getSortOrder() {
        return this.sortOrder;
    }

    public void setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
    }

    public SearchBoxMode getSearchBoxMode() {
        return searchBoxMode;
    }

    public void setSearchBoxMode(SearchBoxMode searchBoxMode) {
        this.searchBoxMode = searchBoxMode;
    }

    private boolean searchName(T stack, Pattern p) {
        return p.matcher(Platform.getItemDisplayName(stack)).find();
    }

    private boolean searchTooltip(T stack, Pattern p) {
        List<String> tooltip = Platform.getTooltip(stack);
        for (String line : tooltip) {
            if (p.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean searchMod(T stack, Pattern p) {
        if (stack instanceof IAEItemStack)
            return p.matcher(Platform.getModId((IAEItemStack) stack)).find();
        if (stack instanceof IAEFluidStack)
            return p.matcher(Platform.getModId((IAEFluidStack) stack)).find();
        return true;
    }

    private boolean searchAspects(T stack, Pattern p) {
        AspectList aspects = TCUtil.getItemAspects(stack.asItemStackRepresentation());
        if (aspects == null || aspects.size() < 1)
            return false;
        final Pattern pf = p;
        Stream<Aspect> stream = aspects.aspects.keySet().stream();
        return stream.anyMatch(aspect -> pf.matcher(aspect.getName()).find());
    }

    private int checkSortDir(int i) {
        return this.getSortDir() == SortDir.ASCENDING ? i : -i;
    }

    private void sortByName() {
        this.view.sort((o1, o2) -> this.checkSortDir(AEUtil.getDisplayName(o1).compareToIgnoreCase(AEUtil.getDisplayName(o2))));
    }

    private void sortByMod() {
        this.view.sort((o1, o2) -> {
            int i = AEUtil.getModID(o1).compareToIgnoreCase(AEUtil.getModID(o2));
            if (i == 0)
                i = AEUtil.getDisplayName(o1).compareToIgnoreCase(AEUtil.getDisplayName(o2));
            return this.checkSortDir(i);
        });
    }

    private void sortByCount() {
        this.view.sort((o1, o2) -> this.checkSortDir(Long.compare(AEUtil.getStackSize(o2), AEUtil.getStackSize(o1))));
    }

    private void sortByInvTweaks() {
        InvTweaksAPI api = ThEInvTweaks.getApi();
        if (api == null)
            sortByName();
        else
            this.view.sort((o1, o2) -> this.checkSortDir(api.compareItems(o1.asItemStackRepresentation(), o2.asItemStackRepresentation())));
    }
}
