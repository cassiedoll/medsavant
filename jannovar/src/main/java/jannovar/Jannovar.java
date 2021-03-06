package jannovar;

/**
 * Command line functions from apache
 */
import jannovar.annotation.Annotation;
import jannovar.annotation.AnnotationList;
import jannovar.common.ChromosomeMap;
import jannovar.common.Constants;
import jannovar.common.Constants.Release;
import jannovar.exception.AnnotationException;
import jannovar.exception.FileDownloadException;
import jannovar.exception.InvalidAttributException;
import jannovar.exception.JannovarException;
import jannovar.exception.VCFParseException;
import jannovar.exome.Variant;
import jannovar.io.EnsemblFastaParser;
import jannovar.io.FastaParser;
import jannovar.io.GFFparser;
import jannovar.io.RefSeqFastaParser;
import jannovar.io.SerializationManager;
import jannovar.io.TranscriptDataDownloader;
import jannovar.io.UCSCKGParser;
import jannovar.io.VCFLine;
import jannovar.io.VCFReader;
import jannovar.reference.Chromosome;
import jannovar.reference.TranscriptModel;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.Parser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * This is the driver class for a program called Jannovar. It has two purposes
 * <OL>
 * <LI>Take the UCSC files knownGene.txt, kgXref.txt, knownGeneMrna.txt, and
 * knownToLocusLink.txt, and to create corresponding
 * {@link jannovar.reference.TranscriptModel TranscriptModel} objects and to
 * serialize them. The resulting serialized file can be used both by this
 * program itself (see next item) or by the main Exomizer program to annotated
 * VCF file.
 * <LI>Using the serialized file of {@link jannovar.reference.TranscriptModel
 * TranscriptModel} objects (see above item) annotate a VCF file using
 * annovar-type program logic. Note that this functionality is also used by the
 * main Exomizer program and thus this program can be used as a stand-alone
 * annotator ("Jannovar") or as a way of testing the code for the Exomizer.
 * </OL>
 * <P>
 * To run the "Jannovar" executable:
 * <P>
 * {@code java -Xms1G -Xmx1G -jar Jannovar.jar -V xyz.vcf -D $SERIAL}
 * <P>
 * This will annotate a VCF file. The results of jannovar annotation are shown
 * in the form
 *
 * <PRE>
 * Annotation {original VCF line}
 * </PRE>
 * <P>
 * Just a reminder, to set up annovar to do this, use the following commands.
 *
 * <PRE>
 *   perl annotate_variation.pl --downdb knownGene --buildver hg19 humandb/
 * </PRE>
 *
 * then, to annotate a VCF file called BLA.vcf, we first need to convert it to
 * Annovar input format and run the main annovar program as follows.
 *
 * <PRE>
 * $ perl convert2annovar.pl BLA.vcf -format vcf4 > BLA.av
 * $ perl annotate_variation.pl -buildver hg19 --geneanno BLA.av --dbtype knowngene humandb/
 * </PRE>
 *
 * This will create two files with all variants and a special file with exonic
 * variants.
 * <p>
 * There are three ways of using this program.
 * <ol>
 * <li>To create a serialized version of the UCSC gene definition data. In this
 * case, the command-line flag <b>- S</b> is provide as is the path to the four
 * UCSC files. Then, {@code anno.serialize()} is true and a file <b>ucsc.ser</b>
 * is created.
 * <li>To deserialize the serialized data (<b>ucsc.ser</b>). In this case, the
 * flag <b>- D</b> must be used.
 * <li>To simply read in the UCSC data without creating a serialized file.
 * </ol>
 * Whichever of the three versions is chosen, the user may additionally pass the
 * path to a VCF file using the <b>-v</b> flag. If so, then this file will be
 * annotated using the UCSC data, and a new version of the file will be written
 * to a file called test.vcf.jannovar (assuming the original file was named
 * test.vcf). The
 *
 * @author Peter N Robinson
 * @version 0.33 (29 December, 2013)
 */
public class Jannovar {
    private static final Log LOG = LogFactory.getLog(Jannovar.class.getName());
    
    /**
     * Location of a directory which will be used as download directory with
     * subfolders (by genome release e.g. hg19,mm9) in whichthe files defining
     * the transcript models will be stored. (the files may or may not be
     * compressed with gzip). The same variable is also used to indicate the
     * output location of the serialized file. The default value is "data/hg19/"
     */
    private String dirPath = null;
    /**
     * Flag to indicate that Jannovar should download known gene definitions
     * files from the UCSC server.
     */
    private boolean createUCSC;
    /**
     * Flag to indicate Jannovar should download transcript definition files for
     * RefSeq.
     */
    private boolean createRefseq;
    /**
     * Flag to indicate Jannovar should download transcript definition files for
     * Ensembl.
     */
    private boolean createEnsembl;
    /**
     * List of all lines from knownGene.txt file from UCSC
     */
    private ArrayList<TranscriptModel> transcriptModelList = null;
    /**
     * Map of Chromosomes
     */
    private HashMap<Byte, Chromosome> chromosomeMap = null;
    /**
     * List of variants from input file to be analysed.
     */
    private final ArrayList<Variant> variantList = null;
    /**
     * Name of the UCSC serialized data file that will be created by Jannovar.
     */
    private static final String UCSCserializationFileName = "ucsc_%s.ser";
    /**
     * Name of the Ensembl serialized data file that will be created by
     * Jannovar.
     */
    private static final String EnsemblSerializationFileName = "ensembl_%s.ser";
    /**
     * Name of the refSeq serialized data file that will be created by Jannovar.
     */
    private static final String RefseqSerializationFileName = "refseq_%s.ser";
    /**
     * Flag to indicate that Jannovar should serialize the UCSC data. This flag
     * is set to true automatically if the user enters --create-ucsc (then, the
     * four files are downloaded and subsequently serialized). If the user
     * enters the flag {@code -U path}, then Jannovar interprets path as the
     * location of a directory that already contains the UCSC files (either
     * compressed or uncompressed), and sets this flag to true to perform
     * serialization and then to exit. The name of the serialized file that gets
     * created is "ucsc.ser" (this cannot be changed from the command line, see
     * {@link #UCSCserializationFileName}).
     */
    private boolean performSerialization = false;
    /**
     * Name of file with serialized UCSC data. This should be the complete path
     * to the file, and will be used for annotating VCF files.
     */
    private String serializedFile = null;
    /**
     * Path to a VCF file waiting to be annotated.
     */
    private String VCFfilePath = null;
    /**
     * An FTP proxy for downloading the UCSC files from behind a firewall.
     */
    private String proxy = null;
    /**
     * An FTP proxy port for downloading the UCSC files from behind a firewall.
     */
    private String proxyPort = null;

    /**
     * Flag indicating whether to output annotations in Jannovar format
     * (default: false).
     */
    private boolean jannovarFormat;

    /**
     * Flag indication whether the annotations for all affected transcripts
     * should be reported.
     */
    private boolean showAll;

    /**
     * genome release for the download and the creation of the serialized
     * transcript model file
     */
    private Release genomeRelease = Release.HG19;
    /**
     * Output folder for the annotated VCF files (default: current folder)
     */
    private String outVCFfolder = null;

    /**
     * chromosomal position an NA change (e.g. chr1:12345C>A)
     */
    private String chromosomalChange;

    public static void main(String argv[]) throws JannovarException{
        Jannovar anno = new Jannovar(argv);
        /*
         * Option 1. Download the UCSC files from the server, create the
         * ucsc.ser file, and return.
         */
        try {
            if (anno.createUCSC()) {
                anno.downloadTranscriptFiles(jannovar.common.Constants.UCSC, anno.genomeRelease);
                anno.inputTranscriptModelDataFromUCSCFiles();
                anno.serializeUCSCdata();
                return;
            } else if (anno.createEnsembl()) {
                anno.downloadTranscriptFiles(jannovar.common.Constants.ENSEMBL, anno.genomeRelease);
                anno.inputTranscriptModelDataFromEnsembl();
                anno.serializeEnsemblData();
                return;
            } else if (anno.createRefseq()) {
                anno.downloadTranscriptFiles(jannovar.common.Constants.REFSEQ, anno.genomeRelease);
                anno.inputTranscriptModelDataFromRefseq();
                anno.serializeRefseqData();
                return;
            }
        } catch (JannovarException e) {
            LOG.error("[JANNOVAR ERROR] Error while attempting to download transcript definition files.");
            LOG.error("[JANNOVAR ERROR] " + e.toString());
            LOG.error("[JANNOVAR ERROR] A common error is the failure to set the network proxy (see tutorial).");
            throw e;
        }

        /*
         * Option 2. The user must provide the ucsc.ser file to do analysis. (or
         * the ensembl.ser or refseq.ser files). We can either annotate a VCF
         * file (3a) or create a separate annotation file (3b).
         */
        if (anno.deserialize()) {
            try {
                anno.deserializeTranscriptDefinitionFile();
            } catch (JannovarException je) {
                LOG.error("[JANNOVAR ERROR] Could not deserialize UCSC data: " + je.toString());
                throw je;
            }
        } else {
            LOG.error("[JANNOVAR ERROR] You need to pass ucscs.ser file to perform analysis.");
            usage();
            throw new JannovarException("Missing .ser file (ucscs.ser not passed?)");
        }
        /*
         * When we get here, the program has deserialized data and put it into
         * the Chromosome objects. We can now start to annotate variants.
         */
        if (anno.hasVCFfile()) {
            try {
                anno.annotateVCF(); /* 3a or 3b */

            } catch (JannovarException je) {
                LOG.error("[JANNOVAR ERROR] Could not annotate VCF data: " + je.toString());
                throw je;
            }
        } else {
            if (anno.chromosomalChange == null) {
                LOG.error("[JANNOVAR ERROR] No VCF file found and no chromosomal position and variation was found");
                throw new JannovarException("No VCF file found an no chromosomal position and variation was found");
            } else {
                try {
                    anno.annotatePosition();
                } catch (JannovarException je) {
                    LOG.error("[ERROR] Could not annotate input data: " + anno.chromosomalChange);                    
                    throw je;
                }

            }
        }
    }

    /**
     * The constructor parses the command-line arguments.
     *
     * @param argv the arguments passed through the command
     */
    public Jannovar(String argv[]) throws JannovarException{
        parseCommandLineArguments(argv);
        if (!this.dirPath.endsWith(System.getProperty("file.separator"))) {
            this.dirPath += System.getProperty("file.separator");
        }
        if (this.outVCFfolder != null && !this.outVCFfolder.endsWith(System.getProperty("file.separator"))) {
            this.outVCFfolder += System.getProperty("file.separator");
        }
    }

    /**
     * @return true if user wants to download UCSC files
     */
    public boolean createUCSC() {
        return this.createUCSC;
    }

    /**
     * @return true if user wants to download refseq files
     */
    public boolean createRefseq() {
        return this.createRefseq;
    }

    /**
     * @return true if user wants to download ENSEMBL files
     */
    public boolean createEnsembl() {
        return this.createEnsembl;
    }

    /**
     * This function creates a {@link TranscriptDataDownloader} object in order
     * to download the required transcript data files. If the user has set the
     * proxy and proxy port via the command line, we use these to download the
     * files.
     *
     * @param source the source of the transcript data (e.g. RefSeq, Ensembl,
     * UCSC)
     * @param rel the genome {@link Release}
     */
    public void downloadTranscriptFiles(int source, Release rel) throws JannovarException {
        TranscriptDataDownloader downloader;
        try {
            if (this.proxy != null && this.proxyPort != null) {
                downloader = new TranscriptDataDownloader(this.dirPath + genomeRelease.getUCSCString(genomeRelease), this.proxy, this.proxyPort);
            } else {
                downloader = new TranscriptDataDownloader(this.dirPath + genomeRelease.getUCSCString(genomeRelease));
            }
            downloader.downloadTranscriptFiles(source, rel);
        } catch (FileDownloadException e) {
            LOG.error(e);
            throw new JannovarException("File Download error: "+e.getMessage());                        
        }
    }

    /**
     * @return true if we should serialize the UCSC data.
     */
    public boolean serialize() {
        return this.performSerialization;
    }

    /**
     * @return true if we should deserialize a file with UCSC data to perform
     * analysis
     */
    public boolean deserialize() {
        return this.serializedFile != null;
    }

    /**
     * @return true if we should annotate a VCF file
     */
    public boolean hasVCFfile() {
        return this.VCFfilePath != null;
    }

    /**
     * Annotate a single line of a VCF file, and output the line together with
     * the new INFO fields representing the annotations.
     *
     * @param line an object representing the original VCF line
     * @param v the Variant object that was parsed from the line
     * @param out A file handle to write to.
     */
    private void annotateVCFLine(VCFLine line, Variant v, Writer out) throws IOException, AnnotationException, JannovarException {
        byte chr = v.getChromosomeAsByte();
        int pos = v.get_position();
        String ref = v.get_ref();
        String alt = v.get_alt();
        if (alt.charAt(0) == '[' || alt.charAt(0) == ']' || alt.equals(".")) {
            String[] a = line.getOriginalVCFLine().split("\t");

            Chromosome c = chromosomeMap.get(chr);
            if (a.length > 1 && c != null) {
                out.write(c.getChromosomeName());
                for (int i = 1; i < a.length; ++i) {
                    out.write("\t" + a[i]);
                }
                out.write("\n");
            } else {
                out.write(line.getOriginalVCFLine() + "\n");
            }
        } else {

            Chromosome c = chromosomeMap.get(chr);
            if (c == null) {
                String e = String.format("Could not identify chromosome \"%d\"", chr);
                throw new AnnotationException(e);
            }
            AnnotationList anno = c.getAnnotationList(pos, ref, alt);
            if (anno == null) {
                String e = String.format("No annotations found for variant %s", v.toString());
                throw new AnnotationException(e);
            }
            String annotation;
            String effect;
            if (this.showAll) {
                annotation = anno.getAllTranscriptAnnotations();
                effect = anno.getAllTranscriptVariantEffects();
            } else {
                annotation = anno.getSingleTranscriptAnnotation();
                effect = anno.getVariantType().toString();
            }
            String A[] = line.getOriginalVCFLine().split("\t");

            //ADDED BY Jim V, to make sure chromosomes are always formatted properly.
            out.write(c.getChromosomeName() + "\t");

            for (int i = 1; i < 7; ++i) //was from 0 to 7 prior to above addition
            {
                out.write(A[i] + "\t");
            }

            /* Now add the stuff to the INFO line */
            String INFO;
            /*
             * The if clause is necessary to avoid writing a final ";" if the
             * INFO lineis empty, which wouldbe invalid VCF format.
             */
            if (A[7].length() > 0) {
                INFO = String.format("EFFECT=%s;HGVS=%s;%s", effect, annotation, A[7]);
            } else {
                INFO = String.format("EFFECT=%s;HGVS=%s", effect, annotation, A[7]);
            }
            out.write(INFO + "\t");
            for (int i = 8; i < A.length; ++i) {
                out.write(A[i] + "\t");
            }
            out.write("\n");
        }
    }

    /**
     * This function outputs a single line in Jannovar format.
     *
     * @param n The current number (one for each variant in the VCF file)
     * @param v The current variant with one or more annotations
     * @param out File handle to write Jannovar file.
     */
    private void outputJannovarLine(int n, Variant v, Writer out) throws IOException, AnnotationException, JannovarException {
        byte chr = v.getChromosomeAsByte();
        String chrStr = v.get_chromosome_as_string();
        int pos = v.get_position();
        String ref = v.get_ref();
        String alt = v.get_alt();
        String gtype = v.getGenotypeAsString();
        float qual = v.getVariantPhredScore();
        Chromosome c = chromosomeMap.get(chr);
        if (c == null) {
            String e = String.format("Could not identify chromosome \"%d\"", chr);
            throw new AnnotationException(e);
        }
        AnnotationList anno = c.getAnnotationList(pos, ref, alt);
        if (anno == null) {
            String e = String.format("No annotations found for variant %s", v.toString());
            throw new AnnotationException(e);
        }

        ArrayList<Annotation> lst = anno.getAnnotationList();
        for (Annotation a : lst) {
            String effect = a.getVariantTypeAsString();
            String annt = a.getVariantAnnotation();
            String sym = a.getGeneSymbol();
            String s = String.format("%d\t%s\t%s\t%s\t%s\t%d\t%s\t%s\t%s\t%.1f", n, effect, sym, annt, chrStr, pos, ref, alt, gtype, qual);
            out.write(s + "\n");
        }
    }

    /**
     * This function outputs a VCF file that corresponds to the original VCF
     * file but additionally has annotations for each variant. A new file is
     * created with the suffix "jv.vcf";
     */
    private void outputAnnotatedVCF(VCFReader parser) throws JannovarException {
        File f = new File(this.VCFfilePath);
        String outname = f.getName();
        if (outVCFfolder != null) {
            outname = outVCFfolder + outname;
        }
        int i = outname.lastIndexOf("vcf");
        if (i < 0) {
            i = outname.lastIndexOf("VCF");
        }
        if (i < 0) {
            outname = outname + ".jv.vcf";
        } else {
            outname = outname.substring(0, i) + "jv.vcf";
        }
        try {
            FileWriter fstream = new FileWriter(outname);
            BufferedWriter out = new BufferedWriter(fstream);
            /**
             * Write the header of the new VCF file
             */
            ArrayList<String> lst = parser.getAnnotatedVCFHeader();

            for (String s : lst) {
                out.write(s + "\n");
            }
            /**
             * Now write each of the variants.
             */
            Iterator<VCFLine> iter = parser.getVCFLineIterator();
            while (iter.hasNext()) {
                VCFLine line = iter.next();
                Variant v = line.toVariant();
                try {
                    annotateVCFLine(line, v, out);
                } catch (AnnotationException e) {
                    LOG.info("[WARN] Annotation error: " + e.toString());
                }
            }
            out.close();
        } catch (IOException e) {
            String msg = "Error writing annotated VCF file: "+e.toString();
            LOG.error(msg);            
            throw new JannovarException(msg);
        }
        LOG.info("Wrote annotated VCF file to \"" + outname + "\"");
    }

    /**
     * This function writes detailed annotations to file. One annotation is
     * written for each of the transcripts affected by a variant, and the file
     * is a tab-separated file in "Jannovar" format.
     *
     * @param parser The VCFParser that has extracted a list of variants from
     * the VCF file.
     */
    private void outputJannovarFormatFile(VCFReader parser) throws JannovarException {
        File f = new File(this.VCFfilePath);
        String outname = f.getName() + ".jannovar";
        try {
            FileWriter fstream = new FileWriter(outname);
            BufferedWriter out = new BufferedWriter(fstream);
            /**
             * Output each of the variants.
             */
            int n = 0;
            Iterator<Variant> iter = parser.getVariantIterator();
            while (iter.hasNext()) {
                n++;
                Variant v = iter.next();
                try {
                    outputJannovarLine(n, v, out);
                } catch (AnnotationException e) {
                    System.out.println("[WARN] Annotation error: " + e.toString());
                }
            }
            out.close();
        } catch (IOException e) {
            LOG.error("[ERROR] Error writing annotated VCF file");
            LOG.error("[ERROR] " + e.toString());
            throw new JannovarException("Error writing annotated VCF file: "+e.getMessage());
        }
        LOG.info("[INFO] Wrote annotations to \"" + outname + "\"");
    }

    /**
     * THis function will simply annotate given chromosomal position with HGVS
     * compliant output e.g. chr1:909238G>C -->
     * PLEKHN1:NM_032129.2:c.1460G>C,p.(Arg487Pro)
     *
     * @throws AnnotationException
     */
    private void annotatePosition() throws AnnotationException, JannovarException {
        //("input: " + this.chromosomalChange);
        Pattern pat = Pattern.compile("(chr[0-9MXY]+):([0-9]+)([ACGTN])>([ACGTN])");
        Matcher mat = pat.matcher(this.chromosomalChange);

        if (!mat.matches() | mat.groupCount() != 4) {
            LOG.error("[ERROR] Input string for the chromosomal change does not fit the regular expression ... :(");
            throw new JannovarException("Input string for the chromosomal change does not fit the regular expression...:(");
            
        }

        byte chr = ChromosomeMap.identifier2chromosom.get(mat.group(1));
        int pos = Integer.parseInt(mat.group(2));
        String ref = mat.group(3);
        String alt = mat.group(4);

        Chromosome c = chromosomeMap.get(chr);
        if (c == null) {
            String e = String.format("Could not identify chromosome \"%d\"", chr);
            throw new AnnotationException(e);
        }
        AnnotationList anno = c.getAnnotationList(pos, ref, alt);
        if (anno == null) {
            String e = String.format("No annotations found for variant %s", this.chromosomalChange);
            throw new AnnotationException(e);
        }
        String annotation;
        String effect;
        if (this.showAll) {
            annotation = anno.getAllTranscriptAnnotations();
            effect = anno.getAllTranscriptVariantEffects();
        } else {
            annotation = anno.getSingleTranscriptAnnotation();
            effect = anno.getVariantType().toString();
        }

        LOG.info(String.format("EFFECT=%s;HGVS=%s", effect, annotation));

    }

    /**
     * This function inputs a VCF file, and prints the annotated version thereof
     * to a file (name of the original file with the suffix .jannovar).
     *
     * @throws jannovar.exception.JannovarException
     */
    public void annotateVCF() throws JannovarException {
        VCFReader parser = new VCFReader(this.VCFfilePath);
        VCFLine.setStoreVCFLines();
        try {
            parser.inputVCFheader();
        } catch (VCFParseException e) {
            LOG.error("[ERROR] Unable to parse VCF file");
            LOG.error(e.toString());
            throw new JannovarException(e.getMessage());
        }
        if (this.jannovarFormat) {
            outputJannovarFormatFile(parser);
        } else {
            outputAnnotatedVCF(parser);
        }
    }

    /**
     * Inputs the GFF data from RefSeq files, convert the resulting
     * {@link jannovar.reference.TranscriptModel TranscriptModel} objects to
     * {@link jannovar.interval.Interval Interval} objects, and store these in a
     * serialized file.
     *
     * @throws JannovarException
     */
    public void serializeRefseqData() throws JannovarException {
        SerializationManager manager = new SerializationManager();
        LOG.info("Serializing RefSeq data as " + String.format(dirPath + Jannovar.RefseqSerializationFileName, genomeRelease.getUCSCString(genomeRelease)));
        manager.serializeKnownGeneList(String.format(dirPath + Jannovar.RefseqSerializationFileName, genomeRelease.getUCSCString(genomeRelease)), this.transcriptModelList);
    }

    /**
     * Inputs the GFF data from Ensembl files, convert the resulting
     * {@link jannovar.reference.TranscriptModel TranscriptModel} objects to
     * {@link jannovar.interval.Interval Interval} objects, and store these in a
     * serialized file.
     *
     * @throws jannovar.exception.JannovarException
     */
    public void serializeEnsemblData() throws JannovarException {
        SerializationManager manager = new SerializationManager();
        LOG.info("Serializing Ensembl data as " + String.format(dirPath + Jannovar.EnsemblSerializationFileName, genomeRelease.getUCSCString(genomeRelease)));
        manager.serializeKnownGeneList(String.format(dirPath + Jannovar.EnsemblSerializationFileName, genomeRelease.getUCSCString(genomeRelease)), this.transcriptModelList);
    }

    /**
     * Inputs the KnownGenes data from UCSC files, convert the resulting
     * {@link jannovar.reference.TranscriptModel TranscriptModel} objects to
     * {@link jannovar.interval.Interval Interval} objects, and store these in a
     * serialized file.
     *
     * @throws jannovar.exception.JannovarException
     */
    public void serializeUCSCdata() throws JannovarException {
        SerializationManager manager = new SerializationManager();
        LOG.info("Serializing UCSC data as " + String.format(dirPath + Jannovar.UCSCserializationFileName, genomeRelease.getUCSCString(genomeRelease)));
        manager.serializeKnownGeneList(String.format(dirPath + Jannovar.UCSCserializationFileName, genomeRelease.getUCSCString(genomeRelease)), this.transcriptModelList);
    }

    /**
     * To run Jannovar, the user must pass a transcript definition file with the
     * -D flag. This can be one of the files ucsc.ser, ensembl.ser, or
     * refseq.ser (or a comparable file) containing a serialized version of the
     * TranscriptModel objects created to contain info about the transcript
     * definitions (exon positions etc.) extracted from UCSC, Ensembl, or Refseq
     * and necessary for annotation.
     *
     * @throws JannovarException
     */
    public void deserializeTranscriptDefinitionFile() throws JannovarException {
        ArrayList<TranscriptModel> kgList;
        SerializationManager manager = new SerializationManager();
        kgList = manager.deserializeKnownGeneList(this.serializedFile);
        this.chromosomeMap = Chromosome.constructChromosomeMapWithIntervalTree(kgList);
    }

    /**
     * Input the RefSeq data.
     */
    private void inputTranscriptModelDataFromRefseq() throws JannovarException{
        // parse GFF/GTF
        GFFparser gff = new GFFparser();
        String path = this.dirPath + genomeRelease.getUCSCString(genomeRelease);
        if (!path.endsWith(System.getProperty("file.separator"))) {
            path += System.getProperty("file.separator");
        }
        switch (this.genomeRelease) {
            case MM9:
                gff.parse(path + Constants.refseq_gff_mm9);
                break;
            case MM10:
                gff.parse(path + Constants.refseq_gff_mm10);
                break;
            case HG18:
                gff.parse(path + Constants.refseq_gff_hg18);
                break;
            case HG19:
                gff.parse(path + Constants.refseq_gff_hg19);
                break;
            case HG38:
                gff.parse(path + Constants.refseq_gff_hg38);
                break;
            default:
                LOG.error("Unknown release: " + genomeRelease);
                throw new JannovarException("Unknown release: "+genomeRelease);                
        }
        try {
            this.transcriptModelList = gff.getTranscriptModelBuilder().buildTranscriptModels();
        } catch (InvalidAttributException e) {
            LOG.error("Unable to input data from the Refseq files", e);            
            throw new JannovarException(e.getMessage());
        }
        // add sequences
        FastaParser efp = new RefSeqFastaParser(path + Constants.refseq_rna, transcriptModelList);
        int before = transcriptModelList.size();
        transcriptModelList = efp.parse();
        int after = transcriptModelList.size();
        // System.out.println(String.format("[INFO] removed %d (%d --> %d) transcript models w/o rna sequence",
        // before-after,before, after));

        LOG.info(String.format("Found %d transcript models from Refseq GFF resource, %d of which had sequences", before, after));
    }

    /**
     * Input the Ensembl data.
     */
    private void inputTranscriptModelDataFromEnsembl() throws JannovarException{
        // parse GFF/GTF

        GFFparser gff = new GFFparser();
        String path;
        path = this.dirPath + genomeRelease.getUCSCString(genomeRelease);
        if (!path.endsWith(System.getProperty("file.separator"))) {
            path += System.getProperty("file.separator");
        }
        switch (this.genomeRelease) {
            case MM9:
                path += Constants.ensembl_mm9;
                break;
            case MM10:
                path += Constants.ensembl_mm10;
                break;
            case HG18:
                path += Constants.ensembl_hg18;
                break;
            case HG19:
                path += Constants.ensembl_hg19;
                break;
            default:
                String msg = "[ERROR] Unknown release: " + genomeRelease;
                LOG.info(msg);
                throw new JannovarException(msg);                                
        }
        gff.parse(path + Constants.ensembl_gtf);
        try {
            this.transcriptModelList = gff.getTranscriptModelBuilder().buildTranscriptModels();
            // System.out.println("[INFO] Got: "+this.transcriptModelList.size()
            // + " Ensembl transcripts");
        } catch (InvalidAttributException e) {
            LOG.error("Unable to input data from the Ensembl files", e);            
            throw new JannovarException(e.getMessage());
        }
        // add sequences
        EnsemblFastaParser efp = new EnsemblFastaParser(path + Constants.ensembl_cdna, transcriptModelList);
        int before = transcriptModelList.size();
        transcriptModelList = efp.parse();
        int after = transcriptModelList.size();
        // System.out.println(String.format("[INFO] removed %d (%d --> %d) transcript models w/o rna sequence",
        // before-after,before, after));

        LOG.info(String.format("Found %d transcript models from Ensembl GFF resource, %d of which had sequences", before, after));
    }

    /**
     * Input the four UCSC files for the KnownGene data.
     */
    private void inputTranscriptModelDataFromUCSCFiles() throws JannovarException{
        String path = this.dirPath + genomeRelease.getUCSCString(genomeRelease);
        if (!path.endsWith(System.getProperty("file.separator"))) {
            path += System.getProperty("file.separator");
        }
        UCSCKGParser parser = new UCSCKGParser(path);
        try {
            parser.parseUCSCFiles();
        } catch (Exception e) {
            LOG.error("Unable to input data from the UCSC files",e);
            throw new JannovarException(e.getMessage());            
        }
        this.transcriptModelList = parser.getKnownGeneList();
    }

    /**
     * A simple printout of the chromosome map for debugging purposes.
     */
    public void debugShowChromosomeMap() {
        for (Byte c : chromosomeMap.keySet()) {
            Chromosome chromo = chromosomeMap.get(c);
            //System.out.println("Chrom. " + c + ": " + chromo.getNumberOfGenes() + " genes");
        }
    }

    /**
     * Parse the command line.
     *
     * @param args Copy of the command line arguments.
     */
    private void parseCommandLineArguments(String[] args) throws JannovarException{
        try {
            Options options = new Options();
            options.addOption(new Option("h", "help", false, "Shows this help"));
            options.addOption(new Option("U", "downloaded-data", true, "Path to directory with previously downloaded transcript definition files."));
            options.addOption(new Option("S", "serialize", false, "Serialize"));
            options.addOption(new Option("D", "deserialize", true, "Path to serialized file with UCSC data"));
            options.addOption(new Option("d", "data", true, "Path to write data storage folder (genome files, serialized files, ...)"));
            options.addOption(new Option("O", "output", true, "Path to output folder for the annotated VCF file"));
            options.addOption(new Option("V", "vcf", true, "Path to VCF file"));
            options.addOption(new Option("a", "showall", false, "report annotations for all transcripts to VCF file"));
            options.addOption(new Option("P", "position", true, "chromosomal position to HGVS (e.g. chr1:909238G>C)"));
            options.addOption(new Option("J", "janno", false, "Output Jannovar format"));
            options.addOption(new Option("g", "genome", true, "genome build (mm9, mm10, hg18, hg19, hg38 - only refseq), default hg19"));
            options.addOption(new Option(null, "create-ucsc", false, "Create UCSC definition file"));
            options.addOption(new Option(null, "create-refseq", false, "Create RefSeq definition file"));
            options.addOption(new Option(null, "create-ensembl", false, "Create Ensembl definition file"));
            options.addOption(new Option(null, "proxy", true, "FTP Proxy"));
            options.addOption(new Option(null, "proxy-port", true, "FTP Proxy Port"));

            Parser parser = new GnuParser();
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption("h") || cmd.hasOption("H") || args.length == 0) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("java -jar Jannovar.jar [-options]", options);
                usage();       
                throw new JannovarException("Invalid command line");
                
            }
            this.jannovarFormat = cmd.hasOption("J");
            this.showAll = cmd.hasOption('a');

            if (cmd.hasOption("create-ucsc")) {
                this.createUCSC = true;
                this.performSerialization = true;
            } else {
                this.createUCSC = false;
            }

            if (cmd.hasOption("create-refseq")) {
                this.createRefseq = true;
                this.performSerialization = true;
            } else {
                this.createRefseq = false;
            }

            if (cmd.hasOption("create-ensembl")) {
                this.createEnsembl = true;
                this.performSerialization = true;
            } else {
                this.createEnsembl = false;
            }

            // path to the data storage
            if (cmd.hasOption('d')) {
                this.dirPath = cmd.getOptionValue('d');
            } else {
                this.dirPath = Constants.DEFAULT_DATA;
            }
            if (!this.dirPath.endsWith(System.getProperty("file.separator"))) {
                this.dirPath += System.getProperty("file.separator");
            }

            if (cmd.hasOption("genome")) {
                String g = cmd.getOptionValue("genome");
                if (g.equals("mm9")) {
                    this.genomeRelease = Release.MM9;
                }
                if (g.equals("mm10")) {
                    this.genomeRelease = Release.MM10;
                }
                if (g.equals("hg18")) {
                    this.genomeRelease = Release.HG18;
                }
                if (g.equals("hg19")) {
                    this.genomeRelease = Release.HG19;
                }
                if (g.equals("hg38")) {
                    if (this.createRefseq) {
                        this.genomeRelease = Release.HG38;
                    } else {
                        LOG.error("Genome release hg38 only available for Refseq");
                        throw new JannovarException("Genome release hg38 only available for Refseq");
                    }
                }
            } else {
                if (performSerialization) {
                    LOG.info("Genome release set to default: hg19");
                    throw new JannovarException("Genome relase set to default: hg19");
                }
                this.genomeRelease = Release.HG19;
            }

            if (cmd.hasOption('O')) {
                outVCFfolder = cmd.getOptionValue('O');
                File file = new File(outVCFfolder);
                if (!file.exists()) {
                    file.mkdirs();
                }
            }

            if (cmd.hasOption('S')) {
                this.performSerialization = true;
            }

            if (cmd.hasOption("proxy")) {
                this.proxy = cmd.getOptionValue("proxy");
            }

            if (cmd.hasOption("proxy-port")) {
                this.proxyPort = cmd.getOptionValue("proxy-port");
            }

            if (cmd.hasOption("U")) {
                this.dirPath = getRequiredOptionValue(cmd, 'U');
            }

            if (cmd.hasOption('D')) {
                this.serializedFile = cmd.getOptionValue('D');
            }

            if (cmd.hasOption("V")) {
                this.VCFfilePath = cmd.getOptionValue("V");
            }

            if (cmd.hasOption("P")) {
                this.chromosomalChange = cmd.getOptionValue("P");
            }

        } catch (ParseException pe) {
            LOG.error("Error parsing command line options");
            LOG.error(pe.getMessage());
            throw new JannovarException("Error parsing command line options: "+pe.getMessage());
        }
    }

    /**
     * This function is used to ensure that certain options are passed to the
     * program before we start execution.
     *
     * @param cmd An apache CommandLine object that stores the command line
     * arguments
     * @param name Name of the argument that must be present
     * @return Value of the required option as a String.
     */
    private static String getRequiredOptionValue(CommandLine cmd, char name) throws JannovarException{
        String val = cmd.getOptionValue(name);
        if (val == null) {
            String str = "Aborting because the required argument \"-" + name + "\" wasn't specified! Use the -h for more help.";
            LOG.error(str);
            throw new JannovarException(str);            
        }
        return val;
    }

    private static void usage() {
        LOG.info("***   Jannovar: Usage     ****");
        LOG.info("Use case 1: Download UCSC data and create transcript data file (ucsc_hg19.ser)");
        LOG.info("$ java -jar Jannovar.jar --create-ucsc [-U name of output directory]");
        LOG.info("Use case 2: Add annotations to a VCF file");
        LOG.info("$ java -jar Jannovar.jar -D ucsc_hg19.ser -V example.vcf");
        LOG.info("Use case 3: Write new file with Jannovar-format annotations of a VCF file");
        LOG.info("$ java -jar Jannovar -D ucsc_hg19.ser -V vcfPath -J");
        LOG.info("*** See the tutorial for details ***");
    }

}
/* eof */
