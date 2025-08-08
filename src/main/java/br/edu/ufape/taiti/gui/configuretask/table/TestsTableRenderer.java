package br.edu.ufape.taiti.gui.configuretask.table;

import com.intellij.ui.JBColor;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class TestsTableRenderer extends DefaultTableCellRenderer {

    private final JCheckBox checkBox;

    public TestsTableRenderer() {
        this.checkBox = new JCheckBox();
        this.checkBox.setHorizontalAlignment(SwingConstants.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (column == 0) {
            Color bg;
            if (row == 0) {
                bg = table.getTableHeader().getBackground();
            } else {
                bg = isSelected ? table.getSelectionBackground() : table.getBackground();
            }
            checkBox.setBackground(bg);
            checkBox.setEnabled(true);
            checkBox.setVisible(true);
            checkBox.setSelected(value != null && (Boolean)value);

        } else {
            if (row == 0) {
                table.setRowHeight(0, 30);
                c.setBackground(table.getTableHeader().getBackground());
                c.setToolTipText("");
            } else {
                Color bg = isSelected ? table.getSelectionBackground() : table.getBackground();
                c.setBackground(bg);

                TestsTableModel model = (TestsTableModel) table.getModel();
                TestRow testRow = model.getRow(row);

                String fileName = testRow.getFile().getName();
                int line = testRow.getLineNumber();
                String text = "<html><nobr>" + testRow.getTest() +
                        "<br><font color='%s' size='3'>" +
                        fileName + ":" + line + "</font></nobr></html>";

                Color baseColor = c.getForeground();
                Color newColor = new Color(
                        baseColor.getRed(),
                        baseColor.getGreen(),
                        baseColor.getBlue(),
                        51
                );
                Color transparentColor = new JBColor(newColor, newColor);
                String hexColor = String.format("#%02x%02x%02x",
                        transparentColor.getRed(),
                        transparentColor.getGreen(),
                        transparentColor.getBlue()
                );

                c.setText(String.format(text, hexColor));

                String tooltip = "<html><b>Scenario:</b> " + testRow.getTest() +
                        "<br><b>File:</b> " + testRow.getFile().getAbsolutePath() +
                        "<br><b>Line:</b> " + line + "</html>";
                c.setToolTipText(tooltip);
            }
        }

        if (column == 0) {
            return this.checkBox;
        } else {
            return c;
        }

    }

}