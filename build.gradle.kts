import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar
import org.jetbrains.gradle.ext.compiler
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings

plugins {
    id("java")
    id("java-library")
    id("maven-publish")
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.7"
    id("eclipse")
    id("com.gtnewhorizons.retrofuturagradle") version "1.3.26"
    id("com.matthewprenger.cursegradle") version "1.4.0"
}

val minecraftVersion: String by project
val modVersion: String by project
val mavenGroup: String by project
val modName: String by project
val archiveBase: String by project
val modArchiveName: String by project

version = modVersion + (System.getenv("CI_SHA_SHORT") ?: "")
group = mavenGroup

// Set the toolchain version to decouple the Java we run Gradle with from the Java used to compile and run the mod
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
    // Generate sources and javadocs jars when building and publishing
    withSourcesJar()
    // withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.isFork = true
    options.isIncremental = true
}

val embed = configurations.create("embed")
configurations {
    "implementation" {
        extendsFrom(embed)
    }
}

configure<BasePluginExtension> {
    archivesName.set("$modArchiveName-$minecraftVersion")
}

minecraft {
    mcVersion = "1.12.2"

    // MCP Mappings
    mcpMappingChannel = "stable"
    mcpMappingVersion = "39"

    // Set username here, the UUID will be looked up automatically
    username = "Developer"

    // Add any additional tweaker classes here
    // extraTweakClasses.add("org.spongepowered.asm.launch.MixinTweaker")

    // Add various JVM arguments here for runtime
    val args = mutableListOf("-ea:${project.group}")
    if (projectProperty("useCoreMod")) {
        val coreModPluginClassName: String by project
        args += "-Dfml.coreMods.load=$coreModPluginClassName"
    }
    if (projectProperty("useMixins")) {
        args += "-Dmixin.hotSwap=true"
        args += "-Dmixin.checks.interfaces=true"
        args += "-Dmixin.debug.export=true"
    }
    if (projectProperty("useMixins")) {
        args += "-Dmixin.hotSwap=true"
        args += "-Dmixin.checks.interfaces=true"
        args += "-Dmixin.debug.export=true"
    }
    extraRunJvmArguments.addAll(args)

    // Include and use dependencies Access Transformer files
    useDependencyAccessTransformers = false

    // Add any properties you want to swap out for a dynamic value at build time here
    // Any properties here will be added to a class at build time, the name can be configured below
    // Example:
    injectedTags.put("VERSION", project.version)
    injectedTags.put("MOD_ID", archiveBase)
    injectedTags.put("NAME", modName)
}

// Generate a group.archives_base_name.Tags class
tasks.injectTags.configure {
    // Change Tags class' name here:
    outputClassName.set("${project.group}.${archiveBase}.Reference")
}

repositories {
    maven {
        name = "CleanroomMC Maven"
        url = uri("https://maven.cleanroommc.com")
    }
    maven {
        name = "SpongePowered Maven"
        url = uri("https://repo.spongepowered.org/maven")
    }
    maven {
        name = "CurseMaven"
        url = uri("https://cursemaven.com")
        content {
            includeGroup("curse.maven")
        }
    }
    mavenLocal() // Must be last for caching to work
}

dependencies {
    val useMixins: Boolean = projectProperty("useMixins")
    if (useMixins) {
        implementation(libs.mixinBooter)
    }

    implementation(libs.curse.hei)
    compileOnly(libs.curse.hwyla)
    implementation(libs.curse.top)

    compileOnly(libs.curse.inventoryTweaks)
    compileOnly(libs.curse.mouseTweaks)
    api(rfg.deobf(libs.curse.baubles.get().toString()))
    api(rfg.deobf(libs.curse.extraCells.get().toString()))
    api(rfg.deobf(libs.curse.mekanism.get().toString()))
    api(rfg.deobf(libs.curse.thaumcraft.get().toString()))
    api(rfg.deobf(libs.curse.thaumicAug.get().toString()))
    api(rfg.deobf(libs.curse.aaf.get().toString()))
    api(libs.curse.thaumicJei)
    api(rfg.deobf(libs.curse.ae2.get().toString()))

    // Testing mods
    // Unsure if needed in future
    //    api("com.brandon3055.brandonscore:BrandonsCore:${version_bc}:universal")
    //    api("curse.maven:codechickenlib:${version_ccl}")
    //    api("com.brandon3055.projectintelligence:ProjectIntelligence:${version_pi}:universal")

    val useSpark: Boolean = projectProperty("useSpark")
    if (useSpark) {
        // for profiling
        runtimeOnly(libs.curse.spark)
    }

    if (useMixins) {
        // Change your mixin refmap name here:
        val mixin: String =
            modUtils.enableMixins(
                libs.mixinBooter.get().toString(),
                "mixins.${archiveBase}.refmap.json"
            ).toString()
        api(mixin) {
            isTransitive = false
        }
        annotationProcessor("org.ow2.asm:asm-debug-all:5.2")
        annotationProcessor("com.google.guava:guava:24.1.1-jre")
        annotationProcessor("com.google.code.gson:gson:2.8.6")
        annotationProcessor(mixin) {
            isTransitive = false
        }
    }
}

// Adds Access Transformer files to tasks
if (projectProperty("useAccessTransformer")) {
    sourceSets.main.get().resources.files.forEach {
        if (it.name.lowercase().endsWith("_at.cfg")) {
            tasks.deobfuscateMergedJarToSrg.get().accessTransformerFiles.from(it)
            tasks.srgifyBinpatchedJar.get().accessTransformerFiles.from(it)
        }
    }
}

tasks.withType<ProcessResources> {
    // This will ensure that this task is redone when the versions change
    inputs.property("modversion", project.version)
    inputs.property("mcversion", project.minecraft.mcVersion)

    // Replace various properties in mcmod.info and pack.mcmeta if applicable
    filesMatching(listOf("mcmod.info", "pack.mcmeta")) {
        // Replace version and mcversion
        expand(
            "modversion" to project.version,
            "mcversion" to project.minecraft.mcVersion
        )
    }

    if (projectProperty("useAccessTransformer")) {
        rename("(.+_at.cfg)", "META-INF/$1") // Make sure Access Transformer files are in META-INF folder
    }
}

tasks.create<Jar>("apiJar") {
    from(sourceSets.api.get().output)
    from(sourceSets.api.get().java)
    archiveClassifier = "api"
    tasks.named("build").get().dependsOn(this)
}

tasks.withType<Jar> {
    manifest {
        val attributes = mutableMapOf<String, Any>()
        if (projectProperty("useCoreMod")) {
            val coreModPluginClassName: String by project
            attributes["FMLCorePlugin"] = coreModPluginClassName
            if (projectProperty("includeMod")) {
                attributes["FMLCorePluginContainsFMLMod"] = true
                attributes["ForceLoadAsMod"] = project.gradle.startParameter.taskNames[0] == "build"
            }
        }
        if (projectProperty("useAccessTransformer")) {
            attributes["FMLAT"] = "theeng" + "_at.cfg"
        }
        attributes(attributes)
    }
    // Add all embedded dependencies into the jar
    from(provider { embed.map { if (it.isDirectory()) it else zipTree(it) } })
}

idea {
    module {
        inheritOutputDirs = true
    }
    project {
        settings {
            runConfigurations {
                create("1. Run Client", org.jetbrains.gradle.ext.Gradle::class) {
                    taskNames = listOf("runClient")
                }
                create("2. Run Server", org.jetbrains.gradle.ext.Gradle::class) {
                    taskNames = listOf("runServer")
                }
                create("3. Run Obfuscated Client", org.jetbrains.gradle.ext.Gradle::class) {
                    taskNames = listOf("runObfClient")
                }
                create("4. Run Obfuscated Server", org.jetbrains.gradle.ext.Gradle::class) {
                    taskNames = listOf("runObfServer")
                }
            }

            compiler.javac {
                afterEvaluate {
                    javacAdditionalOptions = "-encoding utf8"
                    tasks.withType<JavaCompile> {
                        val args = options.compilerArgs.joinToString(separator = " ") { "\"$it\"" }
                        moduleJavacAdditionalOptions = mapOf(
                            project.name + ".main" to args
                        )
                    }
                }
            }
        }
    }
}

tasks.named("processIdeaSettings").configure {
    dependsOn("injectTags")
}

tasks.named<Jar>("jar") {
    enabled = true
    from(sourceSets.named("api").get().output)
    finalizedBy(tasks.reobfJar)
}

tasks.named<ReobfuscatedJar>("reobfJar") {
    inputJar.set(tasks.named<Jar>("jar").flatMap { it.archiveFile })
}

val javadocTask = tasks.withType<Javadoc> {
    isFailOnError = false
}

tasks.create<Jar>("javadocJar") {
    from("build/docs/javadoc")
    archiveClassifier = "javadoc"
    dependsOn(javadocTask)
}

inline fun <reified T : Any> projectProperty(propertyKey: String): T {
    val value = project.properties[propertyKey].let { it.toString() }
    return when (T::class) {
        Boolean::class -> value.toBoolean() as T
        else -> throw IllegalArgumentException()
    }
}
