package org.example.citywatch.view.bookmarks;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

import org.example.citywatch.entity.IncidentSource;
import org.example.citywatch.interface_adapter.bookmarks.BookmarksController;
import org.example.citywatch.interface_adapter.bookmarks.BookmarksViewModel;
import org.example.citywatch.interface_adapter.bookmarks.BookmarksViewModel.BookmarkItem;

public final class BookmarksView extends JPanel {
    private final BookmarksController controller;
    private final BookmarksViewModel viewModel;

    private final DefaultListModel<BookmarkItem> listModel = new DefaultListModel<>();
    private final JList<BookmarkItem> bookmarks = new JList<>(listModel);

    private final JButton openButton = new JButton("Open");
    private final JButton removeButton = new JButton("Remove bookmark");
    private final JButton reloadButton = new JButton("Reload");

    private final JLabel messageLabel = new JLabel(" ");
    private final JTextArea errorArea = new JTextArea(2, 40);

    private BiConsumer<IncidentSource, String> onOpen = (source, id) -> { };
    private boolean busy;

    public BookmarksView(BookmarksController controller, BookmarksViewModel viewModel) {
        super(new BorderLayout(12, 12));
        this.controller = Objects.requireNonNull(controller);
        this.viewModel = Objects.requireNonNull(viewModel);

        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setPreferredSize(new Dimension(700, 400));
        add(new JLabel("Bookmarked reports, newest first"), BorderLayout.NORTH);

        bookmarks.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        bookmarks.setCellRenderer((list, value,
                                   index, selected, focused) -> {
            JLabel label = (JLabel) renderer.getListCellRendererComponent(
                    list, value, index, selected, focused);
            label.putClientProperty("html.disable", Boolean.TRUE);
            label.setText(describe(value));
            return label;
        });
        add(new JScrollPane(bookmarks), BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.add(openButton);
        buttons.add(removeButton);
        buttons.add(reloadButton);

        errorArea.setEditable(false);
        errorArea.setFocusable(false);
        errorArea.setOpaque(false);
        errorArea.setLineWrap(true);
        errorArea.setWrapStyleWord(true);
        errorArea.setForeground(new Color(160, 0, 0));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(messageLabel, BorderLayout.CENTER);
        bottom.add(errorArea, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        bookmarks.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                updateControls();
            }
        });

        openButton.addActionListener(event -> {
            BookmarkItem selected = bookmarks.getSelectedValue();
            if (selected != null) {
                onOpen.accept(selected.source, selected.sourceRecordId);
            }
        });
        removeButton.addActionListener(event -> {
            BookmarkItem selected = bookmarks.getSelectedValue();
            if (selected != null) {
                runAsync(() -> controller.remove(selected.source, selected.sourceRecordId));
            }
        });
        reloadButton.addActionListener(event -> loadBookmarks());

        viewModel.addPropertyChangeListener(event -> {
            if (BookmarksViewModel.BOOKMARKS_PROPERTY.equals(event.getPropertyName())) {
                listModel.clear();
                for (BookmarkItem item : viewModel.getBookmarks()) {
                    listModel.addElement(item);
                }
                messageLabel.setText(viewModel.getMessage().isEmpty()
                        ? " " : viewModel.getMessage());
            }
            errorArea.setText(viewModel.getError());
            updateControls();
        });

        updateControls();
    }

    /**
     * Asks for the current bookmarks and call once when the screen is shown.
     */
    public void loadBookmarks() {
        runAsync(controller::load);
    }

    /**
     * Sets what happens when the user opens a bookmark,
     * e.g. showing update history(not implemented yet, to do for UC8).
     */
    public void setOnOpen(BiConsumer<IncidentSource, String> onOpen) {
        this.onOpen = Objects.requireNonNull(onOpen);
    }

    private static String describe(BookmarkItem item) {
        String text = item.title + "  |  " + item.savedAtText;
        return item.statusText.isEmpty() ? text : text + "  |  " + item.statusText;
    }

    private void updateControls() {
        boolean selected = bookmarks.getSelectedValue() != null;
        bookmarks.setEnabled(!busy);
        openButton.setEnabled(!busy && selected);
        removeButton.setEnabled(!busy && selected);
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
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    viewModel.showError("Operation interrupted. Please retry.");
                } catch (ExecutionException exception) {
                    viewModel.showError("Unexpected failure. Reload and retry: "
                            + exception.getCause().getMessage());
                } finally {
                    busy = false;
                    updateControls();
                }
            }
        }.execute();
    }
}
