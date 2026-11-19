package br.edu.ufape.taiti.gui.taskbar;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class PriorityRenderer extends JPanel implements TableCellRenderer {

    private String priority;

    public PriorityRenderer() {
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column) {

        priority = value != null ? value.toString() : "";

        if (isSelected) {
            setBackground(table.getSelectionBackground());
        } else {
            setBackground(table.getBackground());
        }

        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Color color;

        switch (priority) {
            case "HIGH":
                color = new Color(220, 50, 50);
                break;

            case "MEDIUM":
                color = new Color(255, 200, 50);
                break;

            case "LOW":
                color = new Color(50, 180, 80);
                break;

            default:
                color = Color.GRAY;
        }

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        int diameter = 12;

        int x = (getWidth() - diameter) / 2;
        int y = (getHeight() - diameter) / 2;

        g2.setColor(color);
        g2.fillOval(x, y, diameter, diameter);

        g2.dispose();
    }
}