package org.example.citywatch.view.incident_preferences;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import org.example.citywatch.interface_adapter.incident_preferences.IncidentPreferencesController;
import org.example.citywatch.interface_adapter.incident_preferences.IncidentPreferencesViewModel;
import org.example.citywatch.use_case.incident_preferences.IncidentPreferencesOutputData;

public final class IncidentPreferencesView extends JPanel {
    private final IncidentPreferencesController controller;
    private final IncidentPreferencesViewModel viewModel;

    private final JTextField categoriesField = new JTextField(24);
    private final JComboBox<AgeOption> ageChoices = new JComboBox<>();
    private final JCheckBox includeUnknownTimes = new JCheckBox("Include");

    private final JTextField proximityField = new JTextField(8);
    private final JTextField recencyField = new JTextField(8);
    private final JTextField categoryField = new JTextField(8);
    private final JTextField severityField = new JTextField(8);

    private final JButton saveButton = new JButton("Save preferences");
    private final JButton reloadButton = new JButton("Reload");
    private final JTextArea statusArea = new JTextArea(3, 40);

    private boolean loaded;
    private boolean busy;

    public IncidentPreferencesView(
            IncidentPreferencesController controller,
            IncidentPreferencesViewModel viewModel) {
        super(new BorderLayout(12, 12));
        this.controller = controller;
        this.viewModel = viewModel;

        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        ageChoices.addItem(new AgeOption("Any time", Optional.empty()));
        ageChoices.addItem(new AgeOption(
                "Last 24 hours", Optional.of(Duration.ofHours(24))));
        ageChoices.addItem(new AgeOption(
                "Last 72 hours", Optional.of(Duration.ofHours(72))));
        ageChoices.addItem(new AgeOption(
                "Last 7 days", Optional.of(Duration.ofDays(7))));

        add(new JLabel(
                        "Leave categories empty for all categories. "
                                + "Larger weights give a factor more influence."),
                BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Category identifiers (comma-separated)"));
        form.add(categoriesField);
        form.add(new JLabel("Report recency"));
        form.add(ageChoices);
        form.add(new JLabel("Reports with unknown timestamps"));
        form.add(includeUnknownTimes);
        form.add(new JLabel("Proximity weight"));
        form.add(proximityField);
        form.add(new JLabel("Recency weight"));
        form.add(recencyField);
        form.add(new JLabel("Category weight"));
        form.add(categoryField);
        form.add(new JLabel("Severity weight"));
        form.add(severityField);

        JPanel formContainer = new JPanel(new BorderLayout());
        formContainer.add(form, BorderLayout.NORTH);
        add(formContainer, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.add(saveButton);
        buttons.add(reloadButton);

        statusArea.setEditable(false);
        statusArea.setFocusable(false);
        statusArea.setOpaque(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(statusArea, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        saveButton.addActionListener(event -> savePreferences());
        reloadButton.addActionListener(event -> loadPreferences());

        viewModel.addPropertyChangeListener(event -> {
            if ("preferences".equals(event.getPropertyName())) {
                populate(viewModel.getPreferences());
            }
            else if ("error".equals(event.getPropertyName())) {
                statusArea.setForeground(new Color(160, 0, 0));
                statusArea.setText(viewModel.getError());
            }
        });

        updateControls();
    }

    public void loadPreferences() {
        runAsync(controller::load);
    }

    private void savePreferences() {
        // Capture Swing values on the EDT before starting background work.
        String categories = categoriesField.getText();
        AgeOption age = (AgeOption) ageChoices.getSelectedItem();
        boolean unknownTimes = includeUnknownTimes.isSelected();
        String proximity = proximityField.getText();
        String recency = recencyField.getText();
        String category = categoryField.getText();
        String severity = severityField.getText();

        if (age == null) {
            return;
        }

        runAsync(() -> controller.save(
                categories,
                age.maximumAge,
                unknownTimes,
                proximity,
                recency,
                category,
                severity));
    }

    private void populate(IncidentPreferencesOutputData data) {
        categoriesField.setText(
                String.join(", ", new TreeSet<>(data.categories)));
        selectAge(data.maximumAge);
        includeUnknownTimes.setSelected(data.includeUnknownTimes);
        proximityField.setText(Double.toString(data.proximityWeight));
        recencyField.setText(Double.toString(data.recencyWeight));
        categoryField.setText(Double.toString(data.categoryWeight));
        severityField.setText(Double.toString(data.severityWeight));

        loaded = true;
        statusArea.setForeground(new Color(0, 110, 0));
        statusArea.setText(data.saved ? "Preferences saved." : "");
        updateControls();
    }

    private void selectAge(Optional<Duration> maximumAge) {
        for (int i = 0; i < ageChoices.getItemCount(); i++) {
            if (ageChoices.getItemAt(i).maximumAge.equals(maximumAge)) {
                ageChoices.setSelectedIndex(i);
                return;
            }
        }

        AgeOption custom = new AgeOption(
                "Saved custom time limit", maximumAge);
        ageChoices.addItem(custom);
        ageChoices.setSelectedItem(custom);
    }

    private void updateControls() {
        boolean editable = loaded && !busy;

        for (JTextField field : List.of(
                categoriesField,
                proximityField,
                recencyField,
                categoryField,
                severityField)) {
            field.setEnabled(editable);
        }

        ageChoices.setEnabled(editable);
        includeUnknownTimes.setEnabled(editable);
        saveButton.setEnabled(editable);
        reloadButton.setEnabled(!busy);
    }

    private void runAsync(Runnable action) {
        if (busy) {
            return;
        }

        busy = true;
        statusArea.setForeground(Color.DARK_GRAY);
        statusArea.setText("Working...");
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
                    viewModel.showError(
                            "Operation interrupted. Please retry.");
                }
                catch (ExecutionException exception) {
                    viewModel.showError(
                            "Unexpected failure. Reload and retry: "
                                    + exception.getCause().getMessage());
                }
                finally {
                    busy = false;
                    updateControls();
                }
            }
        }.execute();
    }

    private static final class AgeOption {
        private final String label;
        private final Optional<Duration> maximumAge;

        private AgeOption(String label, Optional<Duration> maximumAge) {
            this.label = label;
            this.maximumAge = maximumAge;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}