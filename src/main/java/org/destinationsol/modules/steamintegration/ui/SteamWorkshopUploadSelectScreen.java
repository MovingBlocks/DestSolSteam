/*
 * Copyright 2026 The Terasology Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.destinationsol.modules.steamintegration.ui;

import org.destinationsol.SolApplication;
import org.destinationsol.modules.ModuleManager;
import org.destinationsol.ui.nui.NUIScreenLayer;
import org.destinationsol.ui.nui.widgets.KeyActivatedButton;
import org.terasology.gestalt.module.Module;
import org.terasology.gestalt.naming.Name;
import org.terasology.nui.backends.libgdx.GDXInputUtil;
import org.terasology.nui.itemRendering.StringTextRenderer;
import org.terasology.nui.widgets.UIButton;
import org.terasology.nui.widgets.UIList;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SteamWorkshopUploadSelectScreen extends NUIScreenLayer {
    @Inject
    protected ModuleManager moduleManager;
    @Inject
    protected SolApplication solApplication;

    @Inject
    public SteamWorkshopUploadSelectScreen() {
    }

    @Override
    public void onAdded() {
        UIList<Module> moduleList = find("modulesList", UIList.class);
        moduleList.setItemRenderer(new StringTextRenderer<Module>() {
            @Override
            public String getString(Module value) {
                return value.getId().toString();
            }
        });
        List<Module> modules = new ArrayList<>();
        Set<Name> EXCLUDED_MODULES = new HashSet<>(moduleManager.getBuiltInModules().stream().map(module -> module.getId()).collect(Collectors.toSet()));
        EXCLUDED_MODULES.add(new Name("core"));
        for (Name moduleId : moduleManager.getRegistry().getModuleIds()) {
            if (!EXCLUDED_MODULES.contains(moduleId)) {
                Module module = moduleManager.getRegistry().getLatestModuleVersion(moduleId);
                if (!module.getResources().getRootPaths().stream().anyMatch(path -> path.endsWith(".jar"))) {
                    modules.add(module);
                }
            }
        }
        moduleList.setList(modules);

        KeyActivatedButton cancelButton = find("cancelButton", KeyActivatedButton.class);
        cancelButton.setKey(GDXInputUtil.GDXToNuiKey(solApplication.getOptions().getKeyEscape()));
        cancelButton.subscribe(button -> nuiManager.setScreen(nuiManager.createScreen("steam-integration:steamWorkshopScreen")));

        UIButton uploadButton = find("uploadButton", UIButton.class);
        uploadButton.subscribe(button -> {
            SteamWorkshopUploadEditorScreen uploadEditorScreen =
                    (SteamWorkshopUploadEditorScreen) nuiManager.createScreen("steam-integration:steamWorkshopUploadEditorScreen");
            uploadEditorScreen.setTargetModule(moduleList.getSelection());
            nuiManager.setScreen(uploadEditorScreen);
        });
    }
}
