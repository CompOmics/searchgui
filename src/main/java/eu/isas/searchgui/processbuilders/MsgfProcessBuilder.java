package eu.isas.searchgui.processbuilders;

import com.compomics.software.cli.CommandLineUtils;
import com.compomics.software.CompomicsWrapper;
import com.compomics.util.exceptions.ExceptionHandler;
import com.compomics.util.experiment.biology.aminoacids.sequence.AminoAcidPattern;
import com.compomics.util.experiment.biology.enzymes.Enzyme;
import com.compomics.util.experiment.biology.enzymes.EnzymeFactory;
import com.compomics.util.experiment.biology.modifications.Modification;
import com.compomics.util.experiment.biology.modifications.ModificationFactory;
import com.compomics.util.experiment.identification.Advocate;
import com.compomics.util.parameters.identification.search.DigestionParameters;
import com.compomics.util.parameters.identification.search.SearchParameters;
import com.compomics.util.parameters.identification.tool_specific.MsgfParameters;
import com.compomics.util.parameters.UtilitiesUserParameters;
import com.compomics.util.pride.CvTerm;
import com.compomics.util.waiting.WaitingHandler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class will set up and start a process to perform an MS-GF+ search.
 *
 * @author Harald Barsnes
 * @author Marc Vaudel
 */
public class MsgfProcessBuilder extends SearchGUIProcessBuilder {

    /**
     * The MS-GF+ modifications file.
     */
    private File msgfModFile;
    /**
     * The MS-GF+ enzymes file.
     */
    private File msgfEnzymesFile;
    /**
     * The temp folder for MS-GF+ files.
     */
    private File msgfTempFolder;
    /**
     * The MS-GF+ enzyme map. Key: utilities enzyme name, element: ms-gf+ index.
     */
    private HashMap<String, Integer> enzymeMap = new HashMap<>();
    /**
     * The post-translational modifications factory.
     */
    private ModificationFactory modificationFactory = ModificationFactory.getInstance();
    /**
     * The modification file for MS-GF+.
     */
    private final String MOD_FILE = "Mods.txt";
    /**
     * The enzymes file for MS-GF+.
     */
    private final String ENZYMES_FILE = "enzymes.txt";
    /**
     * The name of the folder where the parameters are located. Assumed to be in
     * the MS-GF+ installation folder.
     */
    private final String PARAMS_FOLDER_NAME = "params";
    /**
     * The name of the MS-GF+ executable.
     */
    public final static String EXECUTABLE_FILE_NAME = "MSGFPlus.jar";
    /**
     * The spectrum file to search.
     */
    private File spectrumFile;
    /**
     * The MS-GF+ parameters.
     */
    private MsgfParameters msgfParameters;
    /**
     * The search parameters.
     */
    private SearchParameters searchParameters;

    /**
     * Constructor.
     *
     * @param msgfDirectory directory location of MSGFPlus.jar
     * @param msgfTempFolder the temp folder for MSGF+
     * @param mgfFile the file containing the spectra
     * @param fastaFile the FASTA file
     * @param outputFile the output file
     * @param searchParameters the search parameters
     * @param waitingHandler the waiting handler
     * @param exceptionHandler the handler of exceptions
     * @param nThreads the number of threads to use
     * @param isCommandLine true if run from the command line, false if GUI
     *
     * @throws java.io.IOException exception thrown whenever an error occurred
     * while getting the Java home
     * @throws java.lang.ClassNotFoundException exception thrown whenever an
     * error occurred while getting the SearchGUI path
     */
    public MsgfProcessBuilder(
            File msgfDirectory,
            File msgfTempFolder,
            File mgfFile,
            File fastaFile,
            File outputFile,
            SearchParameters searchParameters,
            WaitingHandler waitingHandler,
            ExceptionHandler exceptionHandler,
            int nThreads,
            boolean isCommandLine
    ) throws IOException, ClassNotFoundException {

        this.searchParameters = searchParameters;
        msgfParameters = (MsgfParameters) searchParameters.getIdentificationAlgorithmParameter(Advocate.msgf.getIndex());

        this.waitingHandler = waitingHandler;
        this.exceptionHandler = exceptionHandler;
        this.msgfTempFolder = msgfTempFolder;
        this.spectrumFile = mgfFile;

        // create the temp folder if it does not exist
        if (!msgfTempFolder.exists()) {
            msgfTempFolder.mkdirs();
        }

        // make sure that the msgf+ jar file is executable
        File msgfExecutable = new File(msgfDirectory.getAbsolutePath() + File.separator + EXECUTABLE_FILE_NAME);
        msgfExecutable.setExecutable(true);

        // create the ms-gf+ params folder
        File msgfParamsFolder = new File(msgfTempFolder, "params");
        msgfParamsFolder.mkdir();

        // create the ms-gf+ modification file
        msgfModFile = new File(msgfParamsFolder, MOD_FILE);
        createModificationsFile();

        // create ms-gf+ enzyme file
        msgfEnzymesFile = new File(msgfParamsFolder, ENZYMES_FILE);
        createEnzymesFile();

        // set java home
        UtilitiesUserParameters utilitiesUserParameters = UtilitiesUserParameters.loadUserParameters();
        CompomicsWrapper wrapper = new CompomicsWrapper();
        ArrayList<String> javaHomeAndOptions = wrapper.getJavaHomeAndOptions(utilitiesUserParameters.getSearchGuiPath());
        process_name_array.add(javaHomeAndOptions.get(0)); // set java home

        // set java options
        if (!isCommandLine) {
            for (int i = 1; i < javaHomeAndOptions.size(); i++) {
                process_name_array.add(javaHomeAndOptions.get(i));
            }
        } else {
            // add the jvm arguments for searchgui to ms-gf+
            RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
            List<String> aList = bean.getInputArguments();
            for (String element : aList) {
                process_name_array.add(element);
            }
        }

        // add the MSGFPlus.jar
        process_name_array.add("-jar");
        process_name_array.add(CommandLineUtils.getCommandLineArgument(new File(msgfDirectory, EXECUTABLE_FILE_NAME)));

        // add the spectrum file
        process_name_array.add("-s");
        process_name_array.add(CommandLineUtils.getCommandLineArgument(spectrumFile));

        // add the database
        process_name_array.add("-d");
        process_name_array.add(CommandLineUtils.getCommandLineArgument(fastaFile));

        // set the output file
        process_name_array.add("-o");
        process_name_array.add(CommandLineUtils.getCommandLineArgument(outputFile));

        // set the precursor mass tolerance
        Double precursorMassError = searchParameters.getPrecursorAccuracy();
        String precursorMassErrorUnit = "ppm";
        if (searchParameters.getPrecursorAccuracyType() == SearchParameters.MassAccuracyType.DA) {
            precursorMassErrorUnit = "Da";
        }
        process_name_array.add("-t");
        process_name_array.add(precursorMassError + precursorMassErrorUnit);

        // enable/disable the msgf+ decoy search
        process_name_array.add("-tda");
        if (msgfParameters.searchDecoyDatabase()) {
            process_name_array.add("1");
        } else {
            process_name_array.add("0");
        }

        // link to the msgf+ modifications file
        process_name_array.add("-mod");
        process_name_array.add(CommandLineUtils.getCommandLineArgument(msgfModFile));

        // max variable modifications per peptide
        process_name_array.add("-numMods");
        process_name_array.add("" + msgfParameters.getNumberOfModificationsPerPeptide());

        // add min/max precursor charge
        process_name_array.add("-minCharge");
        process_name_array.add("" + searchParameters.getMinChargeSearched());
        process_name_array.add("-maxCharge");
        process_name_array.add("" + searchParameters.getMaxChargeSearched());

        // set the instrument type
        process_name_array.add("-inst");
        process_name_array.add("" + msgfParameters.getInstrumentID());

        // set the number of threads to use
        process_name_array.add("-thread");
        process_name_array.add("" + nThreads);

        // set the number of tasks
        if (msgfParameters.getNumberOfTasks() != null) {
            process_name_array.add("-tasks");
            process_name_array.add("" + msgfParameters.getNumberOfTasks());
        }

        // set the fragmentation method
        process_name_array.add("-m");
        process_name_array.add("" + msgfParameters.getFragmentationType());

        // set the enzyme
        Integer msgfEnzyme = getEnzymeMapping(searchParameters.getDigestionParameters());
        if (msgfEnzyme != null) {
            process_name_array.add("-e");
            process_name_array.add(msgfEnzyme.toString());
        }

        // set the number of tolerable termini
        process_name_array.add("-ntt");
        process_name_array.add("" + msgfParameters.getNumberTolerableTermini());

        // set the protocol
        process_name_array.add("-protocol");
        process_name_array.add("" + msgfParameters.getProtocol());

        // set the min/max peptide lengths
        process_name_array.add("-minLength");
        process_name_array.add("" + msgfParameters.getMinPeptideLength());
        process_name_array.add("-maxLength");
        process_name_array.add("" + msgfParameters.getMaxPeptideLength());

        // set the number of matches per spectrum
        process_name_array.add("-n");
        process_name_array.add("" + msgfParameters.getNumberOfSpectrumMatches());

        // allow inclusion of spectra with high-density centroid data
//        process_name_array.add("-allowDenseCentroidedPeaks");
//        if (msgfParameters.getAllowDenseCentroidedPeaks()) {
//            process_name_array.add("1");
//        } else {
//            process_name_array.add("0");
//        }
        
        // provide additional output
        process_name_array.add("-addFeatures");
        if (msgfParameters.isAdditionalOutput()) {
            process_name_array.add("1");
        } else {
            process_name_array.add("0");
        }

        // set mass of charge carrier, default: mass of proton (1.00727649)
        //process_name_array.add("-ccm");
        //process_name_array.add("1.00727649"); // @TODO: implement?
        // set the maximum missed cleavages
        DigestionParameters digestionPreferences = searchParameters.getDigestionParameters();
        if (digestionPreferences.getCleavageParameter() == DigestionParameters.CleavageParameter.enzyme) {

            Integer missedCleavages = null;
            for (Enzyme enzyme : digestionPreferences.getEnzymes()) {
                int enzymeMissedCleavages = digestionPreferences.getnMissedCleavages(enzyme.getName());
                if (missedCleavages == null || enzymeMissedCleavages > missedCleavages) {
                    missedCleavages = enzymeMissedCleavages;
                }
            }

            if (missedCleavages != null) {
                process_name_array.add("-maxMissedCleavages");
                process_name_array.add("" + missedCleavages);
            }
        }

        // set the range of allowed isotope peak errors
        process_name_array.add("-ti");
        process_name_array.add(CommandLineUtils.getQuoteType()
                + searchParameters.getMinIsotopicCorrection()
                + "," + searchParameters.getMaxIsotopicCorrection()
                + CommandLineUtils.getQuoteType());

        // set the report level
        //process_name_array.add("-verbose");
        //process_name_array.add("1"); // @TODO: implement per-thread progress? i.e. set to 0
        process_name_array.trimToSize();

        // print the command to the log file
        System.out.println(System.getProperty("line.separator") + System.getProperty("line.separator") + "ms-gf+ command: ");

        for (Object element : process_name_array) {
            System.out.print(element + " ");
        }

        System.out.println(System.getProperty("line.separator"));

        pb = new ProcessBuilder(process_name_array);

        pb.directory(msgfTempFolder);
        // set error out and std out to same stream
        pb.redirectErrorStream(true);
    }

    /**
     * Creates the MS-GF+ modifications file.
     *
     * @throws IOException if the modification file could not be created
     */
    private void createModificationsFile() throws IOException {

        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(msgfModFile));
            try {
                // add the number of modifications per peptide
                bw.write("#max number of modifications per peptide\n");
                bw.write("NumMods=" + msgfParameters.getNumberOfModificationsPerPeptide() + "\n\n");

                // add the fixed modifications
                bw.write("#fixed modifications\n");
                ArrayList<String> fixedModifications = searchParameters.getModificationParameters().getFixedModifications();

                for (String modName : fixedModifications) {

                    bw.write(getModificationFormattedForMsgf(modName, true) + "\n");

                }
                bw.write("\n");

                // add the variable modifications
                bw.write("#variable modifications\n");
                ArrayList<String> variableModifications = searchParameters.getModificationParameters().getVariableModifications();

                for (String modName : variableModifications) {

                    bw.write(getModificationFormattedForMsgf(modName, false) + "\n");

                }

            } finally {
                bw.close();
            }
        } catch (IOException ioe) {

            throw new IllegalArgumentException(
                    "Could not create MS-GF+ modifications file. Unable to write file: '"
                    + ioe.getMessage()
                    + "'."
            );

        }
    }

    /**
     * Creates the MS-GF+ enzymes file.
     *
     * @throws IOException if the enzymes file could not be written
     */
    private void createEnzymesFile() throws IOException {

        // Format: ShortName,CleaveAt,Terminus
        // - ShortName: an unique short name of the enzyme (e.g. Tryp). No space is allowed.
        // - CleaveAt: the residues cleaved by the enzyme (e.g. KR). Put "null" in case of no specificy.
        // - Terminus: Whether the enzyme cleaves C-term (C) or N-term (N)
        // - Description: description of the enzyme
        // Example: Tryp,KR,C,Trypsin
        enzymeMap = new HashMap<>();

        try {

            BufferedWriter bw = new BufferedWriter(new FileWriter(msgfEnzymesFile));

            try {

                EnzymeFactory enzymeFactory = EnzymeFactory.getInstance();
                int enzymeCounter = 10; // as there are ten default ms-gf+ enzymes

                for (Enzyme enzyme : enzymeFactory.getEnzymes()) {

                    String enzymeName = enzyme.getName();
                    Integer enzymeIndex = getEnzymeMapping(enzyme);

                    if (enzymeIndex == null) {

                        String cleavageType;
                        String cleavageSite = "";

                        if (!enzyme.getAminoAcidBefore().isEmpty()) {
                            cleavageType = "C";
                            for (Character character : enzyme.getAminoAcidBefore()) {
                                cleavageSite += character;
                            }
                        } else {
                            cleavageType = "N";
                            for (Character character : enzyme.getAminoAcidAfter()) {
                                cleavageSite += character;
                            }
                        }

                        String nameWithoutComma = enzymeName;
                        nameWithoutComma = nameWithoutComma.replaceAll(",", "");
                        String nameWithoutCommaAndSpaces = nameWithoutComma.replaceAll(" ", "_");

                        bw.write(nameWithoutCommaAndSpaces + ",");
                        bw.write(cleavageSite + ",");
                        bw.write(cleavageType + ",");
                        bw.write(nameWithoutComma + System.getProperty("line.separator"));

                        enzymeMap.put(enzymeName, enzymeCounter++);
                    }
                }
            } finally {
                bw.close();
            }
        } catch (IOException ioe) {
            throw new IOException("Could not create MS-GF+ enzymes file. Unable to write file: '" + ioe.getMessage() + "'.");
        }
    }

    /**
     * Get the given modification as a string in the MS-GF+ format.
     *
     * @param modName the utilities name of the modification
     * @param fixed if the modification is fixed or not
     * @return the given modification as a string in the MS-GF+ format
     */
    private String getModificationFormattedForMsgf(String modName, boolean fixed) {

        Modification modification = modificationFactory.getModification(modName);

        // get the targeted amino acids
        String aminoAcidsAtTarget = "";
        AminoAcidPattern aminoAcidPattern = modification.getPattern();

        if (aminoAcidPattern != null) {

            for (Character aa : modification.getPattern().getAminoAcidsAtTarget()) {

                aminoAcidsAtTarget += aa;

            }
        }

        if (aminoAcidsAtTarget.length() == 0) {

            aminoAcidsAtTarget = "*";

        }

        // get the type of the modification
        String position = "";
        switch (modification.getModificationType()) {
            case modaa:
                position = "any";
                break;
            case modc_protein:
            case modcaa_protein:
                position = "Prot-C-term";
                break;
            case modc_peptide:
            case modcaa_peptide:
                position = "C-term";
                break;
            case modn_protein:
            case modnaa_protein:
                position = "Prot-N-term";
                break;
            case modn_peptide:
            case modnaa_peptide:
                position = "N-term";
                break;
            default:
                throw new UnsupportedOperationException("Modification type " + modification.getModificationType() + " not supported.");
        }

        // use unimod name if possible
        String cvTermName = modName;
        CvTerm cvTerm = modification.getUnimodCvTerm();
        if (cvTerm != null) {
            cvTermName = cvTerm.getName();
        }

        // set the modification type as fixed or variable
        String modType = "fix";
        if (!fixed) {
            modType = "opt";
        }

        // return the modification as string
        return modification.getRoundedMass() + "," + aminoAcidsAtTarget + "," + modType + "," + position + "," + cvTermName;
    }

    @Override
    public String getType() {
        return "MS-GF+";
    }

    @Override
    public String getCurrentlyProcessedFileName() {
        return spectrumFile.getName();
    }

    /**
     * Tries to map the utilities digestion preferences to the default enzymes
     * supported by MS-GF+. Null if not found.
     *
     * @param digestionPreferences the utilities digestion preferences
     *
     * @return the index of the MS-GF+ enzyme
     * 
     * @throws IOException exception thrown whenever an IO error occurs
     */
    private Integer getEnzymeMapping(DigestionParameters digestionPreferences) throws IOException {

        if (digestionPreferences.getCleavageParameter() == DigestionParameters.CleavageParameter.wholeProtein) {
            return 9;
        } else if (digestionPreferences.getCleavageParameter() == DigestionParameters.CleavageParameter.unSpecific) {
            return 0;
        } else if (digestionPreferences.getEnzymes().size() > 1) {
            throw new IOException("Multiple enzymes not supported by MS-GF+!");
        }

        Enzyme enzyme = digestionPreferences.getEnzymes().get(0);
        return getEnzymeMapping(enzyme);
    }

    /**
     * Tries to map the utilities enzyme to the default enzymes supported by
     * MS-GF+. Null if not found.
     *
     * @param enzyme the utilities enzyme
     *
     * @return the index of the MS-GF+ enzyme
     */
    private Integer getEnzymeMapping(Enzyme enzyme) {

        String enzymeName = enzyme.getName();
        if (enzymeName.equalsIgnoreCase("Trypsin")) {
            return 1;
        }
        if (enzymeName.equalsIgnoreCase("Chymotrypsin")) {
            return 2;
        }
        if (enzymeName.equalsIgnoreCase("Lys-C")) {
            return 3;
        }
        if (enzymeName.equalsIgnoreCase("Lys-N")) {
            return 4;
        }
        if (enzymeName.equalsIgnoreCase("Glu-C")) {
            return 5;
        }
        if (enzymeName.equalsIgnoreCase("Arg-C")) {
            return 6;
        }
        if (enzymeName.equalsIgnoreCase("Asp-N")) {
            return 7;
        } // else if (enzymeName.equalsIgnoreCase("alphaLP")) { // alphaLP: Alpha-lytic protease (aLP) is an alternative specificity protease for proteomics applications.
        //      msgfEnzymeIndex = 8;                        //          cleaves after T, A, S, and V residues. It generates peptides of similar average length as trypsin.
        // };

        return enzymeMap.get(enzyme.getName());
    }
}
