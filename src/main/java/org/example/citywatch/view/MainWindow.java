package org.example.citywatch.view;

import java.awt.Dimension;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;

/** The application window; feature construction belongs in AppBuilder. */
public final class MainWindow extends JFrame {
    private final JTabbedPane navigation = new JTabbedPane();

    public MainWindow() {
        super("CityWatch");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        navigation.setPreferredSize(new Dimension(900, 500));
        setContentPane(navigation);
    }

    /** Registers a feature's existing view, retaining its state between tab changes. */
    public void addView(String title, JComponent view) {
        navigation.addTab(title, view);
    }
}
