package com.pelig.replaytoworld.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RrWorldSelectionList extends ObjectSelectionList<RrWorldSelectionEntry> {

    private final RrImportWorldScreen screen;
    private String filter;

    public RrWorldSelectionList(RrImportWorldScreen screen, Minecraft minecraft, int width, int height,
                                  int y0, int itemHeight, String filter) {
        super(minecraft, width, height, y0, itemHeight);
        this.screen = screen;
        this.filter = filter;
        this.reload();
    }

    @Override
    protected void clearEntries() {
        this.children().forEach(RrWorldSelectionEntry::close);
        super.clearEntries();
    }

    public void reload() {
        this.clearEntries();
        Path savesDir = this.minecraft.gameDirectory.toPath().resolve("saves");
        List<RrWorldSelectionEntry> loaded = new ArrayList<>();

        if (Files.isDirectory(savesDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(savesDir)) {
                for (Path path : stream) {
                    if (!Files.isDirectory(path)) continue;
                    if (!Files.exists(path.resolve("level.dat"))) continue;
                    try {
                        BasicFileAttributes attrs = Files.getFileAttributeView(path, BasicFileAttributeView.class).readAttributes();
                        long lastModified = Math.max(attrs.creationTime().toMillis(), attrs.lastModifiedTime().toMillis());
                        loaded.add(new RrWorldSelectionEntry(this, this.minecraft, path, lastModified));
                    } catch (IOException ignored) {}
                }
            } catch (IOException ignored) {}
        }

        loaded.sort((a, b) -> a.worldName.compareToIgnoreCase(b.worldName));

        String lowerFilter = this.filter.toLowerCase(Locale.ROOT);
        for (RrWorldSelectionEntry entry : loaded) {
            if (entry.matchesFilter(lowerFilter)) this.addEntry(entry);
        }
        this.refreshScrollAmount();
        this.screen.updateButtonStatus(this.getSelected());
    }

    public void updateFilter(String filter) {
        this.filter = filter;
        this.reload();
    }

    @Override
    public void setSelected(@Nullable RrWorldSelectionEntry entry) {
        super.setSelected(entry);
        this.screen.updateButtonStatus(entry);
    }

    @Override
    public int getRowWidth() { return 270; }
}
