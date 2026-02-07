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

import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamPublishedFileID;
import com.codedisaster.steamworks.SteamResult;
import com.codedisaster.steamworks.SteamUGC;
import com.codedisaster.steamworks.SteamUGCCallback;
import com.codedisaster.steamworks.SteamUGCDetails;
import com.codedisaster.steamworks.SteamUGCQuery;
import com.codedisaster.steamworks.SteamUser;
import com.codedisaster.steamworks.SteamUserCallback;
import com.codedisaster.steamworks.SteamUtils;
import com.codedisaster.steamworks.SteamUtilsCallback;
import org.destinationsol.SolApplication;
import org.destinationsol.modules.ModuleManager;
import org.destinationsol.ui.nui.NUIScreenLayer;
import org.destinationsol.ui.nui.widgets.KeyActivatedButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.gestalt.module.Module;
import org.terasology.gestalt.module.ModuleFactory;
import org.terasology.gestalt.module.ModuleMetadata;
import org.terasology.gestalt.module.ModuleMetadataJsonAdapter;
import org.terasology.nui.backends.libgdx.GDXInputUtil;
import org.terasology.nui.databinding.ReadOnlyBinding;
import org.terasology.nui.itemRendering.StringTextRenderer;
import org.terasology.nui.widgets.UIButton;
import org.terasology.nui.widgets.UIList;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SteamWorkshopScreen extends NUIScreenLayer implements SteamUGCCallback, SteamUtilsCallback, SteamUserCallback {
    private static final Logger logger = LoggerFactory.getLogger(SteamWorkshopScreen.class);
    @Inject
    protected SolApplication solApplication;
    @Inject
    protected ModuleManager moduleManager;
    private List<WorkshopModule> filteredModules;
    private Set<WorkshopModule> subscribedModules;
    private UIList<WorkshopModule> moduleList;
    private int steamAppId;
    private SteamUGC steamUGC;
    private SteamUGC.UserUGCList queryFilter;
    private SteamUGCQuery steamUGCQuery;

    public SteamWorkshopScreen() {
    }

    @Inject
    public SteamWorkshopScreen(SolApplication solApplication, ModuleManager moduleManager) {
        this.solApplication = solApplication;
        this.moduleManager = moduleManager;
    }

    @Override
    public void initialise() {
        if (!SteamAPI.isSteamRunning()) {
            return;
        }

        SteamUtils steamUtils = new SteamUtils(this);

        steamAppId = steamUtils.getAppID();
        SteamUser steamUser = new SteamUser(this);
        steamUGC = new SteamUGC(this);
        queryFilter = SteamUGC.UserUGCList.Subscribed;
        steamUGCQuery = steamUGC.createQueryUserUGCRequest(steamUser.getSteamID().getAccountID(), queryFilter, SteamUGC.MatchingUGCType.ItemsReadyToUse, SteamUGC.UserUGCListSortOrder.VoteScoreDesc, steamAppId, steamAppId, 1);
        steamUGC.sendQueryUGCRequest(steamUGCQuery);

        subscribedModules = new HashSet<>();
        filteredModules = new ArrayList<>();

        moduleList = find("modulesList", UIList.class);
        moduleList.setList(filteredModules);
        moduleList.setItemRenderer(new StringTextRenderer<WorkshopModule>() {
            @Override
            public String getString(WorkshopModule value) {
                if (!subscribedModules.contains(value)) {
                    return value.name;
                } else {
                    return value.name + " (Subscribed)";
                }
            }
        });
        moduleList.subscribe((list, module) -> {
            if (subscribedModules.contains(module)) {
                steamUGC.unsubscribeItem(module.workshopId);
            } else {
                steamUGC.subscribeItem(module.workshopId);
            }
        });

        UIButton browseButton = find("browseButton", UIButton.class);
        browseButton.subscribe(button -> {
            queryFilter = SteamUGC.UserUGCList.WillVoteLater;
            steamUGCQuery = steamUGC.createQueryAllUGCRequest(SteamUGC.UGCQueryType.RankedByVote, SteamUGC.MatchingUGCType.ItemsReadyToUse, steamAppId, steamAppId, 1);
            steamUGC.sendQueryUGCRequest(steamUGCQuery);
        });

        UIButton subscribedButton = find("subscribedButton", UIButton.class);
        subscribedButton.subscribe(button -> {
            steamUGCQuery = steamUGC.createQueryUserUGCRequest(steamUser.getSteamID().getAccountID(), SteamUGC.UserUGCList.Subscribed, SteamUGC.MatchingUGCType.ItemsReadyToUse, SteamUGC.UserUGCListSortOrder.VoteScoreDesc, steamAppId, steamAppId, 1);
            steamUGC.sendQueryUGCRequest(steamUGCQuery);
        });

        UIButton yourUploadsButton = find("yourUploadsButton", UIButton.class);
        yourUploadsButton.subscribe(button -> {
            queryFilter = SteamUGC.UserUGCList.Published;
            steamUGCQuery = steamUGC.createQueryUserUGCRequest(steamUser.getSteamID().getAccountID(), queryFilter, SteamUGC.MatchingUGCType.ItemsReadyToUse, SteamUGC.UserUGCListSortOrder.VoteScoreDesc, steamAppId, steamAppId, 1);
            steamUGC.sendQueryUGCRequest(steamUGCQuery);
        });

        UIButton subscribeButton = find("subscribeButton", UIButton.class);
        subscribeButton.bindEnabled(new ReadOnlyBinding<Boolean>() {
            @Override
            public Boolean get() {
                WorkshopModule selectedModule = moduleList.getSelection();
                return selectedModule != null && !subscribedModules.contains(selectedModule);
            }
        });
        subscribeButton.subscribe(button -> {
            WorkshopModule selectedModule = moduleList.getSelection();
            steamUGC.subscribeItem(selectedModule.workshopId);
        });

        UIButton unsubscribeButton = find("unsubscribeButton", UIButton.class);
        unsubscribeButton.bindEnabled(new ReadOnlyBinding<Boolean>() {
            @Override
            public Boolean get() {
                WorkshopModule selectedModule = moduleList.getSelection();
                return selectedModule != null && subscribedModules.contains(selectedModule);
            }
        });
        unsubscribeButton.subscribe(button -> {
            WorkshopModule selectedModule = moduleList.getSelection();
            steamUGC.unsubscribeItem(selectedModule.workshopId);
        });

        UIButton createButton = find("createButton", UIButton.class);
        createButton.subscribe(button -> {
            SteamWorkshopUploadSelectScreen uploadScreen = (SteamWorkshopUploadSelectScreen) nuiManager.createScreen("steam-integration:steamWorkshopUploadSelectScreen");
            nuiManager.setScreen(uploadScreen);
        });

        KeyActivatedButton confirmButton = find("confirmButton", KeyActivatedButton.class);
        confirmButton.setKey(GDXInputUtil.GDXToNuiKey(solApplication.getOptions().getKeyEscape()));
        confirmButton.subscribe(button -> {
            nuiManager.setScreen(solApplication.getMenuScreens().modules);
        });
    }

    @Override
    public void onAdded() {
        if (steamUGC == null) {
            steamUGC = new SteamUGC(this);
        }
    }

    @Override
    public void onUGCQueryCompleted(SteamUGCQuery query, int numResultsReturned, int totalMatchingResults, boolean isCachedData, SteamResult result) {
        if (result == SteamResult.OK && query.isValid()) {
            filteredModules.clear();
            for (int ugcNo = 0; ugcNo < numResultsReturned; ugcNo++) {
                SteamUGCDetails steamUGCDetails = new SteamUGCDetails();
                steamUGC.getQueryUGCResult(steamUGCQuery, ugcNo, steamUGCDetails);
                WorkshopModule workshopModule = new WorkshopModule(steamUGCDetails.getTitle(), steamUGCDetails.getPublishedFileID(), steamUGCDetails.getOwnerID());
                if (steamUGC.getItemState(steamUGCDetails.getPublishedFileID()).contains(SteamUGC.ItemState.Subscribed)) {
                    subscribedModules.add(workshopModule);
                }
                filteredModules.add(workshopModule);
            }
            moduleList.setList(filteredModules);
        }
        steamUGC.releaseQueryUserUGCRequest(steamUGCQuery);
    }

    @Override
    public void onSubscribeItem(SteamPublishedFileID publishedFileID, SteamResult result) {
        if (result == SteamResult.OK) {
            if (steamUGC.getItemState(publishedFileID).iterator().next() != SteamUGC.ItemState.Installed) {
                steamUGC.downloadItem(publishedFileID, false);
            } else {
                onDownloadItemResult(steamAppId, publishedFileID, SteamResult.OK);
            }

            for (WorkshopModule module : moduleList.getList()) {
                if (module.workshopId.equals(publishedFileID)) {
                    subscribedModules.add(module);
                    return;
                }
            }

            if (queryFilter == SteamUGC.UserUGCList.WillVoteLater) {
                steamUGCQuery = steamUGC.createQueryAllUGCRequest(SteamUGC.UGCQueryType.RankedByVote, SteamUGC.MatchingUGCType.ItemsReadyToUse, steamAppId, steamAppId, 1);
                steamUGC.sendQueryUGCRequest(steamUGCQuery);
            }
        }
    }

    @Override
    public void onUnsubscribeItem(SteamPublishedFileID publishedFileID, SteamResult result) {
        if (result == SteamResult.OK) {
            for (WorkshopModule workshopModule : subscribedModules) {
                if (workshopModule.workshopId.equals(publishedFileID)) {
                    subscribedModules.remove(workshopModule);
                    moduleManager.getRegistry().removeIf(module -> ((Long) SteamPublishedFileID.getNativeHandle(publishedFileID)).equals(module.getMetadata().getExtension("steamWorkshopId", Long.class)));
                    return;
                }
            }
        }
    }

    @Override
    public void onDownloadItemResult(int appID, SteamPublishedFileID publishedFileID, SteamResult result) {
        if (result == SteamResult.OK) {
            SteamUGC.ItemInstallInfo installInfo = new SteamUGC.ItemInstallInfo();
            steamUGC.getItemInstallInfo(publishedFileID, installInfo);
            try {
                ModuleFactory moduleFactory = new ModuleFactory();
                ModuleMetadataJsonAdapter moduleMetadataJsonAdapter = (ModuleMetadataJsonAdapter) moduleFactory.getModuleMetadataLoaderMap().get("module.json");
                moduleMetadataJsonAdapter.registerExtension("steamWorkshopId", Long.class);
                Module symthesisedModule = moduleFactory.createDirectoryModule(new File(installInfo.getFolder()));

                ModuleMetadata moduleMetadata = symthesisedModule.getMetadata();
                Long synthesisedModuleWorkshopIdAttribute = moduleMetadata.getExtension("steamWorkshopId", Long.class);
                if (synthesisedModuleWorkshopIdAttribute == null || SteamPublishedFileID.getNativeHandle(publishedFileID) != synthesisedModuleWorkshopIdAttribute) {
                    logger.warn("Steam Workshop module '{}' contains an invalid workshop id: '{}'", symthesisedModule.getId().toString(), synthesisedModuleWorkshopIdAttribute);
                    try (FileWriter writer = new FileWriter(new File(installInfo.getFolder(), "module.json"))) {
                        moduleMetadata.setExtension("steamWorkshopId", SteamPublishedFileID.getNativeHandle(publishedFileID));
                        moduleMetadataJsonAdapter.write(moduleMetadata, writer);
                    }
                }
                moduleManager.getRegistry().add(symthesisedModule);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void update(float delta) {
        SteamAPI.runCallbacks();
        super.update(delta);
    }

    @Override
    public void onRemoved() {
        if (steamUGC != null) {
            steamUGC.dispose();
            steamUGC = null;
        }
        super.onRemoved();
    }

    private static final class WorkshopModule {
        public final String name;
        public final SteamPublishedFileID workshopId;
        public final SteamID ownerId;

        public WorkshopModule(String name, SteamPublishedFileID workshopId, SteamID ownerId) {
            this.name = name;
            this.workshopId = workshopId;
            this.ownerId = ownerId;
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof WorkshopModule) {
                return this.workshopId.equals(((WorkshopModule) other).workshopId);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return workshopId.hashCode();
        }
    }
}
