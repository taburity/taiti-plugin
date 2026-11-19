package br.edu.ufape.taiti.gui;

import br.edu.ufape.taiti.gui.configuretask.fileview.*;
import br.edu.ufape.taiti.gui.configuretask.tree.TaitiTree;
import br.edu.ufape.taiti.gui.configuretask.tree.TaitiTreeFileNode;
import br.edu.ufape.taiti.gui.configuretask.table.TestRow;
import br.edu.ufape.taiti.gui.configuretask.table.TestsTableModel;
import br.edu.ufape.taiti.gui.configuretask.table.TestsTableRenderer;
import br.edu.ufape.taiti.tool.ScenarioTestInformation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.table.JBTable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

public class MainPanel {

    // All panels created programmatically — NOT from .form binding
    private final JPanel rootPanel;
    private final JPanel centerPanel;
    private final JPanel southPanel;
    private final JPanel treePanel;
    private final JSplitPane mainSplit;
    private final JSplitPane rightSplit;

    private TaitiTree tree;
    private JBTable table;
    private TestsTableModel tableModel;
    private FeatureFileView featureFileView;
    private FeatureFileViewModel featureFileViewModel;

    private final ArrayList<ScenarioTestInformation> scenarios;
    private final RepositoryOpenFeatureFile repositoryOpenFeatureFile;
    private final Project project;

    // Callback to TaitiDialog for enabling/disabling the OK button
    private Runnable onSelectionChanged;

    // Right panel — dedicated component
    private TestSummaryPanel testSummaryPanel;

    // Center panel header labels
    private JLabel centerFileLabel;
    private JLabel centerScenarioCountLabel;

    // Currently displayed .feature file
    private File currentCenterFile;

    public MainPanel(Project project) {
        this.project = project;
        scenarios = new ArrayList<>();
        repositoryOpenFeatureFile = new RepositoryOpenFeatureFile();

        // Build panel hierarchy programmatically so fields are never null
        treePanel   = new JPanel(new BorderLayout());
        centerPanel = new JPanel(new BorderLayout());
        southPanel  = new JPanel(new BorderLayout());

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, null);
        mainSplit.setDividerLocation(310);
        mainSplit.setDividerSize(4);

        rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerPanel, southPanel);
        rightSplit.setDividerLocation(520);
        rightSplit.setDividerSize(4);

        mainSplit.setRightComponent(rightSplit);

        rootPanel = new JPanel(new BorderLayout());
        rootPanel.add(mainSplit, BorderLayout.CENTER);

        centerPanel.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                if (featureFileView != null) featureFileView.setTableWidth(e.getComponent().getWidth());
            }
        });

        configureTree();
        initTable();
        initCenterPanel();
        initSouthPanel();
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    public JPanel getRootPanel()                          { return rootPanel; }
    public ArrayList<ScenarioTestInformation> getScenarios() { return scenarios; }
    public JBTable getTable()                             { return table; }
    public TestsTableModel getTableModel()                { return tableModel; }

    public void setOnSelectionChanged(Runnable callback) {
        this.onSelectionChanged = callback;
    }

    public int getSelectedScenarioCount() {
        return Math.max(0, tableModel.getRowCount() - 1);
    }

    public void loadExistingScenarios(List<ScenarioTestInformation> existingScenarios) {
        Map<File, List<Integer>> scenariosByFile = new HashMap<>();
        for (ScenarioTestInformation scenario : existingScenarios) {
            File absoluteFile = findFeatureFile(project, scenario.getFilePath());
            if (absoluteFile != null) {
                scenariosByFile.computeIfAbsent(absoluteFile, k -> new ArrayList<>())
                        .add(scenario.getLineNumber());
            }
        }
        for (Map.Entry<File, List<Integer>> entry : scenariosByFile.entrySet()) {
            File file = entry.getKey();
            for (int lineNumber : entry.getValue()) {
                scenarios.add(new ScenarioTestInformation(file.getAbsolutePath(), lineNumber));
                tableModel.addRow(new TestRow(file, false, getScenarioName(file, lineNumber), lineNumber));
            }
        }
        tableModel.fireTableDataChanged();
        refreshSummary();
    }

    public void updateCenterPanel(File file) {
        currentCenterFile = file;
        String filePath = file.getAbsolutePath();
        String fileName = file.getName();
        OpenFeatureFile openFeatureFile;

        if (repositoryOpenFeatureFile.exists(file)) {
            openFeatureFile = repositoryOpenFeatureFile.getFeatureFile(file);
        } else {
            ArrayList<FileLine> fileLines = new ArrayList<>();
            try (Scanner scanner = new Scanner(new FileReader(filePath))) {
                int countLine = 1;
                fileLines.add(new FileLine(false, fileName, -1));
                fileLines.add(new FileLine(false, "", -1));
                while (scanner.hasNextLine()) {
                    fileLines.add(new FileLine(false, scanner.nextLine(), countLine++));
                }
            } catch (FileNotFoundException e) {
                fileLines.add(new FileLine(false, "Error loading file.", -1));
            }
            openFeatureFile = new OpenFeatureFile(file, fileLines);
            if (openFeatureFile.getFileLines().size() >= 2) {
                repositoryOpenFeatureFile.addFeatureFile(openFeatureFile);
            }
        }

        featureFileViewModel = new FeatureFileViewModel(
                file, openFeatureFile.getFileLines(), scenarios, tableModel);
        featureFileView.setModel(featureFileViewModel);
        featureFileView.setTableWidth(centerPanel.getWidth());
        featureFileView.setRowHeight(0, 30);

        featureFileView.getColumnModel().getColumn(0).setCellRenderer(new CheckBoxCellRenderer(file));
        featureFileView.getColumnModel().getColumn(0).setCellEditor(new CheckBoxEditor(new JCheckBox(), file));
        featureFileView.getColumnModel().getColumn(1).setCellRenderer(new FileLineRenderer(file));
        featureFileView.getTableHeader().setUI(null);

        if (centerFileLabel != null) centerFileLabel.setText(fileName);
        updateScenarioCount(openFeatureFile);
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private File findFeatureFile(Project project, String relativePath) {
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir == null) return null;
        try {
            String normalizedPath = Paths.get(relativePath).normalize().toString();
            VirtualFile virtualFile = projectDir.findFileByRelativePath(normalizedPath);
            if (virtualFile == null || !virtualFile.exists())
                virtualFile = findFileCaseInsensitive(projectDir, normalizedPath);
            if (virtualFile != null && virtualFile.exists()) return new File(virtualFile.getPath());
        } catch (InvalidPathException ignored) {}
        return null;
    }

    private VirtualFile findFileCaseInsensitive(VirtualFile baseDir, String relativePath) {
        String[] parts = relativePath.split("[\\\\/]");
        VirtualFile current = baseDir;
        for (String part : parts) {
            if (current == null) break;
            VirtualFile child = current.findChild(part);
            if (child == null) {
                for (VirtualFile f : current.getChildren()) {
                    if (f.getName().equalsIgnoreCase(part)) { child = f; break; }
                }
            }
            current = child;
        }
        return current;
    }

    private String getScenarioName(File file, int lineNumber) {
        try (Scanner scanner = new Scanner(new FileReader(file))) {
            int currentLine = 1;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (currentLine == lineNumber) return line.trim();
                currentLine++;
            }
        } catch (IOException e) { e.printStackTrace(); }
        return "Scenario at line: " + lineNumber;
    }

    private void updateScenarioCount(OpenFeatureFile openFeatureFile) {
        if (centerScenarioCountLabel == null) return;
        long count = openFeatureFile.getFileLines().stream()
                .filter(l -> l.getLine() != null
                        && (l.getLine().trim().startsWith("Scenario:")
                        || l.getLine().trim().startsWith("Scenario Outline:")))
                .count();
        centerScenarioCountLabel.setText(
                count + " scenario" + (count != 1 ? "s" : "") + " found");
    }

    private int getCurrentFileTotalScenarios() {
        if (currentCenterFile == null) return 0;
        OpenFeatureFile openFeatureFile = repositoryOpenFeatureFile.getFeatureFile(currentCenterFile);
        if (openFeatureFile == null) return 0;
        return (int) openFeatureFile.getFileLines().stream()
                .filter(l -> l.getLine() != null
                        && (l.getLine().trim().startsWith("Scenario:")
                        || l.getLine().trim().startsWith("Scenario Outline:")))
                .count();
    }

    private int getCurrentFileSelectedScenarios() {
        if (currentCenterFile == null) return 0;
        int count = 0;
        for (int r = 1; r < tableModel.getRowCount(); r++) {
            TestRow row = tableModel.getRow(r);
            if (currentCenterFile.equals(row.getFile())) {
                count++;
            }
        }
        return count;
    }

    private void refreshSummary() {
        int fileSelected = getCurrentFileSelectedScenarios();
        int fileTotal    = getCurrentFileTotalScenarios();
        String fname = currentCenterFile != null ? currentCenterFile.getName() : null;
        if (testSummaryPanel != null) testSummaryPanel.update(fileSelected, fileTotal, fname);
        if (onSelectionChanged != null) onSelectionChanged.run();
    }

    private void clearCurrentFileSelection() {
        if (currentCenterFile == null) return;
        ArrayList<TestRow> toRemove = new ArrayList<>();
        for (int r = 1; r < tableModel.getRowCount(); r++) {
            TestRow row = tableModel.getRow(r);
            if (currentCenterFile.equals(row.getFile())) toRemove.add(row);
        }
        for (TestRow row : toRemove) {
            tableModel.removeRow(row);
            OpenFeatureFile openFeatureFile = repositoryOpenFeatureFile.getFeatureFile(row.getFile());
            if (openFeatureFile != null) {
                int deselectedLine = openFeatureFile.deselectLine(row.getTest());
                scenarios.remove(new ScenarioTestInformation(
                        row.getFile().getAbsolutePath(), deselectedLine));
            }
        }
        if (featureFileViewModel != null) featureFileViewModel.fireTableDataChanged();
        refreshSummary();
    }

    // ---------------------------------------------------------------
    // Panel builders
    // ---------------------------------------------------------------

    // === LEFT PANEL: feature tree ===
    private void configureTree() {
        String projectPath = "";
        String projectName = "";
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir != null) {
            projectPath = projectDir.getPath();
            projectName = projectDir.getName();
        }

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(projectName);
        tree = new TaitiTree(new DefaultTreeModel(rootNode));
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    DefaultMutableTreeNode node =
                            (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                    if (node != null && node.isLeaf() && !node.getAllowsChildren()) {
                        TaitiTreeFileNode fileNode = (TaitiTreeFileNode) node.getUserObject();
                        updateCenterPanel(fileNode.getFile());
                    }
                }
            }
        });

        // Header
        JPanel stepBadge = makeStepBadge("1");
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);
        JLabel title    = new JLabel("Select .feature files");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        JLabel subtitle = new JLabel("Browse and select files containing the test scenarios.");
        subtitle.setFont(subtitle.getFont().deriveFont(11f));
        subtitle.setForeground(JBColor.GRAY);
        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(2));
        titleBlock.add(subtitle);

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        header.setOpaque(false);
        header.add(stepBadge, BorderLayout.WEST);
        header.add(titleBlock, BorderLayout.CENTER);

        JPanel leftTop = new JPanel();
        leftTop.setLayout(new BoxLayout(leftTop, BoxLayout.Y_AXIS));
        leftTop.add(header);
        treePanel.add(leftTop, BorderLayout.NORTH);

        // Tree
        File featureDirectory = tree.findFeatureDirectory(projectPath);
        if (featureDirectory != null) {
            File parent = featureDirectory.getParentFile();
            ArrayList<DefaultMutableTreeNode> parentsNodes = new ArrayList<>();
            while (parent != null && !parent.getName().equals(projectName)) {
                parentsNodes.add(new DefaultMutableTreeNode(parent.getName()));
                parent = parent.getParentFile();
            }
            tree.addParentsNodeToTree(parentsNodes, rootNode, parentsNodes.size() - 1);
            DefaultMutableTreeNode featureNode =
                    new DefaultMutableTreeNode(featureDirectory.getName());
            if (!parentsNodes.isEmpty()) parentsNodes.get(0).add(featureNode);
            else rootNode.add(featureNode);
            tree.addNodesToTree(featureDirectory, featureNode);
            treePanel.add(new JScrollPane(tree), BorderLayout.CENTER);
        } else {
            JLabel label = new JLabel(".feature directory not found");
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setForeground(JBColor.GRAY);
            treePanel.add(label, BorderLayout.CENTER);
        }

        // Hint
        JLabel hint = new JLabel(
                "<html><i>\u2022 Tip: Select a file to view its scenarios.</i></html>");
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(JBColor.GRAY);
        hint.setBorder(BorderFactory.createEmptyBorder(6, 12, 8, 12));
        treePanel.add(hint, BorderLayout.SOUTH);
    }

    // === CENTER PANEL: file content ===
    private void initCenterPanel() {
        featureFileView = new FeatureFileView();
        featureFileView.setShowGrid(false);
        featureFileView.getTableHeader().setResizingAllowed(false);
        featureFileView.getTableHeader().setReorderingAllowed(false);
        featureFileView.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        featureFileView.setDragEnabled(false);
        featureFileView.setRowSelectionAllowed(false);

        JPanel stepBadge = makeStepBadge("2");
        JLabel titleLabel = new JLabel("File contents");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        header.setOpaque(false);
        header.add(stepBadge, BorderLayout.WEST);
        header.add(titleLabel, BorderLayout.CENTER);

        centerFileLabel = new JLabel("Select a .feature file from the list.");
        centerFileLabel.setFont(centerFileLabel.getFont().deriveFont(Font.BOLD, 12f));

        centerScenarioCountLabel = new JLabel("");
        centerScenarioCountLabel.setFont(centerScenarioCountLabel.getFont().deriveFont(11f));
        centerScenarioCountLabel.setForeground(JBColor.GRAY);
        centerScenarioCountLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        JPanel fileNameRow = new JPanel(new BorderLayout(8, 0));
        fileNameRow.setBorder(BorderFactory.createEmptyBorder(0, 12, 6, 12));
        fileNameRow.setOpaque(false);
        fileNameRow.add(centerFileLabel,         BorderLayout.WEST);
        fileNameRow.add(centerScenarioCountLabel, BorderLayout.EAST);

        JPanel topBlock = new JPanel();
        topBlock.setLayout(new BoxLayout(topBlock, BoxLayout.Y_AXIS));
        topBlock.add(header);
        topBlock.add(fileNameRow);

        centerPanel.add(topBlock, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(featureFileView), BorderLayout.CENTER);
    }

    // === TABLE model (used by TaitiDialog) ===
    private void initTable() {
        tableModel = new TestsTableModel();
        table = new JBTable() {
            @Override public Component prepareRenderer(
                    javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (getModel().getRowCount() <= 1 && row == 0) {
                    if (column == 1) {
                        JLabel lbl = new JLabel("No test selected.");
                        lbl.setHorizontalAlignment(SwingConstants.CENTER);
                        lbl.setForeground(JBColor.GRAY);
                        return lbl;
                    } else if (column == 0) return new JLabel();
                }
                return c;
            }
        };
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(false);
        table.setModel(tableModel);
        tableModel.addRow(new TestRow(null, false, "Tests", 0));
        table.setRowHeight(40);
        table.getColumnModel().getColumn(0).setPreferredWidth(30);
        table.getColumnModel().getColumn(1).setPreferredWidth(270);
        table.getColumnModel().getColumn(0).setCellRenderer(new TestsTableRenderer());
        table.getColumnModel().getColumn(1).setCellRenderer(new TestsTableRenderer());
        table.getTableHeader().setUI(null);
        tableModel.addTableModelListener(e -> refreshSummary());
    }

    // === RIGHT PANEL: delegates entirely to TestSummaryPanel ===
    private void initSouthPanel() {
        testSummaryPanel = new TestSummaryPanel();
        testSummaryPanel.setOnClearClicked(this::clearCurrentFileSelection);
        southPanel.add(testSummaryPanel, BorderLayout.CENTER);
    }

    // ---------------------------------------------------------------
    // Shared layout helper (used by configureTree + initCenterPanel)
    // ---------------------------------------------------------------

    private JPanel makeStepBadge(String number) {
        JPanel badge = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
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
}
