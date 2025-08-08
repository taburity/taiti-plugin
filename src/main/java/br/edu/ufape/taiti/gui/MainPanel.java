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

import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

public class MainPanel {
    private JPanel rootPanel;
    private JPanel centerPanel;
    private JPanel southPanel;
    private JPanel treePanel;

    private JSplitPane mainSplit;
    private JSplitPane rightSplit;

    private TaitiTree tree;

    private JBTable table;
    private TestsTableModel tableModel;
    private FeatureFileView featureFileView;
    private FeatureFileViewModel featureFileViewModel;
    private JPanel selectedTestsPanel;

    private final ArrayList<ScenarioTestInformation> scenarios;
    private final RepositoryOpenFeatureFile repositoryOpenFeatureFile;

    private final Project project;

    public MainPanel(Project project) {
        this.project = project;
        scenarios = new ArrayList<>();
        repositoryOpenFeatureFile = new RepositoryOpenFeatureFile();

        configurePanels();
        configureTree();
        initTable();
        initCenterPanel();
    }

    public JPanel getRootPanel() {
        return rootPanel;
    }

    public ArrayList<ScenarioTestInformation> getScenarios() {
        return scenarios;
    }

    public JBTable getTable() {
        return table;
    }

    private File findFeatureFile(Project project, String relativePath) {
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir == null) {
            System.out.println("Project directory not found");
            return null;
        }

        try {
            // Converter caminho relativo para o formato do sistema
            String normalizedPath = Paths.get(relativePath).normalize().toString();

            // Usar a API do IntelliJ para encontrar o arquivo
            VirtualFile virtualFile = projectDir.findFileByRelativePath(normalizedPath);

            if (virtualFile == null || !virtualFile.exists()) {
                // Tentar encontrar com match case insensitivo
                virtualFile = findFileCaseInsensitive(projectDir, normalizedPath);
            }

            if (virtualFile != null && virtualFile.exists()) {
                return new File(virtualFile.getPath());
            }

            System.out.println("File not found: " + normalizedPath);
            return null;

        } catch (InvalidPathException e) {
            System.out.println("Invalid path format: " + relativePath);
            return null;
        }
    }

    private VirtualFile findFileCaseInsensitive(VirtualFile baseDir, String relativePath) {
        String[] parts = relativePath.split("[\\\\/]");
        VirtualFile current = baseDir;

        for (String part : parts) {
            if (current == null) break;

            VirtualFile child = current.findChild(part); // Tenta match exato primeiro
            if (child == null) {
                // Busca case insensitive
                for (VirtualFile f : current.getChildren()) {
                    if (f.getName().equalsIgnoreCase(part)) {
                        child = f;
                        break;
                    }
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

                if (currentLine == lineNumber) {
                    if (line.trim().toLowerCase().startsWith("scenario")) {
                        return line.trim();
                    } else {
                        // se a linha não for a do "Scenario", retorna a string bruta
                        return line.trim();
                    }
                }
                currentLine++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Scenario at line: " + lineNumber;
    }

    public void loadExistingScenarios(List<ScenarioTestInformation> existingScenarios) {
        Map<File, List<Integer>> scenariosByFile = new HashMap<>();

        for (ScenarioTestInformation scenario : existingScenarios) {
            File absoluteFile = findFeatureFile(this.project, scenario.getFilePath());
            int lineNumber = scenario.getLineNumber();
            if (absoluteFile != null) {
                scenariosByFile
                        .computeIfAbsent(absoluteFile, k -> new ArrayList<>())
                        .add(lineNumber);
            }
        }

        // Adiciona cenários na tabela e na lista de cenários
        for (Map.Entry<File, List<Integer>> entry : scenariosByFile.entrySet()) {
            File file = entry.getKey();
            List<Integer> lines = entry.getValue();

            for (int lineNumber : lines) {
                scenarios.add(new ScenarioTestInformation(file.getAbsolutePath(), lineNumber));
                String scenarioName = getScenarioName(file, lineNumber);
                tableModel.addRow(new TestRow(file, false, scenarioName, lineNumber));
            }

            tableModel.fireTableDataChanged();
        }
    }

    public void updateCenterPanel(File file) {
        String filePath = file.getAbsolutePath();
        String fileName = file.getName();
        OpenFeatureFile openFeatureFile;

        if (repositoryOpenFeatureFile.exists(file)) {
            openFeatureFile = repositoryOpenFeatureFile.getFeatureFile(file);
        } else {
            ArrayList<FileLine> fileLines = new ArrayList<>();
            Scanner scanner;
            try {
                scanner = new Scanner(new FileReader(filePath));
                int countLine = 1;
                fileLines.add(new FileLine(false, fileName, -1));
                fileLines.add(new FileLine(false, "", -1));
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    fileLines.add(new FileLine(false, line, countLine));
                    countLine++;
                }
            } catch (FileNotFoundException e) {
                fileLines.add(new FileLine(false, "Error loading file.", -1));
            }

            openFeatureFile = new OpenFeatureFile(file, fileLines);
            if (openFeatureFile.getFileLines().size() >= 2) {
                repositoryOpenFeatureFile.addFeatureFile(openFeatureFile);
            }
        }

        featureFileViewModel = new FeatureFileViewModel(file, openFeatureFile.getFileLines(), scenarios, tableModel);
        featureFileView.setModel(featureFileViewModel);
        featureFileView.setTableWidth(centerPanel.getWidth());
        featureFileView.setRowHeight(0, 30);

        featureFileView.getColumnModel().getColumn(0).setCellRenderer(new CheckBoxCellRenderer(file));
        featureFileView.getColumnModel().getColumn(0).setCellEditor(new CheckBoxEditor(new JCheckBox(), file));
        featureFileView.getColumnModel().getColumn(1).setCellRenderer(new FileLineRenderer(file));
        featureFileView.getTableHeader().setUI(null);
    }

    private void initCenterPanel() {
        featureFileView = new FeatureFileView();
        featureFileView.setShowGrid(false);
        featureFileView.getTableHeader().setResizingAllowed(false);
        featureFileView.getTableHeader().setReorderingAllowed(false);
        featureFileView.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        featureFileView.setDragEnabled(false);
        featureFileView.setRowSelectionAllowed(false);

        // Adicionar painel de título para o centerPanel
        JPanel centerTitlePanel = new JPanel(new BorderLayout());
        JLabel centerTitleLabel = new JLabel("File Content");
        centerTitleLabel.setFont(centerTitleLabel.getFont().deriveFont(Font.BOLD));
        centerTitleLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        centerTitlePanel.add(centerTitleLabel, BorderLayout.WEST);
        centerPanel.add(centerTitlePanel, BorderLayout.NORTH);

        centerPanel.add(new JScrollPane(featureFileView), BorderLayout.CENTER);
    }

    private void initTable() {
        table = new JBTable() {
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                
                // Se a tabela estiver vazia (apenas com o cabeçalho), mostra a mensagem centralizada
                if (getModel().getRowCount() <= 1 && row == 0) {
                    if(column == 1) {
                        JLabel label = new JLabel("No tests selected. Select tests from the file tree.");
                        label.setHorizontalAlignment(SwingConstants.CENTER);
                        label.setForeground(JBColor.gray);
                        label.setFont(label.getFont().deriveFont(12f));
                        return label;
                    } else if(column == 0){
                        return new JLabel();
                    }
                }
                
                return c;
            }
        };
        
        table.setShowGrid(false);
        table.getTableHeader().setResizingAllowed(false);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(false);

        // Criar painel principal para a seção de testes
        JPanel testManagementPanel = new JPanel(new BorderLayout(0, 10));
        testManagementPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Criar painel de título para Test Management
        JPanel titlePanel = new JPanel(new BorderLayout());
        JLabel titleLabel = new JLabel("Test Management");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        titlePanel.add(titleLabel, BorderLayout.WEST);
        
        // Criar painel para o botão com borda e espaçamento
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.X_AXIS));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        
        JButton removeScenarioBtn = new JButton("Remove Selected Tests");
        controlPanel.add(Box.createHorizontalGlue());
        controlPanel.add(removeScenarioBtn);
        
        // Adicionar a tabela em um painel com scroll
        JScrollPane tableScrollPane = new JScrollPane(table);
        tableScrollPane.setBorder(BorderFactory.createLineBorder(JBColor.border()));
        
        // Organizar os componentes no painel principal
        testManagementPanel.add(titlePanel, BorderLayout.NORTH);
        testManagementPanel.add(tableScrollPane, BorderLayout.CENTER);
        testManagementPanel.add(controlPanel, BorderLayout.SOUTH);
        
        // Adicionar o painel completo ao southPanel
        southPanel.add(testManagementPanel, BorderLayout.CENTER);

        // Implementar a ação do botão "Remove" com ActionListener
        removeScenarioBtn.addActionListener(e -> {
            ArrayList<TestRow> testRowsChecked = new ArrayList<>();

            // Identificar todas as linhas marcadas
            for (int r = 1; r < tableModel.getRowCount(); r++) {
                if ((boolean) tableModel.getValueAt(r, 0)) {
                    TestRow testRow = tableModel.getRow(r);
                    testRowsChecked.add(testRow);
                }
            }
            
            // Desmarcar checkbox do cabeçalho
            if (tableModel.getRowCount() > 0) {
                tableModel.getRow(0).setCheckbox(false);
            }
            
            // Remover todas as linhas marcadas
            for (TestRow t : testRowsChecked) {
                tableModel.removeRow(t);
                
                if (t.getFile() != null) {
                    OpenFeatureFile openFeatureFile = repositoryOpenFeatureFile.getFeatureFile(t.getFile());
                    if (openFeatureFile != null) {
                        int deselectedLine = openFeatureFile.deselectLine(t.getTest());
                        scenarios.remove(new ScenarioTestInformation(t.getFile().getAbsolutePath(), deselectedLine));
                    }
                }
            }
            
            // Atualizar a visualização após todas as remoções
            if (featureFileViewModel != null) {
                featureFileViewModel.fireTableDataChanged();
            }
        });

        tableModel = new TestsTableModel();
        table.setModel(tableModel);
        
        tableModel.addRow(new TestRow(null, false, "Tests", 0));
        table.setRowHeight(40);
        table.getColumnModel().getColumn(0).setPreferredWidth(30);
        table.getColumnModel().getColumn(1).setPreferredWidth(270);
        table.getColumnModel().getColumn(0).setCellRenderer(new TestsTableRenderer());
        table.getColumnModel().getColumn(1).setCellRenderer(new TestsTableRenderer());
        table.getTableHeader().setUI(null);
        
        // Adicionar listener de redimensionamento para ajustar largura da coluna
        southPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                table.getColumnModel().getColumn(1).setPreferredWidth(e.getComponent().getWidth() - 35);
            }
        });
    }

    private void configureTree() {
        String projectPath = "";
        String projectName = "";

        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        if (projectDir != null) {
            projectPath = projectDir.getPath();
            projectName = projectDir.getName();
        }

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(projectName);
        DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);

        tree = new TaitiTree(treeModel);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();

                    if (node == null) return;

                    Object nodeInfo = node.getUserObject();
                    if (node.isLeaf() && !node.getAllowsChildren()) {
                        TaitiTreeFileNode taitiTreeFileNode = (TaitiTreeFileNode) nodeInfo;
                        updateCenterPanel(taitiTreeFileNode.getFile());
                    }
                }
            }
        });

        File featureDirectory = tree.findFeatureDirectory(projectPath);
        if (featureDirectory != null) {
            // Adicionar painel de título para treePanel
            JPanel treeTitlePanel = new JPanel(new BorderLayout());
            JLabel treeTitleLabel = new JLabel("Features");
            treeTitleLabel.setFont(treeTitleLabel.getFont().deriveFont(Font.BOLD));
            treeTitleLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            treeTitlePanel.add(treeTitleLabel, BorderLayout.WEST);
            treePanel.add(treeTitlePanel, BorderLayout.NORTH);

            // get every parent directory of features directory
            File parent = featureDirectory.getParentFile();
            ArrayList<DefaultMutableTreeNode> parentsNodes = new ArrayList<>();
            while (!parent.getName().equals(projectName)) {
                parentsNodes.add(new DefaultMutableTreeNode(parent.getName()));
                parent = parent.getParentFile();
            }

            // add every parent directory of feature directory to the tree
            tree.addParentsNodeToTree(parentsNodes, rootNode, parentsNodes.size() - 1);

            // add the feature directory to the tree
            DefaultMutableTreeNode featureNode = new DefaultMutableTreeNode(featureDirectory.getName());
            if (parentsNodes.size() > 0) {
                parentsNodes.get(0).add(featureNode);
            } else {
                rootNode.add(featureNode);
            }

            // populating the tree with the files into feature directory
            tree.addNodesToTree(featureDirectory, featureNode);
            treePanel.add(new JScrollPane(tree), BorderLayout.CENTER);
        } else {
            JLabel label = new JLabel("Could not find feature directory");
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setForeground(JBColor.gray);
            treePanel.add(label, BorderLayout.CENTER);
        }
    }

    private void configurePanels() {
        // Reset borders
        rootPanel.setBorder(null);
        treePanel.setBorder(null);
        centerPanel.setBorder(null);
        southPanel.setBorder(null);

        // Configure layouts
        centerPanel.setLayout(new BorderLayout());
        treePanel.setLayout(new BorderLayout());
        southPanel.setLayout(new BorderLayout());

        // Configure borders
        rootPanel.setBorder(BorderFactory.createLineBorder(JBColor.border()));
        centerPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, JBColor.border()));
        southPanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, JBColor.border()));

        // Configure main split (tree | right split)
        mainSplit.setBorder(null);
        mainSplit.setDividerLocation(250);
        mainSplit.setDividerSize(2);

        // Configure right split (center | south)
        rightSplit.setBorder(null);
        rightSplit.setDividerLocation(500);
        rightSplit.setDividerSize(2);

        // Add resize listener for center panel
        centerPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (featureFileView != null) {
                    featureFileView.setTableWidth(e.getComponent().getWidth());
                }
            }
        });
    }
}