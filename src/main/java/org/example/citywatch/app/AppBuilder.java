package org.example.citywatch.app;

import java.awt.BorderLayout;
import java.nio.file.Path;
import java.util.Objects;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import org.example.citywatch.data_access.profile.FileProfileRepository;
import org.example.citywatch.interface_adapter.saved_locations.SavedLocationsController;
import org.example.citywatch.interface_adapter.saved_locations.SavedLocationsPresenter;
import org.example.citywatch.interface_adapter.saved_locations.SavedLocationsViewModel;
import org.example.citywatch.use_case.gateway.ProfileRepository;
import org.example.citywatch.use_case.saved_locations.SavedLocationsInteractor;
import org.example.citywatch.view.MainWindow;
import org.example.citywatch.view.saved_locations.SavedLocationsView;

public final class AppBuilder {
    // Share this repository with every feature that reads or changes the profile.
    private final ProfileRepository profileRepository;

    public AppBuilder() {
        this(new FileProfileRepository(Path.of(
                System.getProperty("user.home"),
                ".citywatch",
                "profile.properties")));
    }

    public AppBuilder(ProfileRepository profileRepository) {
        this.profileRepository = Objects.requireNonNull(profileRepository);
    }

    public JFrame build() {
        MainWindow window = new MainWindow();
        SavedLocationsView savedLocations = createSavedLocationsView();

        // Replace placeholders with wired feature views as they become available.
        window.addView("Saved Locations", savedLocations);
        window.addView("Incident Preferences",
                placeholder("Incident preferences are coming soon."));
        window.addView("Road Dashboard",
                placeholder("The road dashboard is coming soon."));
        window.addView("TTC Disruptions",
                placeholder("TTC disruptions are coming soon."));
        window.addView("Bookmarks",
                placeholder("Bookmarks are coming soon."));

        window.pack();
        window.setLocationRelativeTo(null);
        savedLocations.loadLocations();
        return window;
    }

    private SavedLocationsView createSavedLocationsView() {
        SavedLocationsViewModel viewModel = new SavedLocationsViewModel();
        SavedLocationsPresenter presenter = new SavedLocationsPresenter(viewModel);
        SavedLocationsInteractor interactor =
                new SavedLocationsInteractor(profileRepository, presenter);
        SavedLocationsController controller =
                new SavedLocationsController(interactor);

        return new SavedLocationsView(controller, viewModel);
    }

    private JPanel placeholder(String message) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(message, SwingConstants.CENTER), BorderLayout.CENTER);
        return panel;
    }
}
