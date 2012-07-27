/*
 *    Copyright 2011-2012 University of Toronto
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.ut.biolab.medsavant.view.genetics.filter;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.rmi.RemoteException;
import java.sql.SQLException;
import javax.swing.*;

import org.ut.biolab.medsavant.api.MedSavantFilterPlugin;
import org.ut.biolab.medsavant.controller.FilterController;
import org.ut.biolab.medsavant.db.DefaultPatientTableSchema;
import org.ut.biolab.medsavant.format.CustomField;
import org.ut.biolab.medsavant.model.OntologyType;
import org.ut.biolab.medsavant.ontology.OntologyFilter;
import org.ut.biolab.medsavant.ontology.OntologyFilterView;
import org.ut.biolab.medsavant.util.ClientMiscUtils;
import org.ut.biolab.medsavant.view.component.KeyValuePairPanel;
import org.ut.biolab.medsavant.view.images.IconFactory;
import org.ut.biolab.medsavant.view.util.DialogUtils;
import org.ut.biolab.medsavant.view.util.ViewUtil;


/**
 * Class which lets us create the user interface around a filter-view without having to instantiate it.  The actual UI is maintained
 * using Marc's KeyValuePairPanel, using the filterID as the key.
 *
 * @author Andrew
 */
public abstract class FilterHolder {

    private final String name;
    private final String filterID;
    protected final int queryID;
    protected FilterView filterView;
    
    private KeyValuePairPanel parent;
    private JToggleButton editButton;
    private JButton clearButton;

    public FilterHolder(String name, String filterID, int queryID) {
        this.name = name;
        this.filterID = filterID;
        this.queryID = queryID;
    }

    public String getFilterID() {
        return filterID;
    }

    public String getFilterName() {
        return name;
    }

    public FilterView getFilterView() throws Exception {
        if (filterView == null) {
            filterView = createFilterView();
        }
        return filterView;
    }

    public abstract FilterView createFilterView() throws Exception;

    void addTo(KeyValuePairPanel kvp, final boolean longRunning) {
        parent = kvp;
        kvp.addKey(name);

        kvp.setKeyColour(name, SearchConditionsPanel.DEFAULT_KEY_COLOR);

        JLabel detailString = new JLabel("");
        detailString.setForeground(Color.orange);

        kvp.setValue(name, detailString);

        clearButton = ViewUtil.getTexturedButton(IconFactory.getInstance().getIcon(IconFactory.StandardIcon.DELETE));
        clearButton.setVisible(false);
        kvp.setAdditionalColumn(name, 0, clearButton);

        editButton = ViewUtil.getTexturedToggleButton(IconFactory.getInstance().getIcon(IconFactory.StandardIcon.CONFIGURE));
        editButton.addActionListener(new ActionListener() {

            boolean acceptedBeingLong = false;

            @Override
            public void actionPerformed(ActionEvent ae) {

                int result = DialogUtils.YES;
                if (!acceptedBeingLong && editButton.isSelected() && longRunning) {
                    result = DialogUtils.askYesNo("Warning", "This is a complex search condition that may take a long time to complete. Would you like to continue anyways?");
                    if (result == DialogUtils.YES) {
                        acceptedBeingLong = true;
                    }
                }

                if (result == DialogUtils.YES) {

                    if (filterView == null) {
                        try {
                            parent.setDetailComponent(name, getFilterView());
                        } catch (Exception e) {
                            DialogUtils.displayException("Problem displaying filter", "Problem getting values for filter " + name, e);
                        }
                    }

                    clearButton.setVisible(true);
                    parent.toggleDetailVisibility(name);
                    parent.invalidate();
                    parent.repaint();
                } else {
                    editButton.setSelected(false);
                }

            }
        });
        kvp.setAdditionalColumn(name, 1, editButton);

        clearButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent ae) {
                try {
                    FilterController.removeFilter(name, queryID);
                    filterView = null;
                    clearButton.setVisible(false);
                    editButton.setSelected(false);
                    parent.toggleDetailVisibility(name, false);
                } catch (Exception ex) {
                    ClientMiscUtils.reportError("Error removing filter: %s", ex);
                }
            }
        });
    }
}


/**
 * Many filters follow the same pattern, with an id called "FILTER_ID", a
 * name called "FILTER_NAME", and a constructor which takes a single
 * <code>int queryID</code> parameter.
 */
class SimpleFilterHolder extends FilterHolder {

    private final Class wrappedClass;

    SimpleFilterHolder(Class clazz, int queryID) throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        super((String)clazz.getField("FILTER_NAME").get(null), (String)clazz.getField("FILTER_ID").get(null), queryID);
        wrappedClass = clazz;
    }

    @Override
    public FilterView createFilterView() throws Exception {
        return (FilterView)wrappedClass.getDeclaredConstructor(int.class).newInstance(queryID);
    }
}



/**
 * A common placeholder which wraps the filter for a custom field in the
 * patient or variant tables.
 */
class FieldFilterHolder extends FilterHolder {

    private final CustomField field;
    private final FilterUtils.WhichTable whichTable;

    FieldFilterHolder(CustomField field, FilterUtils.WhichTable table, int queryID) {
        super(field.getAlias(), field.getColumnName(), queryID);
        this.field = field;
        this.whichTable = table;
    }


    @Override
    public FilterView createFilterView() throws Exception {
       if (field.getColumnName().equals(DefaultPatientTableSchema.COLUMNNAME_OF_GENDER)) {
            return new StringListFilterView(whichTable, field.getColumnName(), queryID, field.getAlias());
        } else {
            switch (field.getColumnType()) {
                case INTEGER:
                    return new NumericFilterView(whichTable, field.getColumnName(), queryID, field.getAlias(), false);
                case FLOAT:
                case DECIMAL:
                    return new NumericFilterView(whichTable, field.getColumnName(), queryID, field.getAlias(), true);
                case BOOLEAN:
                    return new BooleanFilterView(whichTable, field.getColumnName(), queryID, field.getAlias());
                case VARCHAR:
                default:
                    return new StringListFilterView(whichTable, field.getColumnName(), queryID, field.getAlias());
            }
        }
    }
}

class OntologyFilterHolder extends FilterHolder {

    private final OntologyType ontology;

    OntologyFilterHolder(OntologyType ont, int queryID) {
        super(OntologyFilter.ontologyToTitle(ont), OntologyFilter.ontologyToFilterID(ont), queryID);
        this.ontology = ont;
    }

    @Override
    public FilterView createFilterView() throws SQLException, RemoteException {
        return new OntologyFilterView(ontology, queryID);
    }
}


class PluginFilterHolder extends FilterHolder {
    private final MedSavantFilterPlugin plugin;
    
    PluginFilterHolder(MedSavantFilterPlugin p, int queryID) {
        super(p.getTitle(), p.getDescriptor().getID(), queryID);
        plugin = p;
    }

    @Override
    public FilterView createFilterView() {
        return PluginFilterView.getFilterView(plugin, queryID);
    }
}