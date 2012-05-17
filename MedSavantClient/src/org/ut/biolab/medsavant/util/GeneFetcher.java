/*
 *    Copyright 2012 University of Toronto
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

package org.ut.biolab.medsavant.util;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.ut.biolab.medsavant.MedSavantClient;
import org.ut.biolab.medsavant.controller.LoginController;
import org.ut.biolab.medsavant.model.Gene;
import org.ut.biolab.medsavant.model.GeneSet;


/**
 * Swing worker which fetches genes for a given gene set.  Used by both GenesDetailedView and RegionWizard.  Note that this
 * implementation fetches genes, not transcripts.
 *
 * @author tarkvara
 */
public abstract class GeneFetcher extends MedSavantWorker<List<Gene>> {
    private static final Log LOG = LogFactory.getLog(GeneFetcher.class);

    private GeneSet selectedSet;

    public GeneFetcher(GeneSet selectedSet, String page) {
        super(page);
        this.selectedSet = selectedSet;
    }

    @Override
    protected List<Gene> doInBackground() throws Exception {
        return MedSavantClient.GeneSetManager.getGenes(LoginController.sessionId, selectedSet);
    }

    
    @Override
    protected void showSuccess(List<Gene> result) {
        //List<Object[]> list = new ArrayList<Object[]>();
        Object[][] data = new Object[result.size()][];
        for (int i = 0; i < result.size(); i++) {
            Gene g = result.get(i);
            data[i] = new Object[] { g.getName(), g.getChrom(), g.getStart(), g.getEnd(), g.getCodingStart(), g.getCodingEnd() };
        }
        setData(data);
    }

    /**
     * Do something with the data we've just retrieved.  Typically populating a list.
     * @param data data collected from genes
     */
    public abstract void setData(Object[][] data);
}
