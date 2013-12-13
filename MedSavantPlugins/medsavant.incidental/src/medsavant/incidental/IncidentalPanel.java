package medsavant.incidental;

import com.jidesoft.pane.CollapsiblePane;
import com.jidesoft.pane.CollapsiblePanes;
import com.jidesoft.swing.CheckBoxList;
import java.util.Calendar;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.GroupLayout;
import org.ut.biolab.medsavant.client.view.component.RoundedPanel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import medsavant.incidental.localDB.IncidentalDB;
import medsavant.incidental.localDB.IncidentalHSQLServer;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.ut.biolab.medsavant.client.project.ProjectController;
import org.ut.biolab.medsavant.client.util.MedSavantWorker;
import org.ut.biolab.medsavant.client.view.component.ProgressWheel;
import org.ut.biolab.medsavant.client.view.dialog.IndividualSelector;
import org.ut.biolab.medsavant.client.view.util.ViewUtil;
import org.ut.biolab.medsavant.shared.format.AnnotationFormat;
import org.ut.biolab.medsavant.shared.format.CustomField;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.ut.biolab.medsavant.MedSavantClient;
import org.ut.biolab.medsavant.client.settings.DirectorySettings;
import org.ut.biolab.medsavant.client.view.MedSavantFrame;
import org.ut.biolab.medsavant.client.view.component.SearchableTablePanel;


/**
 * Default panel view for Incidentalome app
 * 
 * @author rammar
 */
public class IncidentalPanel extends JPanel {
	private static final Log LOG = LogFactory.getLog(MedSavantClient.class);
	private static final Properties properties= new Properties();
	private static final String PROPERTIES_FILENAME= DirectorySettings.getMedSavantDirectory().getPath() +
				File.separator + "cache" + File.separator + "incidentalome_app_settings.xml";
	private static final String DEFAULT_CGD_URL= "http://research.nhgri.nih.gov/CGD/download/txt/CGD.txt.gz";
	private static final String DEFAULT_CGD_FILENAME= "CGD.txt";
	private static final int DEFAULT_COVERAGE_THRESHOLD= 10;
	private static final double	DEFAULT_HET_RATIO= 0.3;
	private static final double DEFAULT_AF_THRESHOLD= 0.05;
	private static final String[] DEFAULT_AF_DB_LIST= new String[] {
		"1000g2012apr_all, AnnotationFrequency", "esp6500_all, Score"};
			
	private final int TOP_MARGIN= 0;
	private final int SIDE_MARGIN= 100;
	private final int BOTTOM_MARGIN= 10;
	private final int TEXT_AREA_WIDTH= 80;
	private final int TEXT_AREA_HEIGHT= 25;
	
	public static final String PAGE_NAME = "Incidentalome";
	private static final String INCIDENTAL_DB_USER= "incidental_user";
	private static final String INCIDENTAL_DB_PASSWORD= "$hazam!2734"; // random password
	
	private int coverageThreshold;
	private double hetRatio;
	private double afThreshold;
	private String[] chooserAFArray;
	
	private boolean analysisRunning= false;
	private boolean dbLoaded= false;
	
	private JPanel view;
	private RoundedPanel workview;
	private JButton choosePatientButton;
	private JButton analyzeButton;
	private String analyzeButtonDefaultText= "Refresh";
	private IndividualSelector customSelector;
	private Set<String> selectedIndividuals;
	private String currentIndividual;
	private String currentIndividualDNA;
	private Calendar date;
	private MedSavantWorker MSWorker;
	private JScrollPane variantPane;
	private ProgressWheel pw;
	private JLabel progressLabel;
	private final int preferredNumColumns= 10;
	private JLabel coverageThresholdLabel= new JLabel("Min. variant coverage (X)");
	private JTextField coverageThresholdText;
	private JButton coverageThresholdHelp;
	private JLabel hetRatioLabel= new JLabel("Min. ratio of alternate/total reads");
	private JTextField hetRatioText;
	private JButton hetRatioHelp;
	private JLabel afThresholdLabel= new JLabel("Max. allele frequency");
	private JTextField afThresholdText;
	private JButton afThresholdHelp;
	private JButton chooseAFColumns;
	private JButton chooseAFColumnsHelp;
	private CheckBoxList chooser;
	//private JSeparator statusSeparator= new JSeparator(SwingConstants.HORIZONTAL);
	private URL cgdURL;
	private CollapsiblePane collapsible;
	
	private IncidentalHSQLServer server;
    
	
	public IncidentalPanel() {
		/* Set up the properties based on stored user preference. */
		try {
			loadProperties();
		} catch (Exception e) {
			System.err.println("Error loading properties.");
			e.printStackTrace();
		}
		
		setupView();
		add(view);
		
		server= new IncidentalHSQLServer(INCIDENTAL_DB_USER, INCIDENTAL_DB_PASSWORD);
	}

	
	private void setupView() {
		view= ViewUtil.getClearPanel();
		view.setLayout(new BorderLayout());
		//view.setBorder(BorderFactory.createLineBorder(Color.RED));
		view.setBorder(BorderFactory.createEmptyBorder(TOP_MARGIN, SIDE_MARGIN, BOTTOM_MARGIN, SIDE_MARGIN));
		
		choosePatientButton= new JButton("Choose Patient");
		choosePatientButton.setFont(new Font(choosePatientButton.getFont().getName(),
			Font.PLAIN, 18));
				
		analyzeButton= new JButton(analyzeButtonDefaultText);
		analyzeButton.setFont(new Font(analyzeButton.getFont().getName(),
			Font.BOLD, 14));
		analyzeButton.setEnabled(false); // cannot click until valid DNA ID is selected
		analyzeButton.setVisible(false);
		
		Dimension d= new Dimension(TEXT_AREA_WIDTH, TEXT_AREA_HEIGHT);
		coverageThresholdText= new JTextField(Integer.toString(coverageThreshold));
		coverageThresholdText.setMinimumSize(d);
		coverageThresholdText.setHorizontalAlignment(JTextField.RIGHT);
		coverageThresholdHelp= ViewUtil.getHelpButton("Coverage Threshold", 
				"Minimum number of sequence reads supporting the alternate allele.");
		hetRatioText= new JTextField(Double.toString(hetRatio));
		hetRatioText.setMinimumSize(d);
		hetRatioText.setHorizontalAlignment(JTextField.RIGHT);
		hetRatioHelp= ViewUtil.getHelpButton("Alt/Total Ratio", 
				"In order for a variant to be included, it must exceeed this threshold, "
				+ "so as not to be excluded as an erroneous variant. "
				+ "Below this threshold, alternate alleles are not reported.");
		afThresholdText= new JTextField(Double.toString(afThreshold));
		afThresholdText.setMinimumSize(d);
		afThresholdText.setHorizontalAlignment(JTextField.RIGHT);
		afThresholdHelp= ViewUtil.getHelpButton("Allele Frequency Threshold", 
				"The maximum allele frequency for this variant. In order for a "
				+ "variant to be reported, allele frequency must be below this "
				+ "threshold across all allele frequency databases.");
		
		
		//statusSeparator.setVisible(false);
		
		progressLabel= new JLabel();
		progressLabel.setVisible(false);
		
		pw= new ProgressWheel();
		pw.setIndeterminate(true);
		pw.setVisible(false);
		
		chooser= new CheckBoxList(getDbColumnList());
		chooser.addCheckBoxListSelectedValues(chooserAFArray);
		
		variantPane= new JScrollPane();
		
		/* Choose the patient's sample using the individual selector. */
		customSelector= new IndividualSelector();
		choosePatientButton.addActionListener(
			new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					customSelector.setVisible(true);
					selectedIndividuals= customSelector.getHospitalIDsOfSelectedIndividuals();
					
					if (customSelector.hasMadeSelection() && selectedIndividuals.size() == 1) {
						
						currentIndividual= selectedIndividuals.iterator().next();
						currentIndividualDNA= customSelector.getDNAIDsOfSelectedIndividuals().iterator().next();
						
						if (currentIndividualDNA != null) {
							choosePatientButton.setText(currentIndividual);
							analyzeButton.setEnabled(true);
							analyzeButton.doClick(); // trigger button's actionPerformed() even though it's not visible
						} else {
							choosePatientButton.setText("No DNA ID for " + currentIndividual);
						}
					} else if (customSelector.getHospitalIDsOfSelectedIndividuals().size() > 1){
						choosePatientButton.setText("Choose only 1 patient");
					}
				}
			}
		);
		
		
		/* Run incidental findings analysis */
		analyzeButton.addActionListener(
			new ActionListener() {
				
				@Override
				public void actionPerformed (ActionEvent e) {
					if (selectedIndividuals != null && selectedIndividuals.size() == 1 && !analysisRunning) {

						analysisRunning= true;
										
						MSWorker= new MedSavantWorker<Object> (
							IncidentalPanel.class.getCanonicalName()) {

							IncidentalFindings incFin;
								
							@Override
							protected Object doInBackground() throws Exception {
								/* Starts a new thread for background tasks. */
								
								//statusSeparator.setVisible(true);
								progressLabel.setVisible(true);
								pw.setVisible(true);
								analyzeButton.setText("Cancel analysis");
								analyzeButton.setVisible(true);
								
								//if (!server.isRunning()) { // No need to run a local server if using JDBC driver from hsqldb
								if (!dbLoaded) {
									progressLabel.setText("Preparing local filtering database...");
									try {
										//server.startServer(); // No need to run a local server if using JDBC driver from hsqldb
										dbLoaded= true;
										IncidentalDB.populateDB(server.getURL(), INCIDENTAL_DB_USER, INCIDENTAL_DB_PASSWORD, properties);
									} catch (SQLException e) {
										e.printStackTrace();
									}
								}
								
								progressLabel.setText("Downloading and filtering variants...");
								
								/* Get all the user settings. */
								setAllValuesFromFields();
								
								/* Every time an analysis is run, parameters/settings are saved. */
								saveProperties();
								
								/*  Get incidental findings. */
								incFin= new IncidentalFindings(currentIndividualDNA, 
										coverageThreshold, hetRatio, afThreshold, 
										Arrays.asList(chooser.getCheckBoxListSelectedValues()));
								
								
								if (this.isCancelled()) {
									progressLabel.setText("Analysis Cancelled.");
									pw.setVisible(false);
									analyzeButton.setEnabled(true);
									analyzeButton.setText(analyzeButtonDefaultText);
								} else {
									progressLabel.setText(incFin.getVariantCount() + " variants. " +
											"Click on column to sort. Hold CTRL while clicking to sort by multiple columns.");
								}
								pw.setVisible(false);
								return null;
							}

							@Override
							protected void showSuccess(Object t) {	
							/* All updates to display should happen here to be run. */
								updateVariantPane(incFin);
								analyzeButton.setText(analyzeButtonDefaultText);
								analysisRunning= false;
							}
							
						};
						
						MSWorker.execute();
						
					} else if (selectedIndividuals != null && selectedIndividuals.size() == 1
						&& analysisRunning) {
						analysisRunning= false;
						MSWorker.cancel(true);
						analyzeButton.setEnabled(false);
						progressLabel.setText("Cancelling Analysis...");
					}
				}
			}
		);		
		
		
		chooseAFColumns= new JButton("Choose Allelle Frequency DBs");
		chooseAFColumnsHelp= ViewUtil.getHelpButton("Allele Frequency Database selector", 
				"Choose the databases to use when filtering for allele frequency.");
		chooseAFColumns.addActionListener(
			new ActionListener() {
				
				@Override
				public void actionPerformed (ActionEvent e) {
					JScrollPane chooserScrollPane= new JScrollPane(chooser);
					chooserScrollPane.setBorder(BorderFactory.createEmptyBorder(2,3,4,1)); // just a little bit of border
					chooserScrollPane.setBackground(Color.LIGHT_GRAY);
					
					JDialog f = new JDialog(MedSavantFrame.getInstance(),"Allele Frequency Database selector");
					f.add(chooserScrollPane);
					f.setPreferredSize(new Dimension(300, 500));
					f.setMinimumSize(new Dimension(300, 500));
					f.setLocationRelativeTo(null);
					f.setVisible(true);
				}
			}
		);
		
		
		/* Set up the layout for the UI.
		 * GroupLayout requires defintion of the same components from both 
		 * horizontal and verical perspectives. */
		
		/* Set up the layout for the advanced options collapsible panel. */
		collapsible= new CollapsiblePane("Advanced options");
		collapsible.setLayout(new MigLayout());
		collapsible.add(coverageThresholdLabel);
		collapsible.add(coverageThresholdText);
		collapsible.add(coverageThresholdHelp, "wrap");
		collapsible.add(hetRatioLabel);
		collapsible.add(hetRatioText);
		collapsible.add(hetRatioHelp, "wrap");
		collapsible.add(afThresholdLabel);
		collapsible.add(afThresholdText);
		collapsible.add(afThresholdHelp, "wrap");
		collapsible.add(chooseAFColumns);
		collapsible.add(chooseAFColumnsHelp);
		collapsible.setStyle(CollapsiblePane.PLAIN_STYLE);
		collapsible.setFocusPainted(false);
		collapsible.collapse(true);
		
		/* Patient selection panel. */
		CollapsiblePanes patientPanel= new CollapsiblePanes();
		patientPanel.add(choosePatientButton);
		patientPanel.add(collapsible);
		patientPanel.add(analyzeButton);
		
		/* Progress bar panel. */
		JPanel progressPanel= new JPanel(new MigLayout("", "center", "center"));
		progressPanel.add(progressLabel, "wrap");
		progressPanel.add(pw, "");
		
		/* Final window layout along with size preferences. */
		collapsible.setMinimumSize(new Dimension(350,20));
		variantPane.setPreferredSize(variantPane.getMaximumSize());
		
		workview= new RoundedPanel(10);
		workview.setLayout(new MigLayout("", "center", "top"));
		workview.add(patientPanel, "cell 0 0 1 2");
		workview.add(variantPane, "cell 1 0");
		workview.add(progressPanel, "cell 1 1");
		
		/* Add the UI to the main app panel. */
		view.add(workview, BorderLayout.CENTER);
    }
	
		
	private void updateVariantPane (IncidentalFindings i) {
		SearchableTablePanel stp;
		if (properties.getProperty("sortable_table_panel_columns") == null) {
			stp= i.getTableOutput(null);
		} else {
			stp= i.getTableOutput(
				getIntArrayFromString(properties.getProperty("sortable_table_panel_columns")));
		}
		stp.getColumnChooser().setProperties(properties, PROPERTIES_FILENAME);
		variantPane.setViewportView(stp);
	}
	
	
	public JPanel getView() {
		return view;
	}
    
	
	/** Set all values from JTextFields. Also set the relevant properties. */
	private void setAllValuesFromFields() {
		coverageThreshold= Integer.parseInt(coverageThresholdText.getText());
		hetRatio= Double.parseDouble(hetRatioText.getText());
		afThreshold= Double.parseDouble(afThresholdText.getText());
		
		/* Set the properties. */
		properties.setProperty("coverage_threshold", Integer.toString(coverageThreshold));
		properties.setProperty("het_ratio", Double.toString(hetRatio));
		properties.setProperty("af_threshold", Double.toString(afThreshold));
		
		// quote-enclosed, comma-delimited list as string
		String afChooserStringList= "\"" + StringUtils.join(Arrays.asList(
			chooser.getCheckBoxListSelectedValues()), "\"\t\"") + "\"";
		properties.setProperty("af_chooser_list", afChooserStringList);
	}
	
	
	/** Get the header for the table using the column aliases. */
	public Object[] getDbColumnList() {
		List<String> t= new ArrayList<String>();

		try {
			AnnotationFormat[] afs = ProjectController.getInstance().getCurrentAnnotationFormats();
			for (AnnotationFormat af : afs)
				for (CustomField field : af.getCustomFields())
					t.add(field.getAlias());
		} catch (Exception e) {
			LOG.error(e);
		}
	
		return t.toArray();
	}

	
	/**
	 * Load the properties file if it exists.
	 */
	private void loadProperties () throws Exception {
		File propertiesFile= new File(PROPERTIES_FILENAME);
		if (!propertiesFile.exists()) {
			/* Set the defaults. */
			long defaultDate= (new GregorianCalendar(2013, Calendar.NOVEMBER, 
				27)).getTimeInMillis(); // CGD date at time of coding
			
			properties.setProperty("CGD_DB_date", Long.toString(defaultDate));
			properties.setProperty("CGD_DB_URL", DEFAULT_CGD_URL.toString());
			properties.setProperty("CGD_DB_filename", DEFAULT_CGD_FILENAME);
			
			properties.setProperty("coverage_threshold", Integer.toString(DEFAULT_COVERAGE_THRESHOLD));
			properties.setProperty("het_ratio", Double.toString(DEFAULT_HET_RATIO));
			properties.setProperty("af_threshold", Double.toString(DEFAULT_AF_THRESHOLD));
			
			String afChooserStringList= "\"" + StringUtils.join(Arrays.asList(DEFAULT_AF_DB_LIST), "\"\t\"") + "\"";
			properties.setProperty("af_chooser_list", afChooserStringList);
						
			saveProperties();
		} else {
			properties.loadFromXML(new FileInputStream(propertiesFile));
		}
		
		/* Set the parameters from properties. */
		cgdURL= new URL(properties.getProperty("CGD_DB_URL"));
		coverageThreshold= Integer.parseInt(properties.getProperty("coverage_threshold"));
		hetRatio= Double.parseDouble(properties.getProperty("het_ratio"));
		afThreshold= Double.parseDouble(properties.getProperty("af_threshold"));

		String s= properties.getProperty("af_chooser_list");
		chooserAFArray= (s.substring(1, s.length() - 1)).split("\"\t\"");
		
		// Update CGD file if necessary
		updateCGD();
		copyCGD();		
	}

	
	/** 
	 * Save the current set of properties to the properties XML file.
	 */
	private void saveProperties() {
		try {
			properties.storeToXML(new FileOutputStream(PROPERTIES_FILENAME), 
				"Configuration options for incidentalome app");
		} catch (Exception e) {
			System.err.println("[IncidentalPanel]: Error saving properties XML file.");
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Update the CGD database if a new one exists at the specified URL.
	 */
	private void updateCGD() throws Exception {
		HttpURLConnection conn= (HttpURLConnection) cgdURL.openConnection();
		Date urlDate= new Date(conn.getLastModified());
		Date currentDate= new Date(Long.parseLong((String) properties.getProperty("CGD_DB_date")));
		
		if (currentDate.before(urlDate)) {
			// notify users
			DateFormat dateFormat= new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			System.out.println("[Incidental Panel]: Existing CGD version from " + 
				dateFormat.format(currentDate) + " to be replaced by newer CGD version from " +
				dateFormat.format(urlDate));
			
			// download file to cache, uncompress, removed compressed file, set new properties
			File cgdFile= new File(DirectorySettings.getMedSavantDirectory().getPath() +
				File.separator + "cache" + File.separator + 
				FilenameUtils.getName(cgdURL.getFile()));	
			FileUtils.copyURLToFile(cgdURL, cgdFile);
			File newCgdFile= gunzip(cgdFile);
			cgdFile.delete();
			changeCGDHeader(newCgdFile); // should overwrite existing CGD.txt file if exists
			
			// modify and save properties
			properties.setProperty("CGD_DB_date", Long.toString(urlDate.getTime()));
			properties.setProperty("CGD_DB_filename", newCgdFile.getName());
			
			saveProperties();
		}
	}
	
	
	/** 
	 * Copy CGD file to cache if it is not already there.
	 */
	private void copyCGD() {
		File f= new File(DirectorySettings.getMedSavantDirectory().getPath() +
				File.separator + "cache" + File.separator + properties.getProperty("CGD_DB_filename"));
		if (!f.exists()) { // copy the default pre-packaged CGD file from Nov. 26, 2013.
			try {
				InputStream in= IncidentalPanel.class.getResourceAsStream("/db_files/CGD.txt");
				OutputStream out= new FileOutputStream(f);
				IOUtils.copy(in, out);
				in.close();
				out.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	
	/** Uncompresses the gzipped File. 
	 *  Code adapted from StackOverFlow example.
	 * @param gzipFile the gzipped file object
	 * @precondition Expecting the gzipFile has a .gz extension
	 * @return the new file object
	 */
	public static File gunzip(File gzipFile) {
		// Get the file name without the .gz extension
		Pattern gunzipFilenamePattern= Pattern.compile("^(.+).gz$", Pattern.CASE_INSENSITIVE);
		Matcher gunzipFilenameMatcher= gunzipFilenamePattern.matcher(gzipFile.getPath());
		String gunzipFilename= null;
		if (gunzipFilenameMatcher.find())
			gunzipFilename= gunzipFilenameMatcher.group(1);
		
		// read the compressed file and output an uncompressed version
		GZIPInputStream in = null;
		OutputStream out = null;
		try {
		   in = new GZIPInputStream(new FileInputStream(gzipFile));
		   out = new FileOutputStream(gunzipFilename);
		   byte[] buf = new byte[1024 * 4];
		   int len;
		   while ((len = in.read(buf)) > 0) {
			   out.write(buf, 0, len); // if you use write(buf), end up writing weird characters if buf not full
		   }
		   in.close();
		   out.close();
		}
		catch (IOException e) {
		   e.printStackTrace();
		}
		
		return new File(gunzipFilename);
	}
	
	
	/** Change CGD header to predefined header. Since the file is small, make 
	 * all changes in memory and output a new file by the same name. */
	public static void changeCGDHeader(File cgdFile) throws FileNotFoundException, IOException {
		List<String> newLines= new LinkedList<String>();
		BufferedReader reader= null;
		
		// Store the custom header - just the first line
		reader= new BufferedReader(
			new InputStreamReader(IncidentalDB.class.getResourceAsStream("/db_files/CGD_header.txt"))); 
		newLines.add(reader.readLine());
		reader.close();
		
		// Store all the non-header lines from the current CGD file
		reader= new BufferedReader(new FileReader(cgdFile));
		boolean inHeader= true;
		String line= reader.readLine();
		while (line != null) {
			if (inHeader) {
				inHeader= false;
			} else {
				newLines.add(line);
			}
			
			line= reader.readLine();
		}
		reader.close();
		
		// Overwrite all the new lines to the file
		BufferedWriter writer= new BufferedWriter(new FileWriter(cgdFile, false)); // do not append
		for (String l : newLines) {
			writer.write(l);
			writer.newLine();
		}
		writer.close();
	}
	
	/**
	 * Convert string list of integers into an int[].
	 * @param arr String list in the format "[1,2,3,4,5]
	 */
	private int[] getIntArrayFromString(String arr) {
		String[] items = arr.replaceAll("\\[", "").replaceAll("\\]", "").split("\\s?,\\s?");

		int[] results = new int[items.length];

		for (int i = 0; i < items.length; i++) {
			try {
				results[i] = Integer.parseInt(items[i]);
			} catch (NumberFormatException nfe) {};
		}
		
		return results;
	}
}