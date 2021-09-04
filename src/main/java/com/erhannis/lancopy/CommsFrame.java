/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.mathnstuff.FactoryHashMap;
import com.erhannis.mathnstuff.MeUtils;
import com.erhannis.mathnstuff.Pair;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import jcsp.lang.ChannelOutput;

/**
 *
 * @author erhannis
 */
public class CommsFrame extends javax.swing.JFrame {

    public static class ColoredLine {

        public static final Color RED = MeUtils.interpolateColors(Color.RED, Color.WHITE, 0.5);
        public static final Color GREEN = MeUtils.interpolateColors(Color.GREEN, Color.WHITE, 0.5);
        public static final Color BLUE = MeUtils.interpolateColors(Color.BLUE, Color.WHITE, 0.5);
        public static final Color GREY = Color.GRAY;
        public Color color = GREY;
    }

    public static class CommLabel extends ColoredLine {

        private final Comm comm;
        private Boolean state = null;

        private static SecureRandom r = new SecureRandom();

        public CommLabel(Comm comm) {
            this.comm = comm;
        }

        public Boolean getState() {
            return state;
        }

        public void setState(Boolean state) {
            this.state = state;
            if (state == null) {
                this.color = ColoredLine.GREY;
            } else if (state == true) {
                this.color = ColoredLine.GREEN;
            } else if (state == false) {
                this.color = ColoredLine.RED;
            }
        }

        @Override
        public String toString() {
            return comm + " : " + state;
        }
    }

    private DefaultMutableTreeNode root = new DefaultMutableTreeNode("Nodes");
    private DefaultTreeModel modelNodes = new DefaultTreeModel(root);

    private HashMap<UUID, DefaultMutableTreeNode> id2adNode = new HashMap<>();
    private FactoryHashMap<UUID, HashMap<String, DefaultMutableTreeNode>> id2typeNodes = new FactoryHashMap<>((s) -> {
        return new HashMap<>();
    });
    private HashMap<Comm, CommLabel> comm2label = new HashMap<>();

    private final ChannelOutput<Collection<Comm>> pokeOut;
    private final ChannelOutput<List<Comm>> subscribeOut;

    /**
     * Creates new form CommsFrame
     */
    public CommsFrame(ChannelOutput<Collection<Comm>> pokeOut, ChannelOutput<List<Comm>> subscribeOut) {
        this.pokeOut = pokeOut;
        this.subscribeOut = subscribeOut;

        initComponents();
        treeNodes.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                JComponent c = (JComponent) super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                c.setOpaque(false);
                if (value instanceof DefaultMutableTreeNode) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                    if (node.getUserObject() instanceof ColoredLine) {
                        ColoredLine line = (ColoredLine) node.getUserObject();
                        if (sel) {
                            c.setBackground(MeUtils.interpolateColors(line.color, Color.WHITE, 0.5));
                        } else {
                            c.setBackground(line.color);
                        }
                        c.setOpaque(true);
                    }
                }
                return c;
            }
        });
    }

    public void update(Advertisement ad) {
        SwingUtilities.invokeLater(() -> {
            DefaultMutableTreeNode adNode;
            if (id2adNode.containsKey(ad.id)) {
                adNode = id2adNode.get(ad.id);
            } else {
                adNode = new DefaultMutableTreeNode(ad);
                root.add(adNode);
                id2adNode.put(ad.id, adNode);
            }
            //TODO Remove removed comms
            for (Comm comm : ad.comms) {
                if (!comm2label.containsKey(comm)) {
                    CommLabel commLabel = new CommLabel(comm);
                    DefaultMutableTreeNode commNode = new DefaultMutableTreeNode(commLabel);

                    HashMap<String, DefaultMutableTreeNode> typeNodes = id2typeNodes.get(ad.id);
                    DefaultMutableTreeNode typeNode = typeNodes.get(comm.type);
                    if (typeNode == null) {
                        typeNode = new DefaultMutableTreeNode(comm.type);
                        adNode.add(typeNode);
                        typeNodes.put(comm.type, typeNode);
                    }

                    typeNode.add(commNode);
                    comm2label.put(comm, commLabel);
                }
            }
            //TODO Overkill
            modelNodes.nodeStructureChanged(root);
            //TODO Specifically new Ads
            for (int i = 0; i < treeNodes.getRowCount(); i++) {
                treeNodes.expandRow(i);
            }
            treeNodes.repaint();
        });
    }

    public void update(Pair<Comm, Boolean> commStatus) {
        SwingUtilities.invokeLater(() -> {
            Comm comm = commStatus.a;
            CommLabel commLabel = comm2label.get(comm);
            if (commLabel != null) {
                commLabel.setState(commStatus.b);
            } else {
                comm.equals(comm);
                System.out.println("Wasn't sure if this code would get called");
                commLabel = new CommLabel(comm);
                commLabel.setState(commStatus.b);

                DefaultMutableTreeNode commNode = new DefaultMutableTreeNode(commLabel);

                HashMap<String, DefaultMutableTreeNode> typeNodes = id2typeNodes.get(comm.owner.id);
                DefaultMutableTreeNode typeNode = typeNodes.get(comm.type);
                if (typeNode == null) {
                    typeNode = new DefaultMutableTreeNode(comm.type);
                    id2adNode.get(comm.owner.id).add(typeNode);
                    typeNodes.put(comm.type, typeNode);
                }

                typeNode.add(commNode);
                comm2label.put(comm, commLabel);
            }
            ////TODO Overkill
            modelNodes.nodeStructureChanged(root);
            //TODO Specifically new Ads
            for (int i = 0; i < treeNodes.getRowCount(); i++) {
                treeNodes.expandRow(i);
            }
            treeNodes.repaint();
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        treeNodes = new javax.swing.JTree();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("Comms");

        treeNodes.setModel(modelNodes      );
        treeNodes.setToolTipText("Dbl click or hit enter to test selected comms.  +ctrl to subscribe.");
        treeNodes.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                treeNodesMouseClicked(evt);
            }
        });
        treeNodes.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                treeNodesKeyPressed(evt);
            }
        });
        jScrollPane1.setViewportView(treeNodes);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 375, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 311, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void affectSelected(boolean subscribe) {
        List<Comm> comms = new LinkedList<Comm>();
        TreePath[] selected = treeNodes.getSelectionPaths();
        if (selected == null) {
            return;
        }
        for (int i = 0; i < selected.length; i++) {
            TreePath path = (TreePath) selected[i];
            Object object = path.getLastPathComponent();
            if (object instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) object;
                if (node.getUserObject() instanceof CommLabel) {
                    CommLabel label = (CommLabel) node.getUserObject();
                    label.setState(null);
                    comms.add(label.comm);
                }
            }
        }
        if (subscribe) {
            subscribeOut.write(comms);
        } else {
            pokeOut.write(comms);
        }
    }

    private void treeNodesMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_treeNodesMouseClicked
        if (evt.getClickCount() >= 2) {
            if (evt.isControlDown()) {
                affectSelected(true);
            } else {
                affectSelected(false);
            }
        }
    }//GEN-LAST:event_treeNodesMouseClicked

    private void treeNodesKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_treeNodesKeyPressed
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
            if (evt.isControlDown()) {
                affectSelected(true);
            } else {
                affectSelected(false);
            }
        }
    }//GEN-LAST:event_treeNodesKeyPressed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTree treeNodes;
    // End of variables declaration//GEN-END:variables
}
