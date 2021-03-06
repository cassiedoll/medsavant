/*
 *    Copyright 2011 University of Toronto
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
package medsavant.pubmed;

import edu.iastate.jtm.assistant.PubMedAssistant;
import org.ut.biolab.medsavant.client.api.MedSavantVariantInspectorApp;
import org.ut.biolab.medsavant.client.view.genetics.inspector.SubInspector;
import org.ut.biolab.medsavant.shared.vcf.VariantRecord;
import com.Ostermiller.util.Browser;
import java.io.IOException;
import org.xml.sax.SAXException;

/**
 * Demonstration plugin to show how to do a simple panel.
 *
 * @author tarkvara
 */
public class PubMedInspectorApp extends MedSavantVariantInspectorApp {

    private PubMedVariantInspector inspector;

    public PubMedInspectorApp() {
        super();
        Browser.init();
        inspector = new PubMedVariantInspector();
    }

    @Override
    public void setVariantRecord(VariantRecord vr) {
        inspector.setVariantRecord(vr);
    }

    @Override
    public SubInspector getSubInspector() {
        return inspector;
    }

    public static void main(String[] args) throws IOException, SAXException {
        Browser.init();
        PubMedAssistant assistant = new PubMedAssistant();
        assistant.setVisible(true);        // PuMA extends JFrame
        assistant.search("any PubMed query");
    }
}
