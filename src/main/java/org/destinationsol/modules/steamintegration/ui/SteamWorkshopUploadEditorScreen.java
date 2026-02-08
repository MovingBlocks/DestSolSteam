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
import com.codedisaster.steamworks.SteamPublishedFileID;
import com.codedisaster.steamworks.SteamRemoteStorage;
import com.codedisaster.steamworks.SteamResult;
import com.codedisaster.steamworks.SteamUGC;
import com.codedisaster.steamworks.SteamUGCCallback;
import com.codedisaster.steamworks.SteamUGCUpdateHandle;
import com.codedisaster.steamworks.SteamUtils;
import com.codedisaster.steamworks.SteamUtilsCallback;
import org.destinationsol.SolApplication;
import org.destinationsol.modules.ModuleManager;
import org.destinationsol.ui.nui.NUIScreenLayer;
import org.destinationsol.ui.nui.widgets.KeyActivatedButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.gestalt.i18n.I18nMap;
import org.terasology.gestalt.module.Module;
import org.terasology.gestalt.module.ModuleMetadata;
import org.terasology.gestalt.module.ModuleMetadataJsonAdapter;
import org.terasology.nui.backends.libgdx.GDXInputUtil;
import org.terasology.nui.databinding.ReadOnlyBinding;
import org.terasology.nui.widgets.UIButton;
import org.terasology.nui.widgets.UIDropdown;
import org.terasology.nui.widgets.UILoadBar;
import org.terasology.nui.widgets.UIText;

import javax.inject.Inject;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

public class SteamWorkshopUploadEditorScreen extends NUIScreenLayer implements SteamUGCCallback, SteamUtilsCallback {
    private static final Logger logger = LoggerFactory.getLogger(SteamWorkshopUploadEditorScreen.class);
    @Inject
    protected ModuleManager moduleManager;
    @Inject
    protected SolApplication solApplication;
    private Module targetModule;
    private UIDropdown<SteamRemoteStorage.PublishedFileVisibility> visibilityDropdown;
    private UIText nameInput;
    private UIText descriptionInput;
    private UIText updateNoteInput;
    private KeyActivatedButton cancelButton;
    private UIButton uploadButton;
    private UILoadBar uploadProgressBar;
    private int steamAppId;
    private SteamUGC steamUGC;
    private SteamUGCUpdateHandle ugcUpdateHandle;
    private Path ugcUploadTempPath;

    @Inject
    public SteamWorkshopUploadEditorScreen() {
    }

    public void setTargetModule(Module targetModule) {
        this.targetModule = targetModule;
    }

    @Override
    public void initialise() {
        visibilityDropdown = find("visibilityDropdown", UIDropdown.class);
        visibilityDropdown.setOptions(Arrays.asList(SteamRemoteStorage.PublishedFileVisibility.values()));
        visibilityDropdown.setSelection(SteamRemoteStorage.PublishedFileVisibility.Private);
        nameInput = find("nameInput", UIText.class);
        descriptionInput = find("descriptionInput", UIText.class);
        updateNoteInput = find("updateNoteInput", UIText.class);

        uploadProgressBar = find("uploadProgressBar", UILoadBar.class);
        uploadProgressBar.setVisible(false);
        uploadProgressBar.bindValue(new ReadOnlyBinding<Float>() {
            @Override
            public Float get() {
                if (steamUGC == null || ugcUpdateHandle == null) {
                    return 0.0f;
                }
                SteamUGC.ItemUpdateInfo updateInfo = new SteamUGC.ItemUpdateInfo();
                steamUGC.getItemUpdateProgress(ugcUpdateHandle, updateInfo);
                return (float) updateInfo.getBytesProcessed() / updateInfo.getBytesTotal();
            }
        });

        steamUGC = new SteamUGC(this);

        cancelButton = find("cancelButton", KeyActivatedButton.class);
        cancelButton.setKey(GDXInputUtil.GDXToNuiKey(solApplication.getOptions().getKeyEscape()));
        cancelButton.subscribe(button -> nuiManager.setScreen(nuiManager.createScreen("steam-integration:steamWorkshopScreen")));

        uploadButton = find("uploadButton", UIButton.class);
        uploadButton.subscribe(button -> {
            cancelButton.setVisible(false);
            uploadButton.setVisible(false);
            uploadProgressBar.setVisible(true);
            Long steamWorkshopId = targetModule.getMetadata().getExtension("steamWorkshopId", Long.class);
            if (steamWorkshopId == null) {
                steamUGC.createItem(steamAppId, SteamRemoteStorage.WorkshopFileType.Community);
            } else {
                updateItem(new SteamPublishedFileID(steamWorkshopId));
            }
        });
    }

    @Override
    public void onAdded() {
        {
            SteamUtils steamUtils = new SteamUtils(this);
            steamAppId = steamUtils.getAppID();
            steamUtils.dispose();
        }

        nameInput.setText(targetModule.getMetadata().getDisplayName().toString());
        descriptionInput.setText(targetModule.getMetadata().getDescription().toString());
        updateNoteInput.setText("");

        uploadProgressBar.setVisible(false);
        cancelButton.setVisible(true);
        uploadButton.setVisible(true);

        if (steamUGC == null) {
            steamUGC = new SteamUGC(this);
        }
    }

    @Override
    public void onCreateItem(SteamPublishedFileID publishedFileID, boolean needsToAcceptWLA, SteamResult result) {
        if (result == SteamResult.OK) {
            updateItem(publishedFileID);
        }
    }

    private static void copyDirectory(Path source, Path destination) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
                Path destinationDirectory = destination.resolve(source.relativize(path));
                if (!Files.exists(destinationDirectory)) {
                    Files.createDirectory(destinationDirectory);
                }
                return super.preVisitDirectory(path, basicFileAttributes);
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
                Files.copy(path, destination.resolve(source.relativize(path)), StandardCopyOption.REPLACE_EXISTING);
                return super.visitFile(path, basicFileAttributes);
            }
        });
    }

    private static void deleteDirectory(Path directory) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException e) throws IOException {
                path.toFile().delete();
                return super.postVisitDirectory(path, e);
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
                path.toFile().delete();
                return super.visitFile(path, basicFileAttributes);
            }
        });
    }

    private void updateItem(SteamPublishedFileID publishedFileID) {
        ugcUpdateHandle = steamUGC.startItemUpdate(steamAppId, publishedFileID);
        steamUGC.setItemTitle(ugcUpdateHandle, nameInput.getText());
        steamUGC.setItemDescription(ugcUpdateHandle, descriptionInput.getText());
        steamUGC.setItemVisibility(ugcUpdateHandle, visibilityDropdown.getSelection());

        Path moduleRootPath = targetModule.getResources().getRootPaths().get(0).toAbsolutePath();

        ModuleMetadata moduleMetadata = targetModule.getMetadata();
        if (!nameInput.getText().equals(moduleMetadata.getDisplayName().toString())) {
            moduleMetadata.setDisplayName(new I18nMap(nameInput.getText()));
        }
        if (!descriptionInput.getText().equals(moduleMetadata.getDescription().toString())) {
            moduleMetadata.setDescription(new I18nMap(descriptionInput.getText()));
        }
        moduleMetadata.setExtension("steamWorkshopId", SteamPublishedFileID.getNativeHandle(publishedFileID));
        try (FileWriter fileWriter = new FileWriter(moduleRootPath.resolve("module.json").toFile())) {
            ModuleMetadataJsonAdapter moduleMetadataJsonAdapter = new ModuleMetadataJsonAdapter();
            moduleMetadataJsonAdapter.registerExtension("steamWorkshopId", Long.class);
            moduleMetadataJsonAdapter.write(moduleMetadata, fileWriter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            ugcUploadTempPath = Files.createTempDirectory("DestinationSol_" + targetModule.getId().toString());
            copyDirectory(moduleRootPath.resolve("assets"), ugcUploadTempPath.resolve("assets"));
            copyDirectory(moduleRootPath.resolve("deltas"), ugcUploadTempPath.resolve("deltas"));
            copyDirectory(moduleRootPath.resolve("overrides"), ugcUploadTempPath.resolve("overrides"));
            Path moduleCodeRoot = moduleRootPath.resolve("build/classes");
            if (moduleCodeRoot.toFile().exists()) {
                Files.createDirectories(ugcUploadTempPath.resolve("build/classes"));
                copyDirectory(moduleCodeRoot, ugcUploadTempPath.resolve("build/classes"));
            }
            Files.copy(moduleRootPath.resolve("module.json"), ugcUploadTempPath.resolve("module.json"));

            final String[] filePatternsToInclude = {
                    // Read-me files
                    ".*README.*",
                    // Licence files (including variations on spelling)
                    ".*LICENSE.*", ".*LICENCE.*", ".*COPYING.*", ".*NOTICE.*"
            };

            Files.walk(moduleRootPath, 1).filter(Files::isRegularFile).forEach(filePath -> {
                for (String filePattern : filePatternsToInclude) {
                    if (Pattern.matches(filePattern, filePath.getFileName().toString().toUpperCase(Locale.ENGLISH))) {
                        try {
                            Files.copy(filePath, ugcUploadTempPath.resolve(filePath.getFileName()));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });

            Path modulePreviewImage = moduleRootPath.resolve("preview.png");
            if (modulePreviewImage.toFile().exists()) {
                steamUGC.setItemPreview(ugcUpdateHandle, modulePreviewImage.toAbsolutePath().toString());
            }
        } catch (IOException e) {
            logger.error("Failed to create upload staging area", e);
            ugcUpdateHandle = null;
            nuiManager.setScreen(nuiManager.createScreen("steam-integration:steamWorkshopScreen"));
            return;
        }

        steamUGC.setItemContent(ugcUpdateHandle, ugcUploadTempPath.toString());
        steamUGC.submitItemUpdate(ugcUpdateHandle, updateNoteInput.getText());
    }

    @Override
    public void onSubmitItemUpdate(SteamPublishedFileID publishedFileID, boolean needsToAcceptWLA, SteamResult result) {
        if (result == SteamResult.OK) {
            try {
                deleteDirectory(ugcUploadTempPath);
            } catch (IOException e) {
                logger.error("Failed to delete upload staging area", e);
            }
            ugcUploadTempPath = null;
            ugcUpdateHandle = null;

            nuiManager.setScreen(nuiManager.createScreen("steam-integration:steamWorkshopScreen"));
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
}
