package org.example.citywatch.app;

import java.nio.file.Path;
import javax.swing.JFrame;

import org.example.citywatch.data_access.profile.FileProfileRepository;
import org.example.citywatch.interface_adapter.saved_locations.SavedLocationsController;
import org.example.citywatch.interface_adapter.saved_locations.SavedLocationsPresenter;
import org.example.citywatch.interface_adapter.saved_locations.SavedLocationsViewModel;
import org.example.citywatch.use_case.gateway.ProfileRepository;
import org.example.citywatch.use_case.saved_locations.SavedLocationsInteractor;
import org.example.citywatch.view.saved_locations.SavedLocationsView;

public final class AppBuilder {
    public JFrame build() {
        Path profileFile = Path.of(
                System.getProperty("user.home"),
                ".citywatch",
                "profile.properties");

        ProfileRepository repository = new FileProfileRepository(profileFile);
        SavedLocationsViewModel viewModel = new SavedLocationsViewModel();
        SavedLocationsPresenter presenter = new SavedLocationsPresenter(viewModel);
        SavedLocationsInteractor interactor =
                new SavedLocationsInteractor(repository, presenter);
        SavedLocationsController controller =
                new SavedLocationsController(interactor);
        SavedLocationsView view = new SavedLocationsView(controller, viewModel);

        JFrame window = new JFrame("CityWatch — Saved Locations");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setContentPane(view);
        window.pack();
        window.setLocationRelativeTo(null);

        view.loadLocations();
        return window;
    }
}
