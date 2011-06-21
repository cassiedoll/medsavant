/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.ut.biolab.medsavant.view;

import org.ut.biolab.medsavant.model.event.SectionChangedEvent;
import java.awt.Color;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.JTabbedPane;
import org.ut.biolab.medsavant.model.event.SectionChangedEventListener;
import org.ut.biolab.medsavant.view.genetics.GeneticsSubView;
import org.ut.biolab.medsavant.view.subview.SubView;

/**
 *
 * @author mfiume
 */
public class SessionView extends JPanel implements SectionChangedEventListener {

    private JTabbedPane panes;

    //private static final String DEFAULT_SUBVIEW = "Variants";
   // private SplitView splitView;

    public SessionView() {
        this.setBackground(Color.darkGray);
        init();
    }

    private void init() {
        this.setLayout(new BorderLayout());
        initViewContainer();
        initViews();
    }

    private void addSubView(SubView view) {
        panes.addTab(view.getName(), view);
        //splitView.addSubsection(view.getName(), view);
    }

    private void initViewContainer() {
        panes = new JTabbedPane(JTabbedPane.TOP);
        panes.setFont(new Font("Arial", Font.PLAIN, 14));
        this.add(panes, BorderLayout.CENTER);
        //splitView = new SplitView();
        //splitView.addSectionChangedListener(this);
        //this.add(splitView, BorderLayout.CENTER);
    }

    private void initViews() {

        //splitView.addSection("Library");
        //addSubView(new LibraryVariantsPage());
        //splitView.addSection("Search");
        //addSubView(new PatientsPage());
        addSubView(new GeneticsSubView());
        //addSubView(new AnnotationsPage());
        panes.setSelectedIndex(0);
        //splitView.setSubsection(DEFAULT_SUBVIEW);
    }

    public void sectionChangedEventReceived(SectionChangedEvent e) {
        //System.out.println("View received section changed to " + e.getSection());
    }
}
