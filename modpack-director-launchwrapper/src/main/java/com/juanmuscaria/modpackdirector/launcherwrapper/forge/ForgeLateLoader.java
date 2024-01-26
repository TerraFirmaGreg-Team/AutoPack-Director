package com.juanmuscaria.modpackdirector.launcherwrapper.forge;

import com.juanmuscaria.modpackdirector.ModpackDirector;
import com.juanmuscaria.modpackdirector.launcherwrapper.ModpackDirectorTweaker;
import net.jan.moddirector.core.manage.ModDirectorError;
import net.jan.moddirector.core.manage.install.InstalledMod;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;

public class ForgeLateLoader {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private final ModpackDirectorTweaker directorTweaker;
    private final ModpackDirector director;
    private final LaunchClassLoader classLoader;
    private final List<String> loadedCoremods;
    private final List<ITweaker> modTweakers;

    private List<String> reflectiveIgnoredMods;
    private List<String> reflectiveReparsedCoremods;
    private MethodHandle handleCascadingTweakMethodHandle;
    private MethodHandle loadCoreModMethodHandle;
    private MethodHandle addUrlMethodHandle;
    private MethodHandle sortTweakListMethodHandle;
    private MethodHandle addJarMethodHandle;
    private boolean addJarRequiresAtList;

    public ForgeLateLoader(ModpackDirectorTweaker directorTweaker, ModpackDirector director, LaunchClassLoader classLoader) {
        this.directorTweaker = directorTweaker;
        this.director = director;
        this.classLoader = classLoader;
        this.loadedCoremods = new ArrayList<>();
        this.modTweakers = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public void execute() {
        for (String commandlineCoreMod :
            System.getProperty(ForgeConstants.COREMODS_LOAD_PROPERTY, "").split(",")) {
            if (!commandlineCoreMod.isEmpty()) {
                directorTweaker.logger().debug("Ignoring coremod {0} which has been loaded on the commandline",
                    commandlineCoreMod);
                loadedCoremods.add(commandlineCoreMod);
            }
        }

        if (!reflectiveSetup()) {
            return;
        }

        directorTweaker.logger().info("Trying to late load {0} mods", director.getInstalledMods().size());
        director.getInstalledMods().forEach(this::handle);

        boolean sortSucceeded = false;

        if (sortTweakListMethodHandle != null) {
            @SuppressWarnings("unchecked")
            List<ITweaker> realTweakers = (List<ITweaker>) Launch.blackboard.get("Tweaks");
            Launch.blackboard.put("Tweaks", modTweakers);

            try {
                sortTweakListMethodHandle.invoke();
                sortSucceeded = true;
            } catch (Throwable t) {
                directorTweaker.logger().error("Error while invoking sortTweakList method", t);
            }

            Launch.blackboard.put("Tweaks", realTweakers);
        }

        if (!sortSucceeded) {
            directorTweaker.logger().warn("Mod tweak list could not be sorted, hoping the best...");
        }

        Launch.blackboard.put("LateTweakers", modTweakers);

        List<String> tweakClasses = (List<String>) Launch.blackboard.get("TweakClasses");
        boolean deobfFound = false;

        for (int i = 0; i < tweakClasses.size(); i++) {
            if (tweakClasses.get(i).endsWith(".FMLDeobfTweaker")) {
                directorTweaker.logger().debug("Found deobf tweaker at index {0}, adding after deobf tweaker after it", i);
                tweakClasses.add(i + 1, "net.jan.moddirector.launchwrapper.forge.AfterDeobfTweaker");
                deobfFound = true;
            }
        }

        if (!deobfFound) {
            directorTweaker.logger().warn("Failed to find deobf tweaker, injecting after deobf tweaker at first place");
            tweakClasses.add(0, "net.jan.moddirector.launchwrapper.forge.AfterDeobfTweaker");
        }
    }

    @SuppressWarnings("unchecked")
    private boolean reflectiveSetup() {
        Class<?> coreModManagerClass;

        try {
            coreModManagerClass =
                Class.forName(ForgeConstants.CORE_MOD_MANAGER_CLASS, false, getClass().getClassLoader());
            directorTweaker.logger().info("Found new CoreModManager at {0}!",
                ForgeConstants.CORE_MOD_MANAGER_CLASS);
        } catch (ClassNotFoundException e) {
            directorTweaker.logger().debug("Unable to find new CoreModManager class, trying old...");

            try {
                coreModManagerClass = Class.forName(ForgeConstants.CORE_MOD_MANAGER_CLASS_LEGACY);
                directorTweaker.logger().info("Found old CoreModManager at {0}!",
                    ForgeConstants.CORE_MOD_MANAGER_CLASS);
            } catch (ClassNotFoundException ex) {
                directorTweaker.logger().info("Unable to find old CoreModManager class, Forge support disabled!");
                return false;
            }
        }

        try {
            Method sortTweakListMethod = getMethod(new String[]{
                ForgeConstants.SORT_TWEAK_LIST_METHOD
            }, coreModManagerClass);

            sortTweakListMethodHandle = LOOKUP.unreflect(sortTweakListMethod);
        } catch (ReflectiveOperationException e) {
            directorTweaker.logger().warn("Failed to get method for sorting tweaks, loading might fail!", e);
        }

        try {
            Method getIgnoredModsMethod = getMethod(new String[]{
                ForgeConstants.IGNORED_MODS_METHOD,
                ForgeConstants.IGNORED_MODS_METHOD_LEGACY
            }, coreModManagerClass);

            reflectiveIgnoredMods = (List<String>) getIgnoredModsMethod.invoke(null);
        } catch (ReflectiveOperationException e) {
            directorTweaker.logger().warn("Failed to get method for retrieving ignored mods, loading might fail!", e);
            reflectiveIgnoredMods = new ArrayList<>();
        }

        try {
            Method getReparseableCoremodsMethod = getMethod(new String[]{
                ForgeConstants.GET_REPARSEABLE_COREMODS_METHOD
            }, coreModManagerClass);

            reflectiveReparsedCoremods = (List<String>) getReparseableCoremodsMethod.invoke(null);
        } catch (ReflectiveOperationException e) {
            directorTweaker.logger().warn("Failed to get method for retrieving reparseable coremods, loading might fail!", e);
            reflectiveReparsedCoremods = new ArrayList<>();
        }

        try {
            Method handleCascadingTweakMethod = getMethod(new String[]{
                ForgeConstants.HANDLE_CASCADING_TWEAK_METHOD
            }, coreModManagerClass, File.class, JarFile.class, String.class, LaunchClassLoader.class, Integer.class);

            handleCascadingTweakMethodHandle = LOOKUP.unreflect(handleCascadingTweakMethod);
        } catch (ReflectiveOperationException e) {
            directorTweaker.logger().warn("Failed to get method for adding tweakers via FML, loading might fail, but trying to fall back to Launchwrapper directly!", e);
        }

        try {
            Method loadCoreModMethod = getMethod(new String[]{
                ForgeConstants.LOAD_CORE_MOD_METHOD
            }, coreModManagerClass, LaunchClassLoader.class, String.class, File.class);

            loadCoreModMethodHandle = LOOKUP.unreflect(loadCoreModMethod);
        } catch (ReflectiveOperationException e) {
            directorTweaker.logger().warn("Failed to get method for loading core mods via FML, loading might fail!", e);
        }

        Class<?> modAccessTransformerClass = null;

        try {
            modAccessTransformerClass =
                Class.forName(ForgeConstants.MOD_ACCESS_TRANSFORMER_CLASS, false, getClass().getClassLoader());
            directorTweaker.logger().info("Found new ModAccessTransformer at {0}!",
                modAccessTransformerClass.getName());
        } catch (ClassNotFoundException e) {
            directorTweaker.logger().debug("Unable to find new ModAccessTransformer class, trying old...");

            try {
                modAccessTransformerClass =
                    Class.forName(ForgeConstants.MOD_ACCESS_TRANSFORMER_CLASS_LEGACY, false,
                        getClass().getClassLoader());
                directorTweaker.logger().info("Found old ModAccessTransformer at {0}!",
                    modAccessTransformerClass.getName());
            } catch (ClassNotFoundException classNotFoundException) {
                directorTweaker.logger().warn("Failed to find ModAccessTransformer class even after trying legacy name. Access transformers for downloaded mods disabled, loading might fail!", e);
            }
        }

        if (modAccessTransformerClass != null) {
            try {
                Method addJarMethod = getMethod(new String[]{
                    ForgeConstants.ADD_JAR_METHOD
                }, modAccessTransformerClass, JarFile.class);
                addJarMethodHandle = LOOKUP.unreflect(addJarMethod);
                addJarRequiresAtList = false;
            } catch (NoSuchMethodException e) {
                Exception secondException = null;

                try {
                    Method addJarMethod = getMethod(new String[]{
                        ForgeConstants.ADD_JAR_METHOD
                    }, modAccessTransformerClass, JarFile.class, String.class);
                    addJarMethodHandle = LOOKUP.unreflect(addJarMethod);
                    addJarRequiresAtList = true;
                } catch (IllegalAccessException | NoSuchMethodException second) {
                    secondException = second;
                }

                if (addJarMethodHandle == null) {
                    directorTweaker.logger().warn("Failed to find method for injecting access transformers, loading might fail if they are required!");
                    directorTweaker.logger().warn("\tFailure 1:", e);
                    if (secondException != null) {
                        directorTweaker.logger().warn("\tFailure 2:", secondException);
                    }
                }
            } catch (IllegalAccessException e) {
                directorTweaker.logger().warn("Failed to access method for injecting access transformers, loading might fail if they are required!", e);
            }
        }

        try {
            Method addUrlMethod = getMethod(new String[]{
                "addURL"
            }, URLClassLoader.class, URL.class);
            addUrlMethodHandle = LOOKUP.unreflect(addUrlMethod);
        } catch (ReflectiveOperationException e) {
            directorTweaker.logger().warn("Failed to get addUrl method for URLClassLoader (wtf?), loading might fail!");
        }

        return true;
    }

    private Method getMethod(String[] possibleNames, Class<?> targetClass, Class<?>... args)
        throws NoSuchMethodException {
        Method method = null;

        for (String possibleName : possibleNames) {
            try {
                method = targetClass.getDeclaredMethod(possibleName, args);
            } catch (NoSuchMethodException ignored) {
            }
        }

        if (method == null) {
            throw new NoSuchMethodException("Failed to find method using names [" +
                String.join(", ", possibleNames) + "] on class " + targetClass.getName());
        } else {
            method.setAccessible(true);
            return method;
        }
    }

    private void handle(InstalledMod mod) {
        Path injectedFile = mod.getFile();

        if (!mod.shouldInject()) {
            return;
        }

        reflectiveIgnoredMods.remove(injectedFile.toFile().getName());

        try (JarFile jar = new JarFile(injectedFile.toFile())) {
            Manifest manifest = jar.getManifest();

            if (manifest != null) {
                Attributes attributes = manifest.getMainAttributes();

                injectAccessTransformers(jar, manifest);

                String tweakClass;
                if ((tweakClass = attributes.getValue(ForgeConstants.TWEAK_CLASS_ATTRIBUTE)) != null) {
                    int tweakOrder = 0;
                    String tweakOrderString;
                    if ((tweakOrderString = attributes.getValue(ForgeConstants.TWEAK_ORDER_ATTRIBUTE)) != null) {
                        try {
                            tweakOrder = Integer.parseInt(tweakOrderString);
                        } catch (NumberFormatException e) {
                            directorTweaker.logger().warn("Failed to parse tweak order for {0}", injectedFile.toString(), e);
                        }
                    }

                    injectTweaker(
                        injectedFile, jar, tweakClass, tweakOrder,
                        mod.getOptionBoolean("launchwrapperTweakerForceNext", false));
                    return;
                }

                String corePlugin;
                if ((corePlugin = attributes.getValue(ForgeConstants.CORE_PLUGIN_ATTRIBUTE)) != null) {
                    injectCorePlugin(injectedFile, corePlugin);

                    if (attributes.getValue(ForgeConstants.CORE_PLUGIN_CONTAINS_MOD_ATTRIBUTE) != null) {
                        addReparseableJar(injectedFile);
                    } else {
                        addLoadedCoreMod(injectedFile);
                    }
                }
            } else {
                directorTweaker.logger().warn("Downloaded file {0} has no manifest!", injectedFile.toString());
            }
        } catch (IOException e) {
            directorTweaker.logger().warn("Failed to open indexed file {0} as jar, ignoring",
                injectedFile.toString(), e);
        }
    }

    private void addReparseableJar(Path injectedFile) {
        String fileName = injectedFile.toFile().getName();
        if (!reflectiveReparsedCoremods.contains(fileName)) {
            reflectiveReparsedCoremods.add(fileName);
            directorTweaker.logger().debug("Marked {0} as reparseable coremod", injectedFile.toString());
        }
    }

    private void addLoadedCoreMod(Path injectedFile) {
        String filename = injectedFile.toFile().getName();
        if (!reflectiveIgnoredMods.contains(filename)) {
            reflectiveIgnoredMods.add(filename);
            directorTweaker.logger().debug("Marked {0} as loaded coremod", injectedFile.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private void injectTweaker(Path injectedFile, JarFile jar, String tweakerClass, Integer sortingOrder,
                               boolean forceNext) {
        URL fileUrl = null;

        try {
            fileUrl = injectedFile.toUri().toURL();
        } catch (MalformedURLException e) {
            directorTweaker.logger().error("Failed to convert path to url, loading might fail!", e);
        }

        if (fileUrl != null) {
            try {
                addUrlMethodHandle.invoke(classLoader.getClass().getClassLoader(), fileUrl);
                classLoader.addURL(fileUrl);
            } catch (Throwable e) {
                directorTweaker.logger().error("Failed to inject tweaker url into ClassLoader, loading might fail!", e);
            }
        }

        if (forceNext) {
            directorTweaker.logger().info("Late injecting tweaker {0} from {1}, forcing it to be called next!",
                tweakerClass, injectedFile.toString());

            try {
                ITweaker tweaker = (ITweaker) Class.forName(tweakerClass, true, classLoader).getDeclaredConstructor().newInstance();
                classLoader.addClassLoaderExclusion(tweakerClass.substring(0, tweakerClass.lastIndexOf('.')));
                directorTweaker.callInjectedTweaker(tweaker);
            } catch (ReflectiveOperationException e) {
                directorTweaker.logger().error("Failed to manually load tweaker so it can be injected next, falling back to Forge!", e);
                forceNext = false;
            }
        }

        if (forceNext) {
            return;
        }

        boolean injectionSucceeded = false;

        if (handleCascadingTweakMethodHandle != null) {
            directorTweaker.logger().info("Late injecting tweaker {0} from {1} using FML",
                tweakerClass, injectedFile.toString());

            try {
                handleCascadingTweakMethodHandle.invoke(
                    injectedFile.toFile(),
                    jar,
                    tweakerClass,
                    classLoader,
                    sortingOrder
                );
                injectionSucceeded = true;
            } catch (Throwable e) {
                directorTweaker.logger().error("Error while injecting tweaker via FML, falling back to Launchwrapper's own mechanism!", e);
            }
        }

        if (!injectionSucceeded) {
            directorTweaker.logger().info("Late injecting tweaker {0} from {1} using Launchwrapper",
                tweakerClass, injectedFile.toString());
            ((List<String>) Launch.blackboard.get("TweakClasses")).add(tweakerClass);
        }
    }

    private void injectCorePlugin(Path injectedFile, String coreModClass) {
        if (loadedCoremods.contains(coreModClass)) {
            directorTweaker.logger().debug("Not injecting core plugin {0} from {1} because it has already been!",
                coreModClass, injectedFile.toString());
            return;
        }

        directorTweaker.logger().info("Now injecting core plugin {0} from {1}",
            coreModClass, injectedFile.toString());

        try {
            classLoader.addURL(injectedFile.toUri().toURL());
            Object ret = loadCoreModMethodHandle.invoke(classLoader, coreModClass, injectedFile.toFile());
            if (ret instanceof ITweaker) {
                modTweakers.add((ITweaker) ret);
            }
        } catch (Throwable e) {
            directorTweaker.logger().error("Failed to inject core plugin!", e);
            director.addError(new ModDirectorError(Level.SEVERE,
                "Failed to inject core plugin!", e));
        }
    }

    private void injectAccessTransformers(JarFile jar, Manifest manifest) {
        if (addJarMethodHandle != null) {
            try {
                directorTweaker.logger().debug("Added {-} to possible access transformers", jar.getName());
                if (addJarRequiresAtList) {
                    String ats = manifest.getMainAttributes().getValue(ForgeConstants.FML_AT_ATTRIBUTE);
                    if (ats != null && !ats.isEmpty()) {
                        addJarMethodHandle.invoke(jar, ats);
                    }
                } else {
                    addJarMethodHandle.invoke(jar);
                }
            } catch (Throwable t) {
                directorTweaker.logger().warn("Failed to add jar to access transformers", t);
            }
        }
    }
}
