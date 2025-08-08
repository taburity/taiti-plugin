package br.edu.ufape.taiti.gui.configuretask.tree;

import com.intellij.ui.treeStructure.Tree;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.File;
import java.util.ArrayList;

public class TaitiTree extends Tree {

    private final String CUCUMBER_FILES_DIRECTORY = "features";

    public TaitiTree(DefaultTreeModel treeModel) {
        super(treeModel);
    }

    public File findFeatureDirectory(String path) {
        File root = new File(path);
        File[] listFiles = root.listFiles();
        File featuresFolder = null;

        if (listFiles == null) {
            return featuresFolder;
        }

        for (File file : listFiles) {
            if(featuresFolder == null) {
                if (file.isDirectory()) {
                    if (file.getAbsolutePath().endsWith(File.separator + CUCUMBER_FILES_DIRECTORY)) {
                        featuresFolder = file;
                    } else {
                        featuresFolder = findFeatureDirectory(file.getAbsolutePath());
                    }
                }
            }
        }

        return featuresFolder;
    }

    public void addParentsNodeToTree(ArrayList<DefaultMutableTreeNode> parentsNodes, DefaultMutableTreeNode node, int index) {
        if (index < 0) {
            return;
        }
        DefaultMutableTreeNode parentNode = parentsNodes.get(index);
        node.add(parentNode);
        addParentsNodeToTree(parentsNodes, parentNode, index - 1);
    }

    public boolean addNodesToTree(File currentDir, DefaultMutableTreeNode parentNode) {
        File[] listFiles = currentDir.listFiles();
        boolean hasValidChildren = false;

        if (listFiles == null) return false;

        for (File file : listFiles) {
            if (file.isDirectory()) {
                DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(file.getName());
                boolean childHasContent = addNodesToTree(file, childNode);

                if (childHasContent) {
                    parentNode.add(childNode);
                    hasValidChildren = true;
                }
            } else if (isFeatureFile(file)) {
                parentNode.add(new DefaultMutableTreeNode(new TaitiTreeFileNode(file), false));
                hasValidChildren = true;
            }
        }

        return hasValidChildren;
    }

    private boolean isFeatureFile(File file) {
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".feature");
    }

}