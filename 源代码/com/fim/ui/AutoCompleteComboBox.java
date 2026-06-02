package com.fim.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class AutoCompleteComboBox extends JComboBox<String> {
    private List<String> allItems = new ArrayList<>();
    private String currentText = "";
    private boolean autoCompleteEnabled = true;

    public AutoCompleteComboBox() {
        setEditable(true);
        JTextField editor = (JTextField) getEditor().getEditorComponent();

        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (!autoCompleteEnabled) return;
                if (e.getKeyCode() == KeyEvent.VK_ENTER
                    || e.getKeyCode() == KeyEvent.VK_UP
                    || e.getKeyCode() == KeyEvent.VK_DOWN
                    || e.getKeyCode() == KeyEvent.VK_ESCAPE) return;

                SwingUtilities.invokeLater(() -> {
                    String text = editor.getText();
                    if (text != null && !text.equals(currentText)) {
                        currentText = text;
                        filterItems(text);
                    }
                });
            }
        });

        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { scheduleFilter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { scheduleFilter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { scheduleFilter(); }
            private void scheduleFilter() {
                SwingUtilities.invokeLater(() -> {
                    String text = editor.getText();
                    if (text != null && !text.equals(currentText)) {
                        currentText = text;
                        filterItems(text);
                    }
                });
            }
        });
    }

    public void setAllItems(List<String> items) {
        this.allItems = new ArrayList<>(items);
        autoCompleteEnabled = false;
        removeAllItems();
        for (String item : allItems) {
            addItem(item);
        }
        autoCompleteEnabled = true;
    }

    private void filterItems(String text) {
        if (text == null || text.isEmpty()) {
            autoCompleteEnabled = false;
            Object selected = getSelectedItem();
            removeAllItems();
            for (String item : allItems) {
                addItem(item);
            }
            if (selected != null) setSelectedItem(selected);
            hidePopup();
            autoCompleteEnabled = true;
            return;
        }

        String lower = text.toLowerCase();
        List<String> matched = new ArrayList<>();
        for (String item : allItems) {
            if (item.toLowerCase().contains(lower)) {
                matched.add(item);
            }
        }

        autoCompleteEnabled = false;
        Object selected = getSelectedItem();
        removeAllItems();
        for (String item : matched) {
            addItem(item);
        }
        setSelectedItem(text);
        if (!matched.isEmpty()) {
            showPopup();
        } else {
            hidePopup();
        }
        autoCompleteEnabled = true;
    }

    public String getText() {
        Object sel = getSelectedItem();
        if (sel != null) return sel.toString();
        JTextField editor = (JTextField) getEditor().getEditorComponent();
        return editor != null ? editor.getText() : "";
    }
}
