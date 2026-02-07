/*
 * Copyright 2023 The Terasology Foundation
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
import org.destinationsol.ui.nui.screens.mainMenu.ModulesScreen;
import org.terasology.nui.widgets.UIButton;

import javax.inject.Inject;

public class SteamIntegratedModulesScreen extends ModulesScreen {
    @Inject
    public SteamIntegratedModulesScreen(SolApplication solApplication, ModuleManager moduleManager) {
        super(solApplication, moduleManager);
    }

    @Override
    public void onAdded() {
        super.onAdded();

        UIButton steamWorkshopButton =  this.find("steamWorkshopButton", UIButton.class);
        steamWorkshopButton.subscribe(widget -> nuiManager.setScreen(nuiManager.createScreen("steam-integration:steamWorkshopScreen")));
    }
}
