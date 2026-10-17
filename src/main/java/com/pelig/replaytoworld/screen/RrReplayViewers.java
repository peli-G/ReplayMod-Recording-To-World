package com.pelig.replaytoworld.screen;

import com.pelig.replaytoworld.ReplayToWorldMod;
import com.replaymod.lib.de.johni0702.minecraft.gui.container.GuiPanel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiButton;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiElement;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.GuiLabel;
import com.replaymod.lib.de.johni0702.minecraft.gui.element.advanced.AbstractGuiResourceLoadingList;
import com.replaymod.lib.de.johni0702.minecraft.gui.function.Tickable;
import com.replaymod.replay.ReplayModReplay;
import com.replaymod.replay.gui.screen.GuiReplayViewer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Opens ReplayMod's own Replay Viewer (same look as ReplayMod) with Replay-to-World's buttons added.
 *
 *  - Normal mode: pick one replay, "Convert to World" opens the options screen
 *    (convert / combine / import into an existing world).
 *  - Combine mode: every click on a replay ticks / unticks it. The combine order is shown as a green
 *    [1], [2], ... in front of the replay name; later replays win where chunks overlap.
 */
public final class RrReplayViewers {

    private static final int SPACING = 5;

    private RrReplayViewers() {}

    // ---------------------------------------------------------------- normal mode

    public static void openNormal() {
        GuiReplayViewer viewer = new GuiReplayViewer(ReplayModReplay.instance);
        viewer.upperButtonPanel.removeElement(viewer.loadButton);

        GuiButton convertButton = new GuiButton();
        convertButton.setLabel("Convert to World");
        convertButton.onClick(ignored -> {
            List<GuiReplayViewer.GuiReplayEntry> selected = viewer.list.getSelected();
            if (selected.size() == 1) {
                Minecraft.getInstance().setScreen(new RrConvertOptionsScreen(
                        viewer.toMinecraft(), selected.get(0).file.toPath()));
            }
        });
        // ReplayMod enables its "replay specific" buttons only while exactly one replay is selected.
        viewer.replaySpecificButtons.add(convertButton);
        convertButton.setEnabled(false);

        GuiButton gameruleButton = gameruleButton(viewer);

        viewer.upperButtonPanel.addElements(null, convertButton, gameruleButton);
        viewer.display();
        sizeUpperButtons(viewer, convertButton, gameruleButton);
    }

    // ---------------------------------------------------------------- combine mode

    public static void openCombine(Path preselected) {
        GuiReplayViewer viewer = new GuiReplayViewer(ReplayModReplay.instance);
        viewer.upperButtonPanel.removeElement(viewer.loadButton);

        CombineState state = new CombineState(viewer);
        if (preselected != null) state.order.add(preselected.toFile().getAbsoluteFile());

        GuiLabel title = new GuiLabel();
        title.setText("Select Replays to Combine");
        viewer.setTitle(title);
        state.title = title;

        GuiButton combineButton = new GuiButton();
        combineButton.setLabel("Convert Ticked Replay");
        combineButton.onClick(ignored -> {
            List<Path> paths = state.orderedExistingPaths();
            if (!paths.isEmpty()) {
                RrConversionLauncher.start(Minecraft.getInstance(), viewer.toMinecraft(), paths);
            }
        });
        state.combineButton = combineButton;

        GuiButton gameruleButton = gameruleButton(viewer);

        // Invisible element whose only job is to get ticked by jGui every client tick.
        TickingLabel ticker = new TickingLabel(state::refresh);
        ticker.setText("");

        viewer.upperButtonPanel.addElements(null, combineButton, gameruleButton);
        viewer.lowerButtonPanel.addElements(null, ticker);

        state.hookClicks();
        viewer.display();
        sizeUpperButtons(viewer, combineButton, gameruleButton);
        state.refresh();
    }

    // ---------------------------------------------------------------- shared helpers

    private static GuiButton gameruleButton(GuiReplayViewer viewer) {
        GuiButton gameruleButton = new GuiButton();
        gameruleButton.setLabel("Gamerule Options");
        gameruleButton.onClick(ignored -> Minecraft.getInstance().setScreen(
                RrGameRuleScreen.create(viewer.toMinecraft())));
        return gameruleButton;
    }

    /** Same sizing as ReplayMod's own rows: first button spans Rename+Delete, second spans Edit+Cancel. */
    private static void sizeUpperButtons(GuiReplayViewer viewer, GuiButton first, GuiButton second) {
        int renameW = viewer.renameButton.getMinSize().getWidth();
        int deleteW = viewer.deleteButton.getMinSize().getWidth();
        int editorW = viewer.editorButton.getMinSize().getWidth();
        int cancelW = viewer.cancelButton.getMinSize().getWidth();
        first.setSize(renameW + deleteW + SPACING, 20);
        second.setSize(editorW + cancelW + SPACING, 20);
    }

    /** A label that runs a callback every tick (jGui ticks every Tickable element on the screen). */
    private static final class TickingLabel extends GuiLabel implements Tickable {
        private final Runnable onTick;
        TickingLabel(Runnable onTick) { this.onTick = onTick; }
        @Override public void tick() { this.onTick.run(); }
    }

    private static final class CombineState {
        final GuiReplayViewer viewer;
        /** Ticked replays in the order they were ticked. */
        final LinkedHashSet<File> order = new LinkedHashSet<>();
        /** Original name text of every entry we've prefixed, so it can be restored when unticked. */
        final Map<GuiReplayViewer.GuiReplayEntry, String> originalNames = new IdentityHashMap<>();
        Set<File> lastSelection = new HashSet<>();
        GuiButton combineButton;
        GuiLabel title;

        CombineState(GuiReplayViewer viewer) { this.viewer = viewer; }

        /**
         * Chains onto ReplayMod's own selection callback: each click on a replay toggles it.
         * (A plain click leaves exactly the clicked replay selected; Ctrl-click adds/removes one.)
         */
        void hookClicks() {
            Runnable original = null;
            try {
                Field f = AbstractGuiResourceLoadingList.class.getDeclaredField("onSelectionChanged");
                f.setAccessible(true);
                original = (Runnable) f.get(this.viewer.list);
            } catch (Throwable t) {
                ReplayToWorldMod.LOGGER.warn("[ReplayToWorld] Couldn't read ReplayMod's selection callback: {}", t.toString());
            }
            final Runnable replayModCallback = original;
            this.viewer.list.onSelectionChanged(() -> {
                if (replayModCallback != null) replayModCallback.run();
                this.onSelectionChanged();
            });
        }

        private void onSelectionChanged() {
            Set<File> now = new HashSet<>();
            for (GuiReplayViewer.GuiReplayEntry e : this.viewer.list.getSelected()) now.add(e.file.getAbsoluteFile());

            Set<File> clicked;
            boolean ctrlRemovedDownToOne = this.lastSelection.size() > 1 && this.lastSelection.containsAll(now);
            if (now.size() == 1 && !ctrlRemovedDownToOne) {
                clicked = now;                                   // plain click (or ctrl-click from nothing)
            } else {
                clicked = new HashSet<>(this.lastSelection);     // ctrl-click: whatever changed
                clicked.addAll(now);
                Set<File> both = new HashSet<>(this.lastSelection);
                both.retainAll(now);
                clicked.removeAll(both);
            }
            for (File f : clicked) {
                if (!this.order.remove(f)) this.order.add(f);
            }
            this.lastSelection = now;
            this.refresh();
        }

        /** Loaded replays sit inside wrapper panels (list panel -> resources panel -> element -> entry). */
        private static void collectEntries(GuiPanel panel, List<GuiReplayViewer.GuiReplayEntry> out, int depth) {
            if (depth > 4) return;
            for (GuiElement child : panel.getChildren()) {
                if (child instanceof GuiReplayViewer.GuiReplayEntry entry) {
                    out.add(entry);
                } else if (child instanceof GuiPanel inner) {
                    collectEntries(inner, out, depth + 1);
                }
            }
        }

        List<Path> orderedExistingPaths() {
            List<Path> result = new ArrayList<>();
            for (File f : this.order) if (f.exists()) result.add(f.toPath());
            return result;
        }

        /** Updates the [n] prefixes, the title hint and the combine button. Runs every tick. */
        void refresh() {
            List<File> ordered = new ArrayList<>(this.order);
            List<GuiReplayViewer.GuiReplayEntry> entries = new ArrayList<>();
            collectEntries(this.viewer.list.getListPanel(), entries, 0);
            for (GuiReplayViewer.GuiReplayEntry entry : entries) {
                String original = this.originalNames.computeIfAbsent(entry, e -> e.name.getText());
                int index = ordered.indexOf(entry.file.getAbsoluteFile());
                String wanted = index >= 0 ? "§a[" + (index + 1) + "] §r" + original : original;
                if (!wanted.equals(entry.name.getText())) entry.name.setText(wanted);
            }

            int count = 0;
            for (File f : ordered) if (Files.exists(f.toPath())) count++;
            if (this.combineButton != null) {
                this.combineButton.setLabel(count >= 2 ? "Combine " + count + " to World" : "Convert Ticked Replay");
                this.combineButton.setEnabled(count >= 1);
            }
            if (this.title != null) {
                String text = count >= 2 ? "§aCombining " + count + " replays - later ones win overlaps"
                        : count == 1 ? "§e1 ticked - click more replays to combine them"
                        : "§cClick replays to tick them (you need at least one)";
                if (!text.equals(this.title.getText())) this.title.setText(text);
            }
        }
    }
}
