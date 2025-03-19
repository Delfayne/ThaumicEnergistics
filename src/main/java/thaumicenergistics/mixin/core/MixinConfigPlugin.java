package thaumicenergistics.mixin.core;

import com.google.common.collect.ImmutableMap;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dv.minecraft.thaumicenergistics.Reference;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// https://github.com/embeddedt/VintageFix/blob/15291a5829ead82cf7dde9b482a2a2cc95ea45b7/src/main/java/org/embeddedt/vintagefix/core/MixinConfigPlugin.java
public class MixinConfigPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger(Reference.NAME + " Mixin Loader");

    private static final String PACKAGE_PREFIX = "thaumicenergistics.";

    private static final ImmutableMap<String, Consumer<PotentialMixin>> MIXIN_PROCESSING_MAP = ImmutableMap.<String, Consumer<PotentialMixin>>builder()
            .put("Lorg/spongepowered/asm/mixin/Mixin;", p -> p.valid = true)
            .put("Lthaumicenergistics/annotation/ClientOnlyMixin;", p -> p.isClientOnly = true)
            .put("Lthaumicenergistics/annotation/LateMixin;", p -> p.isLate = true)
            .build();

    static class PotentialMixin {
        String className;
        boolean valid;
        boolean isClientOnly;
        boolean isLate;
    }

    private static final List<PotentialMixin> allMixins = new ArrayList<>();

    private void considerClass(String pathString) throws IOException {
        try (InputStream stream = MixinConfigPlugin.class.getClassLoader().getResourceAsStream("thaumicenergistics/mixin/" + pathString)) {
            if (stream == null)
                return;
            ClassReader reader = new ClassReader(stream);
            ClassNode node = new ClassNode();
            reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            if (node.invisibleAnnotations == null)
                return;
            PotentialMixin mixin = new PotentialMixin();
            mixin.className = node.name.replace('/', '.');
            for (AnnotationNode annotation : node.invisibleAnnotations) {
                Consumer<PotentialMixin> consumer = MIXIN_PROCESSING_MAP.get(annotation.desc);
                if (consumer != null)
                    consumer.accept(mixin);
            }
            if (mixin.valid)
                allMixins.add(mixin);
        }
    }

    private static Properties config;

    private static String mixinClassNameToBaseName(String mixinClassName) {
        String noPrefix = mixinClassName.replace(PACKAGE_PREFIX, "");
        return noPrefix.substring(0, noPrefix.lastIndexOf('.'));
    }

    @SuppressWarnings("unchecked")
    private static void writeOrderedProperties(Properties props, OutputStream stream) throws IOException {
        try (PrintWriter writer = new PrintWriter(stream)) {
            writer.println("# Thaumic Energistics config file");
            writer.println();
            List<String> lst = new ArrayList<>((Set<String>) (Set<?>) props.keySet());
            lst.sort(Comparator.naturalOrder());
            for (String k : lst) {
                writer.println(k + "=" + props.getProperty(k));
            }
        }
    }

    @Override
    public void onLoad(String s) {
        if (allMixins.size() == 0) {
            try {
                URI uri = Objects.requireNonNull(MixinConfigPlugin.class.getResource("/mixins.thaumicenergistics.json")).toURI();
                FileSystem fs;
                try {
                    fs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException var11) {
                    fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                }
                List<Path> list;
                Path basePath = fs.getPath("thaumicenergistics", "mixin").toAbsolutePath();
                try (Stream<Path> stream = Files.walk(basePath)) {
                    list = stream.collect(Collectors.toList());
                }
                for (Path p : list) {
                    if (p == null)
                        continue;
                    p = basePath.relativize(p.toAbsolutePath());
                    String pathString = p.toString();
                    if (pathString.endsWith(".class")) {
                        considerClass(pathString);
                    }
                }
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException(e);
            }
            LOGGER.info("Found {} mixins", allMixins.size());
            config = new Properties();
            File targetConfig = new File(Launch.minecraftHome, "config" + File.separator + "thaumicenergistics.properties");
            try {
                if (targetConfig.exists()) {
                    try (InputStream stream = Files.newInputStream(targetConfig.toPath())) {
                        config.load(stream);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                for (PotentialMixin m : allMixins) {
                    String baseName = mixinClassNameToBaseName(m.className);
                    if (!config.containsKey(baseName)) {
                        LOGGER.warn("Added missing entry '{}' to config file", baseName);
                        config.put(baseName, "true");
                    }
                }
                try (OutputStream stream = Files.newOutputStream(targetConfig.toPath())) {
                    writeOrderedProperties(config, stream);
                }
                LOGGER.info("Successfully saved config file");
            } catch (IOException e) {
                LOGGER.error("Exception handling config", e);
            }
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetName, String className) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> set, Set<String> set1) {
    }

    public static boolean isMixinClassApplied(String name) {
        String baseName = mixinClassNameToBaseName(name);
        boolean isEnabled = Boolean.parseBoolean(config.getProperty(baseName, ""));
        if (!isEnabled) {
            LOGGER.warn("Not applying mixin '{}' as '{}' is disabled in config", name, baseName);
        }
        return isEnabled;
    }

    @Override
    public List<String> getMixins() {
        MixinEnvironment.Phase phase = MixinEnvironment.getCurrentEnvironment().getPhase();
        if (phase == MixinEnvironment.Phase.DEFAULT) {
            MixinEnvironment.Side side = MixinEnvironment.getCurrentEnvironment().getSide();
            List<String> list = allMixins.stream()
                    .filter(p -> !p.isClientOnly || side == MixinEnvironment.Side.CLIENT)
                    .filter(p -> p.isLate == LateMixins.atLateStage)
                    .map(p -> p.className)
                    .filter(MixinConfigPlugin::isMixinClassApplied)
                    .map(clz -> clz.replace("thaumicenergistics.mixin.", ""))
                    .collect(Collectors.toList());
            for (String mixin : list) {
                LOGGER.debug("loading {}", mixin);
            }
            return list;
        }
        return null;
    }

    @Override
    public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }
}
