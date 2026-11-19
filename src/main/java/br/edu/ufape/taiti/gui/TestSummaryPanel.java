package br.edu.ufape.taiti.gui;

import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;

/**
 * Right-panel component showing the summary of selected test scenarios.
 *
 * Two states:
 *  - Empty  : no tests selected yet (shows only the "Important" box)
 *  - Filled : at least one test selected (shows checkmark, summary rows, clear button)
 *
 * Usage in MainPanel:
 *   testSummaryPanel = new TestSummaryPanel();
 *   testSummaryPanel.setOnClearClicked(() -> clearCurrentFileSelection());
 *   southPanel.add(testSummaryPanel, BorderLayout.CENTER);
 *
 *   // To update:
 *   testSummaryPanel.update(selectedCount, totalCount, fileName);
 */
public class TestSummaryPanel extends JPanel {

    // Header
    private final JLabel selectedCountBadge;

    // Check block
    private JLabel checkTitle;
    private JLabel checkSub;

    // Filled state
    private final JPanel filledPanel;
    private final JLabel summaryFileName;
    private final JLabel summaryTotal;
    private final JLabel summarySelected;

    // Callback
    private Runnable onClearClicked;

    public TestSummaryPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        // ── Header (always visible) ──────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        header.setOpaque(false);

        JPanel stepBadge = makeStepBadge("3");

        JLabel titleLabel = new JLabel("Selected tests");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        selectedCountBadge = new JLabel("0 selected");
        selectedCountBadge.setFont(selectedCountBadge.getFont().deriveFont(11f));
        selectedCountBadge.setOpaque(true);
        selectedCountBadge.setBackground(new Color(50, 100, 170));
        selectedCountBadge.setForeground(Color.WHITE);
        selectedCountBadge.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        JPanel titleRow = new JPanel(new BorderLayout(6, 0));
        titleRow.setOpaque(false);
        titleRow.add(titleLabel, BorderLayout.CENTER);
        titleRow.add(selectedCountBadge, BorderLayout.EAST);

        header.add(stepBadge, BorderLayout.WEST);
        header.add(titleRow, BorderLayout.CENTER);

        // ── Important box (always visible) ──────────────────────────────────
        JPanel importantBox = buildImportantBox();

        // ── Filled panel (visible only when count > 0) ───────────────────────
        filledPanel = new JPanel();
        filledPanel.setLayout(new BoxLayout(filledPanel, BoxLayout.Y_AXIS));
        filledPanel.setOpaque(false);
        filledPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        filledPanel.setVisible(false);

        // Check icon
        JLabel checkIcon = new JLabel(makeCheckIcon(), SwingConstants.CENTER);
        checkIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        checkTitle = new JLabel("All scenarios in this file have been selected.");
        checkTitle.setFont(checkTitle.getFont().deriveFont(Font.BOLD, 12f));
        checkTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        checkTitle.setHorizontalAlignment(SwingConstants.CENTER);

        checkSub = new JLabel(
                "<html><center>You can uncheck the ones that are not related to the task.</center></html>");
        checkSub.setFont(checkSub.getFont().deriveFont(11f));
        checkSub.setForeground(JBColor.GRAY);
        checkSub.setAlignmentX(Component.CENTER_ALIGNMENT);
        checkSub.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel checkBlock = new JPanel();
        checkBlock.setLayout(new BoxLayout(checkBlock, BoxLayout.Y_AXIS));
        checkBlock.setOpaque(false);
        checkBlock.setAlignmentX(Component.LEFT_ALIGNMENT);
        checkBlock.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        checkBlock.add(checkIcon);
        checkBlock.add(Box.createVerticalStrut(8));
        checkBlock.add(checkTitle);
        checkBlock.add(Box.createVerticalStrut(4));
        checkBlock.add(checkSub);

        // Summary rows
        JLabel resumoTitle = new JLabel("Summary of selected tests");
        resumoTitle.setFont(resumoTitle.getFont().deriveFont(Font.BOLD, 12f));
        resumoTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        summaryFileName = new JLabel("—");
        summaryTotal    = new JLabel("0");
        summarySelected = new JLabel("0");

        JPanel summaryRows = buildSummaryTable();
        summaryRows.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Clear button
        JButton clearBtn = new JButton("\uD83D\uDDD1 Clear selection for this file");
        clearBtn.setForeground(new JBColor(new Color(180, 40, 40), new Color(220, 80, 80)));
        clearBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        clearBtn.addActionListener(e -> { if (onClearClicked != null) onClearClicked.run(); });

        JPanel summaryBlock = new JPanel();
        summaryBlock.setLayout(new BoxLayout(summaryBlock, BoxLayout.Y_AXIS));
        summaryBlock.setOpaque(false);
        summaryBlock.setAlignmentX(Component.LEFT_ALIGNMENT);
        summaryBlock.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        summaryBlock.add(resumoTitle);
        summaryBlock.add(Box.createVerticalStrut(10));
        summaryBlock.add(summaryRows);
        summaryBlock.add(Box.createVerticalStrut(12));
        summaryBlock.add(clearBtn);

        filledPanel.add(checkBlock);
        filledPanel.add(summaryBlock);

        // ── Body scroll ──────────────────────────────────────────────────────
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(importantBox);
        body.add(filledPanel);

        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public void setOnClearClicked(Runnable callback) {
        this.onClearClicked = callback;
    }

    /**
     * Refreshes the panel with the latest counts.
     *
     * @param selectedCount number of scenarios currently selected
     * @param totalCount    total scenarios in the tableModel (same as selectedCount here,
     *                      pass the value you want shown in "Total scenarios")
     * @param fileName      name of the currently open .feature file (may be null)
     */
    public void update(int selectedCount, int totalCount, String fileName) {
        selectedCountBadge.setText(selectedCount + (selectedCount == 1 ? " selected" : " selected"));
        filledPanel.setVisible(selectedCount > 0);

        summarySelected.setText(String.valueOf(selectedCount));
        summaryTotal.setText(String.valueOf(totalCount));
        summaryFileName.setText(fileName != null ? fileName : "—");

        if (totalCount > 0 && selectedCount >= totalCount) {
            checkTitle.setText("All scenarios in this file have been selected.");
            checkSub.setText("<html><center>You can uncheck the ones that are not related to the task.</center></html>");
        } else {
            checkTitle.setText(selectedCount + " scenario" + (selectedCount == 1 ? "" : "s") + " selected in this file.");
            checkSub.setText("<html><center>You can uncheck or select more scenarios in the table.</center></html>");
        }
    }

    // ── Private builders ────────────────────────────────────────────────────

    /**
     * Builds the three-row summary table using a real GridBagLayout so the
     * value column is always flush-right regardless of label width.
     */
    private JPanel buildSummaryTable() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        addSummaryRow(grid, "\uD83D\uDCC4", "File",                    summaryFileName,  0);
        addSummaryRow(grid, "\u25CB",        "Total scenarios",          summaryTotal,     1);
        addSummaryRow(grid, "\u2705",        "Selected scenarios",       summarySelected,  2);

        // Spacer row to push content up if the panel is tall
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy   = 3;
        filler.gridx   = 0;
        filler.gridwidth = 3;
        filler.weighty = 1.0;
        filler.fill    = GridBagConstraints.VERTICAL;
        grid.add(Box.createVerticalGlue(), filler);

        return grid;
    }

    private void addSummaryRow(JPanel grid, String icon, String label,
                               JLabel valueLabel, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy   = row;
        gbc.insets  = new Insets(3, 0, 3, 6);
        gbc.anchor  = GridBagConstraints.WEST;
        gbc.fill    = GridBagConstraints.NONE;
        gbc.weightx = 0;

        // Icon
        gbc.gridx = 0;
        JLabel iconLbl = new JLabel(icon);
        iconLbl.setPreferredSize(new Dimension(22, 22));
        iconLbl.setHorizontalAlignment(SwingConstants.CENTER);
        grid.add(iconLbl, gbc);

        // Key — stretches to fill
        gbc.gridx   = 1;
        gbc.weightx = 1.0;
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.insets  = new Insets(3, 0, 3, 8);
        JLabel keyLbl = new JLabel(label);
        keyLbl.setForeground(JBColor.GRAY);
        keyLbl.setFont(keyLbl.getFont().deriveFont(11f));
        grid.add(keyLbl, gbc);

        // Value — pinned right, no stretch
        gbc.gridx   = 2;
        gbc.weightx = 0;
        gbc.fill    = GridBagConstraints.NONE;
        gbc.anchor  = GridBagConstraints.EAST;
        gbc.insets  = new Insets(3, 0, 3, 0);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 11f));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        grid.add(valueLabel, gbc);
    }

    private JPanel buildImportantBox() {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(8, 12, 8, 12),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new JBColor(new Color(60, 120, 60), new Color(45, 90, 45)), 1, true),
                        BorderFactory.createEmptyBorder(10, 12, 10, 12))));
        box.setBackground(new JBColor(new Color(235, 247, 235), new Color(30, 50, 30)));
        box.setOpaque(true);

        JLabel title = new JLabel("\u2605 Important");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        title.setForeground(new JBColor(new Color(40, 120, 30), new Color(140, 200, 100)));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel text = new JLabel(
                "<html>The selected tests will be saved to a .csv file and attached to the Trello task.</html>");
        text.setFont(text.getFont().deriveFont(11f));
        text.setForeground(new JBColor(new Color(35, 95, 30), new Color(180, 220, 160)));
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        box.add(title);
        box.add(text);
        return box;
    }

    private JPanel makeStepBadge(String number) {
        JPanel badge = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(80, 120, 200));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setOpaque(false);
        badge.setPreferredSize(new Dimension(24, 24));
        badge.setLayout(new GridBagLayout());
        JLabel lbl = new JLabel(number);
        lbl.setForeground(Color.WHITE);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 11f));
        badge.add(lbl);

        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        wrapper.setOpaque(false);
        wrapper.add(badge);
        wrapper.add(Box.createHorizontalStrut(8));
        return wrapper;
    }

    private Icon makeCheckIcon() {
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(50, 180, 90));
                g2.setStroke(new BasicStroke(3f));
                g2.drawOval(x + 2, y + 2, 44, 44);
                g2.drawLine(x + 13, y + 24, x + 21, y + 33);
                g2.drawLine(x + 21, y + 33, x + 35, y + 17);
                g2.dispose();
            }
            @Override public int getIconWidth()  { return 48; }
            @Override public int getIconHeight() { return 48; }
        };
    }
}
