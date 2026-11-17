package org.example.citywatch.view.saved_locations;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

import org.example.citywatch.interface_adapter.saved_locations.SavedLocationsController;
import org.example.citywatch.interface_adapter.saved_locations.SavedLocationsViewModel;
import org.example.citywatch.use_case.saved_locations.SavedLocationsOutputData.LocationData;

public final class SavedLocationsView extends JPanel {
    private final SavedLocationsController controller;
    private final SavedLocationsViewModel viewModel;

    private final DefaultListModel<LocationData> listModel = new DefaultListModel<>();
    private final JList<LocationData> locations = new JList<>(listModel);

    private final JTextField labelField = new JTextField(18);
    private final JTextField latitudeField = new JTextField(18);
    private final JTextField longitudeField = new JTextField(18);
    private final JTextField radiusField = new JTextField(18);

    private final JButton addButton = new JButton("Add");
    private final JButton editButton = new JButton("Save changes");
    private final JButton deleteButton = new JButton("Delete");
    private final JButton clearButton = new JButton("Clear form");
    private final JButton reloadButton = new JButton("Reload");

    private final JTextArea errorArea = new JTextArea(3, 40);
    private boolean busy;

    public SavedLocationsView(SavedLocationsController controller,
                              SavedLocationsViewModel viewModel) {
        super(new BorderLayout(12, 12));
        this.controller = controller;
        this.viewModel = viewModel;

        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setPreferredSize(new Dimension(850, 420));
        add(new JLabel("Saved locations — select a location to edit or delete"),
                BorderLayout.NORTH);

        locations.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();

        locations.setCellRenderer((list, value, index, selected, focused) -> {
            JLabel label = (JLabel) renderer.getListCellRendererComponent(
                    list, value, index, selected, focused);
            label.putClientProperty("html.disable", Boolean.TRUE);
            label.setText(String.format(Locale.ROOT,
                    "%s | %.5f, %.5f | %s m",
                    value.label, value.latitude, value.longitude,
                    Double.toString(value.radiusMetres)));
            return label;
        });

        JPanel form = new JPanel(new GridLayout(4, 2, 8, 8));
        form.add(new JLabel("Label"));
        form.add(labelField);
        form.add(new JLabel("Latitude (-90 to 90)"));
        form.add(latitudeField);
        form.add(new JLabel("Longitude (-180 to 180)"));
        form.add(longitudeField);
        form.add(new JLabel("Monitoring radius (metres)"));
        form.add(radiusField);

        JPanel formContainer = new JPanel(new BorderLayout());
        formContainer.add(form, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(locations), formContainer);
        split.setResizeWeight(0.55);
        add(split, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.add(addButton);
        buttons.add(editButton);
        buttons.add(deleteButton);
        buttons.add(clearButton);
        buttons.add(reloadButton);

        errorArea.setEditable(false);
        errorArea.setFocusable(false);
        errorArea.setOpaque(false);
        errorArea.setLineWrap(true);
        errorArea.setWrapStyleWord(true);
        errorArea.setForeground(new Color(160, 0, 0));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(errorArea, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        locations.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                LocationData selected = locations.getSelectedValue();
                if (selected != null) {
                    labelField.setText(selected.label);
                    latitudeField.setText(Double.toString(selected.latitude));
                    longitudeField.setText(Double.toString(selected.longitude));
                    radiusField.setText(Double.toString(selected.radiusMetres));
                }
                updateControls();
            }
        });

        addButton.addActionListener(event -> save(false));
        editButton.addActionListener(event -> save(true));
        deleteButton.addActionListener(event -> {
            LocationData selected = locations.getSelectedValue();
            if (selected != null) {
                runAsync(() -> controller.delete(selected.id));
            }
        });
        clearButton.addActionListener(event -> clearForm());
        reloadButton.addActionListener(event -> loadLocations());

        viewModel.addPropertyChangeListener(event -> {
            if ("locations".equals(event.getPropertyName())) {
                listModel.clear();
                for (LocationData location : viewModel.getLocations()) {
                    listModel.addElement(location);
                }
                clearForm();
            }

            // Errors leave the form intact so the user can correct or retry it.
            errorArea.setText(viewModel.getError());
        });

        updateControls();
    }

    public void loadLocations() {
        runAsync(controller::load);
    }

    private void save(boolean editing) {
        LocationData selected = locations.getSelectedValue();
        if (editing && selected == null) {
            return;
        }

        // Read Swing components on the EDT before starting background work.
        String label = labelField.getText();
        String latitude = latitudeField.getText();
        String longitude = longitudeField.getText();
        String radius = radiusField.getText();

        runAsync(() -> {
            if (editing) {
                controller.edit(selected.id, label, latitude, longitude, radius);
            }
            else {
                controller.add(label, latitude, longitude, radius);
            }
        });
    }

    private void clearForm() {
        locations.clearSelection();
        labelField.setText("");
        latitudeField.setText("");
        longitudeField.setText("");
        radiusField.setText("");
        updateControls();
    }

    private void updateControls() {
        boolean selected = locations.getSelectedValue() != null;
        locations.setEnabled(!busy);
        labelField.setEnabled(!busy);
        latitudeField.setEnabled(!busy);
        longitudeField.setEnabled(!busy);
        radiusField.setEnabled(!busy);
        addButton.setEnabled(!busy);
        editButton.setEnabled(!busy && selected);
        deleteButton.setEnabled(!busy && selected);
        clearButton.setEnabled(!busy);
        reloadButton.setEnabled(!busy);
    }

    private void runAsync(Runnable action) {
        if (busy) {
            return;
        }

        busy = true;
        updateControls();

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                action.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    viewModel.showError("Operation interrupted. Please retry.");
                }
                catch (ExecutionException exception) {
                    viewModel.showError("Unexpected failure. Reload and retry: "
                            + exception.getCause().getMessage());
                }
                finally {
                    busy = false;
                    updateControls();
                }
            }
        }.execute();
    }
}