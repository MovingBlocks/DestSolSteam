/*
 * Copyright 2026 The Terasology Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.destinationsol.desktop;

import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamLibraryLoaderGdx;
import com.codedisaster.steamworks.SteamPublishedFileID;
import com.codedisaster.steamworks.SteamUGC;
import com.codedisaster.steamworks.SteamUGCCallback;
import org.destinationsol.modules.FacadeModuleConfig;
import org.destinationsol.SolApplication;
import org.destinationsol.modules.steamintegration.ui.SteamIntegratedModulesScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.context.Lifetime;
import org.terasology.gestalt.module.Module;
import org.terasology.gestalt.module.ModuleEnvironment;
import org.terasology.gestalt.module.ModuleFactory;
import org.terasology.gestalt.module.ModuleMetadataJsonAdapter;
import org.terasology.gestalt.module.ModulePathScanner;
import org.terasology.gestalt.module.ModuleRegistry;
import org.terasology.gestalt.module.sandbox.JavaModuleClassLoader;
import org.terasology.gestalt.di.ServiceRegistry;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * This class is the desktop (PC) entry point for the whole DestinationSol application when run under Steam.
 * It handles the creation and launching of LwjglApplication from {@link SolApplication}.
 */
public final class SolSteamDesktop {
    /**
     * This class is basically only a holder for the Java's {@code main(String[])} method, thus needs not to be
     * instantiated.
     */
    private SolSteamDesktop() {
    }

    public static void main(String[] argv) {
        try {
            SteamAPI.loadLibraries(new SteamLibraryLoaderGdx());
            SteamAPI.init();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise Steam", e);
        }
        ServiceRegistry extrasServiceRegistry = new ServiceRegistry();
        // TODO: This is a work-around until we can properly register UI screen classes with the DI system at start-up.
        extrasServiceRegistry.with(SteamIntegratedModulesScreen.class).lifetime(Lifetime.Singleton);
        DesktopLauncher.launchGame(argv, SteamDesktopModuleConfig.class, SteamModulePathScanner.class, extrasServiceRegistry);
    }

    public static class SteamDesktopModuleConfig implements FacadeModuleConfig, SteamUGCCallback {
        @Inject
        public SteamDesktopModuleConfig() {
        }

        @Override
        public Collection<File> getModulePaths() {
            List<File> modulePaths = new ArrayList<>();
            modulePaths.add(Paths.get(".").resolve("modules").toFile());
            SteamUGC steamUGC = new SteamUGC(this);
            SteamPublishedFileID[] subscribedItems = new SteamPublishedFileID[steamUGC.getNumSubscribedItems(false)];
            int subscribedItemCount = steamUGC.getSubscribedItems(subscribedItems, false);
            for (int itemNo = 0; itemNo < subscribedItemCount; itemNo++) {
                SteamPublishedFileID subscribedItem = subscribedItems[itemNo];
                for (SteamUGC.ItemState subscribedItemState : steamUGC.getItemState(subscribedItem)) {
                    if (subscribedItemState == SteamUGC.ItemState.Installed) {
                        SteamUGC.ItemInstallInfo subcribedItemInstallInfo = new SteamUGC.ItemInstallInfo();
                        boolean actuallyInstalled = steamUGC.getItemInstallInfo(subscribedItem, subcribedItemInstallInfo);
                        if (actuallyInstalled && subcribedItemInstallInfo.getFolder() != null && new File(subcribedItemInstallInfo.getFolder()).exists()) {
                            modulePaths.add(new File(subcribedItemInstallInfo.getFolder()));
                        }
                        break;
                    }
                }
            }
            steamUGC.dispose();
            return modulePaths;
        }

        @Override
        public boolean useSecurityManager() {
            return true;
        }

        @Override
        public ModuleEnvironment.ClassLoaderSupplier getClassLoaderSupplier() {
            return JavaModuleClassLoader::create;
        }

        @Override
        public Module createEngineModule() {
            return new ModuleFactory().createPackageModule("org.destinationsol");
        }

        @Override
        public Collection<Module> createFacadeModules() {
            ModuleFactory moduleFactory = new ModuleFactory();
            return Collections.singletonList(moduleFactory.createPackageModule("org.destinationsol.modules.steam-integration"));
        }

        @Override
        public ModuleFactory createModuleFactory() {
            ModuleFactory moduleFactory = new ModuleFactory();
            ModuleMetadataJsonAdapter moduleMetadataJsonAdapter = (ModuleMetadataJsonAdapter) moduleFactory.getModuleMetadataLoaderMap().get("module.json");
            moduleMetadataJsonAdapter.registerExtension("steamWorkshopId", Long.class);
            return moduleFactory;
        }

        @Override
        public Class<?>[] getAPIClasses() {
            return new Class<?>[0];
        }
    }

    public static class SteamModulePathScanner extends ModulePathScanner {
        private static final Logger steamLogger = LoggerFactory.getLogger(SteamModulePathScanner.class);

        @Inject
        public SteamModulePathScanner(ModuleFactory factory) {
            super(factory);
            ModuleMetadataJsonAdapter moduleMetadataJsonAdapter = (ModuleMetadataJsonAdapter) factory.getModuleMetadataLoaderMap().get("module.json");
            moduleMetadataJsonAdapter.registerExtension("steamWorkshopId", Long.class);
        }

        @Override
        public void scan(ModuleRegistry registry, Collection<File> paths) {
            List<File> actualSearchPaths = new ArrayList<>(paths);
            for (File searchPath : paths) {
                if (new File(searchPath, "module.json").exists()) {
                    loadSteamModule(registry, searchPath);
                    actualSearchPaths.remove(searchPath);
                }
            }
            super.scan(registry, actualSearchPaths);
        }

        private void loadSteamModule(ModuleRegistry registry, File modulePath) {
            try {
                ModuleFactory moduleFactory = getModuleFactory();
                Module module = moduleFactory.createModule(modulePath);
                if (registry.add(module)) {
                    steamLogger.info("Discovered module: {}", module);
                } else {
                    steamLogger.info("Discovered duplicate module: {}-{}, skipping", module.getId(), module.getVersion());
                }
            } catch (IOException e) {
                steamLogger.warn("Failed to load module at '{}'", modulePath, e);
            }
        }
    }
}
