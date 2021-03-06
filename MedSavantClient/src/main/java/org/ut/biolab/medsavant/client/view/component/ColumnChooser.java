/**
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.ut.biolab.medsavant.client.view.component;

import cern.colt.Arrays;
import java.awt.Dimension;
import javax.swing.JTable;

import com.jidesoft.grid.TableColumnChooserDialog;
import com.jidesoft.grid.TableColumnChooserPopupMenuCustomizer;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Properties;

import org.ut.biolab.medsavant.client.view.util.DialogUtils;


/**
 * Used by a <code>SearchableTablePanel</code> to select the columns to be displayed.
 *
 * @author tarkvara, rammar
 */
public class ColumnChooser extends TableColumnChooserPopupMenuCustomizer {
    private JTable table;
	
	private Properties properties= null;
	private String propertiesFilename= null;

    public ColumnChooser(JTable t) {
        table = t;
    }

    public void hideColumns(int[] indices) {
        for (int i: indices) {
            hideColumn(table, i);
        }
    }

    public void showDialog() {
        TableColumnChooserDialog dialog = createTableColumnChooserDialog(DialogUtils.getFrontWindow(), "Choose fields to display", table);
        dialog.setPreferredSize(new Dimension(300,500));
        dialog.setSize(new Dimension(300,500));
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
		
		
		/* Optionally save these hidden columns to a Properties object, if initialized. */
		try {
			if (properties != null) {
				int[] selectedColumns= dialog.getSelectedColumns();
				properties.setProperty("sortable_table_panel_columns", Arrays.toString(selectedColumns));
				properties.storeToXML(new FileOutputStream(propertiesFilename), "Configuration options");
			}
		} catch (Exception e) {
			System.err.println("[SearchableTablePanel]: Error storing and saving properties.");
			e.printStackTrace();
		}
    }
	
		
	public void setProperties(Properties p, String filename) {
		if (p != null) {			
			this.properties= p;
			this.propertiesFilename= filename;
		}
	}
}
