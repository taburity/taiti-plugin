package br.edu.ufape.taiti.gui.conflicts;

import br.edu.ufape.taiti.gui.taskbar.LoadingScreen;
import br.edu.ufape.taiti.service.Task;
import br.ufpe.cin.tan.conflict.ConflictAnalyzer;
import br.ufpe.cin.tan.conflict.PlannedTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;

public class ConflictsGUI {
    private static DefaultTableModel modeloTabela;
    private static JPanel content;
    private static JPanel ConflictsPanel;
    private static JPanel labelPanel;
    private final JBTable ShowTable;

    private static Project project;

    public ConflictsGUI(ToolWindow toolWindow, Project project) {
        this.project = project;
        content = new JPanel();
        ConflictsPanel = new JPanel();
        ShowTable = new JBTable();
        labelPanel = new JPanel();
        createTable();

        setLabel("Double-click a task in the TaskList to view its conflicting tasks");

        ShowTable.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = ShowTable.rowAtPoint(e.getPoint());
                int column = ShowTable.columnAtPoint(e.getPoint());

                // Verificar se row e column são valores válidos antes de acessar a célula
                if (row >= 0 && column >= 0) {
                    // Acessar a célula da tabela
                    String paths = (String) modeloTabela.getValueAt(row, 4);
                    String truncatePaths = paths.substring(0, Math.min(paths.length(), 100));
                    ShowTable.setToolTipText("<html>" + modeloTabela.getValueAt(row, 1) +
                            "<br>URL: #" + modeloTabela.getValueAt(row, 2) +
                            "<br>Conflict Rate: " + modeloTabela.getValueAt(row, 3) +
                            "<br>Scenarios: " + truncatePaths + "...</html>");
                } else {
                    ShowTable.setToolTipText(null);
                }
            }
        });

        ShowTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = ShowTable.rowAtPoint(e.getPoint());
                int column = ShowTable.columnAtPoint(e.getPoint());
                if (column == 4 && row >= 0) { // Verifica se a coluna clicada é a quarta e se há uma linha selecionada
                    String cellValue = (String) ShowTable.getValueAt(row, column);
                    showCellContentDialog(cellValue);
                }
            }
        });
    }

    private void createTable() {
        modeloTabela = new DefaultTableModel(null,
                new String[]{"Task ID", "Description", "URL", "Conflict Rate with Selected Task", "Conflict Files"}) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Tornar todas as células não editáveis
            }
        };
        ShowTable.setModel(modeloTabela);

        TableColumnModel columns = ShowTable.getColumnModel();
        columns.getColumn(0).setMinWidth(60);
        columns.getColumn(1).setMinWidth(200);
        columns.getColumn(2).setMinWidth(230);
        columns.getColumn(3).setMinWidth(140);
        columns.getColumn(4).setMinWidth(400);

        ShowTable.setRowHeight(30);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        columns.getColumn(0).setCellRenderer(centerRenderer);
        columns.getColumn(3).setCellRenderer(centerRenderer);

        ConflictsPanel.setLayout(new BorderLayout());

        JScrollPane scrollPane = new JBScrollPane(ShowTable);
        if (ConflictsPanel != null) {
            ConflictsPanel.add(labelPanel, BorderLayout.NORTH);
            ConflictsPanel.add(scrollPane, BorderLayout.CENTER);
        }
        content.setLayout(new BorderLayout());

        content.add(ConflictsPanel, BorderLayout.CENTER);
    }

    static public void fillTable(Task task, ConflictAnalyzer conflictAnalyzer, ArrayList<Task> storysList) {
        modeloTabela.setRowCount(0);
        if (storysList.isEmpty()) {
            return;
        }

        LoadingScreen loadingScreen = new LoadingScreen();
        changePanel(loadingScreen);

        ArrayList<Task> tasksWithItesk = new ArrayList<>();
        for (Task currentTask : storysList) {
            if (task.getiTesk() != null && currentTask.getiTesk() != null) {
                tasksWithItesk.add(currentTask);
            }
        }

        if (tasksWithItesk.isEmpty()) {
            changePanel(loadingScreen);
            return;
        }

        // Calcular as taxas de conflito para cada tarefa
        for (Task currentTask : tasksWithItesk) {
            conflictAnalyzer.computeConflictRiskForPair(task.getiTesk(), currentTask.getiTesk());

            double conflictRate = conflictAnalyzer.getConflictResult().getRelativeConflictRate();
            double formattedConflictRate = Math.round(conflictRate * 100.0);
            currentTask.setConflictRate(formattedConflictRate);
        }

        // Ordena a lista com base na taxa de conflito (decrescente)
        tasksWithItesk.sort((t1, t2) -> Double.compare(t2.getConflictRate(), t1.getConflictRate()));

        // Preenche a tabela com a lista ordenada
        for (Task currentTask : tasksWithItesk) {
            if (currentTask.getConflictRate() == 0.0) {
                continue; // Ignora tarefas com taxa de conflito zero
            }
            conflictAnalyzer.computeConflictRiskForPair(task.getiTesk(), currentTask.getiTesk());

            Collection<String> paths = conflictAnalyzer.getConflictResult().getConflictingFiles();
            Collection<String> conflictsPath = new ArrayList<>();

            for (String str : paths) {
                //remover o caminho do projeto
                String modifiedStr = str.replaceFirst(".*" + project.getName() + "_" + project.getName() + "[\\\\/]", "");
                conflictsPath.add(modifiedStr);
            }

            String stringConflicts = String.join(" \n", conflictsPath);

            int taskId = Integer.parseInt(currentTask.getId());
            String taskDescription = currentTask.getName();
            String taskUrl = currentTask.getUrl();

            modeloTabela.addRow(new Object[]{taskId, taskDescription, taskUrl, currentTask.getConflictRate() + "%", stringConflicts});
        }
        // Remove a tela de carregamento e exibe a tabela
        changePanel(loadingScreen);
    }

    private void showCellContentDialog(String content) {
        JTextArea textArea = new JTextArea(content);
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(500, 500));

        JOptionPane.showMessageDialog(null, scrollPane, "Conflict files paths", JOptionPane.PLAIN_MESSAGE);
    }

    private static void changePanel(JPanel panel) {
        if (content.getComponent(0) != ConflictsPanel) {
            content.remove(panel);
            content.add(ConflictsPanel, BorderLayout.CENTER);
        } else {
            content.remove(ConflictsPanel);
            content.add(panel, BorderLayout.CENTER);
        }

        // Revalida e redesenha o conteúdo
        content.revalidate();
        content.repaint();
    }

    public static void setLabel(String texto) {
        if (labelPanel != null) {
            labelPanel.removeAll();
            labelPanel.validate();
            labelPanel.add(new JLabel(texto));
        }
    }

    public JPanel getContent() {
        return content;
    }
}