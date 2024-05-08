package thaumicenergistics.mixin.core;

import com.google.common.collect.ImmutableList;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.List;

// https://github.com/embeddedt/VintageFix/blob/15291a5829ead82cf7dde9b482a2a2cc95ea45b7/src/main/java/org/embeddedt/vintagefix/core/LateMixins.java
public class LateMixins implements ILateMixinLoader {
    static boolean atLateStage = false;

    @Override
    public List<String> getMixinConfigs() {
        atLateStage = true;
        return ImmutableList.of("mixins.thaumicenergistics.late.json");
    }
}
