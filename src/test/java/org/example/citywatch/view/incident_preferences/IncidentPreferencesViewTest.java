package org.example.citywatch.view.incident_preferences;

import java.awt.Component;
import java.awt.Container;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.example.citywatch.data_access.profile.FileProfileRepository;
import org.example.citywatch.entity.RankingPreset;
import org.example.citywatch.entity.RankingWeights;
import org.example.citywatch.entity.UserPreferences;
import org.example.citywatch.entity.UserProfile;
import org.example.citywatch.interface_adapter.incident_preferences.IncidentPreferencesController;
import org.example.citywatch.interface_adapter.incident_preferences.IncidentPreferencesPresenter;
import org.example.citywatch.interface_adapter.incident_preferences.IncidentPreferencesViewModel;
import org.example.citywatch.use_case.incident_preferences.IncidentPreferencesInteractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class IncidentPreferencesViewTest {
    @TempDir
    Path directory;

    @Test
    void newProfileShowsRecommendedAndItsExplanation() throws Exception {
        Screen screen = openScreen();

        onEventThread(() -> {
            screen.assertSelected("Recommended");
            assertEquals(RankingPreset.values().length,
                    screen.rankingChoices().getItemCount());
            screen.assertExplanation(RankingPreset.RECOMMENDED.getDescription());
            assertTrue(screen.saveButton.isEnabled());
        });
    }

    @ParameterizedTest
    @EnumSource(RankingPreset.class)
    void selectedPresetAndExplanationSurviveSavingAndReopening(
            RankingPreset preset) throws Exception {
        Screen screen = openScreen();
        onEventThread(() -> {
            screen.selectRanking(preset.getLabel());
            screen.assertExplanation(preset.getDescription());
        });

        screen.perform(() -> screen.saveButton.doClick(0));
        assertExactWeights(preset.getWeights(), storedPreferences().getRankingWeights());

        Screen reopened = openScreen();
        onEventThread(() -> {
            reopened.assertSelected(preset.getLabel());
            reopened.assertExplanation(preset.getDescription());
        });
    }

    @ParameterizedTest
    @MethodSource("customWeights")
    void editingOtherPreferencesPreservesCustomWeightsExactly(
            RankingWeights custom) throws Exception {
        saveCustomProfile(custom);
        Screen screen = openScreen();

        onEventThread(() -> {
            screen.assertSelected("Custom");
            screen.control(JTextField.class, field -> true)
                    .setText("CONSTRUCTION");
            JComboBox<?> ageChoices = screen.control(JComboBox.class,
                    box -> box.getItemCount() > 0
                            && "Any time".equals(box.getItemAt(0).toString()));
            selectOption(ageChoices, "Last 24 hours");
        });

        screen.perform(() -> screen.saveButton.doClick(0));

        UserPreferences stored = storedPreferences();
        assertExactWeights(custom, stored.getRankingWeights());
        assertEquals(Set.of("CONSTRUCTION"), stored.getCategories());
        assertEquals(Optional.of(Duration.ofHours(24)), stored.getMaximumAge());

        Screen reopened = openScreen();
        onEventThread(() -> reopened.assertSelected("Custom"));
    }

    static Stream<RankingWeights> customWeights() {
        return Stream.of(
                new RankingWeights(0.6, 0.2, 0.15, 0.05),
                new RankingWeights(0.5, 0.3, 0.15, Math.nextUp(0.05)));
    }

    @Test
    void explicitlySelectingAPresetReplacesCustomWeights() throws Exception {
        saveCustomProfile(new RankingWeights(0.6, 0.2, 0.15, 0.05));
        Screen screen = openScreen();

        onEventThread(() -> {
            screen.assertSelected("Custom");
            screen.selectRanking(RankingPreset.NEARBY_FIRST.getLabel());
        });
        screen.perform(() -> screen.saveButton.doClick(0));

        assertExactWeights(RankingPreset.NEARBY_FIRST.getWeights(),
                storedPreferences().getRankingWeights());

        Screen reopened = openScreen();
        onEventThread(() -> {
            reopened.assertSelected(RankingPreset.NEARBY_FIRST.getLabel());
            assertEquals(RankingPreset.values().length,
                    reopened.rankingChoices().getItemCount());
        });
    }

    private Screen openScreen() throws Exception {
        AtomicReference<Screen> result = new AtomicReference<>();
        onEventThread(() -> result.set(new Screen(profileFile())));
        Screen screen = result.get();
        screen.perform(screen.view::loadPreferences);
        return screen;
    }

    private Path profileFile() {
        return directory.resolve("profile.properties");
    }

    private UserPreferences storedPreferences() throws Exception {
        return new FileProfileRepository(profileFile()).load().orElseThrow()
                .getPreferences();
    }

    private void saveCustomProfile(RankingWeights weights) throws Exception {
        UserPreferences preferences = new UserPreferences(
                Set.of(), Optional.empty(), true, weights);
        new FileProfileRepository(profileFile()).save(
                UserProfile.empty("local").withPreferences(preferences));
    }

    private static void assertExactWeights(
            RankingWeights expected, RankingWeights actual) {
        assertAll(
                () -> assertEquals(expected.getProximity(), actual.getProximity()),
                () -> assertEquals(expected.getRecency(), actual.getRecency()),
                () -> assertEquals(expected.getCategory(), actual.getCategory()),
                () -> assertEquals(expected.getSeverity(), actual.getSeverity()));
    }

    private static void onEventThread(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }

    private static void selectOption(JComboBox<?> choices, String label) {
        for (int index = 0; index < choices.getItemCount(); index++) {
            if (label.equals(choices.getItemAt(index).toString())) {
                choices.setSelectedIndex(index);
                return;
            }
        }
        fail("Missing option: " + label);
    }

    private static <T extends Component> T findControl(
            Component root, Class<T> type, Predicate<T> matches) {
        if (type.isInstance(root) && matches.test(type.cast(root))) {
            return type.cast(root);
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                T found = findControl(child, type, matches);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // Exercise the real controller, interactor, presenter and file storage without
    // opening a window or accessing private fields. All Swing access stays on the EDT.
    private static final class Screen {
        private final IncidentPreferencesViewModel viewModel;
        private final IncidentPreferencesView view;
        private final JButton saveButton;
        private final JButton reloadButton;

        private Screen(Path file) {
            viewModel = new IncidentPreferencesViewModel();
            IncidentPreferencesPresenter presenter =
                    new IncidentPreferencesPresenter(viewModel);
            IncidentPreferencesInteractor interactor = new IncidentPreferencesInteractor(
                    new FileProfileRepository(file), presenter);
            view = new IncidentPreferencesView(
                    new IncidentPreferencesController(interactor), viewModel);
            saveButton = control(JButton.class,
                    button -> "Save preferences".equals(button.getText()));
            reloadButton = control(JButton.class,
                    button -> "Reload".equals(button.getText()));
        }

        private <T extends Component> T control(Class<T> type, Predicate<T> matches) {
            T found = findControl(view, type, matches);
            assertNotNull(found, "Missing control: " + type.getSimpleName());
            return found;
        }

        private JComboBox<?> rankingChoices() {
            return control(JComboBox.class,
                    box -> box.getItemCount() > 0
                            && "Recommended".equals(box.getItemAt(0).toString()));
        }

        private void selectRanking(String label) {
            selectOption(rankingChoices(), label);
        }

        private void assertSelected(String label) {
            assertEquals(label, String.valueOf(rankingChoices().getSelectedItem()));
        }

        private void assertExplanation(String expected) {
            control(JTextArea.class, area -> expected.equals(area.getText()));
        }

        private void perform(Runnable action) throws Exception {
            CompletableFuture<Void> finished = new CompletableFuture<>();
            PropertyChangeListener listener = event -> {
                if (Boolean.TRUE.equals(event.getNewValue())) {
                    finished.complete(null);
                }
            };
            onEventThread(() -> {
                reloadButton.addPropertyChangeListener("enabled", listener);
                action.run();
            });
            try {
                // Re-enabling Reload marks completion of SwingWorker.done(),
                // including presenter updates; no sleeps or timing guesses are needed.
                finished.get(10, TimeUnit.SECONDS);
            }
            finally {
                onEventThread(() -> reloadButton.removePropertyChangeListener(
                        "enabled", listener));
            }
            onEventThread(() -> assertEquals("", viewModel.getError()));
        }
    }
}
