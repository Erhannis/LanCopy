/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.erhannis.lancopy;

import com.erhannis.lancopy.refactor.Advertisement;
import com.erhannis.lancopy.refactor.Comm;
import com.erhannis.mathnstuff.MeUtils;
import com.erhannis.mathnstuff.Pair;
import java.awt.Color;
import java.awt.Component;
import java.security.SecureRandom;
import java.util.HashMap;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

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

    private DefaultMutableTreeNode root = new DefaultMutableTreeNode("asdf");
    private DefaultTreeModel modelNodes = new DefaultTreeModel(root);

    private HashMap<String, DefaultMutableTreeNode> id2node = new HashMap<>();
    private HashMap<Comm, CommLabel> comm2label = new HashMap<>();

    /**
     * Creates new form CommsFrame
     */
    public CommsFrame() {
        initComponents();
        treeNodes.setCellRenderer(new DefaultTreeCellRenderer(){
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
            if (id2node.containsKey(ad.id)) {
                adNode = id2node.get(ad.id);
            } else {
                adNode = new DefaultMutableTreeNode(ad);
                root.add(adNode);
                id2node.put(ad.id, adNode);
            }
            //TODO Remove removed comms
            for (Comm comm : ad.comms) {
                if (!comm2label.containsKey(comm)) {
                    CommLabel commLabel = new CommLabel(comm);
                    DefaultMutableTreeNode commNode = new DefaultMutableTreeNode(commLabel);
                    adNode.add(commNode);
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
            CommLabel commLabel = comm2label.get(commStatus.a);
            if (commLabel != null) {
                commLabel.setState(commStatus.b);
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

        treeNodes.setModel(modelNodes      );
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

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(CommsFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(CommsFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(CommsFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(CommsFrame.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new CommsFrame().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTree treeNodes;
    // End of variables declaration//GEN-END:variables
}
